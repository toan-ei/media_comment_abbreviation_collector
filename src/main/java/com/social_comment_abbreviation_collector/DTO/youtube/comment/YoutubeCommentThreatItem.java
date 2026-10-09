package com.social_comment_abbreviation_collector.DTO.youtube.comment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeCommentThreatItem(
        String id,
        YoutubeCommentThreatSnippet snippet
) {
}
