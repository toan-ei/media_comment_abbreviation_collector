package com.social_comment_abbreviation_collector.integration;

import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadItem;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.collector.DatabaseCommentBatchHandler;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.CrawlJob;
import com.social_comment_abbreviation_collector.entity.CrawlJobStatus;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import com.social_comment_abbreviation_collector.processor.YoutubeCrawlJobProcessor;
import com.social_comment_abbreviation_collector.repository.CrawlJobRepository;
import com.social_comment_abbreviation_collector.service.CrawlJobService;
import com.social_comment_abbreviation_collector.support.AbstractMySqlIntegrationTest;
import com.social_comment_abbreviation_collector.worker.CrawlWorker;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.comments;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.replyPage;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.thread;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.threadPage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Collector → DatabaseCommentBatchHandler → CommentService → MySQL, driven by the real CrawlWorker
 * and CrawlJobService. Only YoutubeClient is mocked.
 */
@EnabledIf("com.social_comment_abbreviation_collector.support.QaTestDatabase#available")
class Day07IdempotencyIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final String VIDEO = "vid-e2e";

    @Autowired
    YoutubeCommentCollector collector;
    @Autowired
    CommentBatchHandle commentBatchHandle;
    @Autowired
    YoutubeCrawlJobProcessor processor;
    @Autowired
    CrawlJobService crawlJobService;
    @Autowired
    CrawlJobRepository crawlJobRepository;

    private CrawlWorker worker;
    private Content content;

    @BeforeEach
    void setUp() {
        reset(youtubeClient);
        worker = new CrawlWorker(crawlJobService, processor);
        content = newContent(VIDEO);
    }

    private CrawlJob createJob() {
        return crawlJobService.createInititalJobs(List.of(content)).getFirst();
    }

    private CrawlJob reload(CrawlJob job) {
        return crawlJobRepository.findById(job.getId()).orElseThrow();
    }

    private Content reloadContent() {
        return contentRepository.findById(content.getId()).orElseThrow();
    }

    /**
     * Page 1: 100 threads; t0 has 2 complete embedded replies, t1 has 150 replies (paginated via comments.list).
     * Page 2: 40 threads. Totals: 140 top-level + 152 replies = 292.
     */
    private void stubMixedVideo() {
        List<YoutubeCommentThreadItem> page1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            if (i == 0) page1.add(thread("t0", 2L, comments("t0.e", 0, 2)));
            else if (i == 1) page1.add(thread("t1", 150L, comments("t1.r", 0, 5)));
            else page1.add(thread("t" + i));
        }
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage(page1, "P2"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("P2"))).thenReturn(threadPage("u", 40, null));
        when(youtubeClient.getReplies("t1", null)).thenReturn(replyPage(comments("t1.r", 0, 100), "R2"));
        when(youtubeClient.getReplies("t1", "R2")).thenReturn(replyPage(comments("t1.r", 100, 150), null));
    }

    @Test
    void handlerBean_isDatabaseHandler() {
        assertThat(commentBatchHandle).isInstanceOf(DatabaseCommentBatchHandler.class);
    }

    @Test
    void collectAll_persistsEverything_andMetricsAreConsistent() {
        stubMixedVideo();

        CommentCollectionResult result = collector.collectAll(VIDEO, ctx(content), commentBatchHandle);

        assertThat(rowCount()).isEqualTo(292);
        assertThat(typeCounts()).containsEntry("TOP_LEVEL", 140L).containsEntry("REPLY", 152L);
        assertThat(result.topLevelComments()).isEqualTo(typeCounts().get("TOP_LEVEL"));
        assertThat(result.replies()).isEqualTo(typeCounts().get("REPLY"));
        assertThat(orphanReplies()).isEmpty();
        assertThat(duplicateRows()).isEmpty();
        assertThat(result.totalComments()).isEqualTo(result.topLevelComments() + result.replies());
        assertThat(result.insertedComments() + result.duplicateComments())
                .as("inserted + duplicates == scanned").isEqualTo(result.totalComments());
        assertThat(result.insertedComments()).isEqualTo(292);
    }

    @Test
    void collectAll_twice_isIdempotent_andSecondRunReportsDuplicates() {
        stubMixedVideo();
        collector.collectAll(VIDEO, ctx(content), commentBatchHandle);

        CommentCollectionResult second = collector.collectAll(VIDEO, ctx(content), commentBatchHandle);

        assertThat(rowCount()).isEqualTo(292);
        assertThat(duplicateRows()).isEmpty();
        assertThat(second.insertedComments()).isZero();
        assertThat(second.duplicateComments()).isEqualTo(292);
    }

    @Test
    void worker_success_done_lastCrawledAtSet_commentsPersisted() {
        stubMixedVideo();
        CrawlJob job = createJob();
        Instant before = Instant.now();

        worker.run();

        SoftAssertions softly = new SoftAssertions();
        CrawlJob after = reload(job);
        softly.assertThat(after.getStatus()).isEqualTo(CrawlJobStatus.DONE);
        softly.assertThat(after.getRetryCount()).isZero();
        softly.assertThat(rowCount()).isEqualTo(292);
        softly.assertThat(reloadContent().getLastCrawledAt()).isNotNull().isAfterOrEqualTo(before.minusSeconds(1));
        softly.assertAll();
    }

    /** Pages 1-3 persisted, page 4 fails → PENDING; retry re-reads 1-3 without duplicating and completes. */
    @Test
    void worker_partialFailure_thenRetry_noDuplicates() {
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("p1-", 100, "T2"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("T2"))).thenReturn(threadPage("p2-", 100, "T3"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("T3"))).thenReturn(threadPage("p3-", 100, "T4"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("T4"))).thenThrow(new RetryableCrawlException("timeout"));
        CrawlJob job = createJob();
        Instant beforeFailure = Instant.now();

        worker.run();

        SoftAssertions softly = new SoftAssertions();
        CrawlJob afterFailure = reload(job);
        softly.assertThat(afterFailure.getStatus()).isEqualTo(CrawlJobStatus.PENDING);
        softly.assertThat(afterFailure.getRetryCount()).isEqualTo(1);
        softly.assertThat(afterFailure.getNextRunAt()).isAfter(beforeFailure);
        softly.assertThat(rowCount()).as("committed batches survive the failure").isEqualTo(300);
        softly.assertThat(reloadContent().getLastCrawledAt()).as("not a completed crawl").isNull();

        // Make the job due now and let page 4 succeed.
        jdbc.update("UPDATE crawl_jobs SET next_run_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), job.getId());
        reset(youtubeClient);
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("p1-", 100, "T2"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("T2"))).thenReturn(threadPage("p2-", 100, "T3"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("T3"))).thenReturn(threadPage("p3-", 100, "T4"));
        when(youtubeClient.getCommentThreads(eq(VIDEO), anyInt(), eq("T4"))).thenReturn(threadPage("p4-", 50, null));

        worker.run();

        CrawlJob afterRetry = reload(job);
        softly.assertThat(afterRetry.getStatus()).isEqualTo(CrawlJobStatus.DONE);
        softly.assertThat(rowCount()).isEqualTo(350);
        softly.assertThat(duplicateRows()).isEmpty();
        softly.assertThat(reloadContent().getLastCrawledAt()).isNotNull();
        softly.assertAll();
    }

    @Test
    void worker_commentsDisabled_skipped_noRows() {
        when(youtubeClient.getCommentThreads(anyString(), anyInt(), any()))
                .thenThrow(new YoutubeApiException(403, "commentsDisabled", "disabled", null));
        CrawlJob job = createJob();

        worker.run();

        CrawlJob after = reload(job);
        assertThat(after.getStatus()).isEqualTo(CrawlJobStatus.SKIPPED);
        assertThat(after.getRetryCount()).isZero();
        assertThat(rowCount()).isZero();
        assertThat(reloadContent().getLastCrawledAt()).isNull();
    }

    @Test
    void worker_videoNotFound_skipped() {
        when(youtubeClient.getCommentThreads(anyString(), anyInt(), any()))
                .thenThrow(new YoutubeApiException(404, "videoNotFound", "gone", null));
        CrawlJob job = createJob();

        worker.run();

        assertThat(reload(job).getStatus()).isEqualTo(CrawlJobStatus.SKIPPED);
        assertThat(rowCount()).isZero();
    }
}
