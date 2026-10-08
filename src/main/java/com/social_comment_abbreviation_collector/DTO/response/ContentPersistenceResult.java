package com.social_comment_abbreviation_collector.DTO.response;

import com.social_comment_abbreviation_collector.entity.Content;

import java.util.List;

public record ContentPersistenceResult(
        int total,
        int inserted,
        int alreadyExists,
        List<Content> insertedContents
) {
}
