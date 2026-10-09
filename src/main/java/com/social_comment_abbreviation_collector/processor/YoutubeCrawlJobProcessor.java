package com.social_comment_abbreviation_collector.processor;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.entity.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeCrawlJobProcessor implements CrawlJobProcessor{
    private final YoutubeCommentCollector youtubeCommentCollector;
    private final CommentBatchHandle commentBatchHandle;

    @Override
    public void process(ClaimedCrawlJob job) {
        CommentBatchContext context = new CommentBatchContext(job.contentId(), Platform.valueOf(job.platform()), job.externalId());

        CommentCollectionResult result = youtubeCommentCollector.collectAll(job.externalId(), context, commentBatchHandle);

        log.info(
                "[YOUTUBE] job={} video={} scanned={} inserted={} duplicate={} topLevel={} replies={} apiCalls={}",
                job.jobId(),
                job.externalId(),
                result.totalComments(),
                result.insertedComments(),
                result.duplicateComments(),
                result.topLevelComments(),
                result.replies(),
                result.apiCalls()
        );
    }
}
