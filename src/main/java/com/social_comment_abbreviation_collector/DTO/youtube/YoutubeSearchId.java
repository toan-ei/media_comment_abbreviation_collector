package com.social_comment_abbreviation_collector.DTO.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeSearchId(
        String videoId
) {
}
