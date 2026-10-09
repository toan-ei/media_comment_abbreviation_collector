package com.social_comment_abbreviation_collector.repository;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.entity.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CommentIngestionRepository {
    private final JdbcTemplate jdbcTemplate;

    public int insertIgnoreBatch(String contentId, Platform platform, List<CollectedComment> comments){
        if (comments.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT IGNORE INTO comments (
                    content_id,
                    platform,
                    external_comment_id,
                    parent_external_comment_id,
                    text,
                    author_name,
                    like_count,
                    published_at,
                    source_updated_at,
                    crawled_at,
                    has_abbreviation
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Instant crawledAt = Instant.now();

        int[][] results = jdbcTemplate.batchUpdate(sql, comments, comments.size(), (ps, comment) -> {
                            ps.setString(1, contentId);
                            ps.setString(2, platform.name());
                            ps.setString(3, comment.externalCommentId());
                            ps.setString(4, comment.parentExternalCommentId());
                            ps.setString(5, comment.text());
                            ps.setString(6, comment.authorName());
                            ps.setLong(7, comment.likeCount());
                            setTimestamp(ps, 8, comment.publishedAt());
                            setTimestamp(ps, 9, comment.updatedAt());
                            setTimestamp(ps, 10, crawledAt);
                            ps.setBoolean(11, false);
                        }
                );
        int inserted = 0;
        for (int[] batch : results) {
            for (int result : batch) {
                if (result > 0) {
                    inserted += result;
                }
            }
        }

        return inserted;
    }

    private void setTimestamp(java.sql.PreparedStatement ps, int index, Instant value) throws java.sql.SQLException {
        if (value == null) {
            ps.setTimestamp(index, null);
            return;
        }
        ps.setTimestamp(index, Timestamp.from(value));
    }
}
