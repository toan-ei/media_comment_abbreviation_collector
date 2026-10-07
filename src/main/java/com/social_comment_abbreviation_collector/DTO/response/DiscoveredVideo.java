package com.social_comment_abbreviation_collector.DTO.response;

import java.time.Instant;

public record DiscoveredVideo(
        String videoId,
        String title,
        String channelTitle,
        Instant publishedAt,
        String url
) {
}
