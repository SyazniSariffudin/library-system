package dev.syaz.library.exception;

public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException() {
        super("Book was not found.");
    }
}
