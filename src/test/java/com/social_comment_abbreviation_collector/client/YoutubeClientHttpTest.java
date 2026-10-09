package com.social_comment_abbreviation_collector.client;

import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.DTO.response.CommentCollectionResult;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadResponse;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentMapper;
import com.social_comment_abbreviation_collector.config.YoutubeProperties;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.SkipCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import com.social_comment_abbreviation_collector.support.YoutubeFixtures.CapturingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;


import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.CTX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises the real YoutubeClient (URL building + JSON (de)serialization) against a mocked HTTP server.
 * No network, no API key.
 */
class YoutubeClientHttpTest {

    private static final String BASE = "https://yt.test/youtube/v3";

    private MockRestServiceServer server;
    private YoutubeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeClient(builder.build(), new YoutubeProperties(BASE, "test-key"), JsonMapper.builder().build());
    }

    private static String threadJson(String nextPageToken, String... topLevelIds) {
        StringBuilder items = new StringBuilder();
        for (String id : topLevelIds) {
            if (!items.isEmpty()) items.append(',');
            items.append("""
                    {"kind":"youtube#commentThread","id":"%1$s",
                     "snippet":{"videoId":"vid","totalReplyCount":0,
                       "topLevelComment":{"kind":"youtube#comment","id":"%1$s",
                         "snippet":{"textDisplay":"hello %1$s","authorDisplayName":"a","likeCount":2,
                                    "publishedAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}}}}
                    """.formatted(id));
        }
        String next = nextPageToken == null ? "" : "\"nextPageToken\":\"" + nextPageToken + "\",";
        return """
                {"kind":"youtube#commentThreadListResponse",%s
                 "pageInfo":{"totalResults":%d,"resultsPerPage":100},
                 "items":[%s]}
                """.formatted(next, topLevelIds.length, items);
    }

    private static String errorJson(int code, String reason) {
        return """
                {"error":{"code":%d,"message":"msg-%s","errors":[{"message":"m","domain":"youtube","reason":"%s"}]}}
                """.formatted(code, reason, reason);
    }

    @Test
    void getCommentThreads_firstPage_hasNoPageToken() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(queryParam("videoId", "vid"))
                .andExpect(queryParam("part", "snippet,replies"))
                .andExpect(queryParam("maxResults", "100"))
                .andRespond(withSuccess(threadJson(null, "c1"), MediaType.APPLICATION_JSON));

        YoutubeCommentThreadResponse response = client.getCommentThreads("vid", 100, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().snippet().topLevelComment().id()).isEqualTo("c1");
        server.verify();
    }

    /** The page token must be sent as the "pageToken" query parameter, otherwise YouTube always returns page 1. */
    @Test
    void getCommentThreads_sendsPageTokenQueryParam() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andExpect(queryParam("pageToken", "TOKEN_A"))
                .andRespond(withSuccess(threadJson(null, "c2"), MediaType.APPLICATION_JSON));

        client.getCommentThreads("vid", 100, "TOKEN_A");

        server.verify();
    }

    @Test
    void getReplies_sendsParentIdAndPageToken() {
        server.expect(requestTo(startsWith(BASE + "/comments")))
                .andExpect(queryParam("parentId", "top"))
                .andExpect(queryParam("pageToken", "R1"))
                .andRespond(withSuccess("""
                        {"nextPageToken":"R2","items":[]}
                        """, MediaType.APPLICATION_JSON));

        client.getReplies("top", "R1");

        server.verify();
    }

    /**
     * items.list returns its records under "items" (same as every YouTube list endpoint),
     * but YoutubeCommentListRepsonse names the field "items".
     */
    @Test
    void getReplies_deserializesItemsArray() {
        server.expect(requestTo(startsWith(BASE + "/comments")))
                .andRespond(withSuccess("""
                        {"kind":"youtube#commentListResponse","items":[
                          {"kind":"youtube#comment","id":"top.r1","snippet":{"textDisplay":"x","authorDisplayName":"a","likeCount":0,
                            "parentId":"top","publishedAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}}]}
                        """, MediaType.APPLICATION_JSON));

        var response = client.getReplies("top", null);

        assertThat(response.items()).as("replies parsed from JSON 'items'").isNotNull().hasSize(1);
    }

    /** Real items.list responses carry pageInfo WITHOUT totalResults (verified against the live API 2026-10-09). */
    @Test
    void getReplies_pageInfoWithoutTotalResults_isParsed() {
        server.expect(requestTo(startsWith(BASE + "/comments")))
                .andRespond(withSuccess("""
                        {"kind":"youtube#commentListResponse","pageInfo":{"resultsPerPage":100},"items":[]}
                        """, MediaType.APPLICATION_JSON));

        var response = client.getReplies("top", null);

        assertThat(response).isNotNull();
    }

    /**
     * YouTube issues a fresh nextPageToken every time page 1 is requested (verified live).
     * If the token is not actually sent, the crawler re-reads page 1 forever and the repeated-token guard never fires.
     */
    @Test
    void collectAll_overHttp_largeVideo_terminates() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer loopServer = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        YoutubeClient loopClient = new YoutubeClient(builder.build(), new YoutubeProperties(BASE, "test-key"), JsonMapper.builder().build());
        AtomicInteger calls = new AtomicInteger();
        loopServer.expect(ExpectedCount.manyTimes(), requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(request -> {
                    if (calls.incrementAndGet() > 20) throw new AssertionError("INFINITE LOOP: > 20 commentThreads calls");
                    boolean hasToken = request.getURI().getQuery().contains("pageToken=");
                    String body = hasToken ? threadJson(null, "p2a") : threadJson("FRESH_" + calls.get(), "p1a", "p1b");
                    return withSuccess(body, MediaType.APPLICATION_JSON).createResponse(request);
                });
        YoutubeCommentCollector collector = new YoutubeCommentCollector(loopClient, new YoutubeCommentMapper());

        CommentCollectionResult result = collector.collectAll("vid", CTX, new CapturingHandler());

        assertThat(result.threadPages()).isEqualTo(2);
        assertThat(result.topLevelComments()).isEqualTo(3);
    }

    /** commentThreads.list returns "pageInfo"; the DTO field is named "youtubePageInfo". */
    @Test
    void collectPage_reportsTotalResultsFromPageInfo() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withSuccess(threadJson(null, "c1", "c2", "c3"), MediaType.APPLICATION_JSON));
        YoutubeCommentCollector collector = new YoutubeCommentCollector(client, new YoutubeCommentMapper());

        CollectedCommentPage page = collector.collectPage("vid", null);

        assertThat(page.comments()).hasSize(3);
        assertThat(page.totalResults()).isEqualTo(3);
    }

    /** End-to-end over HTTP: page 2 must contain the items of page 2, not page 1 again. */
    @Test
    void collectAll_overHttp_twoPages() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withSuccess(threadJson("TOKEN_A", "p1a", "p1b"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andExpect(queryParam("pageToken", "TOKEN_A"))
                .andRespond(withSuccess(threadJson(null, "p2a"), MediaType.APPLICATION_JSON));
        YoutubeCommentCollector collector = new YoutubeCommentCollector(client, new YoutubeCommentMapper());
        CapturingHandler handler = new CapturingHandler();

        CommentCollectionResult result = collector.collectAll("vid", CTX, handler);

        assertThat(result.topLevelComments()).isEqualTo(3);
        assertThat(result.threadPages()).isEqualTo(2);
        server.verify();
    }

    @Test
    void httpError_parsesGoogleErrorReason() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(403, "commentsDisabled")));

        assertThatThrownBy(() -> client.getCommentThreads("vid", 100, null))
                .isInstanceOfSatisfying(YoutubeApiException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(403);
                    assertThat(e.getReason()).isEqualTo("commentsDisabled");
                });
    }

    @Test
    void commentsDisabled_overHttp_isSkip() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(403, "commentsDisabled")));
        YoutubeCommentCollector collector = new YoutubeCommentCollector(client, new YoutubeCommentMapper());

        assertThatThrownBy(() -> collector.collectAll("vid", CTX, new CapturingHandler())).isInstanceOf(SkipCrawlException.class);
    }

    @Test
    void http429_overHttp_isRetryable() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(429, "rateLimitExceeded")));
        YoutubeCommentCollector collector = new YoutubeCommentCollector(client, new YoutubeCommentMapper());

        assertThatThrownBy(() -> collector.collectAll("vid", CTX, new CapturingHandler())).isInstanceOf(RetryableCrawlException.class);
    }

    @Test
    void http500_withNonJsonBody_isRetryable() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>bad gateway</html>"));
        YoutubeCommentCollector collector = new YoutubeCommentCollector(client, new YoutubeCommentMapper());

        assertThatThrownBy(() -> collector.collectAll("vid", CTX, new CapturingHandler())).isInstanceOf(RetryableCrawlException.class);
    }

    @Test
    void networkTimeout_isRetryable() {
        server.expect(requestTo(startsWith(BASE + "/commentThreads")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.getCommentThreads("vid", 100, null)).isInstanceOf(RetryableCrawlException.class);
    }
}
