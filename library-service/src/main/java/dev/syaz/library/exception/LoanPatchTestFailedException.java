package dev.syaz.library.exception;

public class LoanPatchTestFailedException extends RuntimeException {
    public LoanPatchTestFailedException(Throwable cause) {
        super("JSON Patch test did not match the current loan status.", cause);
    }
}
