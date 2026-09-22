package com.aicommandcenter.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Small helper to persist {@code List<String>} / {@code List<Double>} fields as TEXT
 * columns so the schema stays portable between H2 and PostgreSQL.
 */
public final class JsonLists {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonLists() {
    }

    public static String write(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialise list", ex);
        }
    }

    public static List<String> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public static String writeDoubles(double[] values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialise vector", ex);
        }
    }

    public static double[] readDoubles(String raw) {
        if (raw == null || raw.isBlank()) {
            return new double[0];
        }
        try {
            return MAPPER.readValue(raw, double[].class);
        } catch (Exception ex) {
            return new double[0];
        }
    }
}
