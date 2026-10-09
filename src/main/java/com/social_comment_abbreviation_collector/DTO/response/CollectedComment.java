package com.social_comment_abbreviation_collector.DTO.response;

import java.time.Instant;

public record CollectedComment(
        String externalCommentId,
        String parentExternalCommentId,
        String text,
        String authorName,
        Long likeCount,
        Instant publishedAt,
        Instant updatedAt
) {
}
