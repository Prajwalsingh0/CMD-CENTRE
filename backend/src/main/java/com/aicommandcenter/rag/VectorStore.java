package com.aicommandcenter.rag;

import com.aicommandcenter.document.entity.DocumentChunk;

import java.util.Collection;
import java.util.List;

/**
 * Retrieval storage boundary.
 *
 * <p>Today the only implementation is {@link JpaVectorStore}, which stores embeddings as JSON
 * and scans them in memory. That is a deliberate, documented choice: for a personal knowledge
 * base of a few thousand chunks a linear scan costs well under a millisecond and adds no
 * infrastructure. If the corpus ever grows past the point where that is true, a
 * {@code PgVectorStore} or an external vector service can be dropped in behind this interface
 * without a single change in {@link RagService}.</p>
 */
public interface VectorStore {

    /** Ranked retrieval result. */
    record ScoredChunk(DocumentChunk chunk, double score) {
    }

    void saveAll(List<DocumentChunk> chunks);

    void deleteByDocument(Long documentId);

    long countByUser(Long userId);

    List<DocumentChunk> chunksOf(Long userId);

    /**
     * Ranks the user's chunks against a query vector.
     *
     * @param documentIds optional restriction; {@code null} or empty means "the whole knowledge base"
     */
    List<ScoredChunk> search(Long userId, double[] queryVector, int topK, Collection<Long> documentIds);
}
