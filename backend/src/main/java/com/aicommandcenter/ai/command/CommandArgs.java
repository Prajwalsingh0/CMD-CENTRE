package com.aicommandcenter.ai.command;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.task.entity.TaskStatus;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed, validating view over the argument map produced by the command parser.
 *
 * <p>Nothing downstream ever touches the raw map. Every accessor either returns a value of the
 * right type or fails with a user-visible {@code BadRequestException}; {@link #rejectUnknown(Set)}
 * refuses arguments the chosen tool does not declare, so a hallucinated parameter cannot be
 * smuggled into a service call.</p>
 */
public class CommandArgs {

    private static final int MAX_VALUE_LENGTH = 8_000;

    private final Map<String, String> raw;

    public CommandArgs(Map<String, String> raw) {
        this.raw = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    String trimmed = value.trim();
                    this.raw.put(key.trim(), trimmed.length() > MAX_VALUE_LENGTH
                            ? trimmed.substring(0, MAX_VALUE_LENGTH)
                            : trimmed);
                }
            });
        }
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }

    public String optional(String key) {
        return raw.get(key);
    }

    public String require(String key) {
        String value = raw.get(key);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("MISSING_ARGUMENT", "'" + key + "' is required for this command");
        }
        return value;
    }

    public String require(String key, int maxLength) {
        String value = require(key);
        if (value.length() > maxLength) {
            throw new BadRequestException("INVALID_ARGUMENT",
                    "'" + key + "' must be at most " + maxLength + " characters");
        }
        return value;
    }

    public Long optionalLong(String key) {
        String value = optional(key);
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.replaceAll("[^0-9-]", ""));
            return parsed <= 0 ? null : parsed;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("INVALID_ARGUMENT", "'" + key + "' must be a number");
        }
    }

    public int optionalInt(String key, int fallback, int min, int max) {
        String value = optional(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.replaceAll("[^0-9-]", ""));
            if (parsed < min || parsed > max) {
                throw new BadRequestException("INVALID_ARGUMENT",
                        "'" + key + "' must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("INVALID_ARGUMENT", "'" + key + "' must be a whole number");
        }
    }

    public LocalDate optionalDate(String key) {
        String value = optional(key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("INVALID_DATE",
                    "'" + key + "' must be an ISO date (yyyy-MM-dd); received '" + truncate(value) + "'");
        }
    }

    public Priority optionalPriority(String key) {
        String value = optional(key);
        if (value == null) {
            return null;
        }
        try {
            return Priority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_ARGUMENT",
                    "'" + key + "' must be one of LOW, MEDIUM, HIGH, URGENT");
        }
    }

    public TaskStatus optionalStatus(String key) {
        String value = optional(key);
        if (value == null) {
            return null;
        }
        try {
            return TaskStatus.valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_ARGUMENT",
                    "'" + key + "' must be one of TODO, IN_PROGRESS, COMPLETED, CANCELLED");
        }
    }

    public List<String> optionalList(String key) {
        String value = optional(key);
        if (value == null) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 40) {
                items.add(trimmed);
            }
        }
        return items;
    }

    public Set<String> keys() {
        return raw.keySet();
    }

    /** Fails the command when the model invented an argument the tool does not support. */
    public void rejectUnknown(Set<String> allowed) {
        for (String key : raw.keySet()) {
            if (!allowed.contains(key)) {
                throw new BadRequestException("UNKNOWN_ARGUMENT",
                        "Argument '" + key + "' is not supported by this command");
            }
        }
    }

    private static String truncate(String value) {
        return value.length() > 40 ? value.substring(0, 40) + "…" : value;
    }

    /** Exposed for logging and tests. */
    public Map<String, String> asMap() {
        return Map.copyOf(raw);
    }

    @Override
    public String toString() {
        return "CommandArgs" + Arrays.toString(raw.keySet().toArray());
    }
}
