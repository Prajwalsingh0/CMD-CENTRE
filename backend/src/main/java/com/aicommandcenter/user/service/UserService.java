package com.aicommandcenter.user.service;

import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.exception.ConflictException;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.security.JwtService;
import com.aicommandcenter.user.dto.AuthResponse;
import com.aicommandcenter.user.dto.LoginRequest;
import com.aicommandcenter.user.dto.RegisterRequest;
import com.aicommandcenter.user.dto.SkillRequest;
import com.aicommandcenter.user.dto.SkillResponse;
import com.aicommandcenter.user.dto.UpdateProfileRequest;
import com.aicommandcenter.user.dto.UserResponse;
import com.aicommandcenter.user.entity.User;
import com.aicommandcenter.user.entity.UserSkill;
import com.aicommandcenter.user.repository.UserRepository;
import com.aicommandcenter.user.repository.UserSkillRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
                       UserSkillRepository userSkillRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email);
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        User saved = userRepository.save(user);
        return issueToken(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalise(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        User user = requireUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.headline() != null) {
            user.setHeadline(request.headline().isBlank() ? null : request.headline().trim());
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public SkillResponse addSkill(Long userId, SkillRequest request) {
        requireUser(userId);
        String skill = request.skill().trim();
        if (skill.isEmpty()) {
            throw new BadRequestException("Skill name is required");
        }
        UserSkill entity = userSkillRepository.findByUserIdAndSkillIgnoreCase(userId, skill).orElseGet(UserSkill::new);
        entity.setUserId(userId);
        entity.setSkill(skill);
        entity.setLevel(request.level() == null ? "INTERMEDIATE" : request.level());
        entity.setVerified(Boolean.TRUE.equals(request.verified()));
        entity.setSource("SELF_REPORTED");
        return toSkill(userSkillRepository.save(entity));
    }

    @Transactional
    public void removeSkill(Long userId, Long skillId) {
        UserSkill skill = userSkillRepository.findById(skillId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Skill", skillId));
        userSkillRepository.delete(skill);
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> skills(Long userId) {
        return userSkillRepository.findAllByUserIdOrderBySkillAsc(userId).stream().map(UserService::toSkill).toList();
    }

    /** Password hash is intentionally never mapped into any DTO. */
    public UserResponse toResponse(User user) {
        List<SkillResponse> skills = userSkillRepository.findAllByUserIdOrderBySkillAsc(user.getId())
                .stream().map(UserService::toSkill).toList();
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getHeadline(),
                user.getCreatedAt(), skills);
    }

    private static SkillResponse toSkill(UserSkill skill) {
        return new SkillResponse(skill.getId(), skill.getSkill(), skill.getLevel(), skill.isVerified(), skill.getSource());
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, jwtService.expiresInSeconds(), toResponse(user));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
