package com.social_comment_abbreviation_collector.exception;

import lombok.Getter;

@Getter
public class YoutubeApiException extends RuntimeException{
    private final int statusCode;
    private final String reason;

    public YoutubeApiException(int statusCode, String reason, String message, Throwable cause) {
        super(message, cause);

        this.statusCode = statusCode;
        this.reason = reason;
    }
}
