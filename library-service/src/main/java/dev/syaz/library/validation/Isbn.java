package dev.syaz.library.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Isbn {
    private static final Pattern ISBN_10_PATTERN = Pattern.compile("[0-9]{9}[0-9X]");
    private static final Pattern ISBN_13_PATTERN = Pattern.compile("97[89][0-9]{10}");

    private Isbn() {
    }

    public static String normalize(String value) {
        return value == null ? null : value.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        if (ISBN_10_PATTERN.matcher(value).matches()) {
            return hasValidIsbn10Checksum(value);
        }
        if (ISBN_13_PATTERN.matcher(value).matches()) {
            return hasValidIsbn13Checksum(value);
        }
        return false;
    }

    private static boolean hasValidIsbn10Checksum(String isbn) {
        int checksum = 0;
        for (int position = 0; position < 10; position++) {
            char character = isbn.charAt(position);
            int digit = character == 'X' ? 10 : character - '0';
            int weight = 10 - position;
            checksum += weight * digit;
        }
        return checksum % 11 == 0;
    }

    private static boolean hasValidIsbn13Checksum(String isbn) {
        int checksum = 0;
        for (int position = 0; position < 13; position++) {
            int digit = isbn.charAt(position) - '0';
            int weight = position % 2 == 0 ? 1 : 3;
            checksum += weight * digit;
        }
        return checksum % 10 == 0;
    }
}
