package com.social_comment_abbreviation_collector.support;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.repository.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.PUBLISHED;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.UPDATED;

/**
 * Boots the real application context against the isolated QA schema (see {@link QaTestDatabase}).
 * The schema is generated from the production entities (ddl-auto=create), the worker scheduler is disabled
 * and YoutubeClient is mocked, so no test here calls YouTube.
 */
@SpringBootTest
@EnabledIf("com.social_comment_abbreviation_collector.support.QaTestDatabase#available")
public abstract class AbstractMySqlIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        QaTestDatabase.register(registry);
    }

    @MockitoBean
    protected YoutubeClient youtubeClient;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ContentRepository contentRepository;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM comments");
        jdbc.update("DELETE FROM crawl_jobs");
        jdbc.update("DELETE FROM contents");
    }

    protected Content newContent(String videoId) {
        return contentRepository.save(Content.builder()
                .platform(Platform.YOUTUBE)
                .externalId(videoId)
                .title("title " + videoId)
                .sourceUrl("https://www.youtube.com/watch?v=" + videoId)
                .discoveredAt(Instant.now())
                .build());
    }

    protected static CommentBatchContext ctx(Content content) {
        return new CommentBatchContext(content.getId(), Platform.YOUTUBE, content.getExternalId());
    }

    protected static CollectedComment top(String id) {
        return new CollectedComment(id, null, "text-" + id, "author-" + id, 3L, PUBLISHED, UPDATED);
    }

    protected static CollectedComment top(String id, String text) {
        return new CollectedComment(id, null, text, "author-" + id, 3L, PUBLISHED, UPDATED);
    }

    protected static CollectedComment reply(String id, String parentId) {
        return new CollectedComment(id, parentId, "text-" + id, "author-" + id, 1L, PUBLISHED, UPDATED);
    }

    protected long rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comments", Long.class);
    }

    protected List<String> externalIds() {
        return jdbc.queryForList("SELECT external_comment_id FROM comments ORDER BY external_comment_id", String.class);
    }

    protected Map<String, Object> row(String externalId) {
        return jdbc.queryForMap("SELECT * FROM comments WHERE platform = 'YOUTUBE' AND external_comment_id = ?", externalId);
    }

    /** The duplicate query from the Day 7 spec; must always return 0 rows. */
    protected List<Map<String, Object>> duplicateRows() {
        return jdbc.queryForList("""
                SELECT platform, external_comment_id, COUNT(*) AS total
                FROM comments
                GROUP BY platform, external_comment_id
                HAVING COUNT(*) > 1
                """);
    }

    /** Replies whose parent top-level comment is not stored. */
    protected List<Map<String, Object>> orphanReplies() {
        return jdbc.queryForList("""
                SELECT reply.external_comment_id, reply.parent_external_comment_id
                FROM comments reply
                LEFT JOIN comments parent
                  ON parent.platform = reply.platform
                 AND parent.external_comment_id = reply.parent_external_comment_id
                WHERE reply.parent_external_comment_id IS NOT NULL
                  AND parent.id IS NULL
                """);
    }

    protected Map<String, Long> typeCounts() {
        Map<String, Long> counts = new java.util.HashMap<>(Map.of("TOP_LEVEL", 0L, "REPLY", 0L));
        jdbc.query("""
                SELECT CASE WHEN parent_external_comment_id IS NULL THEN 'TOP_LEVEL' ELSE 'REPLY' END AS type, COUNT(*) AS c
                FROM comments GROUP BY type
                """, rs -> {
            counts.put(rs.getString("type"), rs.getLong("c"));
        });
        return counts;
    }
}
