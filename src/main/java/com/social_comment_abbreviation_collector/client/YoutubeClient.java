package com.social_comment_abbreviation_collector.client;

import com.social_comment_abbreviation_collector.DTO.youtube.YoutubeSearchResponse;
import com.social_comment_abbreviation_collector.config.YoutubeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class YoutubeClient {
    private final RestClient youtubeRestClient;
    private final YoutubeProperties youtubeProperties;


    public YoutubeSearchResponse searchVideos(String keyword, int maxResults){
        return youtubeRestClient.get().uri(uriBuilder -> uriBuilder
                    .path("/search")
                    .queryParam("part", "snippet")
                    .queryParam("q", keyword)
                    .queryParam("type", "video")
                    .queryParam("maxResults", maxResults)
                    .queryParam("key", youtubeProperties.key())
                    .build())
                .retrieve().body(YoutubeSearchResponse.class);
    }

}
