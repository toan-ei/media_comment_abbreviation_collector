package com.social_comment_abbreviation_collector.DTO.response;

public record CrawlJobStatusReponse(
        Long pending,
        Long processing,
        Long done,
        Long failed
) {
}
