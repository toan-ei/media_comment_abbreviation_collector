package com.social_comment_abbreviation_collector.client;

import com.social_comment_abbreviation_collector.DTO.Error.GoogleApiErrorResponse;
import com.social_comment_abbreviation_collector.DTO.youtube.YoutubeSearchResponse;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentListRepsonse;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadResponse;
import com.social_comment_abbreviation_collector.config.YoutubeProperties;
import com.social_comment_abbreviation_collector.exception.RetryableCrawlException;
import com.social_comment_abbreviation_collector.exception.YoutubeApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class YoutubeClient {
    private final RestClient youtubeRestClient;
    private final YoutubeProperties youtubeProperties;
    private final ObjectMapper objectMapper;


    public YoutubeSearchResponse searchVideos(String keyword, int maxResults){
        return youtubeRestClient.get().uri(uriBuilder -> uriBuilder
                    .path("/search")
                    .queryParam("part", "snippet")
                    .queryParam("q", keyword)
                    .queryParam("type", "video")
                    .queryParam("maxResults", maxResults)
                    .queryParam("key", youtubeProperties.key())
                    .build())
                .retrieve().body(YoutubeSearchResponse.class);
    }

    public YoutubeCommentThreadResponse getCommentThreads(String videoId, int maxResults, String pageToken){
        try{
            return youtubeRestClient.get().uri(uriBuilder -> {
                var builder = uriBuilder
                        .path("/commentThreads")
                        .queryParam("part", "snippet,replies")
                        .queryParam("videoId", videoId)
                        .queryParam("maxResults", maxResults)
                        .queryParam("textFormat", "plainText")
                        .queryParam("key", youtubeProperties.key());

                if(pageToken != null && !pageToken.isBlank()){
                    builder.queryParam("pageToken", pageToken);
                }

                return builder.build();
            }).retrieve().body(YoutubeCommentThreadResponse.class);
        } catch (RestClientResponseException e) {
            throw parseYoutubeException(e);

        } catch (RestClientException e) {
            throw new RetryableCrawlException("YouTube request failed", e);
        }


    }

    public YoutubeCommentListRepsonse getReplies(String parentCommentId, String pageToken){
        try{
            return youtubeRestClient.get()
                    .uri(uriBuilder -> {
                        var builder =
                                uriBuilder
                                        .path("/items")
                                        .queryParam("part", "snippet")
                                        .queryParam("parentId", parentCommentId)
                                        .queryParam("maxResults", 100)
                                        .queryParam("textFormat", "plainText")
                                        .queryParam("key", youtubeProperties.key());

                        if (pageToken != null && !pageToken.isBlank()) {
                            builder.queryParam("pageToken", pageToken);
                        }

                        return builder.build();
                    }).retrieve().body(YoutubeCommentListRepsonse.class);
        } catch (RestClientResponseException e){
            throw parseYoutubeException(e);
        } catch (RestClientException e) {
            throw new RetryableCrawlException("Failed to retrieve YouTube replies", e);
        }
    }


    private YoutubeApiException parseYoutubeException(RestClientResponseException exception) {

        String reason = null;
        String message = exception.getMessage();

        try {
            GoogleApiErrorResponse response = objectMapper
                    .readValue(exception.getResponseBodyAsString(), GoogleApiErrorResponse.class);

            if (response.error() != null) {
                message = response.error().message();

                if (response.error().errors() != null && !response.error().errors().isEmpty()) {
                    reason = response.error()
                                    .errors()
                                    .getFirst()
                                    .reason();
                }
            }

        } catch (Exception ignored) {

            // Không parse được error JSON.
            // Vẫn giữ HTTP information.
        }

        return new YoutubeApiException(
                exception.getStatusCode().value(),
                reason,
                message,
                exception
        );
    }
}
