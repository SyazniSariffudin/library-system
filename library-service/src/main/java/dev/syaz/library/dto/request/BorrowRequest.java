package dev.syaz.library.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BorrowRequest(
        @NotNull Long bookId,
        @NotNull Instant dueAt) {
}
