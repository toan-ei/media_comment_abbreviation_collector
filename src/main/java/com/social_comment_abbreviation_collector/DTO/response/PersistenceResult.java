package com.social_comment_abbreviation_collector.DTO.response;

public record PersistenceResult(
        int total,
        int inserted,
        int alreadyExists,
        int jobsCreated
) {
}
