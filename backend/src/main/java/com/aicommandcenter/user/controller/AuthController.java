package com.aicommandcenter.user.controller;

import com.aicommandcenter.security.SecurityUtils;
import com.aicommandcenter.user.dto.AuthResponse;
import com.aicommandcenter.user.dto.LoginRequest;
import com.aicommandcenter.user.dto.RefreshRequest;
import com.aicommandcenter.user.dto.RegisterRequest;
import com.aicommandcenter.user.dto.UserResponse;
import com.aicommandcenter.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    /**
     * Renews a session. Present the refresh token, receive a fresh access token and a rotated
     * refresh token; the presented one is retired by the call.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(userService.refresh(request.refreshToken()));
    }

    /**
     * Signs out by revoking the refresh token. Always 204: an unknown or already-revoked token is
     * not an error worth reporting, and saying so would leak whether a token exists.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null) {
            userService.logout(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.profile(SecurityUtils.currentUserId()));
    }
}
