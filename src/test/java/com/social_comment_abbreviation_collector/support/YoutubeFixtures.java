package com.social_comment_abbreviation_collector.support;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeComment;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentListRepsonse;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentSnippet;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadItem;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadReplies;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadResponse;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadSnippet;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.entity.Platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Test-only builders for YouTube API DTOs.
 * Thread ids are deliberately different from top-level comment ids ("THREAD_" prefix)
 * so tests can detect code that uses the thread id instead of the comment id.
 */
public final class YoutubeFixtures {

    public static final Instant PUBLISHED = Instant.parse("2026-01-02T03:04:05Z");
    public static final Instant UPDATED = Instant.parse("2026-02-03T04:05:06Z");
    public static final CommentBatchContext CTX = new CommentBatchContext("content-1", Platform.YOUTUBE, "video-1");

    private YoutubeFixtures() {
    }

    public static YoutubeComment comment(String id) {
        return new YoutubeComment(id, new YoutubeCommentSnippet(
                "text-" + id, "author-" + id, 7L, PUBLISHED, UPDATED));
    }

    public static YoutubeCommentThreadItem thread(String topLevelId, Long totalReplyCount, List<YoutubeComment> embedded) {
        return new YoutubeCommentThreadItem(
                "THREAD_" + topLevelId,
                new YoutubeCommentThreadSnippet("video-1", comment(topLevelId), totalReplyCount),
                embedded == null ? null : new YoutubeCommentThreadReplies(embedded));
    }

    public static YoutubeCommentThreadItem thread(String topLevelId) {
        return thread(topLevelId, 0L, null);
    }

    public static List<YoutubeComment> comments(String prefix, int from, int toExclusive) {
        return IntStream.range(from, toExclusive).mapToObj(i -> comment(prefix + i)).toList();
    }

    /** Thread page with {@code count} top-level items that have no replies. */
    public static YoutubeCommentThreadResponse threadPage(String prefix, int count, String nextPageToken) {
        List<YoutubeCommentThreadItem> items = IntStream.range(0, count)
                .mapToObj(i -> thread(prefix + i)).toList();
        return new YoutubeCommentThreadResponse(nextPageToken, null, items);
    }

    public static YoutubeCommentThreadResponse threadPage(List<YoutubeCommentThreadItem> items, String nextPageToken) {
        return new YoutubeCommentThreadResponse(nextPageToken, null, items);
    }

    public static YoutubeCommentListRepsonse replyPage(List<YoutubeComment> replies, String nextPageToken) {
        return new YoutubeCommentListRepsonse(nextPageToken, null, replies);
    }

    /** Handler that records every batch it receives and reports every comment as newly inserted. */
    public static final class CapturingHandler implements CommentBatchHandle {
        public final List<List<CollectedComment>> batches = new ArrayList<>();
        public final List<CommentBatchContext> contexts = new ArrayList<>();

        @Override
        public CommentBatchResult handle(CommentBatchContext context, List<CollectedComment> comments) {
            contexts.add(context);
            batches.add(List.copyOf(comments));
            return new CommentBatchResult(comments.size(), comments.size(), 0);
        }

        public List<CollectedComment> all() {
            return batches.stream().flatMap(List::stream).toList();
        }

        public List<Integer> sizes() {
            return batches.stream().map(List::size).toList();
        }
    }
}
