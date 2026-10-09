package com.social_comment_abbreviation_collector.service;


import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import com.social_comment_abbreviation_collector.repository.CommentIngestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentIngestionRepository ingestionRepository;


    @Transactional
    public CommentBatchResult saveBatch(CommentBatchContext context, List<CollectedComment> comments) {

        if (comments == null || comments.isEmpty()) {
            return CommentBatchResult.empty();
        }

        List<CollectedComment> uniqueComments = deduplicateBatch(comments);

        int inserted = ingestionRepository.insertIgnoreBatch(context.contentId(), context.platform(), uniqueComments);

        int duplicates = comments.size() - inserted;

        return new CommentBatchResult(
                comments.size(),
                inserted,
                duplicates
        );
    }


    private List<CollectedComment> deduplicateBatch(List<CollectedComment> comments) {
        Map<String, CollectedComment> unique = new LinkedHashMap<>();

        for (CollectedComment comment : comments) {

            if (comment == null) {
                continue;
            }

            String externalId = comment.externalCommentId();

            if (externalId == null || externalId.isBlank()) {
                continue;
            }

            unique.putIfAbsent(externalId, comment);
        }
        return new ArrayList<>(
                unique.values()
        );
    }
}
