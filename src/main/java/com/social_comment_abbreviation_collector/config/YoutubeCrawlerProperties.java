package com.social_comment_abbreviation_collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crawler.youtube")
public record YoutubeCrawlerProperties(
        boolean fetchReplies,
        int pageSize
) {
}
