package dev.syaz.library.controller;

import dev.syaz.library.dto.request.RegisterBookRequest;
import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.dto.response.PageResponse;
import dev.syaz.library.service.BookService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/books")
public class BookController {
    private final BookService bookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register a physical book copy",
            description = "Matching ISBN metadata creates a new copy ID. "
                    + "Conflicting title or author for an ISBN returns 409."
    )
    public BookResponse register(@Valid @RequestBody RegisterBookRequest request) {
        return bookService.register(request);
    }

    @GetMapping
    @Operation(
            summary = "List a page of physical book copies with their current availability",
            description = "Pages are zero-based and ordered by copy ID. The default page size is 20, "
                    + "with a maximum of 50. Invalid pagination parameters return 400."
    )
    public PageResponse<BookResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageResponse.from(bookService.list(page, size));
    }
}
