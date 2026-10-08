package com.social_comment_abbreviation_collector.repository;


import com.social_comment_abbreviation_collector.entity.CrawlJob;
import com.social_comment_abbreviation_collector.entity.CrawlJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, String> {

    boolean existsByJobKey(String jobKey);

    Optional<CrawlJob> findByJobKey(String jobKey);

    List<CrawlJob> findByStatus(CrawlJobStatus status);

    long countByStatus(CrawlJobStatus status);

    @Query(
            value = """
                SELECT *
                FROM crawl_jobs
                WHERE status = 'PENDING'
                  AND next_run_at <= :now
                ORDER BY priority DESC,
                         created_at ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
            nativeQuery = true
    )
    Optional<CrawlJob> findNextPendingJobForUpdate(@Param("now") Instant now);

    List<CrawlJob> findByStatusAndStartedAtBefore(CrawlJobStatus status, Instant threshold);
}