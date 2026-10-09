package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.CrawlJob;
import com.social_comment_abbreviation_collector.entity.CrawlJobStatus;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.processor.YoutubeCrawlJobProcessor;
import com.social_comment_abbreviation_collector.repository.CrawlJobRepository;
import com.social_comment_abbreviation_collector.worker.CrawlWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlJobServiceTest {

    private CrawlJobRepository repository;
    private CrawlJobService service;
    private CrawlJob job;

    @BeforeEach
    void setUp() {
        repository = mock(CrawlJobRepository.class);
        service = new CrawlJobService(repository);
        Content content = Content.builder().id("content-1").platform(Platform.YOUTUBE)
                .externalId("vid").sourceUrl("https://youtu.be/vid").title("t").build();
        job = CrawlJob.builder().id("job-1").jobKey("INITIAL:content-1").content(content)
                .status(CrawlJobStatus.PENDING).priority(100).retryCount(0).maxRetries(5)
                .nextRunAt(Instant.now()).build();
        when(repository.findById("job-1")).thenReturn(Optional.of(job));
    }

    @Test
    void claim_pendingToProcessing() {
        when(repository.findNextPendingJobForUpdate(any())).thenReturn(Optional.of(job));

        Optional<ClaimedCrawlJob> claimed = service.claimNextJob();

        assertThat(claimed).isPresent();
        assertThat(claimed.get().externalId()).isEqualTo("vid");
        assertThat(job.getStatus()).isEqualTo(CrawlJobStatus.PROCESSING);
        assertThat(job.getStartedAt()).isNotNull();
    }

    @Test
    void markDone_processingToDone() {
        job.setStatus(CrawlJobStatus.PROCESSING);

        service.markDone("job-1");

        assertThat(job.getStatus()).isEqualTo(CrawlJobStatus.DONE);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void markFailedAttempt_processingToPending_retryCountIncremented_nextRunInFuture() {
        job.setStatus(CrawlJobStatus.PROCESSING);
        Instant before = Instant.now();

        service.markFailedAttempt("job-1", new RetryableCrawlException("429"));

        assertThat(job.getStatus()).isEqualTo(CrawlJobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRunAt()).isAfter(before);
        assertThat(job.getLastError()).isEqualTo("429");
    }

    @Test
    void markFailedAttempt_atMaxRetries_becomesFailed() {
        job.setStatus(CrawlJobStatus.PROCESSING);
        job.setRetryCount(4);

        service.markFailedAttempt("job-1", new RetryableCrawlException("still failing"));

        assertThat(job.getStatus()).isEqualTo(CrawlJobStatus.FAILED);
        assertThat(job.getRetryCount()).isEqualTo(5);
    }

    @Test
    void markSkipped_processingToSkipped() {
        job.setStatus(CrawlJobStatus.PROCESSING);

        service.markSkipped("job-1", "Comments are disabled for video");

        assertThat(job.getStatus()).isEqualTo(CrawlJobStatus.SKIPPED);
        assertThat(job.getLastError()).isEqualTo("Comments are disabled for video");
        assertThat(job.getRetryCount()).isZero();
    }

    /** External YouTube crawl must not run inside a DB transaction; only claim/mark* are transactional. */
    @Test
    void transactionBoundaries_shortClaimAndFinish_noTransactionAroundCrawl() throws Exception {
        assertThat(CrawlJobService.class.getMethod("claimNextJob").isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(CrawlJobService.class.getMethod("markDone", String.class).isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(CrawlJobService.class.getMethod("markFailedAttempt", String.class, Exception.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(CrawlJobService.class.getMethod("markSkipped", String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();

        for (Class<?> type : new Class<?>[]{CrawlWorker.class, YoutubeCrawlJobProcessor.class, YoutubeCommentCollector.class}) {
            assertThat(type.isAnnotationPresent(Transactional.class)).as(type.getSimpleName()).isFalse();
            for (Method m : type.getDeclaredMethods()) {
                assertThat(m.isAnnotationPresent(Transactional.class)).as(type.getSimpleName() + "." + m.getName()).isFalse();
            }
        }
        assertThat(Arrays.stream(CrawlJobService.class.getDeclaredFields()).map(f -> f.getType().getSimpleName()))
                .as("service must not call the processor inside its transactions")
                .doesNotContain("CrawlJobProcessor");
    }
}
