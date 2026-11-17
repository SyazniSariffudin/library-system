package dev.syaz.library.controller;

import dev.syaz.library.dto.request.RegisterBorrowerRequest;
import dev.syaz.library.dto.response.BorrowerResponse;
import dev.syaz.library.service.BorrowerService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/borrowers")
public class BorrowerController {
    private final BorrowerService borrowerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register a borrower",
            description = "Email is unique after trimming and lowercasing. Duplicate email returns 409."
    )
    public BorrowerResponse register(@Valid @RequestBody RegisterBorrowerRequest request) {
        return borrowerService.register(request);
    }
}
