package com.social_comment_abbreviation_collector.worker;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import com.social_comment_abbreviation_collector.processor.CrawlJobProcessor;
import com.social_comment_abbreviation_collector.service.CrawlJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crawl.worker.enabled", havingValue = "true", matchIfMissing = true)
public class CrawlWorker {
    private final CrawlJobService crawlJobService;
    private final CrawlJobProcessor crawlJobProcessor;


    @Scheduled(fixedDelayString = "${crawl.worker.delay-ms:2000}")
    public void run(){
        Optional<ClaimedCrawlJob> optionalJob = crawlJobService.claimNextJob();

        if (optionalJob.isEmpty()){
            return;
        }

        ClaimedCrawlJob claimedCrawlJob = optionalJob.get();

        log.info(
                "[WORKER] Claimed job={} content={} video={}",
                claimedCrawlJob.jobId(),
                claimedCrawlJob.contentId(),
                claimedCrawlJob.externalId()
        );

        try{
            crawlJobProcessor.process(claimedCrawlJob);
            crawlJobService.markDone(claimedCrawlJob.jobId());

            log.info("[WORKER] Completed job={}", claimedCrawlJob.jobId());
        } catch (Exception exception){
            log.error("[WORKER] Job failed job={}", claimedCrawlJob.jobId(), exception);

            crawlJobService.markFailedAttempt(claimedCrawlJob.jobId(), exception);
        }
    }

}
