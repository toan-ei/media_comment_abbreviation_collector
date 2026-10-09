package com.social_comment_abbreviation_collector.DTO.Error;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleApiError(
        int code,
        String message,
        List<GoogleApiErrorDetail>  errors
) {
}
