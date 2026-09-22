package com.aicommandcenter.document.service;

import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.document.dto.DocumentContentResponse;
import com.aicommandcenter.document.dto.DocumentInsightOperation;
import com.aicommandcenter.document.dto.DocumentInsightResponse;
import com.aicommandcenter.document.dto.DocumentResponse;
import com.aicommandcenter.document.entity.Document;
import com.aicommandcenter.document.entity.DocumentStatus;
import com.aicommandcenter.document.repository.DocumentChunkRepository;
import com.aicommandcenter.document.repository.DocumentRepository;
import com.aicommandcenter.document.storage.FileStorageService;
import com.aicommandcenter.document.validation.FileValidator;
import com.aicommandcenter.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentService {

    private static final int CONTENT_PREVIEW_CHARS = 20_000;
    private static final int MAX_INSIGHT_INPUT_CHARS = 24_000;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileStorageService storage;
    private final FileValidator fileValidator;
    private final DocumentIngestionService ingestionService;
    private final AiService aiService;
    private final AiActivityService activityService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           FileStorageService storage,
                           FileValidator fileValidator,
                           DocumentIngestionService ingestionService,
                           AiService aiService,
                           AiActivityService activityService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.fileValidator = fileValidator;
        this.ingestionService = ingestionService;
        this.aiService = aiService;
        this.activityService = activityService;
    }

    @Transactional
    public DocumentResponse upload(Long userId, MultipartFile file) {
        FileValidator.ValidatedUpload validated = fileValidator.validate(file);
        String storagePath;
        try (InputStream content = file.getInputStream()) {
            storagePath = storage.store(userId, content, validated.extension());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read the uploaded file", ex);
        }

        Document document = new Document();
        document.setUserId(userId);
        document.setName(validated.displayName());
        document.setContentType(validated.contentType());
        document.setSizeBytes(validated.sizeBytes());
        document.setStoragePath(storagePath);
        document.setStatus(DocumentStatus.PENDING);
        Document saved = documentRepository.save(document);

        ingestionService.ingest(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(Long userId, DocumentStatus status, String search) {
        List<Document> documents = status == null
                ? documentRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                : documentRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        if (search == null || search.isBlank()) {
            return documents.stream().map(this::toResponse).toList();
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return documents.stream()
                .filter(document -> document.getName().toLowerCase(Locale.ROOT).contains(needle))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long userId, Long documentId) {
        return toResponse(require(userId, documentId));
    }

    @Transactional(readOnly = true)
    public DocumentContentResponse content(Long userId, Long documentId) {
        Document document = require(userId, documentId);
        String text = ingestionService.storedText(document.getId());
        if (text.isBlank() && document.getStatus() != DocumentStatus.READY) {
            text = document.getFailureReason() == null ? "" : "(" + document.getFailureReason() + ")";
        }
        boolean truncated = text.length() > CONTENT_PREVIEW_CHARS;
        return new DocumentContentResponse(document.getId(), document.getName(),
                truncated ? text.substring(0, CONTENT_PREVIEW_CHARS) : text,
                truncated, text.length());
    }

    /**
     * Runs one of the fixed, whitelisted document operations. The operation is an enum, not a
     * prompt, so a user cannot turn this endpoint into an arbitrary-instruction channel.
     */
    @Transactional
    public DocumentInsightResponse insight(Long userId, Long documentId, DocumentInsightOperation operation) {
        Document document = require(userId, documentId);
        String text = ingestionService.storedText(document.getId());
        if (text.isBlank()) {
            throw new com.aicommandcenter.exception.BadRequestException("NO_CONTENT",
                    "This document has no extracted text to analyse");
        }
        String input = text.length() > MAX_INSIGHT_INPUT_CHARS ? text.substring(0, MAX_INSIGHT_INPUT_CHARS) : text;
        AiTask task = switch (operation) {
            case SUMMARY -> AiTask.SUMMARIZE;
            case KEY_POINTS -> AiTask.KEY_POINTS;
            case NOTES -> AiTask.NOTES;
            case QUESTIONS -> AiTask.QUESTIONS;
        };
        AiRequest request = AiRequest.of(task,
                "You analyse documents owned by the user. Answer only from the supplied text.",
                input);
        String result = aiService.complete(request).text();

        activityService.record(userId, operation.name() + " on " + document.getName(), "DOCUMENT_INSIGHT",
                AiActivityService.ActivityStatus.SUCCESS,
                "Produced a " + operation.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + " for '" + document.getName() + "' using the " + aiService.providerName() + " provider.");

        return new DocumentInsightResponse(document.getId(), document.getName(), operation.name(),
                result, aiService.providerName(), Instant.now());
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        Document document = require(userId, documentId);
        chunkRepository.deleteAllByDocumentId(document.getId());
        storage.deleteQuietly(document.getStoragePath());
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public Document require(Long userId, Long documentId) {
        return documentRepository.findById(documentId)
                .filter(document -> document.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> recent(Long userId, int limit) {
        return documentRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(Math.max(1, limit))
                .map(this::toResponse)
                .toList();
    }

    public DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getExtractedChars(),
                chunkRepository.countByDocumentId(document.getId()),
                document.getFailureReason(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
