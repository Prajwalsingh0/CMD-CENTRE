package com.aicommandcenter.ai.command;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the date phrases humans actually type into ISO dates.
 *
 * <p>Shared by both parsers so the LLM path and the deterministic path resolve "tomorrow" to the
 * same day — a small thing that stops the two paths from disagreeing.</p>
 */
public final class DateHints {

    private static final Pattern IN_DAYS = Pattern.compile("\\bin\\s+(\\d{1,3})\\s*(day|days|week|weeks)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern SLASHED = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern NDays = Pattern.compile("\\b(\\d{1,3})\\s*[- ]?day\\b", Pattern.CASE_INSENSITIVE);

    private DateHints() {
    }

    /** @return an ISO date string, or {@code null} when the text contains no date hint */
    public static String resolve(String text) {
        return resolve(text, LocalDate.now());
    }

    public static String resolve(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String haystack = text.toLowerCase(Locale.ROOT);

        Matcher iso = ISO.matcher(haystack);
        if (iso.find()) {
            return iso.group(1);
        }
        Matcher slashed = SLASHED.matcher(haystack);
        if (slashed.find()) {
            return String.format("%04d-%02d-%02d", Integer.parseInt(slashed.group(3)),
                    Integer.parseInt(slashed.group(2)), Integer.parseInt(slashed.group(1)));
        }
        Matcher inDays = IN_DAYS.matcher(haystack);
        if (inDays.find()) {
            int amount = Integer.parseInt(inDays.group(1));
            int days = inDays.group(2).startsWith("week") ? amount * 7 : amount;
            return today.plusDays(days).toString();
        }
        if (haystack.contains("day after tomorrow")) {
            return today.plusDays(2).toString();
        }
        if (haystack.contains("tomorrow")) {
            return today.plusDays(1).toString();
        }
        if (haystack.contains("today") || haystack.contains("tonight")) {
            return today.toString();
        }
        if (haystack.contains("next week")) {
            return today.plusWeeks(1).toString();
        }
        if (haystack.contains("next month")) {
            return today.plusMonths(1).toString();
        }
        if (haystack.contains("end of the week") || haystack.contains("by friday") || haystack.contains("this friday")) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).toString();
        }
        Matcher weekday = Pattern.compile("\\b(?:on|by|next)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
                .matcher(haystack);
        if (weekday.find()) {
            DayOfWeek target = DayOfWeek.valueOf(weekday.group(1).toUpperCase(Locale.ROOT));
            return today.with(TemporalAdjusters.next(target)).toString();
        }
        return null;
    }

    /** Extracts a horizon in days from phrases such as "14-day plan". */
    public static Integer horizonDays(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = NDays.matcher(text);
        if (matcher.find()) {
            int days = Integer.parseInt(matcher.group(1));
            return days >= 1 && days <= 365 ? days : null;
        }
        Matcher weeks = Pattern.compile("\\b(\\d{1,2})\\s*[- ]?week\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (weeks.find()) {
            int days = Integer.parseInt(weeks.group(1)) * 7;
            return days <= 365 ? days : null;
        }
        return null;
    }
}
