package com.social_comment_abbreviation_collector.controller;

import com.social_comment_abbreviation_collector.DTO.response.DiscoveredVideo;
import com.social_comment_abbreviation_collector.discovery.YoutubeDiscoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/youtube")
@RequiredArgsConstructor
@Valid
public class YoutubeDiscoveryController {
    private final YoutubeDiscoveryService discoveryService;

    @GetMapping("/search")
    public List<DiscoveredVideo> search(@RequestParam @NotBlank String keyword,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int maxResults){
        return discoveryService.search(keyword, maxResults);
    }

}
