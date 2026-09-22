package com.aicommandcenter.document.service;

import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiException;
import com.aicommandcenter.common.JsonLists;
import com.aicommandcenter.document.entity.Document;
import com.aicommandcenter.document.entity.DocumentChunk;
import com.aicommandcenter.document.entity.DocumentStatus;
import com.aicommandcenter.document.extract.TextChunker;
import com.aicommandcenter.document.extract.TextExtractionService;
import com.aicommandcenter.document.repository.DocumentChunkRepository;
import com.aicommandcenter.document.repository.DocumentRepository;
import com.aicommandcenter.document.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The ingestion half of document intelligence: extract → chunk → embed → persist.
 *
 * <p>Ingestion runs inside the upload request. At the enforced 8 MB ceiling that is a
 * sub-second operation, so a job queue would be cost without benefit; the trade-off is
 * documented in the README rather than hidden.</p>
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileStorageService storage;
    private final AiService aiService;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentChunkRepository chunkRepository,
                                    FileStorageService storage,
                                    AiService aiService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.aiService = aiService;
    }

    /**
     * Extracts, chunks and embeds a stored document. Never throws: a failure is recorded on the
     * document row so the user can see why, and the upload itself is not lost.
     *
     * @return number of chunks indexed, or 0 when ingestion failed
     */
    @Transactional
    public int ingest(Document document) {
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        try {
            Path file = resolveStored(document);
            String text = TextExtractionService.extract(file, extensionOf(document));
            document.setExtractedChars(text.length());

            List<String> chunks = TextChunker.chunk(text);
            if (chunks.isEmpty()) {
                throw new TextExtractionService.ExtractionException("No usable text content was found");
            }
            List<double[]> vectors = aiService.embed(chunks);
            if (vectors.size() != chunks.size()) {
                throw AiException.invalidResponse(aiService.providerName());
            }

            chunkRepository.deleteAllByDocumentId(document.getId());
            List<DocumentChunk> entities = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(document.getId());
                chunk.setUserId(document.getUserId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setEmbedding(JsonLists.writeDoubles(vectors.get(i)));
                entities.add(chunk);
            }
            chunkRepository.saveAll(entities);

            document.setFailureReason(null);
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            log.info("Indexed document {} into {} chunks", document.getId(), entities.size());
            return entities.size();
        } catch (TextExtractionService.ExtractionException ex) {
            return fail(document, ex.getMessage());
        } catch (AiException ex) {
            return fail(document, "The embedding provider is unavailable: " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected ingestion failure for document {}", document.getId(), ex);
            return fail(document, "The document could not be processed");
        }
    }

    @Transactional
    public void removeIndex(Long documentId) {
        chunkRepository.deleteAllByDocumentId(documentId);
    }

    private int fail(Document document, String reason) {
        document.setStatus(DocumentStatus.FAILED);
        document.setFailureReason(reason == null ? "Processing failed" : trim(reason));
        documentRepository.save(document);
        log.warn("Ingestion failed for document {}: {}", document.getId(), document.getFailureReason());
        return 0;
    }

    /** Concatenates the stored chunks; this is the canonical extracted text of a document. */
    @Transactional(readOnly = true)
    public String storedText(Long documentId) {
        return chunkRepository.findAllByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(DocumentChunk::getContent)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private Path resolveStored(Document document) {
        byte[] bytes = storage.read(document.getStoragePath());
        try {
            Path temp = java.nio.file.Files.createTempFile("cmd-centre-doc-", "." + extensionOf(document));
            java.nio.file.Files.write(temp, bytes);
            temp.toFile().deleteOnExit();
            return temp;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to stage stored document for extraction", ex);
        }
    }

    private static String extensionOf(Document document) {
        String name = document.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String trim(String value) {
        return value.length() > 400 ? value.substring(0, 400) : value;
    }
}
