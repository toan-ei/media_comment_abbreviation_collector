package com.social_comment_abbreviation_collector.DTO.response;

public record ClaimedCrawlJob(
        String jobId,
        String contentId,
        String platform,
        String externalId,
        String sourceUrl,
        String title
) {
}
