package com.aicommandcenter.document.repository;

import com.aicommandcenter.document.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findAllByUserId(Long userId);

    List<DocumentChunk> findAllByUserIdAndDocumentIdIn(Long userId, List<Long> documentIds);

    List<DocumentChunk> findAllByDocumentIdOrderByChunkIndexAsc(Long documentId);

    long countByUserId(Long userId);

    long countByDocumentId(Long documentId);

    void deleteAllByDocumentId(Long documentId);

    @Query("select c.documentId, count(c) from DocumentChunk c where c.documentId in :documentIds group by c.documentId")
    List<Object[]> countGroupedByDocumentId(@Param("documentIds") List<Long> documentIds);
}
