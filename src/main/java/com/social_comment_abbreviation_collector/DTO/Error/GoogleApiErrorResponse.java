package com.social_comment_abbreviation_collector.DTO.Error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleApiErrorResponse(
        GoogleApiError error
) {
}
