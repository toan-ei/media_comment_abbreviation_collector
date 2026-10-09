package com.social_comment_abbreviation_collector.collector;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;

import java.util.List;

public interface CommentBatchHandle {
    void handle(List<CollectedComment> comments);
}
