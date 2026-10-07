package com.social_comment_abbreviation_collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SocialCommentAbbreviationCollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialCommentAbbreviationCollectorApplication.class, args);
	}

}
