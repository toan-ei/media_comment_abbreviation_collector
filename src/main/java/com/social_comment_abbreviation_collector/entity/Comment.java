package com.social_comment_abbreviation_collector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "comments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_comment_platform_external_id",
                        columnNames = {
                                "platform",
                                "external_comment_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_comment_content_id",
                        columnList = "content_id"
                ),
                @Index(
                        name = "idx_comment_published_at",
                        columnList = "published_at"
                ),
                @Index(
                        name = "idx_comment_parent_external_id",
                        columnList = "parent_external_comment_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Platform platform;

    @Column(name = "external_comment_id", nullable = false, length = 150)
    private String externalCommentId;

    @Column(name = "parent_external_comment_id", length = 150)
    private String parentExternalCommentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt;

    @Column(name = "has_abbreviation", nullable = false)
    private boolean hasAbbreviation;
}
