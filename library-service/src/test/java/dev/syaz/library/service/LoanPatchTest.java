package dev.syaz.library.service;

import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.enums.LoanStatus;
import dev.syaz.library.exception.InvalidJsonPatchException;
import dev.syaz.library.exception.LoanPatchNotAllowedException;
import dev.syaz.library.exception.LoanPatchTestFailedException;

import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanPatchTest {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final LoanPatch patcher = new LoanPatch(mapper);
    private final LoanResponse current = new LoanResponse(3L, 1L, 2L,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-15T00:00:00Z"), null, LoanStatus.BORROWED, false);

    @Test
    void appliesOrderedOperationsToACopy() {
        assertThat(patcher.apply(mapper.readTree("""
                [{"op":"test","path":"/status","value":"BORROWED"},
                 {"op":"replace","path":"/status","value":"RETURNED"},
                 {"op":"test","path":"/status","value":"RETURNED"}]
                """), current)).isEqualTo(LoanStatus.RETURNED);
        assertThat(current.status()).isEqualTo(LoanStatus.BORROWED);
    }

    @Test
    void failedLaterTestDoesNotMutateSource() {
        assertThatThrownBy(() -> patcher.apply(mapper.readTree("""
                [{"op":"replace","path":"/status","value":"RETURNED"},
                 {"op":"test","path":"/status","value":"BORROWED"}]
                """), current)).isInstanceOf(LoanPatchTestFailedException.class)
                .hasCauseInstanceOf(JsonPatchApplicationException.class);
        assertThat(current.status()).isEqualTo(LoanStatus.BORROWED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[null]", "[{\"op\":\"invalid\",\"path\":\"/status\"}]", "[{\"op\":\"replace\",\"path\":\"/status\"}]"})
    void rejectsMalformedDocuments(String json) {
        assertThatThrownBy(() -> patcher.apply(mapper.readTree(json), current))
                .isInstanceOf(InvalidJsonPatchException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"op\":\"remove\",\"path\":\"/status\"}]",
            "[{\"op\":\"replace\",\"path\":\"/bookId\",\"value\":\"other\"}]",
            "[{\"op\":\"replace\",\"path\":\"/status\",\"value\":\"BORROWED\"}]",
            "[{\"op\":\"replace\",\"path\":\"/status\",\"value\":\"LOST\"}]",
            "[{\"op\":\"replace\",\"path\":\"/status\",\"value\":\"returned\"}]",
            "[{\"op\":\"replace\",\"path\":\"/status\",\"value\":null}]"})
    void rejectsForbiddenChanges(String json) {
        assertThatThrownBy(() -> patcher.apply(mapper.readTree(json), current))
                .isInstanceOf(LoanPatchNotAllowedException.class);
    }

    @Test
    void emptyPatchIsANoOp() {
        assertThat(patcher.apply(mapper.readTree("[]"), current)).isEqualTo(LoanStatus.BORROWED);
    }
}
