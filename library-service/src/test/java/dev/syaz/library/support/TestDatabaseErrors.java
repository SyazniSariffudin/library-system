package dev.syaz.library.support;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

public final class TestDatabaseErrors {
    private TestDatabaseErrors() {
    }

    public static DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException violation = new ConstraintViolationException(
                "private-database-details", new SQLException("private-driver-details"), constraintName);
        return new DataIntegrityViolationException("Persistence failed", new IllegalStateException(violation));
    }
}
