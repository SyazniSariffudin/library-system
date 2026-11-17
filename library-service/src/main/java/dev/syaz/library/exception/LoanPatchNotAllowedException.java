package dev.syaz.library.exception;

public class LoanPatchNotAllowedException extends RuntimeException {
    public LoanPatchNotAllowedException(String message) {
        super(message);
    }
}
