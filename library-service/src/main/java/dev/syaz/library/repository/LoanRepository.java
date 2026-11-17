package dev.syaz.library.repository;

import dev.syaz.library.entity.Loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    // Derived query: book.id = bookId AND returnedAt IS NULL.
    boolean existsByBook_IdAndReturnedAtIsNull(Long bookId);

    Optional<Loan> findByIdAndBorrower_Id(Long id, Long borrowerId);

    // Read only immutable identity before the service acquires the book lock.
    @Query("""
            select loan.book.id
            from Loan loan
            where loan.id = :id and loan.borrower.id = :borrowerId
            """)
    Optional<Long> findBookId(@Param("id") Long id, @Param("borrowerId") Long borrowerId);
}
