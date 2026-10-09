package com.social_comment_abbreviation_collector.DTO.youtube.comment;

import java.util.List;

public record YoutubeCommentThreatResponse(
        String nextPageToken,
        YoutubePageInfo youtubePageInfo,
        List<YoutubeCommentThreatItem> items
) {
}
