package com.social_comment_abbreviation_collector.DTO.Error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleApiErrorDetail(
        String reason,
        String message
) {
}
