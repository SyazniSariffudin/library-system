package dev.syaz.library.exception;

public class InvalidLoanDeadlineException extends RuntimeException {
    public InvalidLoanDeadlineException() {
        super("Expected return deadline must be after the borrowing time.");
    }
}
