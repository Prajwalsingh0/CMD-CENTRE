package com.aicommandcenter.ai.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.MonthDay;

import static org.assertj.core.api.Assertions.assertThat;

class DateHintsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 4); // a Wednesday

    @Test
    @DisplayName("relative days resolve against the supplied reference date")
    void relativeDays() {
        assertThat(DateHints.resolve("call the recruiter today", TODAY)).isEqualTo("2026-03-04");
        assertThat(DateHints.resolve("study Spring Boot tomorrow", TODAY)).isEqualTo("2026-03-05");
        assertThat(DateHints.resolve("review on the day after tomorrow", TODAY)).isEqualTo("2026-03-06");
        assertThat(DateHints.resolve("submit in 3 days", TODAY)).isEqualTo("2026-03-07");
        assertThat(DateHints.resolve("prepare in 2 weeks", TODAY)).isEqualTo("2026-03-18");
        assertThat(DateHints.resolve("revisit next week", TODAY)).isEqualTo("2026-03-11");
    }

    @Test
    @DisplayName("explicit dates win over ambiguous wording")
    void explicitDates() {
        assertThat(DateHints.resolve("due 2026-07-01 please", TODAY)).isEqualTo("2026-07-01");
        assertThat(DateHints.resolve("deadline 12/07/2026", TODAY)).isEqualTo("2026-07-12");
    }

    @Test
    @DisplayName("weekday words resolve to the next occurrence")
    void weekdays() {
        assertThat(DateHints.resolve("by friday", TODAY)).isEqualTo("2026-03-06");
        assertThat(DateHints.resolve("meet on monday", TODAY)).isEqualTo("2026-03-09");
    }

    @Test
    @DisplayName("text without a date hint resolves to nothing rather than guessing")
    void noHintMeansNoDate() {
        assertThat(DateHints.resolve("write the migration", TODAY)).isNull();
        assertThat(DateHints.resolve(null, TODAY)).isNull();
        assertThat(DateHints.resolve("", TODAY)).isNull();
    }

    @Test
    @DisplayName("plan horizons are read from '14-day' style phrases and bounded")
    void horizons() {
        assertThat(DateHints.horizonDays("create a 14-day Java plan")).isEqualTo(14);
        assertThat(DateHints.horizonDays("a 3 week revision plan")).isEqualTo(21);
        assertThat(DateHints.horizonDays("create a plan")).isNull();
        assertThat(DateHints.horizonDays("a 900-day plan")).isNull();
    }

    @Test
    @DisplayName("the reference date is respected so results are reproducible")
    void deterministic() {
        LocalDate other = LocalDate.of(2027, MonthDay.of(1, 1).getMonthValue(), 1);
        assertThat(DateHints.resolve("tomorrow", other)).isEqualTo("2027-01-02");
    }
}
