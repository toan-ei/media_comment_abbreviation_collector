package com.social_comment_abbreviation_collector.repository;


import com.social_comment_abbreviation_collector.entity.CrawlJob;
import com.social_comment_abbreviation_collector.entity.CrawlJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, String> {

    boolean existsByJobKey(String jobKey);

    Optional<CrawlJob> findByJobKey(String jobKey);

    List<CrawlJob> findByStatus(CrawlJobStatus status);

    long countByStatus(CrawlJobStatus status);
}