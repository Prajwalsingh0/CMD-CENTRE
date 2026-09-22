package com.aicommandcenter.rag;

import com.aicommandcenter.common.JsonLists;
import com.aicommandcenter.document.entity.DocumentChunk;
import com.aicommandcenter.document.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON-embedded, in-memory cosine scan. Every query is filtered by {@code userId} before it is
 * ranked, so retrieval can never surface another user's content even if a document id leaks.
 */
@Component
public class JpaVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(JpaVectorStore.class);

    private final DocumentChunkRepository chunkRepository;

    public JpaVectorStore(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public void saveAll(List<DocumentChunk> chunks) {
        chunkRepository.saveAll(chunks);
    }

    @Override
    public void deleteByDocument(Long documentId) {
        chunkRepository.deleteAllByDocumentId(documentId);
    }

    @Override
    public long countByUser(Long userId) {
        return chunkRepository.countByUserId(userId);
    }

    @Override
    public List<DocumentChunk> chunksOf(Long userId) {
        return chunkRepository.findAllByUserId(userId);
    }

    @Override
    public List<ScoredChunk> search(Long userId, double[] queryVector, int topK, Collection<Long> documentIds) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        List<DocumentChunk> candidates = (documentIds == null || documentIds.isEmpty())
                ? chunkRepository.findAllByUserId(userId)
                : chunkRepository.findAllByUserIdAndDocumentIdIn(userId, List.copyOf(documentIds));
        return rank(candidates, queryVector, topK);
    }

    /** Exposed for reuse by the reindex path and for unit testing without a database. */
    public List<ScoredChunk> rank(List<DocumentChunk> candidates, double[] queryVector, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        Set<Integer> skippedDimensions = candidates.stream()
                .map(chunk -> JsonLists.readDoubles(chunk.getEmbedding()).length)
                .filter(length -> length != queryVector.length)
                .collect(Collectors.toSet());
        if (!skippedDimensions.isEmpty()) {
            log.warn("Skipped chunks with embedding dimensions {} incompatible with the query ({}); "
                            + "run POST /api/knowledge/reindex after changing AI provider",
                    skippedDimensions, queryVector.length);
        }
        return candidates.stream()
                .map(chunk -> new ScoredChunk(chunk, VectorMath.cosine(queryVector, JsonLists.readDoubles(chunk.getEmbedding()))))
                .filter(scored -> scored.score() > 0.0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(limit)
                .toList();
    }
}
