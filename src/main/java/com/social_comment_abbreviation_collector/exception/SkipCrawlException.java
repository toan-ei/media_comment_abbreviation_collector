package com.social_comment_abbreviation_collector.exception;

public class SkipCrawlException extends RuntimeException{
    public SkipCrawlException(String message){
        super(message);
    }
}
