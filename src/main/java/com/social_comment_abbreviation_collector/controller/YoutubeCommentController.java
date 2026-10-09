package com.social_comment_abbreviation_collector.controller;

import com.social_comment_abbreviation_collector.DTO.response.CollectedCommentPage;
import com.social_comment_abbreviation_collector.collector.youtube.YoutubeCommentCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/youtube/videos")
@RequiredArgsConstructor
public class YoutubeCommentController {
    private final YoutubeCommentCollector youtubeCommentCollector;

    @GetMapping("/{videoId}/comments")
    public CollectedCommentPage getComments(@PathVariable String videoId){
        return youtubeCommentCollector.collectPage(videoId, null);
    }
}
