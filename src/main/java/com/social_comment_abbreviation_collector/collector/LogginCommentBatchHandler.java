package com.social_comment_abbreviation_collector.collector;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
//@Component
public class LogginCommentBatchHandler implements CommentBatchHandle{
    private final AtomicLong total = new AtomicLong();
/*
    @Override
    public void handle(List<CollectedComment> comments) {
        long current = total.addAndGet(comments.size());
        log.debug("[COMMENT_BATCH] batchSize={} processed={}", comments.size(), current);
    }
*/
    @Override
    public CommentBatchResult handle(CommentBatchContext commentBatchContext, List<CollectedComment> comments) {
        return null;
    }
}
