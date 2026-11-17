package dev.syaz.library.exception;

public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException() {
        super("Loan was not found.");
    }
}
