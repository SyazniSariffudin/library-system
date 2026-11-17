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
import dev.syaz.library.exception.LoanPatchTestFailedException;
import dev.syaz.library.mapper.LoanMapper;
import dev.syaz.library.repository.BookRepository;
import dev.syaz.library.repository.BorrowerRepository;
import dev.syaz.library.repository.LoanRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static dev.syaz.library.support.TestDatabaseErrors.constraintViolation;
import static dev.syaz.library.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoanServiceTest {
    private final BorrowerRepository borrowers = mock(BorrowerRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final Instant now = Instant.parse("2026-01-01T01:00:00Z");
    private final LoanService service = new LoanService(borrowers, books, loans, new LoanPatch(mapper),
            Clock.fixed(now, ZoneOffset.UTC), Mappers.getMapper(LoanMapper.class));
    private final Borrower borrower = withId(new Borrower("Reader", "reader@example.com"), 1L);
    private final Book book = withId(new Book(null), 2L);

    @BeforeEach
    void setUp() {
        when(borrowers.findById(borrower.getId())).thenReturn(Optional.of(borrower));
        when(books.findForUpdate(book.getId())).thenReturn(Optional.of(book));
        when(loans.saveAndFlush(any(Loan.class)))
                .thenAnswer(call -> withId(call.getArgument(0, Loan.class), 3L));
    }

    @Test
    void borrowsAvailableBook() {
        LoanResponse result = service.borrow(borrower.getId(), new BorrowRequest(book.getId(), now.plusSeconds(86400)));
        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo(LoanStatus.BORROWED);
        assertThat(result.borrowedAt()).isEqualTo(now);
        assertThat(result.dueAt()).isEqualTo(now.plusSeconds(86400));
        assertThat(result.overdue()).isFalse();
        var order = inOrder(books, loans);
        order.verify(books).findForUpdate(book.getId());
        order.verify(loans).existsByBook_IdAndReturnedAtIsNull(book.getId());
        order.verify(loans).saveAndFlush(any(Loan.class));
    }

    @Test
    void rejectsMissingPastAndPresentDeadlines() {
        for (Instant deadline : new Instant[]{null, now.minusSeconds(1), now, now.plusNanos(1)}) {
            assertThatThrownBy(() -> service.borrow(borrower.getId(), new BorrowRequest(book.getId(), deadline)))
                    .isInstanceOf(InvalidLoanDeadlineException.class);
        }
        verify(loans, never()).saveAndFlush(any());
    }

    @Test
    void overdueLoanBecomesNotOverdueWhenReturned() {
        Loan loan = withId(new Loan(borrower, book, now.minusSeconds(300), now.minusSeconds(1)), 3L);
        when(loans.findBookId(loan.getId(), borrower.getId())).thenReturn(Optional.of(book.getId()));
        when(loans.findByIdAndBorrower_Id(loan.getId(), borrower.getId())).thenReturn(Optional.of(loan));
        assertThat(service.patch(borrower.getId(), loan.getId(), mapper.readTree("[]")).overdue()).isTrue();
        LoanResponse returned = service.patch(borrower.getId(), loan.getId(), mapper.readTree("""
                [{"op":"replace","path":"/status","value":"RETURNED"}]
                """));
        assertThat(returned.overdue()).isFalse();
        assertThat(returned.returnedAt()).isEqualTo(now);
        assertThat(returned.dueAt()).isEqualTo(now.minusSeconds(1));
    }

    @Test
    void rejectsAlreadyBorrowedCopy() {
        when(loans.existsByBook_IdAndReturnedAtIsNull(book.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.borrow(borrower.getId(), new BorrowRequest(book.getId(), now.plusSeconds(86400))))
                .isInstanceOf(BookAlreadyBorrowedException.class);
        verify(loans, never()).saveAndFlush(any());
    }

    @Test
    void repeatReturnPreservesOriginalTimestamp() {
        Loan loan = existingLoan();
        Instant originalReturn = now.minusSeconds(100);
        loan.returnAt(originalReturn);
        LoanResponse result = service.patch(borrower.getId(), loan.getId(), mapper.readTree("""
                [{"op":"replace","path":"/status","value":"RETURNED"}]
                """));
        assertThat(result.returnedAt()).isEqualTo(originalReturn);
        var order = inOrder(loans, books);
        order.verify(loans).findBookId(loan.getId(), borrower.getId());
        order.verify(books).findForUpdate(book.getId());
        order.verify(loans).findByIdAndBorrower_Id(loan.getId(), borrower.getId());
    }

    @Test
    void failedPatchNeverChangesLoan() {
        Loan loan = existingLoan();
        assertThatThrownBy(() -> service.patch(borrower.getId(), loan.getId(), mapper.readTree("""
                [{"op":"replace","path":"/status","value":"RETURNED"},
                 {"op":"test","path":"/status","value":"BORROWED"}]
                """))).isInstanceOf(LoanPatchTestFailedException.class);
        assertThat(loan.getReturnedAt()).isNull();
        verify(loans, never()).flush();
    }

    @Test
    void borrowerCannotPatchAnotherBorrowersLoan() {
        Long otherBorrowerId = 4L;
        assertThatThrownBy(() -> service.patch(otherBorrowerId, 3L, mapper.readTree("[]")))
                .isInstanceOf(LoanNotFoundException.class);
        verify(books, never()).findForUpdate(any());
    }

    @Test
    void rejectsMissingBorrowerBeforeLockingBook() {
        when(borrowers.findById(borrower.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrow(borrower.getId(), new BorrowRequest(book.getId(), now.plusSeconds(86400))))
                .isInstanceOf(BorrowerNotFoundException.class);
        verifyNoInteractions(books, loans);
    }

    @Test
    void rejectsMissingBookBeforeCreatingLoan() {
        when(books.findForUpdate(book.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrow(borrower.getId(), new BorrowRequest(book.getId(), now.plusSeconds(86400))))
                .isInstanceOf(BookNotFoundException.class);
        verifyNoInteractions(loans);
    }

    @Test
    void rejectsLoanMissingAfterBookIsLocked() {
        when(loans.findBookId(3L, borrower.getId())).thenReturn(Optional.of(book.getId()));

        assertThatThrownBy(() -> service.patch(borrower.getId(), 3L, mapper.readTree("[]")))
                .isInstanceOf(LoanNotFoundException.class);
        verify(books).findForUpdate(book.getId());
        verify(loans, never()).flush();
    }

    @Test
    void translatesActiveLoanConstraintToBusinessException() {
        DataIntegrityViolationException failure = constraintViolation("uq_active_loan_per_book");
        when(loans.saveAndFlush(any(Loan.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.borrow(borrower.getId(), new BorrowRequest(book.getId(), now.plusSeconds(86400))))
                .isInstanceOf(BookAlreadyBorrowedException.class)
                .hasCause(failure);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"uq_borrower_email", "unknown_constraint"})
    void doesNotMisclassifyOtherDatabaseConstraints(String constraintName) {
        DataIntegrityViolationException failure = constraintViolation(constraintName);
        when(loans.saveAndFlush(any(Loan.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.borrow(borrower.getId(), new BorrowRequest(book.getId(), now.plusSeconds(86400))))
                .isSameAs(failure);
    }

    private Loan existingLoan() {
        Loan loan = withId(new Loan(borrower, book, now.minusSeconds(300), now.plusSeconds(86400)), 3L);
        when(loans.findBookId(loan.getId(), borrower.getId())).thenReturn(Optional.of(book.getId()));
        when(loans.findByIdAndBorrower_Id(loan.getId(), borrower.getId())).thenReturn(Optional.of(loan));
        return loan;
    }
}
