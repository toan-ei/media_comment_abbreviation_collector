package com.social_comment_abbreviation_collector.discovery;

import com.social_comment_abbreviation_collector.DTO.response.ContentSaveResult;
import com.social_comment_abbreviation_collector.DTO.response.DiscoveredVideo;
import com.social_comment_abbreviation_collector.DTO.response.DiscoveryResult;
import com.social_comment_abbreviation_collector.DTO.youtube.YoutubeSearchResponse;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeDiscoveryService {
    private final YoutubeClient youtubeClient;
    private final ContentService contentService;

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
                item.snippet().publishedAt(),"https://www.youtube.com/watch?v=" + item.id().videoId(),
                item.snippet().channelId()
        )).toList();
    }


    public DiscoveryResult discover(String keyword, int maxResults) {
        log.info("Starting YouTube discovery keyword={}", keyword);

        List<DiscoveredVideo> videos = search(keyword, maxResults);

        ContentSaveResult result = contentService.contentSaveResult(videos);

        log.info(
                "YouTube discovery completed keyword={}, found={}, inserted={}, alreadyExists={}",
                keyword,
                result.total(),
                result.inserted(),
                result.alreadyExists()
        );

        return new DiscoveryResult(
                "YOUTUBE",
                keyword,
                result.total(),
                result.inserted(),
                result.alreadyExists()
        );
    }
}
