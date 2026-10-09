package com.social_comment_abbreviation_collector.realapi;

import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.collector.CommentBatchHandle;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.repository.ContentRepository;
import com.social_comment_abbreviation_collector.support.QaTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real YouTube API → real collector → MySQL QA schema, run twice on the same video.
 * Opt-in (quota): {@code .\mvnw.cmd test -Dtest=Day07RealApiPersistenceTest -Dreal-api=true}
 * Needs YOUTUBE_API_KEY and the QA DB credentials. The key is never logged.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "real-api", matches = "true")
@EnabledIfEnvironmentVariable(named = "YOUTUBE_API_KEY", matches = ".+")
@EnabledIf("com.social_comment_abbreviation_collector.support.QaTestDatabase#available")
class Day07RealApiPersistenceTest {

    private static final String VIDEO = System.getProperty("real-api.small-video", "01yqAcaYsL0");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        QaTestDatabase.register(registry);
    }

    @Autowired
    YoutubeCommentCollector collector;
    @Autowired
    CommentBatchHandle handler;
    @Autowired
    ContentRepository contentRepository;
    @Autowired
    JdbcTemplate jdbc;

    private long rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comments", Long.class);
    }

    private String describe(String label, CommentCollectionResult r, Throwable error) {
        return r == null
                ? "[%s] FAILED: %s: %s rows=%d".formatted(label, error.getClass().getSimpleName(), error.getMessage(), rows())
                : "[%s] scanned=%d inserted=%d duplicates=%d topLevel=%d replies=%d apiCalls=%d rows=%d".formatted(label,
                r.totalComments(), r.insertedComments(), r.duplicateComments(), r.topLevelComments(), r.replies(), r.apiCalls(), rows());
    }

    @Test
    void sameVideoTwice_isIdempotent() {
        jdbc.update("DELETE FROM comments");
        jdbc.update("DELETE FROM crawl_jobs");
        jdbc.update("DELETE FROM contents");
        Content content = contentRepository.save(Content.builder().platform(Platform.YOUTUBE).externalId(VIDEO)
                .title("real api qa").sourceUrl("https://www.youtube.com/watch?v=" + VIDEO).discoveredAt(Instant.now()).build());
        CommentBatchContext ctx = new CommentBatchContext(content.getId(), Platform.YOUTUBE, VIDEO);

        CommentCollectionResult first = null, second = null;
        Throwable firstError = null, secondError = null;
        try { first = collector.collectAll(VIDEO, ctx, handler); } catch (RuntimeException e) { firstError = e; }
        long rowsAfterFirst = rows();
        System.out.println(describe("run 1", first, firstError));
        try { second = collector.collectAll(VIDEO, ctx, handler); } catch (RuntimeException e) { secondError = e; }
        long rowsAfterSecond = rows();
        System.out.println(describe("run 2", second, secondError));

        assertThat(firstError).as("run 1 error").isNull();
        assertThat(rowsAfterFirst).as("rows after run 1 == scanned").isEqualTo(first.totalComments());
        assertThat(first.insertedComments()).isEqualTo(first.totalComments());
        assertThat(secondError).as("run 2 error").isNull();
        assertThat(rowsAfterSecond).as("row count unchanged by run 2").isEqualTo(rowsAfterFirst);
        assertThat(second.insertedComments()).isZero();
        assertThat(second.duplicateComments()).isEqualTo(second.totalComments());
    }
}
