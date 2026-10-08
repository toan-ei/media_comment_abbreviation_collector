package com.social_comment_abbreviation_collector.entity;

import jakarta.persistence.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "crawl_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_crawl_job_key",
                        columnNames = "job_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_crawl_job_status_next_run",
                        columnList = "status,next_run_at"
                ),
                @Index(
                        name = "idx_crawl_job_priority",
                        columnList = "priority"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "job_key", nullable = false, length = 200)
    private String jobKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_crawl_job_content"
            )
    )
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)

    private CrawlJobStatus status;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {

        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = Instant.now();

    }
}
