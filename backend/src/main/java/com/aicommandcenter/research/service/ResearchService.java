package com.aicommandcenter.research.service;

import com.aicommandcenter.ai.AiException;
import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.ai.local.TextAnalysis;
import com.aicommandcenter.common.JsonLists;
import com.aicommandcenter.config.ResearchProperties;
import com.aicommandcenter.document.entity.DocumentChunk;
import com.aicommandcenter.document.repository.DocumentRepository;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.rag.VectorStore;
import com.aicommandcenter.research.dto.ResearchRequest;
import com.aicommandcenter.research.dto.ResearchResponse;
import com.aicommandcenter.research.entity.ResearchReport;
import com.aicommandcenter.research.repository.ResearchReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Research agent.
 *
 * <p>Two deliberate honesty constraints:</p>
 * <ul>
 *   <li>It performs <strong>no live web retrieval</strong>. Nothing here pretends to have browsed
 *       the internet, and the response always carries a disclosure saying what the report is
 *       actually based on.</li>
 *   <li>Its real corpus is the user's own document knowledge base, retrieved through the same
 *       vector store the RAG module uses. When nothing is retrieved the report is marked
 *       {@code grounded=false} instead of inventing citations.</li>
 * </ul>
 */
@Service
public class ResearchService {

    private static final Logger log = LoggerFactory.getLogger(ResearchService.class);
    private static final int QUICK_TOP_K = 4;
    private static final int DEEP_TOP_K = 10;
    private static final int MAX_CONTEXT_CHARS = 8_000;

    private final ResearchReportRepository reportRepository;
    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final AiService aiService;
    private final AiActivityService activityService;
    private final ResearchProperties properties;

    public ResearchService(ResearchReportRepository reportRepository,
                           VectorStore vectorStore,
                           DocumentRepository documentRepository,
                           AiService aiService,
                           AiActivityService activityService,
                           ResearchProperties properties) {
        this.reportRepository = reportRepository;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.aiService = aiService;
        this.activityService = activityService;
        this.properties = properties;
    }

