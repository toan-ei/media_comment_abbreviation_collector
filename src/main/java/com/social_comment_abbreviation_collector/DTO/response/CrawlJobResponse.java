package com.social_comment_abbreviation_collector.DTO.response;

import com.social_comment_abbreviation_collector.entity.CrawlJob;

import java.time.Instant;

public record CrawlJobResponse(
        String id,
        String jobKey,
        String contentId,
        String status,
        Integer priority,
        Integer retryCount,
        Instant nextRunAt
) {
    public static CrawlJobResponse from(CrawlJob job){
        return new CrawlJobResponse(
                job.getId(),
                job.getJobKey(),
                job.getContent().getId(),
                job.getStatus() != null ? job.getStatus().name() : null,
                job.getPriority(),
                job.getRetryCount(),
                job.getNextRunAt()
        );
    }
}
