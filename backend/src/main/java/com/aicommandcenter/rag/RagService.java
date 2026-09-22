package com.aicommandcenter.rag;

import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.common.JsonLists;
import com.aicommandcenter.document.entity.Document;
import com.aicommandcenter.document.entity.DocumentChunk;
import com.aicommandcenter.document.repository.DocumentRepository;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.rag.dto.AnswerResponse;
import com.aicommandcenter.rag.dto.AnswerSource;
import com.aicommandcenter.rag.dto.KnowledgeAskRequest;
import com.aicommandcenter.rag.dto.ReindexResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Retrieval-augmented question answering over the caller's own documents.
 *
 * <p>The three properties that matter: retrieval is always scoped to the authenticated user;
 * the answer is never produced without its sources travelling alongside it; and when nothing is
 * retrieved the service says so instead of letting a model improvise.</p>
 */
@Service
public class RagService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 12;
    private static final int MAX_CONTEXT_CHARS = 8_000;
    private static final int EXCERPT_CHARS = 280;
    private static final int REINDEX_BATCH = 32;

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final AiService aiService;
    private final AiActivityService activityService;

    public RagService(VectorStore vectorStore,
                      DocumentRepository documentRepository,
                      AiService aiService,
                      AiActivityService activityService) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.aiService = aiService;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public AnswerResponse ask(Long userId, KnowledgeAskRequest request) {
        List<Long> scope = resolveScope(userId, request.documentIds());
        int topK = clamp(request.topK());
        double[] queryVector = embedOne(request.question());
        List<VectorStore.ScoredChunk> hits = vectorStore.search(userId, queryVector, topK, scope);
        Map<Long, String> names = documentNames(userId, hits);

        List<AnswerSource> sources = hits.stream()
                .map(hit -> new AnswerSource(
                        hit.chunk().getDocumentId(),
                        names.getOrDefault(hit.chunk().getDocumentId(), "document"),
                        hit.chunk().getChunkIndex(),
                        round(hit.score()),
                        excerpt(hit.chunk().getContent())))
                .toList();

        String answer;
        boolean grounded = !sources.isEmpty();
        if (grounded) {
            String prompt = buildPrompt(request.question(), hits, names);
            answer = aiService.complete(AiRequest.of(AiTask.ANSWER,
                    "You answer questions about documents the user owns. Use ONLY the numbered sources. "
                            + "Cite each claim with its [n] marker. If the sources do not contain the answer, "
                            + "state plainly that it was not found in the documents.",
                    prompt)).text();
        } else {
            answer = emptyKnowledgeBaseMessage(userId);
        }

        activityService.record(userId, request.question(), "ASK_KNOWLEDGE_BASE",
                AiActivityService.ActivityStatus.SUCCESS,
                grounded
                        ? "Answered from " + sources.size() + " passage(s) across "
                        + sources.stream().map(AnswerSource::documentName).distinct().count() + " document(s)."
                        : "No matching passages found in the knowledge base.");

        return new AnswerResponse(request.question(), answer, sources, grounded,
                aiService.providerName(), aiService.isRemote());
    }

    /**
     * Re-embeds every stored chunk with the currently configured provider. This is the supported
     * way to move an existing knowledge base between providers with different vector dimensions.
     */
    @Transactional
    public ReindexResponse reindex(Long userId) {
        List<DocumentChunk> chunks = vectorStore.chunksOf(userId);
        if (chunks.isEmpty()) {
            return new ReindexResponse(0, 0, aiService.providerName());
        }
        for (int start = 0; start < chunks.size(); start += REINDEX_BATCH) {
            List<DocumentChunk> batch = chunks.subList(start, Math.min(chunks.size(), start + REINDEX_BATCH));
            List<double[]> vectors = aiService.embed(batch.stream().map(DocumentChunk::getContent).toList());
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setEmbedding(JsonLists.writeDoubles(vectors.get(i)));
            }
            vectorStore.saveAll(batch);
        }
        long documents = chunks.stream().map(DocumentChunk::getDocumentId).distinct().count();
        activityService.record(userId, "Reindex knowledge base", "REINDEX_KNOWLEDGE_BASE",
                AiActivityService.ActivityStatus.SUCCESS,
                "Re-embedded " + chunks.size() + " passage(s) with the " + aiService.providerName() + " provider.");
        return new ReindexResponse((int) documents, chunks.size(), aiService.providerName());
    }

    private String emptyKnowledgeBaseMessage(Long userId) {
        boolean hasDocuments = documentRepository.countByUserId(userId) > 0;
        return hasDocuments
                ? "I could not find anything in your documents that answers that question. "
                + "Try different wording, or upload a document that covers the topic."
                : "Your knowledge base is empty. Upload a PDF, TXT or Markdown document first, "
                + "then ask your question again.";
    }

    private String buildPrompt(String question, List<VectorStore.ScoredChunk> hits, Map<Long, String> names) {
        StringBuilder builder = new StringBuilder("SOURCES:\n");
        int index = 0;
        for (VectorStore.ScoredChunk hit : hits) {
            if (builder.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            index++;
            builder.append('[').append(index).append("] ")
                    .append(names.getOrDefault(hit.chunk().getDocumentId(), "document"))
                    .append(" (part ").append(hit.chunk().getChunkIndex() + 1).append(")\n")
                    .append(hit.chunk().getContent()).append("\n\n");
        }
        builder.append("QUESTION:\n").append(question);
        return builder.toString();
    }

    private Map<Long, String> documentNames(Long userId, List<VectorStore.ScoredChunk> hits) {
        List<Long> ids = hits.stream().map(hit -> hit.chunk().getDocumentId()).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        documentRepository.findAllById(ids).stream()
                .filter(document -> document.getUserId().equals(userId))
                .forEach(document -> names.put(document.getId(), document.getName()));
        return names;
    }

    /** Rejects ids the caller does not own instead of silently ignoring them. */
    private List<Long> resolveScope(Long userId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = requestedIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Document> owned = documentRepository.findAllById(distinct).stream()
                .filter(document -> document.getUserId().equals(userId))
                .toList();
        if (owned.size() != distinct.size()) {
            Long unknown = distinct.stream().filter(id -> owned.stream().noneMatch(d -> d.getId().equals(id)))
                    .findFirst().orElse(distinct.get(0));
            throw new ResourceNotFoundException("Document", unknown);
        }
        return distinct;
    }

    private double[] embedOne(String text) {
        List<double[]> vectors = aiService.embed(List.of(text));
        if (vectors.isEmpty()) {
            throw com.aicommandcenter.ai.AiException.invalidResponse(aiService.providerName());
        }
        return vectors.get(0);
    }

    private static int clamp(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.min(Math.max(1, topK), MAX_TOP_K);
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static String excerpt(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= EXCERPT_CHARS ? flat : flat.substring(0, EXCERPT_CHARS) + "…";
    }

    /** Kept for symmetry with the ingestion path and used by tests. */
    public static List<DocumentChunk> sortedByIndex(List<DocumentChunk> chunks) {
        List<DocumentChunk> copy = new ArrayList<>(chunks);
        copy.sort(Comparator.comparingInt(DocumentChunk::getChunkIndex));
        return copy;
    }

    public static String joinForDisplay(List<DocumentChunk> chunks) {
        return sortedByIndex(chunks).stream().map(DocumentChunk::getContent).collect(Collectors.joining("\n\n"));
    }
}
