package com.social_comment_abbreviation_collector.collector.youtube;


import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreatResponse;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.collector.CommentCollector;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.SkipCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class YoutubeCommentCollector implements CommentCollector {
    private static final int PAGE_SIZE = 100;
    private final YoutubeClient youtubeClient;

    @Override
    public Platform supports() {
        return Platform.YOUTUBE;
    }

    @Override
    public CollectedCommentPage collectPage(String externalContentId, String pageToken) {

        YoutubeCommentThreatResponse response;
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
}
