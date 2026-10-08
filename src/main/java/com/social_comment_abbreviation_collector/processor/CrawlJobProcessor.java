package com.social_comment_abbreviation_collector.processor;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;

public interface CrawlJobProcessor {
    void process(ClaimedCrawlJob job);
}