    @Transactional
    public ResearchResponse research(Long userId, ResearchRequest request) {
        int topK = "deep".equalsIgnoreCase(request.depth()) ? DEEP_TOP_K : QUICK_TOP_K;
        List<VectorStore.ScoredChunk> hits = ifRemote(userId, request.topic(), topK);
        Map<Long, String> names = documentNames(userId, hits);

        List<String> sources = hits.stream()
                .map(hit -> names.getOrDefault(hit.chunk().getDocumentId(), "document")
                        + " (part " + (hit.chunk().getChunkIndex() + 1) + ", relevance "
                        + Math.round(hit.score() * 100) + "%)")
                .distinct()
                .toList();
        boolean grounded = !sources.isEmpty();

        String body = generateBody(request.topic(), hits, names);
        List<String> keyFindings = TextAnalysis.keyPoints(body, 7);
        List<String> concepts = TextAnalysis.topKeywords(body.isBlank() ? request.topic() : body, 10);
        List<String> recommendations = recommendations(grounded, request.topic());
        List<String> summaryLines = TextAnalysis.summarize(body.isBlank() ? request.topic() : body, 3);
        String overview = overview(request.topic(), grounded, hits.size(), summaryLines);

        ResearchReport report = new ResearchReport();
        report.setUserId(userId);
        report.setTopic(request.topic().trim());
        report.setOverview(overview);
        report.setKeyFindings(JsonLists.write(keyFindings));
        report.setConcepts(JsonLists.write(concepts));
        report.setRecommendations(JsonLists.write(recommendations));
        report.setSources(JsonLists.write(sources));
        report.setSummary(summaryLines.isEmpty() ? overview : String.join(" ", summaryLines));
        report.setGrounded(grounded);
        ResearchReport saved = reportRepository.save(report);

        activityService.record(userId, "Research: " + request.topic(), "RESEARCH_TOPIC",
                AiActivityService.ActivityStatus.SUCCESS,
                grounded
                        ? "Report built from " + sources.size() + " passage(s) in your own documents."
                        : "Report produced without retrieval — no matching passages in your knowledge base.");

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ResearchResponse> list(Long userId) {
        return reportRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ResearchResponse get(Long userId, Long id) {
        return toResponse(require(userId, id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        reportRepository.delete(require(userId, id));
    }

    @Transactional(readOnly = true)
    public List<ResearchResponse> recent(Long userId, int limit) {
        return reportRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(Math.max(1, limit))
                .map(this::toResponse)
                .toList();
    }

    private List<VectorStore.ScoredChunk> ifRemote(Long userId, String topic, int topK) {
        List<double[]> vectors = aiService.embed(List.of(topic));
        if (vectors.isEmpty()) {
            return List.of();
        }
        return vectorStore.search(userId, vectors.get(0), topK, List.of());
    }

    private String generateBody(String topic, List<VectorStore.ScoredChunk> hits, Map<Long, String> names) {
        StringBuilder context = new StringBuilder();
        if (!hits.isEmpty()) {
            context.append("SOURCES FROM THE USER'S OWN DOCUMENTS:\n");
            int index = 0;
            for (VectorStore.ScoredChunk hit : hits) {
                if (context.length() > MAX_CONTEXT_CHARS) {
                    break;
                }
                index++;
                context.append('[').append(index).append("] ")
                        .append(names.getOrDefault(hit.chunk().getDocumentId(), "document")).append('\n')
                        .append(hit.chunk().getContent()).append("\n\n");
            }
        }
        context.append("TOPIC:\n").append(topic);
        try {
            return aiService.complete(AiRequest.of(AiTask.RESEARCH,
                    "Write a factual research brief on the topic. Do not invent sources.",
                    context.toString())).text();
        } catch (AiException ex) {
            log.info("Research generation failed on provider {}: {}", aiService.providerName(), ex.getCode());
            return "";
        }
    }

    private String overview(String topic, boolean grounded, int passages, List<String> summaryLines) {
        StringBuilder builder = new StringBuilder();
        builder.append("Topic: ").append(topic).append('\n');
        builder.append(grounded
                ? "Basis: " + passages + " passage(s) retrieved from your own document knowledge base."
                : "Basis: no matching passage was found in your document knowledge base.");
        builder.append('\n').append(disclosure());
        if (!summaryLines.isEmpty()) {
            builder.append("\n\n").append(String.join(" ", summaryLines));
        }
        return builder.toString();
    }

    private List<String> recommendations(boolean grounded, String topic) {
        List<String> items = new ArrayList<>();
        if (grounded) {
            items.add("Re-read the cited passages before acting; they are the authoritative source here.");
        } else {
            items.add("Upload a document that covers \"" + topic
                    + "\" and re-run this research so the report has real sources.");
        }
        items.add("Turn the two most important findings into tasks so the research leads to action.");
        if (properties.webEnabled()) {
            items.add("Live web retrieval is enabled in configuration but this build performs none — treat external "
                    + "facts as unverified and check them against a primary source yourself.");
        } else {
            items.add("Web retrieval is disabled (RESEARCH_WEB_ENABLED=false), so only local sources were considered.");
        }
        return items;
    }

    private String disclosure() {
        String web = properties.webEnabled()
                ? "This agent performs no live web retrieval; external facts are not verified here."
                : "Web retrieval is disabled for this deployment (RESEARCH_WEB_ENABLED=false).";
        String provider = aiService.isRemote()
                ? "Text was synthesised by the configured language model."
                : "Text was assembled by the deterministic local provider (no language model involved).";
        return web + " " + provider;
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

    private ResearchReport require(Long userId, Long id) {
        return reportRepository.findById(id)
                .filter(report -> report.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Research report", id));
    }

    public ResearchResponse toResponse(ResearchReport report) {
        return new ResearchResponse(
                report.getId(),
                report.getTopic(),
                report.getOverview(),
                JsonLists.read(report.getKeyFindings()),
                JsonLists.read(report.getConcepts()),
                JsonLists.read(report.getRecommendations()),
                JsonLists.read(report.getSources()),
                report.getSummary(),
                report.isGrounded(),
                disclosure(),
                aiService.providerName(),
                report.getCreatedAt());
    }

    /** Exposed for the in-memory fallback used by tests without a vector store. */
    static List<String> sourceLabels(List<DocumentChunk> chunks) {
        return chunks.stream().map(chunk -> "chunk " + chunk.getChunkIndex()).toList();
    }
}
