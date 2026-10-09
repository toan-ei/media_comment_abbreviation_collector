package com.social_comment_abbreviation_collector.DTO.response;

public record CommentBatchResult(
        int received,
        int inserted,
        int duplicates
) {
    public static CommentBatchResult empty() {
        return new CommentBatchResult(
                0,
                0,
                0
        );
    }
}
