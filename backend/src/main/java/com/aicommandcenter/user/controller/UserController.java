package com.aicommandcenter.user.controller;

import com.aicommandcenter.security.SecurityUtils;
import com.aicommandcenter.user.dto.SkillRequest;
import com.aicommandcenter.user.dto.SkillResponse;
import com.aicommandcenter.user.dto.UpdateProfileRequest;
import com.aicommandcenter.user.dto.UserResponse;
import com.aicommandcenter.user.service.UserService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.profile(SecurityUtils.currentUserId()));
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
