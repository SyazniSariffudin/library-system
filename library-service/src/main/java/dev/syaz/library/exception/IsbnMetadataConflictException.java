package dev.syaz.library.exception;

public class IsbnMetadataConflictException extends RuntimeException {
    public IsbnMetadataConflictException() {
        super("ISBN is already registered with a different title or author.");
    }
}
