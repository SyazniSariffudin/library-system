package dev.syaz.library.repository;

import org.hibernate.exception.ConstraintViolationException;

public final class DatabaseConstraints {
    private DatabaseConstraints() {
    }

    public static boolean isViolationOf(Throwable exception, String constraintName) {
        // Match named constraints only; driver messages are not reliable classifications.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
