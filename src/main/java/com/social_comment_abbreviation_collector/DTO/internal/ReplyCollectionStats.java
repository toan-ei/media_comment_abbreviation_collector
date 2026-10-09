package com.social_comment_abbreviation_collector.DTO.internal;

public record ReplyCollectionStats(
        long replyCount,
        long inserted,
        long duplicates,
        int pages,
        int apiCalls
) {
}
