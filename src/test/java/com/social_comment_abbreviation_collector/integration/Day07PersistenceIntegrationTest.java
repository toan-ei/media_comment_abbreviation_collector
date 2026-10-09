package com.social_comment_abbreviation_collector.integration;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import com.social_comment_abbreviation_collector.entity.Comment;
import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.repository.CommentIngestionRepository;
import com.social_comment_abbreviation_collector.repository.CommentRepository;
import com.social_comment_abbreviation_collector.service.CommentService;
import com.social_comment_abbreviation_collector.support.AbstractMySqlIntegrationTest;
import com.social_comment_abbreviation_collector.support.QaTestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.PUBLISHED;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** CommentService + CommentIngestionRepository against real MySQL (QA schema). */
@EnabledIf("com.social_comment_abbreviation_collector.support.QaTestDatabase#available")
class Day07PersistenceIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    CommentService commentService;
    @Autowired
    CommentIngestionRepository ingestionRepository;
    @Autowired
    CommentRepository commentRepository;

    private static List<CollectedComment> tops(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> top(prefix + i)).toList();
    }

    @Nested
    @DisplayName("Schema")
    class Schema {

        @Test
        void uniqueConstraint_platformExternalCommentId_existsInDatabase() {
            List<String> columns = jdbc.queryForList("""
                    SELECT COLUMN_NAME FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'comments'
                      AND INDEX_NAME = 'uk_comment_platform_external_id' AND NON_UNIQUE = 0
                    ORDER BY SEQ_IN_INDEX
                    """, String.class, QaTestDatabase.SCHEMA);

            assertThat(columns).containsExactly("platform", "external_comment_id");
        }

        @Test
        void foreignKey_commentsContentId_referencesContents() {
            List<String> referenced = jdbc.queryForList("""
                    SELECT REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'comments' AND COLUMN_NAME = 'content_id'
                      AND REFERENCED_TABLE_NAME IS NOT NULL
                    """, String.class, QaTestDatabase.SCHEMA);

            assertThat(referenced).containsExactly("contents");
        }

        @Test
        void textColumn_isUtf8mb4Text() {
            Map<String, Object> col = jdbc.queryForMap("""
                    SELECT DATA_TYPE, CHARACTER_SET_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'comments' AND COLUMN_NAME = 'text'
                    """, QaTestDatabase.SCHEMA);

            assertThat(col.get("DATA_TYPE")).isEqualTo("text");
            assertThat(col.get("CHARACTER_SET_NAME")).isEqualTo("utf8mb4");
        }

        @Test
        void requiredColumnsExist() {
            List<String> columns = jdbc.queryForList("""
                    SELECT COLUMN_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'comments'
                    """, String.class, QaTestDatabase.SCHEMA);

            assertThat(columns).contains("id", "content_id", "platform", "external_comment_id",
                    "parent_external_comment_id", "text", "author_name", "like_count", "published_at",
                    "source_updated_at", "crawled_at", "has_abbreviation");
        }
    }

    @Nested
    @DisplayName("Mapping")
    class Mapping {

        @Test
        void topLevel_allFieldsPersisted() {
            Content content = newContent("vidA");

            CommentBatchResult result = commentService.saveBatch(ctx(content), List.of(top("UgxTop")));

            assertThat(result).isEqualTo(new CommentBatchResult(1, 1, 0));
            Map<String, Object> row = row("UgxTop");
            assertThat(row.get("parent_external_comment_id")).isNull();
            assertThat(row.get("content_id")).isEqualTo(content.getId());
            assertThat(row.get("platform")).isEqualTo("YOUTUBE");
            assertThat(row.get("text")).isEqualTo("text-UgxTop");
            assertThat(row.get("author_name")).isEqualTo("author-UgxTop");
            assertThat(((Number) row.get("like_count")).longValue()).isEqualTo(3L);
            assertThat(row.get("crawled_at")).isNotNull();
            assertThat(row.get("has_abbreviation")).isIn(false, 0, 0L);

            Comment entity = commentRepository.findAll().getFirst();
            assertThat(entity.getPublishedAt()).isEqualTo(PUBLISHED);
            assertThat(entity.getSourceUpdatedAt()).isEqualTo(UPDATED);
            assertThat(entity.isHasAbbreviation()).isFalse();
        }

        /** comments.id is a Hibernate UUID with no DB default; the native INSERT must still give every row a real id. */
        @Test
        void primaryKey_isGeneratedForInsertedRow() {
            Content content = newContent("vidA");

            commentService.saveBatch(ctx(content), List.of(top("UgxTop")));

            assertThat(jdbc.queryForObject("SELECT id FROM comments", String.class))
                    .as("comments.id of a row inserted by CommentIngestionRepository").isNotBlank();
        }

        /** Single-row on purpose: checks reply mapping independently of the multi-row persistence bug. */
        @Test
        void reply_parentIsTopLevelExternalId() {
            Content content = newContent("vidA");

            CommentBatchResult result = commentService.saveBatch(ctx(content), List.of(reply("UgxTop.r1", "UgxTop")));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(row("UgxTop.r1").get("parent_external_comment_id")).isEqualTo("UgxTop");
        }

        /** Top-level + reply of the same thread must both be stored (2 rows). */
        @Test
        void topLevelAndReply_bothPersisted() {
            Content content = newContent("vidA");

            commentService.saveBatch(ctx(content), List.of(top("UgxTop")));
            commentService.saveBatch(ctx(content), List.of(reply("UgxTop.r1", "UgxTop")));

            assertThat(externalIds()).containsExactly("UgxTop", "UgxTop.r1");
            assertThat(orphanReplies()).isEmpty();
        }

        /** Core persistence: a batch of N distinct comments must produce N rows. */
        @Test
        void distinctComments_allPersisted() {
            Content content = newContent("vidA");

            CommentBatchResult result = commentService.saveBatch(ctx(content), List.of(top("A"), top("B"), top("C")));

            assertThat(externalIds()).containsExactly("A", "B", "C");
            assertThat(result.inserted()).isEqualTo(3);
        }

        @Test
        void batchOf100_allPersisted() {
            Content content = newContent("vidA");

            CommentBatchResult result = commentService.saveBatch(ctx(content), tops("c", 100));

            assertThat(rowCount()).isEqualTo(100);
            assertThat(result).isEqualTo(new CommentBatchResult(100, 100, 0));
            assertThat(duplicateRows()).isEmpty();
        }

        @Test
        void nullAuthor_isPersisted() {
            Content content = newContent("vidA");
            CollectedComment c = new CollectedComment("noAuthor", null, "hi", null, 0L, PUBLISHED, UPDATED);

            commentService.saveBatch(ctx(content), List.of(c));

            assertThat(row("noAuthor").get("author_name")).isNull();
        }

        /** One row at a time (table cleared in between) so the check is not masked by the multi-row bug. */
        @Test
        void commentBelongsToItsContent() {
            Content a = newContent("vidA");
            Content b = newContent("vidB");

            commentService.saveBatch(ctx(a), List.of(top("fromA")));
            assertThat(row("fromA").get("content_id")).isEqualTo(a.getId());

            jdbc.update("DELETE FROM comments");
            commentService.saveBatch(ctx(b), List.of(top("fromB")));
            assertThat(row("fromB").get("content_id")).isEqualTo(b.getId());
        }
    }

    @Nested
    @DisplayName("Text integrity")
    class TextIntegrity {

        private static final List<String> UNICODE_TEXTS =
                List.of("t cx thấy đẹp vl 😂😂", "sp này bn tiền?", "đc nha 👍", "こんにちは", "안녕하세요");

        /** One row at a time (table cleared in between): isolates encoding from the multi-row bug. */
        @Test
        void unicodeAndEmoji_roundTrip_singleRow() {
            Content content = newContent("vidA");

            for (int i = 0; i < UNICODE_TEXTS.size(); i++) {
                jdbc.update("DELETE FROM comments");
                commentService.saveBatch(ctx(content), List.of(top("u" + i, UNICODE_TEXTS.get(i))));
                assertThat(row("u" + i).get("text")).isEqualTo(UNICODE_TEXTS.get(i));
            }
        }

        @Test
        void unicodeAndEmoji_roundTrip_oneBatch() {
            Content content = newContent("vidA");
            List<CollectedComment> batch = new ArrayList<>();
            for (int i = 0; i < UNICODE_TEXTS.size(); i++) batch.add(top("u" + i, UNICODE_TEXTS.get(i)));

            commentService.saveBatch(ctx(content), batch);

            assertThat(rowCount()).isEqualTo(UNICODE_TEXTS.size());
            for (int i = 0; i < UNICODE_TEXTS.size(); i++) {
                assertThat(row("u" + i).get("text")).isEqualTo(UNICODE_TEXTS.get(i));
            }
        }

        @Test
        void longText_notTruncated() {
            Content content = newContent("vidA");
            String longText = "Bình luận dài 😂 ".repeat(400); // > 6000 chars

            commentService.saveBatch(ctx(content), List.of(top("long", longText)));

            assertThat(row("long").get("text")).isEqualTo(longText);
        }

        @Test
        void rawText_keptAsIs_andHasAbbreviationFalse() {
            Content content = newContent("vidA");

            commentService.saveBatch(ctx(content), List.of(top("raw1", "SP này OK ko mn!!! 😂")));
            assertThat(row("raw1").get("text")).isEqualTo("SP này OK ko mn!!! 😂");

            jdbc.update("DELETE FROM comments");
            commentService.saveBatch(ctx(content), List.of(top("raw2", "sp này ok ko mn")));
            assertThat(row("raw2").get("has_abbreviation")).isIn(false, 0, 0L);
        }
    }

    @Nested
    @DisplayName("Idempotency & metrics")
    class Idempotency {

        @Test
        void duplicatesWithinBatch_AABCB() {
            Content content = newContent("vidA");

            CommentBatchResult result = commentService.saveBatch(ctx(content),
                    List.of(top("A"), top("A"), top("B"), top("C"), top("B")));

            assertThat(externalIds()).containsExactly("A", "B", "C");
            assertThat(result).isEqualTo(new CommentBatchResult(5, 3, 2));
        }

        @Test
        void duplicatesWithinBatch_AAB_metrics() {
            Content content = newContent("vidA");

            CommentBatchResult result = commentService.saveBatch(ctx(content), List.of(top("A"), top("A"), top("B")));

            assertThat(externalIds()).containsExactly("A", "B");
            assertThat(result).isEqualTo(new CommentBatchResult(3, 2, 1));
        }

        @Test
        void existingRows_AB_thenABCD_addsTwo() {
            Content content = newContent("vidA");
            commentService.saveBatch(ctx(content), List.of(top("A"), top("B")));
            long before = rowCount();

            CommentBatchResult result = commentService.saveBatch(ctx(content), List.of(top("A"), top("B"), top("C"), top("D")));

            assertThat(rowCount() - before).isEqualTo(2);
            assertThat(externalIds()).containsExactly("A", "B", "C", "D");
            assertThat(result).isEqualTo(new CommentBatchResult(4, 2, 2));
        }

        @Test
        void existingRows_AB_thenABC_metrics() {
            Content content = newContent("vidA");
            commentService.saveBatch(ctx(content), List.of(top("A"), top("B")));

            CommentBatchResult result = commentService.saveBatch(ctx(content), List.of(top("A"), top("B"), top("C")));

            assertThat(externalIds()).containsExactly("A", "B", "C");
            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.duplicates()).isEqualTo(2);
        }

        @Test
        void sameBatchTwice_100_staysAt100() {
            Content content = newContent("vidA");
            List<CollectedComment> batch = tops("c", 100);

            CommentBatchResult first = commentService.saveBatch(ctx(content), batch);
            CommentBatchResult second = commentService.saveBatch(ctx(content), batch);

            assertThat(rowCount()).isEqualTo(100);
            assertThat(first.inserted()).isEqualTo(100);
            assertThat(second).isEqualTo(new CommentBatchResult(100, 0, 100));
            assertThat(duplicateRows()).isEmpty();
        }

        @Test
        void sameExternalIdTwice_viaIngestionRepository_oneRow() {
            Content content = newContent("vidA");

            int first = ingestionRepository.insertIgnoreBatch(content.getId(), Platform.YOUTUBE, List.of(top("COMMENT-1")));
            int second = ingestionRepository.insertIgnoreBatch(content.getId(), Platform.YOUTUBE, List.of(top("COMMENT-1")));

            assertThat(rowCount()).isEqualTo(1);
            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
        }

        /** Unique key must reject a duplicate even when the Java dedup / INSERT IGNORE path is bypassed. */
        @Test
        void uniqueConstraint_rejectsDuplicate_onPlainInsert() {
            Content content = newContent("vidA");
            String sql = """
                    INSERT INTO comments (id, content_id, platform, external_comment_id, text, like_count, crawled_at, has_abbreviation)
                    VALUES (?, ?, 'YOUTUBE', 'COMMENT-1', 'x', 0, NOW(6), false)
                    """;
            jdbc.update(sql, "pk-1", content.getId());

            assertThatThrownBy(() -> jdbc.update(sql, "pk-2", content.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(rowCount()).isEqualTo(1);
        }

        /** Only runnable if the platform column accepts a second value; Hibernate maps it as ENUM('YOUTUBE'). */
        @Test
        void sameExternalId_otherPlatform_isAllowedByConstraint() {
            String columnType = jdbc.queryForObject("""
                    SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'comments' AND COLUMN_NAME = 'platform'
                    """, String.class, QaTestDatabase.SCHEMA);
            org.junit.jupiter.api.Assumptions.assumeFalse(columnType.startsWith("enum") && !columnType.contains("'OTHER'"),
                    "N/A: platform column is " + columnType + "; a synthetic second platform cannot be stored");
            Content content = newContent("vidA");
            String sql = """
                    INSERT INTO comments (id, content_id, platform, external_comment_id, text, like_count, crawled_at, has_abbreviation)
                    VALUES (?, ?, ?, 'ABC', 'x', 0, NOW(6), false)
                    """;
            jdbc.update(sql, "pk-1", content.getId(), "YOUTUBE");
            jdbc.update(sql, "pk-2", content.getId(), "OTHER");

            assertThat(rowCount()).isEqualTo(2);
        }

        /** Unique rule is global per platform: the same YouTube comment id under another Content keeps the first owner. */
        @Test
        void sameExternalId_differentContent_keepsFirstOwner() {
            Content a = newContent("vidA");
            Content b = newContent("vidB");
            commentService.saveBatch(ctx(a), List.of(top("shared")));

            CommentBatchResult result = commentService.saveBatch(ctx(b), List.of(top("shared")));

            assertThat(rowCount()).isEqualTo(1);
            assertThat(row("shared").get("content_id")).isEqualTo(a.getId());
            assertThat(result.duplicates()).isEqualTo(1);
        }

        /** Day 7 ignores duplicates; upstream edits are not refreshed (documented limitation). */
        @Test
        void editedComment_isNotRefreshed_knownLimitation() {
            Content content = newContent("vidA");
            commentService.saveBatch(ctx(content), List.of(top("A", "video hay")));

            commentService.saveBatch(ctx(content), List.of(top("A", "video hay vl")));

            assertThat(row("A").get("text")).isEqualTo("video hay");
        }
    }

    @Nested
    @DisplayName("Invalid data / INSERT IGNORE")
    class InvalidData {

        @Test
        void nullOrBlankExternalId_isSkipped_andNotCountedAsDuplicate() {
            Content content = newContent("vidA");
            List<CollectedComment> batch = new ArrayList<>();
            batch.add(top("valid"));
            batch.add(top(""));
            batch.add(top("   "));
            batch.add(new CollectedComment(null, null, "no id", "a", 0L, PUBLISHED, UPDATED));

            CommentBatchResult result = commentService.saveBatch(ctx(content), batch);

            assertThat(externalIds()).containsExactly("valid");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM comments WHERE external_comment_id IS NULL OR TRIM(external_comment_id) = ''",
                    Long.class)).isZero();
            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.duplicates()).as("invalid rows are not duplicates").isZero();
        }

        /** Evidence: the warnings MySQL raises (and INSERT IGNORE hides) when CommentIngestionRepository's INSERT runs. */
        @Test
        void productionInsertStatement_raisesNoHiddenWarnings() {
            Content content = newContent("vidA");

            List<String> warnings = jdbc.execute((java.sql.Connection con) -> {
                List<String> found = new ArrayList<>();
                try (var ps = con.prepareStatement("""
                        INSERT IGNORE INTO comments (content_id, platform, external_comment_id, parent_external_comment_id,
                            text, author_name, like_count, published_at, source_updated_at, crawled_at, has_abbreviation)
                        VALUES (?, 'YOUTUBE', 'W1', NULL, 'x', 'a', 0, NOW(6), NOW(6), NOW(6), false)
                        """)) {
                    ps.setString(1, content.getId());
                    ps.executeUpdate();
                }
                try (var st = con.createStatement(); var rs = st.executeQuery("SHOW WARNINGS")) {
                    while (rs.next()) found.add(rs.getInt("Code") + " " + rs.getString("Message"));
                }
                return found;
            });

            assertThat(warnings).as("MySQL warnings swallowed by INSERT IGNORE").isEmpty();
        }

        /** Comments for a Content that does not exist must fail loudly, not vanish as "duplicates". */
        @Test
        void unknownContentId_failsLoudly() {
            CommentBatchContext ghost = new CommentBatchContext("no-such-content", Platform.YOUTUBE, "ghost");

            Throwable thrown = catchThrowable(() -> commentService.saveBatch(ghost, List.of(top("orphan"))));

            assertThat(rowCount()).isZero();
            assertThat(thrown).as("FK violation must surface as an error, not be swallowed by INSERT IGNORE").isNotNull();
        }

        /** NULL text in a NOT NULL column must not be silently stored as '' and reported as inserted. */
        @Test
        void nullText_isNotSilentlyCoercedToEmpty() {
            Content content = newContent("vidA");
            CollectedComment c = new CollectedComment("nullText", null, null, "a", 0L, PUBLISHED, UPDATED);

            catchThrowable(() -> commentService.saveBatch(ctx(content), List.of(c)));

            List<String> stored = jdbc.queryForList(
                    "SELECT text FROM comments WHERE external_comment_id = 'nullText'", String.class);
            assertThat(stored).as("NULL text silently stored as empty string").doesNotContain("");
        }

        /** external_comment_id is VARCHAR(150); a longer id must not be silently truncated (collision risk). */
        @Test
        void overlongExternalId_isNotSilentlyTruncated() {
            Content content = newContent("vidA");
            String prefix = "x".repeat(150);

            catchThrowable(() -> commentService.saveBatch(ctx(content), List.of(top(prefix + "-one"), top(prefix + "-two"))));

            assertThat(jdbc.queryForList("SELECT external_comment_id FROM comments", String.class))
                    .as("ids stored truncated to 150 chars").doesNotContain(prefix);
        }
    }
}
