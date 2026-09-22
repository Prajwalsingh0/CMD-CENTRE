package com.aicommandcenter.document.dto;

import jakarta.validation.constraints.NotNull;

public record DocumentInsightRequest(@NotNull DocumentInsightOperation operation) {
}
