package com.social_comment_abbreviation_collector.collector;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;

import java.util.List;

public interface CommentBatchHandle {
    CommentBatchResult handle(CommentBatchContext commentBatchContext, List<CollectedComment> comments);
}
