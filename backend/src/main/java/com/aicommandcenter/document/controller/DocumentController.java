package com.aicommandcenter.document.controller;

import com.aicommandcenter.document.dto.DocumentContentResponse;
import com.aicommandcenter.document.dto.DocumentInsightRequest;
import com.aicommandcenter.document.dto.DocumentInsightResponse;
import com.aicommandcenter.document.dto.DocumentResponse;
import com.aicommandcenter.document.entity.DocumentStatus;
import com.aicommandcenter.document.service.DocumentService;
import com.aicommandcenter.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@RequestParam(required = false) DocumentStatus status,
                                                       @RequestParam(required = false) String search) {
        return ResponseEntity.ok(documentService.list(SecurityUtils.currentUserId(), status, search));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(SecurityUtils.currentUserId(), file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.get(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<DocumentContentResponse> content(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.content(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/insights")
    public ResponseEntity<DocumentInsightResponse> insights(@PathVariable Long id,
                                                            @Valid @RequestBody DocumentInsightRequest request) {
        return ResponseEntity.ok(documentService.insight(SecurityUtils.currentUserId(), id, request.operation()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
