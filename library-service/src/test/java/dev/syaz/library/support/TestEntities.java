package dev.syaz.library.support;

import org.springframework.test.util.ReflectionTestUtils;

public final class TestEntities {
    private TestEntities() {
    }

    // Represent an already-saved entity without exposing ID setters in the domain model.
    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
