package dev.syaz.library.service;

import dev.syaz.library.dto.request.BorrowRequest;
import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.entity.Book;
import dev.syaz.library.entity.Borrower;
import dev.syaz.library.entity.Loan;
import dev.syaz.library.enums.LoanStatus;
import dev.syaz.library.exception.BookAlreadyBorrowedException;
import dev.syaz.library.exception.BookNotFoundException;
import dev.syaz.library.exception.BorrowerNotFoundException;
import dev.syaz.library.exception.InvalidLoanDeadlineException;
import dev.syaz.library.exception.LoanNotFoundException;
import dev.syaz.library.mapper.LoanMapper;
import dev.syaz.library.repository.BookRepository;
import dev.syaz.library.repository.BorrowerRepository;
import dev.syaz.library.repository.LoanRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static dev.syaz.library.repository.DatabaseConstraints.isViolationOf;

@Service
@RequiredArgsConstructor
public class LoanService {
    private final BorrowerRepository borrowerRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final LoanPatch loanPatch;
    private final Clock clock;
    private final LoanMapper loanMapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse borrow(Long borrowerId, BorrowRequest request) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(BorrowerNotFoundException::new);
        Book book = findBookForUpdate(request.bookId());
        requireAvailableBook(book.getId());

        Instant borrowedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant dueAt = validateAndNormalizeDueAt(request.dueAt(), borrowedAt);
        Loan loan = Loan.builder()
                .borrower(borrower)
                .book(book)
                .borrowedAt(borrowedAt)
                .dueAt(dueAt)
                .build();
        Loan savedLoan;
        try {
            savedLoan = loanRepository.saveAndFlush(loan);
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_active_loan_per_book")) {
                throw new BookAlreadyBorrowedException(exception);
            }
            throw exception;
        }
        return loanMapper.toResponse(savedLoan, borrowedAt);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse patch(Long borrowerId, Long loanId, JsonNode patch) {
        // Resolve immutable identity first so loan state is loaded only after the book is locked.
        Long bookId = loanRepository.findBookId(loanId, borrowerId)
                .orElseThrow(LoanNotFoundException::new);
        findBookForUpdate(bookId);
        Loan loan = loanRepository.findByIdAndBorrower_Id(loanId, borrowerId)
                .orElseThrow(LoanNotFoundException::new);

        LoanResponse currentLoan = loanMapper.toResponse(loan, clock.instant());
        LoanStatus updatedStatus = loanPatch.apply(patch, currentLoan);
        if (updatedStatus == LoanStatus.RETURNED) {
            loan.returnAt(clock.instant());
        }
        loanRepository.flush();
        return loanMapper.toResponse(loan, clock.instant());
    }

    private Book findBookForUpdate(Long bookId) {
        return bookRepository.findForUpdate(bookId)
                .orElseThrow(BookNotFoundException::new);
    }

    private void requireAvailableBook(Long bookId) {
        if (loanRepository.existsByBook_IdAndReturnedAtIsNull(bookId)) {
            throw new BookAlreadyBorrowedException();
        }
    }

    private Instant validateAndNormalizeDueAt(Instant requestedDueAt, Instant borrowedAt) {
        // Compare at the same precision PostgreSQL uses to store loan timestamps.
        Instant dueAt = requestedDueAt == null ? null : requestedDueAt.truncatedTo(ChronoUnit.MICROS);
        if (dueAt == null || !dueAt.isAfter(borrowedAt)) {
            throw new InvalidLoanDeadlineException();
        }
        return dueAt;
    }
}
