package dev.syaz.library.mapper;

import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.dto.response.BorrowerResponse;
import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.entity.Book;
import dev.syaz.library.entity.BookEdition;
import dev.syaz.library.entity.Borrower;
import dev.syaz.library.entity.Loan;
import dev.syaz.library.enums.LoanStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Instant;

import static dev.syaz.library.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseMapperTest {
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void startMapperContext() {
        // Exercise the generated Spring components, including Lombok getter discovery.
        context = new AnnotationConfigApplicationContext("dev.syaz.library.mapper");
    }

    @AfterAll
    static void closeMapperContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void mapsSavedBorrowerUsingAGeneratedSpringBean() {
        Long id = 1L;
        Borrower borrower = withId(new Borrower("Reader", "reader@example.com"), id);

        BorrowerResponse result = context.getBean(BorrowerMapper.class).toResponse(borrower);

        assertThat(result).isEqualTo(new BorrowerResponse(id, "Reader", "reader@example.com"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void flattensEditionDetailsAndUsesSuppliedAvailability(boolean available) {
        BookEdition edition = mock(BookEdition.class);
        when(edition.getIsbn()).thenReturn("9780134685991");
        when(edition.getTitle()).thenReturn("Effective Java");
        when(edition.getAuthor()).thenReturn("Joshua Bloch");
        Long copyId = 2L;
        Book book = withId(new Book(edition), copyId);

        BookResponse result = context.getBean(BookMapper.class).toResponse(book, available);

        assertThat(result).isEqualTo(new BookResponse(copyId, "9780134685991", "Effective Java", "Joshua Bloch", available));
    }

    @ParameterizedTest
    @EnumSource(LoanStatus.class)
    void flattensLoanRelationshipsAndDerivesStatusFromReturnTimestamp(LoanStatus expectedStatus) {
        Borrower borrower = withId(new Borrower("Reader", "reader@example.com"), 1L);
        Book book = withId(new Book(null), 2L);
        Long loanId = 3L;
        Instant borrowedAt = Instant.parse("2026-01-01T00:00:00Z");
        Loan loan = withId(new Loan(borrower, book, borrowedAt, borrowedAt.plusSeconds(86400)), loanId);
        Instant returnedAt = expectedStatus == LoanStatus.RETURNED ? borrowedAt.plusSeconds(3600) : null;
        if (returnedAt != null) {
            loan.returnAt(returnedAt);
        }

        LoanResponse result = context.getBean(LoanMapper.class).toResponse(loan, borrowedAt);

        assertThat(result).isEqualTo(new LoanResponse(loanId, borrower.getId(), book.getId(),
                borrowedAt, borrowedAt.plusSeconds(86400), returnedAt, expectedStatus, false));
    }

    @Test
    void overdueChangesOnlyAfterDeadlineAndClearsOnReturn() {
        Instant borrowedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant dueAt = borrowedAt.plusSeconds(86400);
        Loan loan = new Loan(new Borrower("Reader", "reader@example.com"), new Book(null), borrowedAt, dueAt);
        LoanMapper mapper = context.getBean(LoanMapper.class);
        assertThat(mapper.toResponse(loan, dueAt.minusSeconds(1)).overdue()).isFalse();
        assertThat(mapper.toResponse(loan, dueAt).overdue()).isFalse();
        assertThat(mapper.toResponse(loan, dueAt.plusNanos(1)).overdue()).isTrue();
        loan.returnAt(dueAt.plusSeconds(1));
        assertThat(mapper.toResponse(loan, dueAt.plusSeconds(2)).overdue()).isFalse();
    }

    @Test
    void loanWithoutDeadlineIsNotReportedOverdue() {
        Loan loan = mock(Loan.class);
        when(loan.getBorrower()).thenReturn(withId(new Borrower("Reader", "reader@example.com"), 1L));
        when(loan.getBook()).thenReturn(withId(new Book(null), 2L));
        assertThat(context.getBean(LoanMapper.class).toResponse(loan, Instant.now()).overdue()).isFalse();
    }
}
