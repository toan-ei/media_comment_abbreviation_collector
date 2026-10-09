package com.social_comment_abbreviation_collector.collector;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.response.CommentBatchResult;
import com.social_comment_abbreviation_collector.service.CommentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.CTX;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.PUBLISHED;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseCommentBatchHandlerTest {

    @Test
    void delegatesToCommentService_andReturnsItsResult() {
        CommentService service = mock(CommentService.class);
        List<CollectedComment> batch = List.of(new CollectedComment("A", null, "t", "a", 0L, PUBLISHED, UPDATED));
        CommentBatchResult expected = new CommentBatchResult(1, 1, 0);
        when(service.saveBatch(CTX, batch)).thenReturn(expected);

        CommentBatchResult result = new DatabaseCommentBatchHandler(service).handle(CTX, batch);

        assertThat(result).isSameAs(expected);
        verify(service).saveBatch(CTX, batch);
    }
}
