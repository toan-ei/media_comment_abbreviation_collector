package com.social_comment_abbreviation_collector.controller;

import com.social_comment_abbreviation_collector.DTO.response.DiscoveryResult;
import com.social_comment_abbreviation_collector.discovery.YoutubeDiscoveryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
@Validated
public class DiscoveryController {
    private final YoutubeDiscoveryService youtubeDiscoveryService;

    @PostMapping("/youtube")
    public DiscoveryResult discoverYoutube(
            @RequestParam
            @NotBlank
            String keyword,

            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(50)
            int maxResults

    ) {
        return youtubeDiscoveryService.discover(keyword, maxResults);
    }
}
