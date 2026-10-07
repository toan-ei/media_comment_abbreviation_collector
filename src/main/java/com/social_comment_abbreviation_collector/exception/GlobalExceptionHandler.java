package com.social_comment_abbreviation_collector.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<?> handleExternalApi(RestClientException exception){
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "timestamp", Instant.now(),
                        "status", 502,
                        "error", "YouTube API request failed"
                ));
    }
}
