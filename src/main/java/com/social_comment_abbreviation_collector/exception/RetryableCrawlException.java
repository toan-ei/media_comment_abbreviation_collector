package com.social_comment_abbreviation_collector.exception;

public class RetryableCrawlException extends RuntimeException{
    public RetryableCrawlException(String message){
        super(message);
    }
    public RetryableCrawlException(String message, Throwable cause){
        super(message,cause);
    }
}
