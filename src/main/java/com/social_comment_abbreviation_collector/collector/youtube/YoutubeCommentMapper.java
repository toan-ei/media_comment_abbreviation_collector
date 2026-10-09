package com.social_comment_abbreviation_collector.collector.youtube;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeComment;
import org.springframework.stereotype.Component;

@Component
public class YoutubeCommentMapper {

    public CollectedComment toTopLevel(YoutubeComment comment){
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
    }

    public CollectedComment toReply(YoutubeComment comment, String parentCommentId) {

        var snippet = comment.snippet();

        return new CollectedComment(
                comment.id(),
                parentCommentId,
                snippet.textDisplay(),
                snippet.authorDisplayName(),
                snippet.likeCount(),
                snippet.publishedAt(),
                snippet.updatedAt()
        );
    }
}
