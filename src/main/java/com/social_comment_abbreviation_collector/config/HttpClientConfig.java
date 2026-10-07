package com.social_comment_abbreviation_collector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient youtubeRestClient(RestClient.Builder builder, YoutubeProperties properties){
        return builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
