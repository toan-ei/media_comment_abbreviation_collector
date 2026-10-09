package com.social_comment_abbreviation_collector.collector.youtube;

import com.social_comment_abbreviation_collector.DTO.response.CollectedComment;
import com.social_comment_abbreviation_collector.DTO.youtube.comment.YoutubeCommentThreadItem;
import org.junit.jupiter.api.Test;

import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.PUBLISHED;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.UPDATED;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.comment;
import static com.social_comment_abbreviation_collector.support.YoutubeFixtures.thread;
import static org.assertj.core.api.Assertions.assertThat;

class YoutubeCommentMapperTest {

    private final YoutubeCommentMapper mapper = new YoutubeCommentMapper();

    @Test
    void toTopLevel_mapsAllFields_andUsesCommentIdNotThreadId() {
        YoutubeCommentThreadItem item = thread("UgxTop", 0L, null);

        CollectedComment result = mapper.toTopLevel(item.snippet().topLevelComment());

        assertThat(result.externalCommentId()).isEqualTo("UgxTop").isNotEqualTo(item.id());
        assertThat(result.parentExternalCommentId()).isNull();
        assertThat(result.text()).isEqualTo("text-UgxTop");
        assertThat(result.authorName()).isEqualTo("author-UgxTop");
        assertThat(result.likeCount()).isEqualTo(7L);
        assertThat(result.publishedAt()).isEqualTo(PUBLISHED);
        assertThat(result.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void toReply_setsParentToTopLevelCommentId() {
        CollectedComment result = mapper.toReply(comment("UgxTop.reply1"), "UgxTop");

        assertThat(result.externalCommentId()).isEqualTo("UgxTop.reply1");
        assertThat(result.parentExternalCommentId()).isEqualTo("UgxTop");
        assertThat(result.text()).isEqualTo("text-UgxTop.reply1");
        assertThat(result.authorName()).isEqualTo("author-UgxTop.reply1");
        assertThat(result.likeCount()).isEqualTo(7L);
        assertThat(result.publishedAt()).isEqualTo(PUBLISHED);
        assertThat(result.updatedAt()).isEqualTo(UPDATED);
    }
}
