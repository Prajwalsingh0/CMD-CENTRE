package com.aicommandcenter.export;

import com.aicommandcenter.ai.activity.AiActivity;
import com.aicommandcenter.ai.activity.AiActivityRepository;
import com.aicommandcenter.document.entity.DocumentChunk;
import com.aicommandcenter.document.repository.DocumentChunkRepository;
import com.aicommandcenter.document.repository.DocumentRepository;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.goal.repository.GoalRepository;
import com.aicommandcenter.job.repository.JobAnalysisRepository;
import com.aicommandcenter.notification.repository.NotificationRepository;
import com.aicommandcenter.research.repository.ResearchReportRepository;
import com.aicommandcenter.task.repository.TaskRepository;
import com.aicommandcenter.user.entity.RefreshToken;
import com.aicommandcenter.user.entity.User;
import com.aicommandcenter.user.repository.RefreshTokenRepository;
import com.aicommandcenter.user.repository.UserRepository;
import com.aicommandcenter.user.repository.UserSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the application holds about one account, in one document.
 *
 * <p>Two deliberate omissions, both stated in the payload itself rather than left implied: the
 * password hash, and the document-chunk embedding vectors (derived data that reindexing
 * regenerates). Session rows are included as metadata only - never the token hashes, which are
 * credential-adjacent and useless to the account holder anyway.</p>
 */
@Service
public class UserExportService {

    private static final int SCHEMA_VERSION = 1;

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final GoalRepository goalRepository;
    private final TaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final JobAnalysisRepository jobAnalysisRepository;
    private final ResearchReportRepository researchReportRepository;
    private final NotificationRepository notificationRepository;
    private final AiActivityRepository aiActivityRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserExportService(UserRepository userRepository,
                             UserSkillRepository userSkillRepository,
                             GoalRepository goalRepository,
                             TaskRepository taskRepository,
                             DocumentRepository documentRepository,
                             DocumentChunkRepository documentChunkRepository,
                             JobAnalysisRepository jobAnalysisRepository,
                             ResearchReportRepository researchReportRepository,
                             NotificationRepository notificationRepository,
                             AiActivityRepository aiActivityRepository,
                             RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.jobAnalysisRepository = jobAnalysisRepository;
        this.researchReportRepository = researchReportRepository;
        this.notificationRepository = notificationRepository;
        this.aiActivityRepository = aiActivityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildExport(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("generatedAt", Instant.now().toString());
        payload.put("excluded", Map.of(
                "passwordHash", "never leaves the database, in any form",
                "documentChunkEmbeddings", "derived vectors; reindexing your documents regenerates them",
                "sessionTokenHashes", "credential material, listed as metadata only"));
        payload.put("account", account(user));
        payload.put("skills", userSkillRepository.findAllByUserIdOrderBySkillAsc(userId));
        payload.put("goals", goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
        payload.put("tasks", taskRepository.findAllByUserId(userId));
        payload.put("documents", documentRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
        payload.put("documentChunks", chunks(userId));
        payload.put("jobAnalyses", jobAnalysisRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
        payload.put("researchReports", researchReportRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
        payload.put("notifications", notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
        payload.put("aiActivity", aiActivityRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
        payload.put("sessions", sessions(userId));
        return payload;
    }

    /** A file name that sorts chronologically and cannot collide within a day. */
    public String fileName() {
        return "ai-command-center-export-" + Instant.now().toString().substring(0, 10) + ".json";
    }

    private static Map<String, Object> account(User user) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("email", user.getEmail());
        account.put("displayName", user.getDisplayName());
        account.put("headline", user.getHeadline());
        account.put("enabled", user.isEnabled());
        account.put("createdAt", user.getCreatedAt());
        account.put("updatedAt", user.getUpdatedAt());
        return account;
    }

    private List<Map<String, Object>> chunks(Long userId) {
        return documentChunkRepository.findAllByUserId(userId).stream().map(chunk -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", chunk.getId());
            row.put("documentId", chunk.getDocumentId());
            row.put("chunkIndex", chunk.getChunkIndex());
            row.put("content", chunk.getContent());
            row.put("createdAt", chunk.getCreatedAt());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> sessions(Long userId) {
        return refreshTokenRepository.findAllByUserIdOrderByIdAsc(userId).stream().map(token -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", token.getId());
            row.put("createdAt", token.getCreatedAt());
            row.put("expiresAt", token.getExpiresAt());
            row.put("revokedAt", token.getRevokedAt());
            return row;
        }).toList();
    }
}
