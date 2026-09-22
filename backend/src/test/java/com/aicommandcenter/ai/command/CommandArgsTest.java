package com.aicommandcenter.ai.command;

import com.aicommandcenter.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandArgsTest {

    @Test
    @DisplayName("blank values are dropped so 'optional' really means absent")
    void blankValuesAreDropped() {
        CommandArgs args = new CommandArgs(Map.of("title", "   ", "dueDate", "2026-01-05"));
        assertThat(args.has("title")).isFalse();
        assertThat(args.optional("title")).isNull();
        assertThat(args.has("dueDate")).isTrue();
    }

    @Test
    @DisplayName("required arguments fail loudly when missing")
    void requiredArguments() {
        CommandArgs args = new CommandArgs(Map.of());
        assertThatThrownBy(() -> args.require("title"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("title");

        assertThatThrownBy(() -> new CommandArgs(Map.of("title", "x".repeat(200))).require("title", 180))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    @DisplayName("dates must be ISO; anything else is refused with the offending value shown")
    void datesAreStrict() {
        assertThat(new CommandArgs(Map.of("dueDate", "2026-03-04")).optionalDate("dueDate"))
                .isEqualTo(LocalDate.of(2026, 3, 4));

        assertThatThrownBy(() -> new CommandArgs(Map.of("dueDate", "tomorrow")).optionalDate("dueDate"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tomorrow");
    }

    @Test
    @DisplayName("enums are parsed case-insensitively and validated")
    void enumsAreValidated() {
        assertThat(new CommandArgs(Map.of("priority", "high")).optionalPriority("priority").name())
                .isEqualTo("HIGH");
        assertThat(new CommandArgs(Map.of("status", "in progress")).optionalStatus("status").name())
                .isEqualTo("IN_PROGRESS");
        assertThatThrownBy(() -> new CommandArgs(Map.of("status", "DONE")).optionalStatus("status"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("numbers are clamped to a declared range")
    void numbersAreClamped() {
        assertThat(new CommandArgs(Map.of("days", "14")).optionalInt("days", 7, 1, 180)).isEqualTo(14);
        assertThat(new CommandArgs(Map.of()).optionalInt("days", 7, 1, 180)).isEqualTo(7);
        assertThatThrownBy(() -> new CommandArgs(Map.of("days", "5000")).optionalInt("days", 7, 1, 180))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new CommandArgs(Map.of("days", "many")).optionalInt("days", 7, 1, 180))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("undeclared arguments are refused — this is the anti-injection gate")
    void undeclaredArgumentsAreRefused() {
        CommandArgs args = new CommandArgs(Map.of("title", "Legit", "sql", "DROP TABLE users"));
        assertThatThrownBy(() -> args.rejectUnknown(Set.of("title", "dueDate")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sql");

        new CommandArgs(Map.of("title", "Legit")).rejectUnknown(Set.of("title"));
    }

    @Test
    @DisplayName("list arguments are split and cleaned")
    void listArguments() {
        CommandArgs args = new CommandArgs(Map.of("tags", " java , spring boot ,, "));
        assertThat(args.optionalList("tags")).containsExactly("java", "spring boot");
        assertThat(new CommandArgs(Map.of()).optionalList("tags")).isEmpty();
    }

    @Test
    @DisplayName("very long values are truncated rather than stored")
    void longValuesAreTruncated() {
        CommandArgs args = new CommandArgs(Map.of("title", "x".repeat(50_000)));
        assertThat(args.require("title").length()).isLessThan(50_000);
    }
}
