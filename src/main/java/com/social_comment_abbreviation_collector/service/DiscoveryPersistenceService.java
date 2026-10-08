package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.ContentPersistenceResult;
import com.social_comment_abbreviation_collector.DTO.response.DiscoveredVideo;
import com.social_comment_abbreviation_collector.DTO.response.PersistenceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscoveryPersistenceService {
    private final ContentService contentService;
    private final CrawlJobService crawlJobService;

    @Transactional
    public PersistenceResult persistYoutubeDiscovery(List<DiscoveredVideo> videos){
        ContentPersistenceResult contentPersistenceResult = contentService.contentSaveResult(videos);

        int jobsCreated = crawlJobService.createInititalJobs(contentPersistenceResult.insertedContents()).size();

        return new PersistenceResult(
                contentPersistenceResult.total(),
                contentPersistenceResult.inserted(),
                contentPersistenceResult.alreadyExists(),
                jobsCreated
        );
    }
}
