package com.social_comment_abbreviation_collector.service;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import com.social_comment_abbreviation_collector.entity.Platform;
import com.social_comment_abbreviation_collector.repository.CommentIngestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.CTX;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.PUBLISHED;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    private CommentIngestionRepository repository;
    private CommentService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommentIngestionRepository.class);
        service = new CommentService(repository);
    }

    private static CollectedComment c(String id) {
        return new CollectedComment(id, null, "t", "a", 0L, PUBLISHED, UPDATED);
    }

    @SuppressWarnings("unchecked")
    private List<CollectedComment> capturedBatch() {
        ArgumentCaptor<List<CollectedComment>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertIgnoreBatch(eq("content-1"), eq(Platform.YOUTUBE), captor.capture());
        return captor.getValue();
    }

    @Test
    void emptyOrNullBatch_returnsEmpty_andSkipsRepository() {
        assertThat(service.saveBatch(CTX, List.of())).isEqualTo(CommentBatchResult.empty());
        assertThat(service.saveBatch(CTX, null)).isEqualTo(CommentBatchResult.empty());
        verify(repository, never()).insertIgnoreBatch(anyString(), any(), anyList());
    }

    @Test
    void duplicatesWithinBatch_areRemovedBeforeInsert_keepingFirstOccurrence() {
        when(repository.insertIgnoreBatch(anyString(), any(), anyList())).thenReturn(3);

        CommentBatchResult result = service.saveBatch(CTX, List.of(c("A"), c("A"), c("B"), c("C"), c("B")));

        assertThat(capturedBatch()).extracting(CollectedComment::externalCommentId).containsExactly("A", "B", "C");
        assertThat(result).isEqualTo(new CommentBatchResult(5, 3, 2));
    }

    @Test
    void usesContextContentIdAndPlatform() {
        when(repository.insertIgnoreBatch(anyString(), any(), anyList())).thenReturn(1);

        service.saveBatch(CTX, List.of(c("A")));

        assertThat(capturedBatch()).hasSize(1);
    }

    @Test
    void invalidExternalIds_areFilteredOut() {
        when(repository.insertIgnoreBatch(anyString(), any(), anyList())).thenReturn(1);
        List<CollectedComment> batch = new ArrayList<>();
        batch.add(c("ok"));
        batch.add(c(null));
        batch.add(c(""));
        batch.add(c("  "));
        batch.add(null);

        service.saveBatch(CTX, batch);

        assertThat(capturedBatch()).extracting(CollectedComment::externalCommentId).containsExactly("ok");
    }

    /** Skipped invalid rows are reported as "duplicates" because duplicates = received - inserted. */
    @Test
    void invalidExternalIds_areNotReportedAsDuplicates() {
        when(repository.insertIgnoreBatch(anyString(), any(), anyList())).thenReturn(1);
        List<CollectedComment> batch = new ArrayList<>();
        batch.add(c("ok"));
        batch.add(c(null));
        batch.add(c(""));

        CommentBatchResult result = service.saveBatch(CTX, batch);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.duplicates()).isZero();
    }

    @Test
    void saveBatch_isOneShortTransactionPerBatch() throws Exception {
        assertThat(CommentService.class.getMethod("saveBatch",
                com.social_comment_abbreviation_collector.DTO.response.CommentBatchContext.class, List.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
