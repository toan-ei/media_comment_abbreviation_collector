package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.CrawlJobResponse;
import com.social_comment_abbreviation_collector.DTO.response.CrawlJobStatusReponse;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.CrawlJob;
import com.social_comment_abbreviation_collector.entity.CrawlJobStatus;
import com.social_comment_abbreviation_collector.repository.CrawlJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrawlJobService {
    private final CrawlJobRepository crawlJobRepository;

    public List<CrawlJob> createInititalJobs(List<Content> contents){
        Instant now = Instant.now();

        List<CrawlJob> jobs = contents.stream().map(content -> {
                String jobKey = "INITIAL:" + content.getId();

                return CrawlJob.builder()
                        .jobKey(jobKey)
                        .content(content)
                        .status(CrawlJobStatus.PENDING)
                        .priority(100)
                        .retryCount(0)
                        .maxRetries(5)
                        .nextRunAt(now)
                        .build();
            }
        ).toList();
        return crawlJobRepository.saveAll(jobs);
    }

    public Page<CrawlJobResponse> getAllJobs(Pageable pageable){
        return crawlJobRepository.findAll(pageable).map(CrawlJobResponse::from);
    }

    public CrawlJobStatusReponse checkStatus(){
        long countPending = crawlJobRepository.countByStatus(CrawlJobStatus.PENDING);
        long countProcessing = crawlJobRepository.countByStatus(CrawlJobStatus.PROCESSING);
        long countDone = crawlJobRepository.countByStatus(CrawlJobStatus.DONE);
        long countFailed = crawlJobRepository.countByStatus(CrawlJobStatus.FAILED);

        return new CrawlJobStatusReponse(
                countPending,
                countProcessing,
                countDone,
                countFailed
        );
    }
}
