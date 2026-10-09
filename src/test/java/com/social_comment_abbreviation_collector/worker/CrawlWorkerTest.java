package com.social_comment_abbreviation_collector.worker;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentMapper;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.SkipCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import com.social_comment_abbreviation_collector.processor.CrawlJobProcessor;
import com.social_comment_abbreviation_collector.processor.YoutubeCrawlJobProcessor;
import com.social_comment_abbreviation_collector.service.CrawlJobService;
import com.social_comment_abbreviation_collector.support.YoutubeFixtures.CapturingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.thread;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.threadPage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CrawlWorkerTest {

    private static final ClaimedCrawlJob JOB =
            new ClaimedCrawlJob("job-1", "content-1", "YOUTUBE", "vid", "https://youtu.be/vid", "title");

    private CrawlJobService service;
    private CrawlJobProcessor processor;
    private CrawlWorker worker;

    @BeforeEach
    void setUp() {
        service = mock(CrawlJobService.class);
        processor = mock(CrawlJobProcessor.class);
        worker = new CrawlWorker(service, processor);
    }

    @Test
    void noJob_doesNothing() {
        when(service.claimNextJob()).thenReturn(Optional.empty());

        worker.run();

        verifyNoInteractions(processor);
    }

    @Test
    void success_claimThenProcessThenMarkDone() {
        when(service.claimNextJob()).thenReturn(Optional.of(JOB));

        worker.run();

        var order = inOrder(service, processor);
        order.verify(service).claimNextJob();
        order.verify(processor).process(JOB);
        order.verify(service).markDone("job-1");
        verify(service, never()).markFailedAttempt(any(), any());
        verify(service, never()).markSkipped(any(), any());
    }

    @Test
    void retryableFailure_marksFailedAttempt_notDone() {
        when(service.claimNextJob()).thenReturn(Optional.of(JOB));
        doThrow(new RetryableCrawlException("429")).when(processor).process(JOB);

        worker.run();

        verify(service).markFailedAttempt(eq("job-1"), isA(RetryableCrawlException.class));
        verify(service, never()).markDone(any());
        verify(service, never()).markSkipped(any(), any());
    }

    @Test
    void skip_marksSkipped_noRetry() {
        when(service.claimNextJob()).thenReturn(Optional.of(JOB));
        doThrow(new SkipCrawlException("Comments are disabled for video")).when(processor).process(JOB);

        worker.run();

        verify(service).markSkipped("job-1", "Comments are disabled for video");
        verify(service, never()).markFailedAttempt(any(), any());
        verify(service, never()).markDone(any());
    }

    @Test
    void unexpectedFailure_marksFailedAttempt() {
        when(service.claimNextJob()).thenReturn(Optional.of(JOB));
        doThrow(new IllegalStateException("Repeated YouTube page token detected")).when(processor).process(JOB);

        worker.run();

        verify(service).markFailedAttempt(eq("job-1"), isA(IllegalStateException.class));
        verify(service, never()).markDone(any());
    }

    /** Page 1 OK, page 2 OK, page 3 fails -> job must go back to retry, never DONE. */
    @Test
    void failureInMiddleOfPagination_jobIsRetriedNotDone() {
        YoutubeClient client = mock(YoutubeClient.class);
        when(client.getCommentThreads(eq("vid"), anyInt(), isNull())).thenReturn(threadPage("p1-", 100, "A"));
        when(client.getCommentThreads(eq("vid"), anyInt(), eq("A"))).thenReturn(threadPage("p2-", 100, "B"));
        when(client.getCommentThreads(eq("vid"), anyInt(), eq("B"))).thenThrow(new RetryableCrawlException("timeout"));
        YoutubeCrawlJobProcessor realProcessor = new YoutubeCrawlJobProcessor(
                new YoutubeCommentCollector(client, new YoutubeCommentMapper()), new CapturingHandler());
        CrawlWorker realWorker = new CrawlWorker(service, realProcessor);
        when(service.claimNextJob()).thenReturn(Optional.of(JOB));

        realWorker.run();

        verify(service).markFailedAttempt(eq("job-1"), isA(RetryableCrawlException.class));
        verify(service, never()).markDone(any());
    }

    /** A 429 coming from items.list (replies) must still lead to a retry. */
    @Test
    void replies429_isStillRetried() {
        YoutubeClient client = mock(YoutubeClient.class);
        when(client.getCommentThreads(anyString(), anyInt(), any())).thenReturn(threadPage(
                List.of(thread("top", 50L, List.of())), null));
        when(client.getReplies(anyString(), any())).thenThrow(new YoutubeApiException(429, "rateLimitExceeded", "slow down", null));
        CrawlWorker realWorker = new CrawlWorker(service, new YoutubeCrawlJobProcessor(
                new YoutubeCommentCollector(client, new YoutubeCommentMapper()), new CapturingHandler()));
        when(service.claimNextJob()).thenReturn(Optional.of(JOB));

        realWorker.run();

        verify(service).markFailedAttempt(eq("job-1"), any());
        verify(service, never()).markDone(any());
        verify(service, never()).markSkipped(any(), any());
    }
}
