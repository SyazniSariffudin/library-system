package dev.syaz.library.controller;

import dev.syaz.library.dto.request.BorrowRequest;
import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.enums.LoanStatus;
import dev.syaz.library.exception.ApiExceptionHandler;
import dev.syaz.library.exception.BookAlreadyBorrowedException;
import dev.syaz.library.exception.BookNotFoundException;
import dev.syaz.library.exception.BorrowerEmailAlreadyRegisteredException;
import dev.syaz.library.exception.BorrowerNotFoundException;
import dev.syaz.library.exception.InvalidJsonPatchException;
import dev.syaz.library.exception.InvalidLoanDeadlineException;
import dev.syaz.library.exception.IsbnMetadataConflictException;
import dev.syaz.library.exception.LoanNotFoundException;
import dev.syaz.library.exception.LoanPatchNotAllowedException;
import dev.syaz.library.exception.LoanPatchTestFailedException;
import dev.syaz.library.service.BookService;
import dev.syaz.library.service.BorrowerService;
import dev.syaz.library.service.LoanService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static dev.syaz.library.support.TestDatabaseErrors.constraintViolation;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebContractTest {
    private final BookService books = mock(BookService.class);
    private final BorrowerService borrowers = mock(BorrowerService.class);
    private final LoanService loans = mock(LoanService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new BookController(books), new BorrowerController(borrowers), new LoanController(loans))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void bookListingDefaultsToFirstPageOfTwenty() throws Exception {
        when(books.list(0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        verify(books).list(0, 20);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 2", "2, 50"})
    void bookListingReturnsRequestedPageAndMetadata(int page, int size) throws Exception {
        var book = new BookResponse(3_000_000_001L, "9780134685991", "Effective Java", "Joshua Bloch", false);
        when(books.list(page, size)).thenReturn(new PageImpl<>(List.of(book), PageRequest.of(page, size), 151));

        mvc.perform(get("/api/v1/books").param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(book.id()))
                .andExpect(jsonPath("$.content[0].available").value(false))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(151))
                .andExpect(jsonPath("$.totalPages").value((151 + size - 1) / size));
        verify(books).list(page, size);
    }

    @Test
    void bookListingDefaultsPageAndSizeIndependently() throws Exception {
        when(books.list(1, 20)).thenReturn(Page.empty(PageRequest.of(1, 20)));
        when(books.list(0, 50)).thenReturn(Page.empty(PageRequest.of(0, 50)));

        mvc.perform(get("/api/v1/books").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/books").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0));
        verify(books).list(1, 20);
        verify(books).list(0, 50);
    }

    @ParameterizedTest
    @CsvSource({"page, -1", "page, invalid", "page, 2147483648", "size, 0", "size, -1", "size, 51",
            "size, invalid", "size, 2147483648"})
    void invalidBookPaginationProducesBadRequest(String parameter, String value) throws Exception {
        mvc.perform(get("/api/v1/books").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(books);
    }

    @Test
    void returnsFieldErrorsForInvalidBorrower() throws Exception {
        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(borrowers);
    }

    @Test
    void malformedJsonProducesProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void patchRejectsOrdinaryJsonContentType() throws Exception {
        mvc.perform(patch(loanUrl()).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(loans);
    }

    @ParameterizedTest
    @CsvSource({"BORROWED, BORROWED", "RETURNED, RETURNED"})
    void loanStatusUsesTheExistingJsonStrings(LoanStatus loanStatus, String expectedStatus) throws Exception {
        Long borrowerId = 1L;
        Long loanId = 3L;
        Instant borrowedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant returnedAt = loanStatus == LoanStatus.RETURNED ? borrowedAt.plusSeconds(60) : null;
        when(loans.patch(eq(borrowerId), eq(loanId), any())).thenReturn(
                new LoanResponse(loanId, borrowerId, 2L, borrowedAt, borrowedAt.plusSeconds(86400), returnedAt, loanStatus, false));

        mvc.perform(patch("/api/v1/borrowers/" + borrowerId + "/loans/" + loanId)
                        .contentType("application/json-patch+json").content("[]"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void acceptsExpectedReturnDeadlineAndSerializesOverdue() throws Exception {
        Long borrowerId = 3_000_000_001L;
        Long bookId = 3_000_000_002L;
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant dueAt = now.plusSeconds(86400);
        when(loans.borrow(eq(borrowerId), any())).thenReturn(
                new LoanResponse(3_000_000_003L, borrowerId, bookId, now, dueAt, null, LoanStatus.BORROWED, false));
        mvc.perform(post("/api/v1/borrowers/" + borrowerId + "/loans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + ",\"dueAt\":\"2026-01-02T08:00:00+08:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3_000_000_003L))
                .andExpect(jsonPath("$.borrowerId").value(borrowerId))
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.dueAt").value("2026-01-02T00:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false));
        verify(loans).borrow(borrowerId, new BorrowRequest(bookId, dueAt));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"dueAt\":null", ",\"dueAt\":\"invalid\"", ",\"dueAt\":\"2027-01-15\""})
    void rejectsMissingNullAndMalformedDeadlines(String deadlineField) throws Exception {
        mvc.perform(post("/api/v1/borrowers/1/loans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":2" + deadlineField + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(loans);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "9223372036854775808", "00000000-0000-0000-0000-000000000001"})
    void malformedIdProduces400(String borrowerId) throws Exception {
        mvc.perform(post("/api/v1/borrowers/" + borrowerId + "/loans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void isbnMetadataConflictProducesConflictProblem() throws Exception {
        when(books.register(any())).thenThrow(new IsbnMetadataConflictException());

        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn\":\"9780134685991\",\"title\":\"Effective Java\",\"author\":\"Joshua Bloch\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ISBN_METADATA_CONFLICT"))
                .andExpect(jsonPath("$.type").value("urn:library:problem:isbn-metadata-conflict"))
                .andExpect(jsonPath("$.detail").value("ISBN is already registered with a different title or author."));
    }

    @Test
    void duplicateEmailProducesConflictWithoutExposingDatabaseCause() throws Exception {
        when(borrowers.register(any())).thenThrow(
                new BorrowerEmailAlreadyRegisteredException(constraintViolation("uq_borrower_email")));

        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reader\",\"email\":\"reader@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value("Email is already registered."))
                .andExpect(jsonPath("$.cause").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("borrowFailures")
    void mapsBorrowFailuresToProblemDetails(RuntimeException failure, int expectedStatus, String code, String detail) throws Exception {
        when(loans.borrow(eq(1L), any())).thenThrow(failure);

        mvc.perform(post("/api/v1/borrowers/1/loans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":2,\"dueAt\":\"2027-01-15T00:00:00Z\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    private static Stream<Arguments> borrowFailures() {
        return Stream.of(
                Arguments.of(new BorrowerNotFoundException(), 404, "RESOURCE_NOT_FOUND", "Borrower was not found."),
                Arguments.of(new BookNotFoundException(), 404, "RESOURCE_NOT_FOUND", "Book was not found."),
                Arguments.of(new BookAlreadyBorrowedException(), 409, "BOOK_ALREADY_BORROWED", "Book is already borrowed."),
                Arguments.of(new InvalidLoanDeadlineException(), 400, "INVALID_DUE_AT",
                        "Expected return deadline must be after the borrowing time."));
    }

    @ParameterizedTest
    @MethodSource("patchFailures")
    void mapsPatchFailuresToProblemDetails(RuntimeException failure, int expectedStatus, String code, String detail) throws Exception {
        when(loans.patch(eq(1L), eq(3L), any())).thenThrow(failure);

        mvc.perform(patch(loanUrl()).contentType("application/json-patch+json").content("[]"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    private static Stream<Arguments> patchFailures() {
        return Stream.of(
                Arguments.of(new LoanNotFoundException(), 404, "RESOURCE_NOT_FOUND", "Loan was not found."),
                Arguments.of(new InvalidJsonPatchException("JSON Patch must be an array of operations."), 400,
                        "INVALID_JSON_PATCH", "JSON Patch must be an array of operations."),
                Arguments.of(new LoanPatchNotAllowedException("Only replace and test on /status are supported."), 422,
                        "PATCH_NOT_ALLOWED", "Only replace and test on /status are supported."),
                Arguments.of(new LoanPatchTestFailedException(new IllegalStateException("private-patch-details")), 409,
                        "PATCH_TEST_FAILED", "JSON Patch test did not match the current loan status."));
    }

    @Test
    void unrecognizedDatabaseFailureProducesSafeInternalError() throws Exception {
        when(borrowers.register(any())).thenThrow(new DataIntegrityViolationException("private-database-details"));

        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reader\",\"email\":\"reader@example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void unexpectedErrorsDoNotExposeServerDetails() throws Exception {
        when(borrowers.register(any())).thenThrow(new IllegalStateException("private-database-details"));
        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reader\",\"email\":\"reader@example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    private String loanUrl() {
        return "/api/v1/borrowers/1/loans/3";
    }
}
