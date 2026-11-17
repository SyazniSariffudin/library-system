package dev.syaz.library.repository;

import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.entity.Book;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select book from Book book where book.id = :id")
    Optional<Book> findForUpdate(@Param("id") Long id);

    @Query(value = """
            select new dev.syaz.library.dto.response.BookResponse(
                book.id, edition.isbn, edition.title, edition.author,
                case when activeLoan.id is null then true else false end
            )
            from Book book
            join book.edition edition
            left join Loan activeLoan on activeLoan.book = book and activeLoan.returnedAt is null
            order by book.id
            """, countQuery = "select count(book) from Book book")
    Page<BookResponse> findAllWithAvailability(Pageable pageable);
}
