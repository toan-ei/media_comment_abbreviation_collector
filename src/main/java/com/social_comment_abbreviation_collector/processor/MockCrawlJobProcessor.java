package com.social_comment_abbreviation_collector.processor;

import com.social_comment_abbreviation_collector.DTO.response.ClaimedCrawlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockCrawlJobProcessor implements CrawlJobProcessor {
    @Override
    public void process(ClaimedCrawlJob job){
        log.info("[PROCESSOR] Mock processing job={}, video={}", job.jobId(), job.externalId());

        try{
            Thread.sleep(2000);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();

            throw new RuntimeException("Worker interrupted", e);
        }

    }

}
