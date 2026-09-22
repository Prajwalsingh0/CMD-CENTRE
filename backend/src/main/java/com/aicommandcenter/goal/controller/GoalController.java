package com.aicommandcenter.goal.controller;

import com.aicommandcenter.goal.dto.GoalRequest;
import com.aicommandcenter.goal.dto.GoalResponse;
import com.aicommandcenter.goal.entity.GoalStatus;
import com.aicommandcenter.goal.service.GoalService;
import com.aicommandcenter.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(@RequestParam(required = false) GoalStatus status) {
        return ResponseEntity.ok(goalService.list(SecurityUtils.currentUserId(), status));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(goalService.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@PathVariable Long id, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(goalService.update(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/{id}/refresh-status")
    public ResponseEntity<GoalResponse> refreshStatus(@PathVariable Long id) {
        return ResponseEntity.ok(goalService.refreshStatus(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        goalService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
