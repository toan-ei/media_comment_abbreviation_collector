package com.social_comment_abbreviation_collector.DTO.youtube.comment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeCommentSnippet(
        String textDisplay,
        String authorDisplayName,
        long likeCount,
        Instant publishedAt,
        Instant updatedAt
) {
}
