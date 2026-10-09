package com.social_comment_abbreviation_collector.DTO.response;

import com.social_comment_abbreviation_collector.entity.Platform;

public record CommentBatchContext(
        String contentId,
        Platform platform,
        String externalContentId
) {
}
