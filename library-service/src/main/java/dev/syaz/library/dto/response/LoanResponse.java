package dev.syaz.library.dto.response;

import dev.syaz.library.enums.LoanStatus;

import java.time.Instant;

public record LoanResponse(
        Long id,
        Long borrowerId,
        Long bookId,
        Instant borrowedAt,
        Instant dueAt,
        Instant returnedAt,
        LoanStatus status,
        boolean overdue) {
}
