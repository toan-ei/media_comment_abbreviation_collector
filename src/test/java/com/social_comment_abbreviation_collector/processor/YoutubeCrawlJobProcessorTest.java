package com.social_comment_abbreviation_collector.processor;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YoutubeCrawlJobProcessorTest {

    private static final ClaimedCrawlJob JOB =
            new ClaimedCrawlJob("job-1", "content-42", "YOUTUBE", "vid-9", "https://youtu.be/vid-9", "t");

    @Test
    void buildsContextFromJob_andPassesHandler() {
        YoutubeCommentCollector collector = mock(YoutubeCommentCollector.class);
        CommentBatchHandle handler = mock(CommentBatchHandle.class);
        when(collector.collectAll(any(), any(), any())).thenReturn(new CommentCollectionResult(1, 0, 1, 1, 0, 1, 0, 1));

        new YoutubeCrawlJobProcessor(collector, handler).process(JOB);

        verify(collector).collectAll(eq("vid-9"),
                eq(new CommentBatchContext("content-42", Platform.YOUTUBE, "vid-9")), same(handler));
    }

    @Test
    void collectorFailure_propagatesToWorker() {
        YoutubeCommentCollector collector = mock(YoutubeCommentCollector.class);
        when(collector.collectAll(any(), any(), any())).thenThrow(new RetryableCrawlException("boom"));

        assertThatThrownBy(() -> new YoutubeCrawlJobProcessor(collector, mock(CommentBatchHandle.class)).process(JOB))
                .isInstanceOf(RetryableCrawlException.class);
    }
}
