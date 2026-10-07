package com.social_comment_abbreviation_collector.repository;

import com.social_comment_abbreviation_collector.entity.Content;
import com.social_comment_abbreviation_collector.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, String> {
    Optional<Content> findByPlatformAndExternalId(Platform platform, String externalId);

    boolean existsByPlatformAndExternalId(Platform platform, String externalId);

    List<Content> findAllByPlatformAndExternalIdIn(Platform platform, Collection<String> externalIds);
}
