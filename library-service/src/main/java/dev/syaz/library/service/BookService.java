package dev.syaz.library.service;

import dev.syaz.library.dto.request.RegisterBookRequest;
import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.entity.Book;
import dev.syaz.library.entity.BookEdition;
import dev.syaz.library.exception.IsbnMetadataConflictException;
import dev.syaz.library.mapper.BookMapper;
import dev.syaz.library.repository.BookEditionRepository;
import dev.syaz.library.repository.BookRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookService {
    private final BookEditionRepository bookEditionRepository;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookResponse register(RegisterBookRequest request) {
        BookEdition edition = findOrRegisterEdition(request);
        Book book = Book.builder()
                .edition(edition)
                .build();
        Book savedBook = bookRepository.saveAndFlush(book);
        return bookMapper.toResponse(savedBook, true);
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> list(int page, int size) {
        return bookRepository.findAllWithAvailability(PageRequest.of(page, size));
    }

    private BookEdition findOrRegisterEdition(RegisterBookRequest request) {
        bookEditionRepository.insertIfAbsent(request.isbn(), request.title(), request.author());
        // A separate SELECT sees the winning concurrent INSERT under READ COMMITTED.
        BookEdition edition = bookEditionRepository.findById(request.isbn()).orElseThrow();

        boolean titleMatches = edition.getTitle().equals(request.title());
        boolean authorMatches = edition.getAuthor().equals(request.author());
        if (!titleMatches || !authorMatches) {
            throw new IsbnMetadataConflictException();
        }
        return edition;
    }
}
