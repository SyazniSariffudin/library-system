package dev.syaz.library.controller;

import dev.syaz.library.dto.request.BorrowRequest;
import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.enums.LoanStatus;
import dev.syaz.library.service.LoanPatch;
import dev.syaz.library.service.LoanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/borrowers/{borrowerId}/loans")
public class LoanController {
    private final LoanService loanService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Borrow a physical copy",
            description = "Requires bookId and dueAt (an ISO-8601 expected return timestamp with timezone, "
                    + "after the server borrowing time). An existing active loan for this book ID returns 409, "
                    + "including repeat requests from the current borrower."
    )
    public LoanResponse borrow(@PathVariable Long borrowerId, @Valid @RequestBody BorrowRequest request) {
        return loanService.borrow(borrowerId, request);
    }

    @PatchMapping(value = "/{loanId}", consumes = LoanPatch.MEDIA_TYPE)
    @Operation(
            summary = "Return a loan using JSON Patch",
            description = "Supports replace and test on /status. Replace accepts RETURNED only. "
                    + "All operations are atomic; failed tests return 409. "
                    + "Repeating a return preserves returnedAt. An empty patch is a no-op."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = LoanPatch.MEDIA_TYPE,
                    array = @ArraySchema(schema = @Schema(implementation = PatchOperation.class)),
                    examples = @ExampleObject(value = "[{\"op\":\"replace\",\"path\":\"/status\",\"value\":\"RETURNED\"}]")
            )
    )
    public ResponseEntity<LoanResponse> patch(
            @PathVariable Long borrowerId,
            @PathVariable Long loanId,
            @RequestBody JsonNode patch) {
        LoanResponse updatedLoan = loanService.patch(borrowerId, loanId, patch);
        return ResponseEntity.ok()
                .header("Accept-Patch", LoanPatch.MEDIA_TYPE)
                .body(updatedLoan);
    }

    record PatchOperation(
            @Schema(allowableValues = {"replace", "test"}, requiredMode = Schema.RequiredMode.REQUIRED)
            String op,
            @Schema(allowableValues = {"/status"}, requiredMode = Schema.RequiredMode.REQUIRED)
            String path,
            @Schema(
                    implementation = LoanStatus.class,
                    description = "RETURNED for replace; the expected status for test",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            Object value) {
    }
}
