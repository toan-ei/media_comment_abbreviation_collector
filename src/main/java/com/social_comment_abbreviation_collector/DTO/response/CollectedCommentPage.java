package com.social_comment_abbreviation_collector.DTO.response;

import java.util.List;

public record CollectedCommentPage(
        List<CollectedComment> comments,
        String nextPageToken,
        int totalResults
) {
    public boolean hasNextPage(){
        return nextPageToken != null && !nextPageToken.isBlank();
    }
}
