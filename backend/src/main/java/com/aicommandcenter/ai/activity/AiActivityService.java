package com.aicommandcenter.ai.activity;

import com.aicommandcenter.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Writes and reads the AI activity trail.
 *
 * <p>Every AI-triggered mutation goes through here, which is what makes the activity page a
 * truthful record rather than a decoration. Column widths are enforced here so a pathological
 * command can never fail the write.</p>
 */
@Service
public class AiActivityService {

    private static final int MAX_COMMAND_CHARS = 600;
    private static final int MAX_SUMMARY_CHARS = 2000;

    private final AiActivityRepository repository;

    public AiActivityService(AiActivityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AiActivityResponse record(Long userId, String command, String intent, ActivityStatus status, String summary) {
        AiActivity activity = new AiActivity();
        activity.setUserId(userId);
        activity.setCommand(truncate(command, MAX_COMMAND_CHARS));
        activity.setIntent(truncate(intent, 60));
        activity.setStatus(status.name());
        activity.setResultSummary(truncate(summary, MAX_SUMMARY_CHARS));
        return toResponse(repository.save(activity));
    }

    @Transactional(readOnly = true)
    public List<AiActivityResponse> list(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 200);
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(safeLimit)
                .map(AiActivityService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiActivityResponse> recent(Long userId, int limit) {
        return repository.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(Math.min(Math.max(1, limit), 10))
                .map(AiActivityService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiActivityResponse> since(Long userId, int days) {
        Instant after = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return repository.findAllByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, after).stream()
                .map(AiActivityService::toResponse)
                .toList();
    }

    @Transactional
    public void clear(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    public static AiActivityResponse toResponse(AiActivity activity) {
        return new AiActivityResponse(activity.getId(), activity.getCommand(), activity.getIntent(),
                activity.getStatus(), activity.getResultSummary(), activity.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Status values recorded in the activity trail. */
    public enum ActivityStatus {
        SUCCESS,
        REJECTED,
        FAILED
    }
}
