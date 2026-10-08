package com.social_comment_abbreviation_collector.worker;


import com.social_comment_abbreviation_collector.service.CrawlJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class StuckJobRecoveryScheduler {
    private final CrawlJobService crawlJobService;

    @Scheduled(fixedDelayString = "${crawler.recovery.delay-ms:60000}")
    public void recover() {

        int recovered = crawlJobService.recoverStuckJobs(Duration.ofMinutes(30));

        if (recovered > 0) {
            log.warn("[RECOVERY] Recovered {} stuck crawl jobs", recovered);
        }
    }
}
