package dev.syaz.library.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnTest {
    @ParameterizedTest
    @ValueSource(strings = {"9780134685991", "9780132350884", "0134685997", "0306406152", "080442957X"})
    void acceptsValidChecksums(String isbn) {
        assertThat(Isbn.isValid(isbn)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "9780134685992", "1234567890128", "978013468599", "X304406152", "0804429570", "abcdefghij"})
    void rejectsInvalidIsbns(String isbn) {
        assertThat(Isbn.isValid(isbn)).isFalse();
    }

    @Test
    void normalizesFormattingWithoutConvertingIdentifiers() {
        assertThat(Isbn.normalize(" 978-0-13-468599-1 ")).isEqualTo("9780134685991");
        assertThat(Isbn.normalize("0-8044-2957-x")).isEqualTo("080442957X");
        assertThat(Isbn.normalize("0134685997")).isNotEqualTo(Isbn.normalize("9780134685991"));
    }
}
