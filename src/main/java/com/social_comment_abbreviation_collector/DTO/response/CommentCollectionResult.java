package com.social_comment_abbreviation_collector.DTO.response;

public record CommentCollectionResult(
        long topLevelComments,
        long replies,
        long totalComments,
        long insertedComments,
        long duplicateComments,
        int threadPages,
        int replyPages,
        int apiCalls
) {
}
