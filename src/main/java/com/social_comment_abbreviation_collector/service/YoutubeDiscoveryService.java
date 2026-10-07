package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.DiscoveredVideo;
import com.social_comment_abbreviation_collector.DTO.youtube.YoutubeSearchResponse;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeDiscoveryService {
    private final YoutubeClient youtubeClient;

    public List<DiscoveredVideo> search(String keyword, int maxResults){

        log.info("Searching YouTube videos keyword={}, maxResults={}",
                keyword,
                maxResults);

        YoutubeSearchResponse response = youtubeClient.searchVideos(keyword, maxResults);
        if(response == null || response.items() == null){
            return List.of();
        }
        return response.items().stream().map(item -> new DiscoveredVideo(
                item.id().videoId(),
                item.snippet().title(),
                item.snippet().channelTitle(),
                item.snippet().publishedAt(),"https://www.youtube.com/watch?v=" + item.id().videoId()
        )).toList();
    }
}
