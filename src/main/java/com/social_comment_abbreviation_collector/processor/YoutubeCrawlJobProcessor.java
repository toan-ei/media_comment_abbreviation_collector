package com.social_comment_abbreviation_collector.processor;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
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
        CommentCollectionResult result = youtubeCommentCollector.collectAll(job.externalId(), commentBatchHandle);

        log.info(
                "[YOUTUBE] job={} video={} topLevel={} replies={} total={} threadPages={} replyPages={} apiCalls={}",
                job.jobId(),
                job.externalId(),
                result.topLevelComments(),
                result.replies(),
                result.totalComments(),
                result.threadPages(),
                result.replyPages(),
                result.apiCalls()
        );
    }
}
