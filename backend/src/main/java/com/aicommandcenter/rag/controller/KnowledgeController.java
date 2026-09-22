package com.aicommandcenter.rag.controller;

import com.aicommandcenter.rag.RagService;
import com.aicommandcenter.rag.dto.AnswerResponse;
import com.aicommandcenter.rag.dto.KnowledgeAskRequest;
import com.aicommandcenter.rag.dto.ReindexResponse;
import com.aicommandcenter.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final RagService ragService;

    public KnowledgeController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> ask(@Valid @RequestBody KnowledgeAskRequest request) {
        return ResponseEntity.ok(ragService.ask(SecurityUtils.currentUserId(), request));
    }

    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponse> reindex() {
        return ResponseEntity.ok(ragService.reindex(SecurityUtils.currentUserId()));
    }
}
