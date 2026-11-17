package dev.syaz.library.exception;

public class BookAlreadyBorrowedException extends RuntimeException {
    public BookAlreadyBorrowedException() {
        this(null);
    }

    public BookAlreadyBorrowedException(Throwable cause) {
        super("Book is already borrowed.", cause);
    }
}
