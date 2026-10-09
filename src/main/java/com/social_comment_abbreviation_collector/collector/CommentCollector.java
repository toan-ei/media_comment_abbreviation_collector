package com.social_comment_abbreviation_collector.collector;

import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.entity.Platform;

public interface CommentCollector {
    Platform supports();
    CollectedCommentPage collectPage(String externalContentId, String pageToken);
    CommentCollectionResult collectAll(String externalContentId, CommentBatchHandle handler);
}
