package dev.syaz.library.dto.response;

public record BookResponse(Long id, String isbn, String title, String author, boolean available) {
}
