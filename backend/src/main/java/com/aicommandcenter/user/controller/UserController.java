package com.aicommandcenter.user.controller;

import com.aicommandcenter.export.UserExportService;
import com.aicommandcenter.security.SecurityUtils;
import com.aicommandcenter.user.dto.SkillRequest;
import com.aicommandcenter.user.dto.SkillResponse;
import com.aicommandcenter.user.dto.UpdateProfileRequest;
import com.aicommandcenter.user.dto.UserResponse;
import com.aicommandcenter.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;
    private final UserExportService userExportService;

    public UserController(UserService userService, UserExportService userExportService) {
        this.userService = userService;
        this.userExportService = userExportService;
    }

    @GetMapping
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.profile(SecurityUtils.currentUserId()));
    }

    /**
     * Every row this application holds about the caller, as a downloadable JSON document.
     * Returns a file rather than an inline body so a browser saves it instead of rendering it.
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        Map<String, Object> payload = userExportService.buildExport(SecurityUtils.currentUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + userExportService.fileName() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);
    }

    @PutMapping
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/skills")
    public ResponseEntity<List<SkillResponse>> skills() {
        return ResponseEntity.ok(userService.skills(SecurityUtils.currentUserId()));
    }

    @PostMapping("/skills")
    public ResponseEntity<SkillResponse> addSkill(@Valid @RequestBody SkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.addSkill(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/skills/{skillId}")
    public ResponseEntity<Void> removeSkill(@PathVariable Long skillId) {
        userService.removeSkill(SecurityUtils.currentUserId(), skillId);
        return ResponseEntity.noContent().build();
    }
}
