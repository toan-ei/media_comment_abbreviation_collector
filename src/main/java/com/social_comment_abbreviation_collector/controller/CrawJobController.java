package com.social_comment_abbreviation_collector.controller;

import com.social_comment_abbreviation_collector.DTO.response.CrawlJobResponse;
import com.social_comment_abbreviation_collector.DTO.response.CrawlJobStatusReponse;
import com.social_comment_abbreviation_collector.service.CrawlJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CrawJobController {
    private final CrawlJobService crawlJobService;

    @GetMapping("/crawl-jobs")
    public Page<CrawlJobResponse> getAllJobs(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return crawlJobService.getAllJobs(pageable);
    }

    @GetMapping("/crawl-jobs/status")
    public CrawlJobStatusReponse checkStatus(){
        return crawlJobService.checkStatus();
    }

}
