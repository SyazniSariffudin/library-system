package dev.syaz.library.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IsbnMetadataConflictException.class)
    ProblemDetail handleIsbnMetadataConflict(IsbnMetadataConflictException exception) {
        return problem(HttpStatus.CONFLICT, "ISBN_METADATA_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({BookNotFoundException.class, BorrowerNotFoundException.class, LoanNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(BookAlreadyBorrowedException.class)
    ProblemDetail handleBookAlreadyBorrowed(BookAlreadyBorrowedException exception) {
        return problem(HttpStatus.CONFLICT, "BOOK_ALREADY_BORROWED", exception.getMessage());
    }

    @ExceptionHandler(BorrowerEmailAlreadyRegisteredException.class)
    ProblemDetail handleBorrowerEmailAlreadyRegistered(BorrowerEmailAlreadyRegisteredException exception) {
        return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", exception.getMessage());
    }

    @ExceptionHandler(InvalidLoanDeadlineException.class)
    ProblemDetail handleInvalidLoanDeadline(InvalidLoanDeadlineException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_DUE_AT", exception.getMessage());
    }

    @ExceptionHandler(InvalidJsonPatchException.class)
    ProblemDetail handleInvalidJsonPatch(InvalidJsonPatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_JSON_PATCH", exception.getMessage());
    }

    @ExceptionHandler(LoanPatchNotAllowedException.class)
    ProblemDetail handleLoanPatchNotAllowed(LoanPatchNotAllowedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "PATCH_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(LoanPatchTestFailedException.class)
    ProblemDetail handleLoanPatchTestFailed(LoanPatchTestFailedException exception) {
        return problem(HttpStatus.CONFLICT, "PATCH_TEST_FAILED", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException exception) {
        return internalError(exception, "database", "Unexpected database constraint failure");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail handleLock(PessimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "RESOURCE_BUSY", "The book is being updated. Retry the request.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        return internalError(exception, "request", "Unexpected request failure");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail detail = body instanceof ProblemDetail existing
                ? existing
                : ProblemDetail.forStatusAndDetail(status, "The request could not be processed.");
        if (detail.getProperties() == null || !detail.getProperties().containsKey("code")) {
            String code = status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
                    ? "UNSUPPORTED_MEDIA_TYPE"
                    : "INVALID_REQUEST";
            detail.setProperty("code", code);
        }
        return super.handleExceptionInternal(exception, detail, headers, status, request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        String problemName = code.toLowerCase(Locale.ROOT).replace('_', '-');
        detail.setType(URI.create("urn:library:problem:" + problemName));
        detail.setProperty("code", code);
        return detail;
    }

    private ProblemDetail internalError(Exception exception, String category, String message) {
        log.atError()
                .addKeyValue("event", "application.error")
                .addKeyValue("error_code", "INTERNAL_ERROR")
                .addKeyValue("error_category", category)
                .setCause(exception)
                .log(message);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    record FieldError(String field, String message) {
    }
}
