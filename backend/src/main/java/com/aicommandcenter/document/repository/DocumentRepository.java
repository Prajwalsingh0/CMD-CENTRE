package com.aicommandcenter.document.repository;

import com.aicommandcenter.document.entity.Document;
import com.aicommandcenter.document.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Document> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, DocumentStatus status);

    List<Document> findAllByUserIdAndStatus(Long userId, DocumentStatus status);

    List<Document> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, DocumentStatus status);
}
