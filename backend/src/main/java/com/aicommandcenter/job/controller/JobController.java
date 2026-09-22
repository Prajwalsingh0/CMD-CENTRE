package com.aicommandcenter.job.controller;

import com.aicommandcenter.job.dto.JobAnalysisResponse;
import com.aicommandcenter.job.dto.JobAnalyzeRequest;
import com.aicommandcenter.job.service.JobAnalysisService;
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
@RequestMapping("/api/jobs")
public class JobController {

    private final JobAnalysisService jobAnalysisService;

    public JobController(JobAnalysisService jobAnalysisService) {
        this.jobAnalysisService = jobAnalysisService;
    }

    @GetMapping
    public ResponseEntity<List<JobAnalysisResponse>> list() {
        return ResponseEntity.ok(jobAnalysisService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping("/analyze")
    public ResponseEntity<JobAnalysisResponse> analyze(@Valid @RequestBody JobAnalyzeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobAnalysisService.analyze(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobAnalysisResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(jobAnalysisService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/prep")
    public ResponseEntity<JobAnalysisResponse> regeneratePrep(@PathVariable Long id) {
        return ResponseEntity.ok(jobAnalysisService.regeneratePrep(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobAnalysisService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
