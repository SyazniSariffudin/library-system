package dev.syaz.library;

import dev.syaz.library.dto.request.BorrowRequest;
import dev.syaz.library.dto.request.RegisterBookRequest;
import dev.syaz.library.dto.request.RegisterBorrowerRequest;
import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.entity.AuditableEntity;
import dev.syaz.library.exception.BookAlreadyBorrowedException;
import dev.syaz.library.exception.BorrowerEmailAlreadyRegisteredException;
import dev.syaz.library.exception.IsbnMetadataConflictException;
import dev.syaz.library.service.BookService;
import dev.syaz.library.service.BorrowerService;
import dev.syaz.library.service.LoanService;
import dev.syaz.library.repository.BookEditionRepository;
import dev.syaz.library.repository.BookRepository;
import dev.syaz.library.repository.BorrowerRepository;
import dev.syaz.library.repository.LoanRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryPersistenceIT {
    private static final Duration LOAN_DURATION = Duration.ofDays(1);
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(
            System.getProperty("postgres.image", "postgres:18.7-alpine"));
    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void start() {
        DATABASE.start();
        try {
            context = new SpringApplicationBuilder(LibraryApplication.class).web(WebApplicationType.NONE).run(
                    "--spring.datasource.url=" + DATABASE.getJdbcUrl(),
                    "--spring.datasource.username=" + DATABASE.getUsername(),
                    "--spring.datasource.password=" + DATABASE.getPassword());
        } catch (RuntimeException error) {
            DATABASE.stop();
            throw error;
        }
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
        DATABASE.stop();
    }

    @Test
    void concurrentBorrowRequestsHaveExactlyOneWinner() throws Exception {
        BookResponse book = register("9780134685991", "Effective Java");
        Long firstBorrowerId = borrower();
        Long secondBorrowerId = borrower();
        List<Boolean> results = concurrently(
                () -> tryBorrow(firstBorrowerId, book.id()),
                () -> tryBorrow(secondBorrowerId, book.id()));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        Long activeLoans = jdbc().queryForObject(
                "select count(*) from loans where book_id = ? and returned_at is null", Long.class, book.id());
        assertThat(activeLoans).isEqualTo(1);
    }

    @Test
    void concurrentMatchingIsbnRegistrationsCreateTwoCopies() throws Exception {
        List<Long> ids = concurrently(
                () -> register("0306406152", "Shared edition").id(),
                () -> register("0306406152", "Shared edition").id());
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
        Long editions = jdbc().queryForObject(
                "select count(*) from book_editions where isbn = ?", Long.class, "0306406152");
        assertThat(editions).isEqualTo(1);
    }

    @Test
    void concurrentConflictingIsbnRegistrationsRejectOneRequest() throws Exception {
        List<Boolean> results = concurrently(
                () -> tryRegister("080442957X", "First title"),
                () -> tryRegister("080442957X", "Other title"));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        Long copies = jdbc().queryForObject("select count(*) from books where isbn = ?", Long.class, "080442957X");
        assertThat(copies).isEqualTo(1);
    }

    @Test
    void duplicateEmailThrowsBusinessExceptionAndRollsBack() {
        BorrowerService service = context.getBean(BorrowerService.class);
        RegisterBorrowerRequest request = new RegisterBorrowerRequest("Reader", UUID.randomUUID() + "@example.com");
        service.register(request);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BorrowerEmailAlreadyRegisteredException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc().queryForObject("select count(*) from borrowers where email = ?", Long.class, request.email()))
                .isEqualTo(1);
    }

    @Test
    void databaseRejectsASecondActiveLoanEvenOutsideServices() {
        BookResponse book = register("9780134685991", "Effective Java");
        Long borrowerId = borrower();
        borrowBook(borrowerId, book.id());

        assertThatThrownBy(() -> jdbc().update("""
                insert into loans (borrower_id, book_id, borrowed_at) values (?, ?, now())
                """, borrowerId, book.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deadlinePersistsAndDatabaseRejectsDeadlineBeforeBorrowing() {
        BookResponse book = register("9780134685991", "Effective Java");
        Instant dueAt = Instant.now().plus(LOAN_DURATION).truncatedTo(ChronoUnit.MICROS);
        LoanResponse loan = loanService().borrow(borrower(), new BorrowRequest(book.id(), dueAt));

        assertThat(jdbc().queryForObject("select due_at from loans where id = ?", Timestamp.class, loan.id()).toInstant())
                .isEqualTo(dueAt);
        assertThatThrownBy(() -> jdbc().update("update loans set due_at = borrowed_at where id = ?", loan.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnRetainsHistoryAndReplayingOldReturnDoesNotReturnNewLoan() {
        BookResponse book = register("9780134685991", "Effective Java");
        Long borrowerId = borrower();
        LoanResponse originalLoan = borrowBook(borrowerId, book.id());
        var returnPatch = context.getBean(JsonMapper.class).readTree("""
                [{"op":"replace","path":"/status","value":"RETURNED"}]
                """);
        LoanResponse returnedLoan = loanService().patch(borrowerId, originalLoan.id(), returnPatch);
        LoanResponse newLoan = borrowBook(borrowerId, book.id());

        LoanResponse replayedReturn = loanService().patch(borrowerId, originalLoan.id(), returnPatch);
        assertThat(replayedReturn.returnedAt()).isEqualTo(returnedLoan.returnedAt());
        Long loanCount = jdbc().queryForObject("select count(*) from loans where book_id = ?", Long.class, book.id());
        assertThat(loanCount).isEqualTo(2);
        Long activeLoanId = jdbc().queryForObject(
                "select id from loans where book_id = ? and returned_at is null", Long.class, book.id());
        assertThat(activeLoanId).isEqualTo(newLoan.id());
        assertThat(context.getBean(BookRepository.class).findAllWithAvailability(Pageable.unpaged()).getContent())
                .filteredOn(item -> item.id().equals(book.id()))
                .singleElement().extracting(BookResponse::available).isEqualTo(false);
    }

    @Test
    void bookPagesHaveStableOrderTotalsAndCurrentAvailability() {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            status.setRollbackOnly();
            BookService books = context.getBean(BookService.class);
            BookResponse returnedCopy = register("9780134685991", "Effective Java");
            BookResponse borrowedCopy = register("9780134685991", "Effective Java");
            Long borrowerId = borrower();
            var returnPatch = context.getBean(JsonMapper.class).readTree("""
                    [{"op":"replace","path":"/status","value":"RETURNED"}]
                    """);
            for (int history = 0; history < 2; history++) {
                LoanResponse loan = borrowBook(borrowerId, returnedCopy.id());
                loanService().patch(borrowerId, loan.id(), returnPatch);
            }
            borrowBook(borrowerId, borrowedCopy.id());
            for (int copy = 0; copy < 53; copy++) {
                register("9780134685991", "Effective Java");
            }

            List<Long> expectedIds = jdbc().queryForList("select id from books order by id", Long.class);
            int totalPages = (expectedIds.size() + 19) / 20;
            List<BookResponse> allCopies = new ArrayList<>();
            for (int page = 0; page < totalPages; page++) {
                var result = books.list(page, 20);
                assertThat(result.getTotalElements()).isEqualTo(expectedIds.size());
                assertThat(result.getTotalPages()).isEqualTo(totalPages);
                assertThat(result.getNumber()).isEqualTo(page);
                assertThat(result.getContent()).extracting(BookResponse::id)
                        .containsExactlyElementsOf(expectedIds.subList(page * 20, Math.min((page + 1) * 20, expectedIds.size())));
                allCopies.addAll(result.getContent());
            }
            assertThat(allCopies).filteredOn(copy -> copy.id().equals(returnedCopy.id()))
                    .singleElement().extracting(BookResponse::available).isEqualTo(true);
            assertThat(allCopies).filteredOn(copy -> copy.id().equals(borrowedCopy.id()))
                    .singleElement().extracting(BookResponse::available).isEqualTo(false);
            assertThat(books.list(0, 50).getContent()).extracting(BookResponse::id)
                    .containsExactlyElementsOf(expectedIds.subList(0, 50));
            var pastLastPage = books.list(totalPages, 20);
            assertThat(pastLastPage.getContent()).isEmpty();
            assertThat(pastLastPage.getTotalElements()).isEqualTo(expectedIds.size());
            assertThat(pastLastPage.getTotalPages()).isEqualTo(totalPages);
        });
    }

    @Test
    void migrationsCreateBigintIdentitiesAndRequiredIndexes() {
        List<String> migrations = jdbc().queryForList("""
                SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
                ORDER BY installed_rank
                """, String.class);
        assertThat(migrations).containsExactly("1");

        List<String> identifierTypes = jdbc().queryForList("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = current_schema() AND column_name IN ('id', 'borrower_id', 'book_id')
                """, String.class);
        assertThat(identifierTypes).hasSize(5).containsOnly("bigint");
        List<String> generationModes = jdbc().queryForList("""
                SELECT identity_generation FROM information_schema.columns
                WHERE table_schema = current_schema() AND column_name = 'id'
                """, String.class);
        assertThat(generationModes).hasSize(3).containsOnly("ALWAYS");

        List<String> indexes = jdbc().queryForList("""
                SELECT indexname FROM pg_indexes WHERE schemaname = current_schema()
                """, String.class);
        assertThat(indexes).contains("ix_books_isbn", "ix_loans_borrower", "ix_loans_book", "uq_active_loan_per_book");

        Map<String, Object> activeLoanIndex = jdbc().queryForMap("""
                SELECT indisunique AS is_unique, indnkeyatts AS key_count, indnatts AS column_count,
                    pg_get_indexdef(indexrelid, 1, true) AS key_column,
                    pg_get_indexdef(indexrelid, 2, true) AS included_column,
                    pg_get_expr(indpred, indrelid) AS predicate
                FROM pg_index WHERE indexrelid = 'uq_active_loan_per_book'::regclass
                """);
        assertThat(activeLoanIndex)
                .containsEntry("is_unique", true)
                .containsEntry("key_column", "book_id")
                .containsEntry("included_column", "id")
                .containsEntry("predicate", "(returned_at IS NULL)");
        assertThat(((Number) activeLoanIndex.get("key_count")).intValue()).isEqualTo(1);
        assertThat(((Number) activeLoanIndex.get("column_count")).intValue()).isEqualTo(2);
    }

    @Test
    void databaseRejectsLoansWithMissingBorrowerOrBook() {
        BookResponse book = register("9780134685991", "Effective Java");
        Long borrowerId = borrower();

        assertThatThrownBy(() -> jdbc().update("""
                INSERT INTO loans (borrower_id, book_id, borrowed_at) VALUES (-1, ?, now())
                """, book.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc().update("""
                INSERT INTO loans (borrower_id, book_id, borrowed_at) VALUES (?, -1, now())
                """, borrowerId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void generatedIdentifiersSupportValuesBeyond32BitIntegers() {
        jdbc().execute("ALTER TABLE borrowers ALTER COLUMN id RESTART WITH 3000000001");
        jdbc().execute("ALTER TABLE books ALTER COLUMN id RESTART WITH 3000000001");
        jdbc().execute("ALTER TABLE loans ALTER COLUMN id RESTART WITH 3000000001");

        Long borrowerId = borrower();
        BookResponse book = register("9780134685991", "Effective Java");
        LoanResponse loan = borrowBook(borrowerId, book.id());

        assertThat(borrowerId).isGreaterThan((long) Integer.MAX_VALUE);
        assertThat(book.id()).isGreaterThan((long) Integer.MAX_VALUE);
        assertThat(loan.id()).isGreaterThan((long) Integer.MAX_VALUE);
        assertThat(loan.borrowerId()).isEqualTo(borrowerId);
        assertThat(loan.bookId()).isEqualTo(book.id());
    }

    @Test
    void allEntitiesPersistAuditTimestampsIncludingNativeEditionInsert() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BookResponse book = register("9780000000101", "Audited edition");
        Long borrowerId = borrower();
        LoanResponse loan = borrowBook(borrowerId, book.id());
        Instant after = Instant.now();

        List<AuditableEntity> entities = List.of(
                context.getBean(BookEditionRepository.class).findById(book.isbn()).orElseThrow(),
                context.getBean(BookRepository.class).findById(book.id()).orElseThrow(),
                context.getBean(BorrowerRepository.class).findById(borrowerId).orElseThrow(),
                context.getBean(LoanRepository.class).findById(loan.id()).orElseThrow());
        for (AuditableEntity entity : entities) {
            assertThat(entity.getCreatedAt()).isBetween(before, after);
            assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
        }

        // Registering another copy must not modify the existing edition's audit timestamps.
        register(book.isbn(), "Audited edition");
        var edition = context.getBean(BookEditionRepository.class).findById(book.isbn()).orElseThrow();
        assertThat(edition.getCreatedAt()).isEqualTo(entities.get(0).getCreatedAt());
        assertThat(edition.getUpdatedAt()).isEqualTo(entities.get(0).getUpdatedAt());
    }

    @Test
    void entityUpdatesPreserveCreationTimeAndNoOpReturnsPreserveModificationTime() {
        BookResponse book = register("9780000000102", "Audited loan");
        Long borrowerId = borrower();
        LoanResponse loan = borrowBook(borrowerId, book.id());
        Instant original = Instant.parse("2000-01-01T00:00:00Z");
        jdbc().update("update loans set created_at = ?, updated_at = ? where id = ?",
                Timestamp.from(original), Timestamp.from(original), loan.id());
        var returnPatch = context.getBean(JsonMapper.class).readTree("""
                [{"op":"replace","path":"/status","value":"RETURNED"}]
                """);

        loanService().patch(borrowerId, loan.id(), returnPatch);
        var updated = context.getBean(LoanRepository.class).findById(loan.id()).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(original);
        assertThat(updated.getUpdatedAt()).isAfter(original);
        assertThat(updated.getReturnedAt()).isNotNull();

        loanService().patch(borrowerId, loan.id(), returnPatch);
        var unchanged = context.getBean(LoanRepository.class).findById(loan.id()).orElseThrow();
        assertThat(unchanged.getCreatedAt()).isEqualTo(original);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(updated.getUpdatedAt());
    }

    @Test
    void baselineProvidesAuditDefaultsForNativeInserts() {
        String schema = "audit_baseline_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(jdbc().getDataSource()).schemas(schema).load().migrate();
        try {
            Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc().update("INSERT INTO " + schema + ".borrowers (name, email) VALUES ('Existing reader', 'old@example.com')");
            jdbc().update("INSERT INTO " + schema + ".book_editions (isbn, title, author) VALUES ('9780000000103', 'Existing edition', 'Author')");
            jdbc().update("INSERT INTO " + schema + ".books (isbn) VALUES ('9780000000103')");
            jdbc().update("INSERT INTO " + schema + ".loans (borrower_id, book_id, borrowed_at) VALUES (1, 1, '2020-01-01T00:00:00Z')");
            Instant after = Instant.now();

            for (String table : List.of("borrowers", "book_editions", "books", "loans")) {
                var timestamps = jdbc().queryForMap("SELECT created_at, updated_at FROM " + schema + "." + table);
                Instant createdAt = ((Timestamp) timestamps.get("created_at")).toInstant();
                assertThat(createdAt).isBetween(before, after);
                assertThat(timestamps.get("updated_at")).isEqualTo(timestamps.get("created_at"));
            }
            assertThat(jdbc().queryForObject("SELECT borrowed_at FROM " + schema + ".loans", Timestamp.class).toInstant())
                    .isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
            List<String> nullableColumns = jdbc().queryForList("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_schema = ? AND column_name IN ('created_at', 'updated_at')
                    """, String.class, schema);
            assertThat(nullableColumns).hasSize(8).containsOnly("NO");
        } finally {
            jdbc().execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            var firstResult = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            var secondResult = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryBorrow(Long borrowerId, Long bookId) {
        try {
            borrowBook(borrowerId, bookId);
            return true;
        } catch (BookAlreadyBorrowedException error) {
            return false;
        }
    }

    private boolean tryRegister(String isbn, String title) {
        try {
            register(isbn, title);
            return true;
        } catch (IsbnMetadataConflictException error) {
            return false;
        }
    }

    private BookResponse register(String isbn, String title) {
        return context.getBean(BookService.class).register(new RegisterBookRequest(isbn, title, "Author"));
    }

    private Long borrower() {
        RegisterBorrowerRequest request = new RegisterBorrowerRequest("Reader", UUID.randomUUID() + "@example.com");
        return context.getBean(BorrowerService.class).register(request).id();
    }

    private LoanResponse borrowBook(Long borrowerId, Long bookId) {
        Instant dueAt = Instant.now().plus(LOAN_DURATION);
        return loanService().borrow(borrowerId, new BorrowRequest(bookId, dueAt));
    }

    private LoanService loanService() {
        return context.getBean(LoanService.class);
    }

    private JdbcTemplate jdbc() {
        return context.getBean(JdbcTemplate.class);
    }
}
