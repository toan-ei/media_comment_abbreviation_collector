package com.social_comment_abbreviation_collector.collector.youtube;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadItem;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.config.YoutubeCrawlerProperties;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.SkipCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import com.social_comment_abbreviation_collector.support.YoutubeFixtures.CapturingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.CTX;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.comments;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.replyPage;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.thread;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.threadPage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YoutubeCommentCollectorTest {

    private static final String VIDEO = "video-1";
    /** Hard cap so a production infinite loop fails the test instead of hanging the build. */
    private static final int LOOP_GUARD = 20;

    private YoutubeClient client;
    private YoutubeCommentCollector collector;
    private CapturingHandler handler;

    @BeforeEach
    void setUp() {
        client = mock(YoutubeClient.class);
        collector = new YoutubeCommentCollector(client, new YoutubeCommentMapper());
        handler = new CapturingHandler();
    }

    @Nested
    @DisplayName("Top-level pagination")
    class TopLevelPagination {

        @Test
        void onePage_collectsAllTopLevelComments() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("c", 33, null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.topLevelComments()).isEqualTo(33);
            assertThat(result.threadPages()).isEqualTo(1);
            assertThat(result.apiCalls()).isEqualTo(1);
            verify(client, times(1)).getCommentThreads(anyString(), anyInt(), any());
        }

        @Test
        void threePages_100_100_50_stopsWhenNoNextPageToken() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("p1-", 100, "A"));
            when(client.getCommentThreads(eq(VIDEO), anyInt(), eq("A"))).thenReturn(threadPage("p2-", 100, "B"));
            when(client.getCommentThreads(eq(VIDEO), anyInt(), eq("B"))).thenReturn(threadPage("p3-", 50, null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.topLevelComments()).isEqualTo(250);
            assertThat(result.threadPages()).isEqualTo(3);
            assertThat(result.apiCalls()).isEqualTo(3);
            assertThat(handler.all()).extracting(CollectedComment::externalCommentId).doesNotHaveDuplicates();
            verify(client, times(3)).getCommentThreads(anyString(), anyInt(), any());
        }

        @Test
        void requestsPageSize100() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("c", 1, null));

            collector.collectAll(VIDEO, CTX, handler);

            verify(client).getCommentThreads(VIDEO, 100, null);
        }
    }

    @Nested
    @DisplayName("Loop protection")
    class LoopProtection {

        @Test
        void repeatedThreadPageToken_failsFast() {
            AtomicInteger calls = new AtomicInteger();
            when(client.getCommentThreads(anyString(), anyInt(), any())).thenAnswer(inv -> {
                if (calls.incrementAndGet() > LOOP_GUARD) throw new AssertionError("INFINITE LOOP: > " + LOOP_GUARD + " thread calls");
                return threadPage("c" + calls.get() + "-", 10, "ABC");
            });

            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RuntimeException.class);
            assertThat(calls.get()).isEqualTo(2);
        }

        /** Repeated token on pages that contain no items must also be detected. */
        @Test
        void repeatedThreadPageToken_onEmptyPages_failsFast() {
            AtomicInteger calls = new AtomicInteger();
            when(client.getCommentThreads(anyString(), anyInt(), any())).thenAnswer(inv -> {
                if (calls.incrementAndGet() > LOOP_GUARD) throw new AssertionError("INFINITE LOOP: > " + LOOP_GUARD + " thread calls");
                return threadPage(List.of(), "ABC");
            });

            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RuntimeException.class);
            assertThat(calls.get()).isLessThanOrEqualTo(2);
        }

        @Test
        void repeatedReplyPageToken_failsFast() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 500L, comments("e", 0, 5))), null));
            AtomicInteger calls = new AtomicInteger();
            when(client.getReplies(eq("top"), any())).thenAnswer(inv -> {
                if (calls.incrementAndGet() > LOOP_GUARD) throw new AssertionError("INFINITE LOOP: > " + LOOP_GUARD + " reply calls");
                return replyPage(comments("r" + calls.get() + "-", 0, 100), "XYZ");
            });

            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RuntimeException.class);
            assertThat(calls.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Replies")
    class Replies {

        @Test
        void caseA_zeroReplies_getRepliesNotCalled() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("t1", 0L, null), thread("t2", 0L, List.of())), null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.replies()).isZero();
            assertThat(result.replyPages()).isZero();
            verify(client, never()).getReplies(anyString(), any());
        }

        @Test
        void caseB_completeEmbeddedReplies_usedWithoutCommentsList() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 3L, comments("e", 0, 3))), null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.replies()).isEqualTo(3);
            assertThat(result.replyPages()).isZero();
            assertThat(result.apiCalls()).isEqualTo(1);
            assertThat(handler.all()).filteredOn(c -> c.parentExternalCommentId() != null)
                    .extracting(CollectedComment::externalCommentId).containsExactly("e0", "e1", "e2");
            verify(client, never()).getReplies(anyString(), any());
        }

        @Test
        void caseC_incompleteEmbeddedReplies_fetchesAllAndDoesNotDuplicate() {
            // YouTube embeds a subset (r0..r4) of the real 20 replies.
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 20L, comments("r", 0, 5))), null));
            when(client.getReplies("top", null)).thenReturn(replyPage(comments("r", 0, 20), null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            List<CollectedComment> replies = handler.all().stream().filter(c -> c.parentExternalCommentId() != null).toList();
            assertThat(result.replies()).isEqualTo(20);
            assertThat(replies).hasSize(20);
            assertThat(replies).extracting(CollectedComment::externalCommentId).doesNotHaveDuplicates();
            verify(client, times(1)).getReplies("top", null);
        }

        @Test
        void replyPagination_230replies_3pages_4apiCalls() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 230L, comments("r", 0, 5))), null));
            when(client.getReplies("top", null)).thenReturn(replyPage(comments("r", 0, 100), "R1"));
            when(client.getReplies("top", "R1")).thenReturn(replyPage(comments("r", 100, 200), "R2"));
            when(client.getReplies("top", "R2")).thenReturn(replyPage(comments("r", 200, 230), null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.topLevelComments()).isEqualTo(1);
            assertThat(result.replies()).isEqualTo(230);
            assertThat(result.replyPages()).isEqualTo(3);
            assertThat(result.threadPages()).isEqualTo(1);
            assertThat(result.apiCalls()).isEqualTo(4);
            assertThat(handler.all()).extracting(CollectedComment::externalCommentId).doesNotHaveDuplicates();
        }

        @Test
        void replies_haveParentIdOfTopLevelComment_notThreadId() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 7L, comments("e", 0, 2))), null));
            when(client.getReplies(anyString(), any())).thenReturn(replyPage(comments("r", 0, 7), null));

            collector.collectAll(VIDEO, CTX, handler);

            verify(client).getReplies("top", null);
            assertThat(handler.all()).filteredOn(c -> c.parentExternalCommentId() != null)
                    .allSatisfy(c -> assertThat(c.parentExternalCommentId()).isEqualTo("top"));
            assertThat(handler.all()).extracting(CollectedComment::externalCommentId).doesNotContain("THREAD_top");
        }

        /** totalReplyCount is a nullable Long in the DTO; a missing value must not crash the crawl. */
        @Test
        void nullTotalReplyCount_isTreatedAsZero() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", null, null)), null));

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.topLevelComments()).isEqualTo(1);
            assertThat(result.replies()).isZero();
        }
    }

    @Nested
    @DisplayName("Statistics & batching")
    class StatisticsAndBatching {

        private void stubMixedVideo() {
            List<YoutubeCommentThreadItem> page1 = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                if (i == 0) page1.add(thread("a" + i, 2L, comments("a0e", 0, 2)));      // complete embedded
                else if (i == 1) page1.add(thread("a" + i, 150L, comments("a1r", 0, 5))); // paginated replies
                else page1.add(thread("a" + i));
            }
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage(page1, "A"));
            when(client.getCommentThreads(eq(VIDEO), anyInt(), eq("A"))).thenReturn(threadPage("b", 40, null));
            when(client.getReplies("a1", null)).thenReturn(replyPage(comments("a1r", 0, 100), "R1"));
            when(client.getReplies("a1", "R1")).thenReturn(replyPage(comments("a1r", 100, 150), null));
        }

        @Test
        void statistics_areConsistent() {
            stubMixedVideo();

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.topLevelComments()).isEqualTo(140);
            assertThat(result.replies()).isEqualTo(152);
            assertThat(result.totalComments()).isEqualTo(result.topLevelComments() + result.replies());
            assertThat(result.totalComments()).isEqualTo(handler.all().size());
            assertThat(result.threadPages()).isEqualTo(2);
            assertThat(result.replyPages()).isEqualTo(2);
            assertThat(result.apiCalls()).isEqualTo(4);
        }

        /** CapturingHandler reports every comment as inserted, so inserted must equal total (incl. paginated replies). */
        @Test
        void insertedAndDuplicateCounts_includeEveryBatch() {
            stubMixedVideo();

            CommentCollectionResult result = collector.collectAll(VIDEO, CTX, handler);

            assertThat(result.insertedComments()).isEqualTo(result.totalComments());
            assertThat(result.duplicateComments()).isZero();
            assertThat(handler.contexts).isNotEmpty().allMatch(CTX::equals);
        }

        @Test
        void batches_neverExceedPageSize() {
            stubMixedVideo();

            collector.collectAll(VIDEO, CTX, handler);

            assertThat(handler.sizes()).isNotEmpty().allSatisfy(size -> assertThat(size).isBetween(1, 100));
        }

        @Test
        void eachPageIsHandedToHandlerBeforeNextPageIsFetched() {
            CapturingHandler spyHandler = spy(new CapturingHandler());
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("p1-", 100, "A"));
            when(client.getCommentThreads(eq(VIDEO), anyInt(), eq("A"))).thenReturn(threadPage("p2-", 100, null));

            collector.collectAll(VIDEO, CTX, spyHandler);

            InOrder order = inOrder(client, spyHandler);
            order.verify(client).getCommentThreads(VIDEO, 100, null);
            order.verify(spyHandler).handle(any(), any());
            order.verify(client).getCommentThreads(VIDEO, 100, "A");
            order.verify(spyHandler).handle(any(), any());
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        /**
         * application.yaml declares crawler.youtube.fetch-replies / page-size (YoutubeCrawlerProperties),
         * but the collector has no way to receive them, so fetch-replies=false cannot work.
         */
        @Test
        void collectorConsumesYoutubeCrawlerProperties() {
            List<Class<?>> ctorParamTypes = Arrays.stream(YoutubeCommentCollector.class.getDeclaredConstructors())
                    .flatMap(c -> Arrays.stream(c.getParameterTypes())).toList();

            assertThat(ctorParamTypes)
                    .as("YoutubeCommentCollector must depend on YoutubeCrawlerProperties to honour fetch-replies/page-size")
                    .contains(YoutubeCrawlerProperties.class);
        }
    }

    @Nested
    @DisplayName("Error classification")
    class ErrorClassification {

        private void threadsThrow(int status, String reason) {
            when(client.getCommentThreads(anyString(), anyInt(), any()))
                    .thenThrow(new YoutubeApiException(status, reason, "boom", null));
        }

        @Test
        void commentsDisabled_isSkip() {
            threadsThrow(403, "commentsDisabled");
            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(SkipCrawlException.class);
        }

        @Test
        void videoNotFound_isSkip() {
            threadsThrow(404, "videoNotFound");
            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(SkipCrawlException.class);
        }

        @Test
        void http429_isRetryable() {
            threadsThrow(429, "rateLimitExceeded");
            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RetryableCrawlException.class);
        }

        @Test
        void http5xx_isRetryable() {
            threadsThrow(503, "backendError");
            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RetryableCrawlException.class);
        }

        @Test
        void quotaExceeded_isRetryable_notSkip() {
            threadsThrow(403, "quotaExceeded");
            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler))
                    .isInstanceOf(RetryableCrawlException.class)
                    .isNotInstanceOf(SkipCrawlException.class);
        }

        @Test
        void networkFailure_isRetryable() {
            when(client.getCommentThreads(anyString(), anyInt(), any()))
                    .thenThrow(new RetryableCrawlException("YouTube request failed"));
            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RetryableCrawlException.class);
        }

        /** items.list errors must be classified the same way as commentThreads.list errors. */
        @Test
        void replies_http429_isRetryable() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 50L, comments("e", 0, 5))), null));
            when(client.getReplies(anyString(), any()))
                    .thenThrow(new YoutubeApiException(429, "rateLimitExceeded", "boom", null));

            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RetryableCrawlException.class);
        }

        @Test
        void replies_quotaExceeded_isRetryable() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull()))
                    .thenReturn(threadPage(List.of(thread("top", 50L, comments("e", 0, 5))), null));
            when(client.getReplies(anyString(), any()))
                    .thenThrow(new YoutubeApiException(403, "quotaExceeded", "boom", null));

            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RetryableCrawlException.class);
        }

        @Test
        void failureOnThirdPage_propagatesRetryable_afterTwoPagesHandled() {
            when(client.getCommentThreads(eq(VIDEO), anyInt(), isNull())).thenReturn(threadPage("p1-", 100, "A"));
            when(client.getCommentThreads(eq(VIDEO), anyInt(), eq("A"))).thenReturn(threadPage("p2-", 100, "B"));
            when(client.getCommentThreads(eq(VIDEO), anyInt(), eq("B")))
                    .thenThrow(new YoutubeApiException(503, "backendError", "boom", null));

            assertThatThrownBy(() -> collector.collectAll(VIDEO, CTX, handler)).isInstanceOf(RetryableCrawlException.class);
            assertThat(handler.all()).hasSize(200);
        }
    }
}
