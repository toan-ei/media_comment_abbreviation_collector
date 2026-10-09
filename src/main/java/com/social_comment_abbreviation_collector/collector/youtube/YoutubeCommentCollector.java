package com.social_comment_abbreviation_collector.collector.youtube;


import com.social_comment_abbreviation_collector.DTO.internal.ReplyCollectionStats;
import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeComment;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentListRepsonse;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadItem;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadResponse;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.collector.CommentCollector;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.SkipCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class YoutubeCommentCollector implements CommentCollector {
    private static final int PAGE_SIZE = 100;
    private final YoutubeClient youtubeClient;
    private final YoutubeCommentMapper youtubeCommentMapper;

    @Override
    public Platform supports() {
        return Platform.YOUTUBE;
    }

    @Override
    public CollectedCommentPage collectPage(String externalContentId, String pageToken) {

        YoutubeCommentThreadResponse response;
        try {
            response = youtubeClient.getCommentThreads(externalContentId, PAGE_SIZE, pageToken);

        } catch (YoutubeApiException exception) {

            throw mapException(exception);
        }

        if (response == null){
            return new CollectedCommentPage(List.of(), null, 0);
        }

        List<CollectedComment> comments = response.items() == null ? List.of()
                : response.items().stream().map(item -> {
                    var comment = item.snippet().topLevelComment();
                    var snippet = comment.snippet();
                    return new CollectedComment(
                            comment.id(),
                            null,
                            snippet.textDisplay(),
                            snippet.authorDisplayName(),
                            snippet.likeCount(),
                            snippet.publishedAt(),
                            snippet.updatedAt()
                    );
        }).toList();
        int totalResults = response.youtubePageInfo() == null ? 0 : response.youtubePageInfo().totalResults();

        return new CollectedCommentPage(
                comments,
                response.nextPageToken(),
                totalResults
        );
    }

    @Override
    public CommentCollectionResult collectAll(String videoId, CommentBatchHandle handler){
        long topLevelCount = 0;
        long replyCount = 0;
        int threadPages = 0;
        int replyPages = 0;
        int apiCalls = 0;
        String pageToken = null;
        Set<String> seenThreadTokens = new HashSet<>();

        do {
            YoutubeCommentThreadResponse response = getThreadPage(videoId, pageToken);

            apiCalls++;
            threadPages++;

            if (response == null || response.items() == null || response.items().isEmpty()) {
                pageToken = response == null ? null : normalizeToken(response.nextPageToken());
                continue;
            }

            List<CollectedComment> topLevels = response.items().stream().map(item ->
                            youtubeCommentMapper.toTopLevel(item.snippet().topLevelComment())).toList();

            handler.handle(topLevels);
            topLevelCount += topLevels.size();

            for (YoutubeCommentThreadItem thread : response.items()) {

                long totalReplies = thread.snippet().totalReplyCount();

                if (totalReplies <= 0) {
                    continue;
                }


                String parentId = thread.snippet().topLevelComment().id();

                List<YoutubeComment> embeddedReplies = getEmbeddedReplies(thread);

                /*
                 * YouTube already returned every reply.
                 */
                if (embeddedReplies.size() == totalReplies) {

                    List<CollectedComment> mapped = embeddedReplies.stream()
                            .map(reply ->
                                    youtubeCommentMapper.toReply(reply, parentId)).toList();

                    handler.handle(mapped);

                    replyCount += mapped.size();
                    continue;
                }
                /*
                 * Embedded replies incomplete.
                 * Ignore them and retrieve ALL replies.
                 */
                ReplyCollectionStats stats = collectAllReplies(parentId, handler);
                replyCount += stats.replyCount();
                replyPages += stats.pages();
                apiCalls += stats.apiCalls();
            }
            pageToken = normalizeToken(response.nextPageToken());

            if (pageToken != null) {
                verifyNewPageToken(pageToken, seenThreadTokens);
            }

        } while (pageToken != null);

        return new CommentCollectionResult(
                topLevelCount,
                replyCount,
                topLevelCount + replyCount,
                threadPages,
                replyPages,
                apiCalls
        );
    }

    private ReplyCollectionStats collectAllReplies(String parentCommentId, CommentBatchHandle handler) {
        String pageToken = null;
        long replyCount = 0;
        int pages = 0;
        int apiCalls = 0;

        Set<String> seenTokens = new HashSet<>();

        do {
            YoutubeCommentListRepsonse response = youtubeClient.getReplies(parentCommentId, pageToken);

            apiCalls++;
            pages++;

            if (response == null) {
                break;
            }

            List<YoutubeComment> replies = response.comments() == null ? List.of() : response.comments();

            if (!replies.isEmpty()) {
                List<CollectedComment> mapped = replies.stream()
                                .map(reply -> youtubeCommentMapper.toReply(reply, parentCommentId))
                                .toList();

                handler.handle(mapped);
                replyCount += mapped.size();
            }


            pageToken = resolveNextToken(response.nextPageToken(), seenTokens);

        } while (pageToken != null);

        return new ReplyCollectionStats(
                replyCount,
                pages,
                apiCalls
        );
    }

    private RuntimeException mapException(YoutubeApiException exception) {

        String reason = exception.getReason();
        if ("commentsDisabled".equals(reason)) {
            return new SkipCrawlException("Comments are disabled for video");
        }
        if ("videoNotFound".equals(reason)) {
            return new SkipCrawlException("YouTube video not found");
        }
        if ("quotaExceeded".equals(reason)) {
            return new RetryableCrawlException("YouTube quota exceeded", exception);
        }
        if (exception.getStatusCode() >= 500) {
            return new RetryableCrawlException("YouTube server error", exception);
        }
        if (exception.getStatusCode() == 429) {
            return new RetryableCrawlException("YouTube rate limit reached", exception);
        }
        return exception;
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token;
    }

    private void verifyNewPageToken(String token, Set<String> seenTokens) {
        if (token == null) {
            return;
        }
        if (!seenTokens.add(token)) {
            throw new IllegalStateException("Repeated YouTube page token detected");
        }
    }

    private String resolveNextToken(String token, Set<String> seenTokens) {
        String normalized = normalizeToken(token);
        if (normalized != null && !seenTokens.add(normalized)) {
            throw new IllegalStateException("Repeated YouTube page token: " + normalized);
        }
        return normalized;
    }

    private List<YoutubeComment> getEmbeddedReplies(YoutubeCommentThreadItem thread) {
        if (thread.replies() == null || thread.replies().comments() == null) {
            return List.of();
        }
        return thread.replies().comments();
    }

    private YoutubeCommentThreadResponse getThreadPage(String videoId, String pageToken) {
        try {
            return youtubeClient.getCommentThreads(
                            videoId,
                            PAGE_SIZE,
                            pageToken
                    );

        } catch (YoutubeApiException exception) {
            throw mapException(exception);
        }
    }

}
