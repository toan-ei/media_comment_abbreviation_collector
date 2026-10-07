package com.social_comment_abbreviation_collector.DTO.youtube;

import java.util.List;

public record YoutubeSearchResponse(
        String nextPageToken,
        List<YoutubeSearchItem> items
) {
}
