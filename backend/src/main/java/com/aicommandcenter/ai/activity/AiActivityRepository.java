package com.aicommandcenter.ai.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AiActivityRepository extends JpaRepository<AiActivity, Long> {

    List<AiActivity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<AiActivity> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    List<AiActivity> findAllByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, Instant after);

    long countByUserId(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    void deleteAllByUserId(Long userId);
}
