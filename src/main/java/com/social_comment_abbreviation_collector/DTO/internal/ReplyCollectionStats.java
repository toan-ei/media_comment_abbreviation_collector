package com.social_comment_abbreviation_collector.DTO.internal;

public record ReplyCollectionStats(
        long replyCount,
        int pages,
        int apiCalls
) {
}
