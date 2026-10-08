package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.DTO.response.CrawlJobResponse;
import com.social_comment_abbreviation_collector.DTO.response.CrawlJobStatusReponse;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.CrawlJob;
import com.social_comment_abbreviation_collector.entity.CrawlJobStatus;
import com.social_comment_abbreviation_collector.processor.CrawlJobProcessor;
import com.social_comment_abbreviation_collector.repository.CrawlJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public Optional<ClaimedCrawlJob> claimNextJob(){
        Instant now = Instant.now();

        Optional<CrawlJob> optionalCrawlJob = crawlJobRepository.findNextPendingJobForUpdate(now);
        if(optionalCrawlJob.isEmpty()){
            return Optional.empty();
        }

        CrawlJob crawlJob = optionalCrawlJob.get();
        crawlJob.setStatus(CrawlJobStatus.PROCESSING);
        crawlJob.setStartedAt(now);
        crawlJob.setFinishedAt(null);
        crawlJob.setLastError(null);

        Content content = crawlJob.getContent();

        ClaimedCrawlJob result = new ClaimedCrawlJob(
                crawlJob.getId(),
                content.getId(),
                content.getPlatform().name(),
                content.getExternalId(),
                content.getSourceUrl(),
                content.getTitle()
        );
        return Optional.of(result);
    }

    @Transactional
    public void markDone(String jobId){
        CrawlJob job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Crawl job not found: " + jobId));
        if(job.getStatus() != CrawlJobStatus.PROCESSING){
            throw new IllegalArgumentException(("cannot mark job done from status: " + job.getStatus()));
        }

        job.setStatus(CrawlJobStatus.DONE);
        job.setFinishedAt(Instant.now());
        job.setLastError(null);
    }

    @Transactional
    public void markFailedAttempt(String jobId, Exception exception){
        CrawlJob job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("crawl job not found"));

        if (job.getStatus() != CrawlJobStatus.PROCESSING){
            return;
        }

        int newRetryCount = job.getRetryCount() + 1;
        job.setRetryCount(newRetryCount);

        job.setLastError(truncateError(exception.getMessage()));

        if (newRetryCount >= job.getMaxRetries()){
            job.setStatus(CrawlJobStatus.FAILED);
            job.setFinishedAt(Instant.now());
            return;
        }

        Duration delay = calculateBackoff(newRetryCount);
        job.setStatus(CrawlJobStatus.PENDING);
        job.setNextRunAt(Instant.now().plus(delay));

        job.setStartedAt(null);
    }

    @Transactional
    public int recoverStuckJobs(Duration timeout){
        Instant threshold = Instant.now().minus(timeout);

        List<CrawlJob> stuckJobs = crawlJobRepository.findByStatusAndStartedAtBefore(CrawlJobStatus.PROCESSING, threshold);

        Instant now = Instant.now();

        for (CrawlJob job : stuckJobs){
            job.setStatus(CrawlJobStatus.PENDING);
            job.setStartedAt(null);
            job.setNextRunAt(now);
            job.setLastError("Recovered stale PROCESSING job");
        }

        return stuckJobs.size();
    }

    private Duration calculateBackoff(int retryCount){
        return switch (retryCount){
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(15);
            default -> Duration.ofHours(1);
        };
    }

    private String truncateError(String message){
        if (message == null) {
            return "Unknown error";
        }

        return message.length() <= 4000 ? message : message.substring(0, 4000);
    }
}
