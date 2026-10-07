package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.ContentSaveResult;
import com.social_comment_abbreviation_collector.DTO.response.DiscoveredVideo;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentService {
    private final ContentRepository contentRepository;

    @Transactional
    public ContentSaveResult contentSaveResult(List<DiscoveredVideo> videos){
        if(videos == null || videos.isEmpty()){
            return new ContentSaveResult(0, 0, 0);
        }

        List<String> externalIds = videos.stream().map(DiscoveredVideo::videoId).distinct().toList();

        Set<String> existingIds = new HashSet<>(
                contentRepository.findAllByPlatformAndExternalIdIn(Platform.YOUTUBE, externalIds)
                        .stream().map(Content::getExternalId).toList()
        );

        Instant discoveredAt = Instant.now();

        List<Content> newContents = videos.stream()
                .filter(video -> !existingIds.contains(video.videoId()))
                .map(video -> Content.builder()
                        .platform(Platform.YOUTUBE)
                        .externalId(video.videoId())
                        .channelId(video.channelId())
                        .channelTitle(video.channelTitle())
                        .title(video.title())
                        .sourceUrl(video.url())
                        .publishedAt(video.publishedAt())
                        .discoveredAt(discoveredAt)
                        .lastCrawledAt(null)
                        .build()
                )
                .toList();

        contentRepository.saveAll(newContents);

        return new ContentSaveResult(
                videos.size(),
                newContents.size(),
                videos.size() - newContents.size()
        );
    }
}
