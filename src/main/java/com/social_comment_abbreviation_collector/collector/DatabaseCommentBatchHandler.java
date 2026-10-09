package com.social_comment_abbreviation_collector.collector;


import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import com.social_comment_abbreviation_collector.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseCommentBatchHandler implements CommentBatchHandle {

    private final CommentService commentService;

    @Override
    public CommentBatchResult handle(CommentBatchContext context, List<CollectedComment> comments) {
        return commentService.saveBatch(context, comments);
    }
}
