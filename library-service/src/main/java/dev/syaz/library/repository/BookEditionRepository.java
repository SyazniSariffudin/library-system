package dev.syaz.library.repository;

import dev.syaz.library.entity.BookEdition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookEditionRepository extends JpaRepository<BookEdition, String> {
    // Native inserts use the database's audit timestamp defaults instead of JPA callbacks.
    @Modifying
    @Query(value = """
            INSERT INTO book_editions (isbn, title, author)
            VALUES (:isbn, :title, :author)
            ON CONFLICT (isbn) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("isbn") String isbn, @Param("title") String title, @Param("author") String author);
}
