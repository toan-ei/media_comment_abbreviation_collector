package com.social_comment_abbreviation_collector.DTO.youtube.comment;

import java.util.List;

public record YoutubeCommentThreadResponse(
        String nextPageToken,
        YoutubePageInfo youtubePageInfo,
        List<YoutubeCommentThreadItem> items
) {
}
