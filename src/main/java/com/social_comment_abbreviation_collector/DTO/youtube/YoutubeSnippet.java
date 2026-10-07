package com.social_comment_abbreviation_collector.DTO.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeSnippet(
        Instant publishedAt,
        String title,
        String channelTitle,
        String channelId
) {
}
