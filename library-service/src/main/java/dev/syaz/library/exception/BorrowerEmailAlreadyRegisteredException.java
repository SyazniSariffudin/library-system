package dev.syaz.library.exception;

public class BorrowerEmailAlreadyRegisteredException extends RuntimeException {
    public BorrowerEmailAlreadyRegisteredException(Throwable cause) {
        super("Email is already registered.", cause);
    }
}
