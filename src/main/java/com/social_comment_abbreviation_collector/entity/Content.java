package com.social_comment_abbreviation_collector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "contents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_content_platform_external_id",
                        columnNames = {
                                "platform",
                                "external_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_content_platform",
                        columnList = "platform"
                ),
                @Index(
                        name = "idx_content_discovered_at",
                        columnList = "discovered_at"
                )
        }
)
public class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Platform platform;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "channel_id", length = 100)
    private String channelId;

    @Column(name = "channel_title", length = 255)
    private String channelTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "last_crawled_at")
    private Instant lastCrawledAt;
}
