package dev.syaz.library.exception;

public class BorrowerNotFoundException extends RuntimeException {
    public BorrowerNotFoundException() {
        super("Borrower was not found.");
    }
}
