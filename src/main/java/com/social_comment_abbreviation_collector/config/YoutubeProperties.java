package com.social_comment_abbreviation_collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube.api")
public record YoutubeProperties (
        String baseUrl,
        String key
) {
}
