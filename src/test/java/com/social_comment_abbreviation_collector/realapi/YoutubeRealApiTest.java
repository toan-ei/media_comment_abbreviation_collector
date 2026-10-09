package com.social_comment_abbreviation_collector.realapi;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadItem;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadResponse;
import com.social_comment_abbreviation_collector.client.YoutubeClient;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentMapper;
import com.social_comment_abbreviation_collector.config.YoutubeProperties;
import com.social_comment_abbreviation_collector.support.YoutubeFixtures.CapturingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.CTX;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.replyPage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Calls the real YouTube Data API. Opt-in to protect quota:
 * <pre>.\mvnw.cmd test -Dtest=YoutubeRealApiTest -Dreal-api=true</pre>
 * Requires YOUTUBE_API_KEY in the environment. The key is never logged.
 */
@EnabledIfSystemProperty(named = "real-api", matches = "true")
@EnabledIfEnvironmentVariable(named = "YOUTUBE_API_KEY", matches = ".+")
class YoutubeRealApiTest {

    private static final String BASE = "https://www.googleapis.com/youtube/v3";
    /** ~33 comment threads, some with replies (checked 2026-10-09). */
    private static final String SMALL_VIDEO = System.getProperty("real-api.small-video", "01yqAcaYsL0");
    /** Very large video; the test stops after 2 thread pages and never fetches replies. */
    private static final String LARGE_VIDEO = System.getProperty("real-api.large-video", "dQw4w9WgXcQ");

    private YoutubeClient client;

    static final class StopProbe extends RuntimeException {
        StopProbe() {
            super("probe limit reached");
        }
    }

    @BeforeEach
    void setUp() {
        client = new YoutubeClient(RestClient.builder().baseUrl(BASE).build(),
                new YoutubeProperties(BASE, System.getenv("YOUTUBE_API_KEY")), JsonMapper.builder().build());
    }

    @Test
    void smallVideo_onePage() {
        CommentCollectionResult result = new YoutubeCommentCollector(client, new YoutubeCommentMapper())
                .collectAll(SMALL_VIDEO, CTX, new CapturingHandler());

        assertThat(result.threadPages()).isEqualTo(1);
        assertThat(result.topLevelComments()).isBetween(1L, 100L);
    }

    @Test
    void videoWithReplies_collectsEveryReportedReply() {
        YoutubeCommentThreadResponse firstPage = client.getCommentThreads(SMALL_VIDEO, 100, null);
        long expectedReplies = firstPage.items().stream()
                .map(YoutubeCommentThreadItem::snippet).map(s -> s.totalReplyCount())
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        assertThat(firstPage.nextPageToken()).as("small video must fit in one page").isNull();
        assertThat(expectedReplies).as("video must have replies").isPositive();

        CapturingHandler handler = new CapturingHandler();
        CommentCollectionResult result = new YoutubeCommentCollector(client, new YoutubeCommentMapper())
                .collectAll(SMALL_VIDEO, CTX, handler);

        assertThat(result.replies()).isPositive();
        assertThat(result.replies()).as("replies collected vs. sum(totalReplyCount)").isEqualTo(expectedReplies);
        assertThat(handler.all()).extracting(CollectedComment::externalCommentId).doesNotHaveDuplicates();
    }

    @Test
    void largeVideo_moreThanOneThreadPage_andPage2DiffersFromPage1() {
        YoutubeClient probe = spy(client);
        doReturn(replyPage(List.of(), null)).when(probe).getReplies(anyString(), any());
        AtomicInteger threadCalls = new AtomicInteger();
        doAnswer(inv -> {
            if (threadCalls.incrementAndGet() > 2) throw new StopProbe();
            return inv.callRealMethod();
        }).when(probe).getCommentThreads(anyString(), anyInt(), any());

        CapturingHandler handler = new CapturingHandler();
        Throwable thrown = catchThrowable(() ->
                new YoutubeCommentCollector(probe, new YoutubeCommentMapper()).collectAll(LARGE_VIDEO, CTX, handler));

        assertThat(thrown).as("crawl should reach a 3rd thread page (stopped by probe), not fail earlier")
                .isInstanceOf(StopProbe.class);
        List<List<CollectedComment>> topLevelBatches = handler.batches.stream()
                .filter(b -> b.stream().allMatch(c -> c.parentExternalCommentId() == null)).toList();
        assertThat(topLevelBatches).hasSizeGreaterThanOrEqualTo(2);
        Set<String> page1 = new HashSet<>(topLevelBatches.get(0).stream().map(CollectedComment::externalCommentId).toList());
        assertThat(topLevelBatches.get(1)).extracting(CollectedComment::externalCommentId)
                .as("page 2 must not repeat page 1").noneMatch(page1::contains);
    }
}
