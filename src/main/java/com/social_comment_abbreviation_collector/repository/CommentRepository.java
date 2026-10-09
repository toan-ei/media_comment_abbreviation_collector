package com.social_comment_abbreviation_collector.repository;


import com.social_comment_abbreviation_collector.entity.Comment;
import com.social_comment_abbreviation_collector.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {

    boolean existsByPlatformAndExternalCommentId(Platform platform, String externalCommentId);

    List<Comment> findAllByPlatformAndExternalCommentIdIn(Platform platform, Collection<String> externalCommentIds);

    long countByContentId(String contentId);
}
