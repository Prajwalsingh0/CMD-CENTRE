package com.aicommandcenter.research.controller;

import com.aicommandcenter.research.dto.ResearchRequest;
import com.aicommandcenter.research.dto.ResearchResponse;
import com.aicommandcenter.research.service.ResearchService;
import com.aicommandcenter.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research")
public class ResearchController {

    private final ResearchService researchService;

    public ResearchController(ResearchService researchService) {
        this.researchService = researchService;
    }

    @GetMapping
    public ResponseEntity<List<ResearchResponse>> list() {
        return ResponseEntity.ok(researchService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<ResearchResponse> research(@Valid @RequestBody ResearchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(researchService.research(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResearchResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(researchService.get(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        researchService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
