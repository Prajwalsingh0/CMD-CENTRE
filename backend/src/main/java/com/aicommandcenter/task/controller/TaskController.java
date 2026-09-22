package com.aicommandcenter.task.controller;

import com.aicommandcenter.common.PageResponse;
import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.security.SecurityUtils;
import com.aicommandcenter.task.dto.TaskRequest;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskResponse>> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) List<TaskStatus> statuses,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(taskService.list(SecurityUtils.currentUserId(), status, statuses, priority,
                goalId, search, dueBefore, overdueOnly, page, size, sort));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<TaskResponse>> overdue() {
        return ResponseEntity.ok(taskService.overdue(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(SecurityUtils.currentUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(@PathVariable Long id,
                                                     @RequestBody StatusPatch request) {
        return ResponseEntity.ok(taskService.updateStatus(SecurityUtils.currentUserId(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    public record StatusPatch(TaskStatus status) {
    }
}
