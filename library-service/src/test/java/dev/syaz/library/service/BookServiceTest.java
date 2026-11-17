package dev.syaz.library.service;

import dev.syaz.library.dto.request.RegisterBookRequest;
import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.entity.Book;
import dev.syaz.library.entity.BookEdition;
import dev.syaz.library.exception.IsbnMetadataConflictException;
import dev.syaz.library.mapper.BookMapper;
import dev.syaz.library.repository.BookEditionRepository;
import dev.syaz.library.repository.BookRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

import static dev.syaz.library.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookServiceTest {
    private final BookEditionRepository editions = mock(BookEditionRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final BookService service = new BookService(editions, books, Mappers.getMapper(BookMapper.class));
    private final RegisterBookRequest request = new RegisterBookRequest("9780134685991", "Effective Java", "Joshua Bloch");
    private final BookEdition edition = mock(BookEdition.class);

    @BeforeEach
    void setUp() {
        when(editions.findById(request.isbn())).thenReturn(Optional.of(edition));
        when(edition.getIsbn()).thenReturn(request.isbn());
        when(edition.getTitle()).thenReturn(request.title());
        when(edition.getAuthor()).thenReturn(request.author());
    }

    @Test
    void registersCopyWhenEditionMetadataMatches() {
        when(books.saveAndFlush(any(Book.class)))
                .thenAnswer(call -> withId(call.getArgument(0, Book.class), 1L));

        assertThat(service.register(request))
                .isEqualTo(new BookResponse(1L, request.isbn(), request.title(), request.author(), true));
    }

    @ParameterizedTest
    @CsvSource({"Other title, Joshua Bloch", "Effective Java, Other author", "Other title, Other author"})
    void rejectsConflictingEditionWithoutSavingCopy(String title, String author) {
        RegisterBookRequest conflicting = new RegisterBookRequest(request.isbn(), title, author);

        assertThatThrownBy(() -> service.register(conflicting)).isInstanceOf(IsbnMetadataConflictException.class);
        verifyNoInteractions(books);
    }
}
