package com.social_comment_abbreviation_collector.DTO.response;

public record ContentSaveResult(
        int total,
        int inserted,
        int alreadyExists
) {
}
