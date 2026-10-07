package com.social_comment_abbreviation_collector.DTO.response;

public record DiscoveryResult(
        String platform,
        String keyword,
        int found,
        int inserted,
        int alreadyExists
) {
}
