package dev.syaz.library.dto.request;

import dev.syaz.library.validation.Isbn;
import dev.syaz.library.validation.ValidIsbn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterBookRequest(
        @NotBlank @ValidIsbn String isbn,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String author) {
    public RegisterBookRequest {
        isbn = Isbn.normalize(isbn);
        title = title == null ? null : title.strip();
        author = author == null ? null : author.strip();
    }
}
