package com.social_comment_abbreviation_collector.DTO.youtube.comment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeCommentListRepsonse(
        String nextPageToken,
        YoutubePageInfo pageInfo,
        List<YoutubeComment> items
) {
}
