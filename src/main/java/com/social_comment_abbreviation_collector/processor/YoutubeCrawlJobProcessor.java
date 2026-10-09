package com.social_comment_abbreviation_collector.processor;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeCrawlJobProcessor implements CrawlJobProcessor{
    private final YoutubeCommentCollector youtubeCommentCollector;

    @Override
    public void process(ClaimedCrawlJob job) {
        CollectedCommentPage page = youtubeCommentCollector.collectPage(job.externalId(), null);

        log.info("[YOUTUBE] job={} video={} fetched={} totalResults={} hasNextPage={}",
                job.jobId(),
                job.externalId(),
                page.comments().size(),
                page.totalResults(),
                page.hasNextPage()
        );
    }
}
