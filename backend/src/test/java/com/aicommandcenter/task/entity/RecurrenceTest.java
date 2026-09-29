package com.aicommandcenter.task.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The date arithmetic behind recurring tasks. */
class RecurrenceTest {

    @Test
    @DisplayName("daily, weekly and monthly advance by their unit")
    void advancesByItsUnit() {
        LocalDate monday = LocalDate.of(2026, 3, 2);

        assertThat(Recurrence.DAILY.next(monday)).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(Recurrence.WEEKLY.next(monday)).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(Recurrence.MONTHLY.next(monday)).isEqualTo(LocalDate.of(2026, 4, 2));
    }

    @Test
    @DisplayName("a monthly task at a month end clamps instead of skipping the month")
    void monthlyClampsToShorterMonths() {
        assertThat(Recurrence.MONTHLY.next(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 28));
        // 2028 is a leap year, so the clamp lands on the 29th
        assertThat(Recurrence.MONTHLY.next(LocalDate.of(2028, 1, 31))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(Recurrence.MONTHLY.next(LocalDate.of(2026, 3, 31))).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("weekly and daily cross month and year boundaries")
    void crossesBoundaries() {
        assertThat(Recurrence.WEEKLY.next(LocalDate.of(2026, 12, 29))).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(Recurrence.DAILY.next(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("NONE never advances and never claims to repeat")
    void noneIsOneOff() {
        assertThat(Recurrence.NONE.repeats()).isFalse();
        assertThat(Recurrence.NONE.next(LocalDate.of(2026, 3, 2))).isNull();
        assertThat(Recurrence.DAILY.repeats()).isTrue();
        assertThat(Recurrence.WEEKLY.repeats()).isTrue();
        assertThat(Recurrence.MONTHLY.repeats()).isTrue();
    }

    @Test
    @DisplayName("a missing date produces no next date for any rule")
    void nullDateIsSafe() {
        for (Recurrence recurrence : Recurrence.values()) {
            assertThat(recurrence.next(null)).isNull();
        }
    }
}
