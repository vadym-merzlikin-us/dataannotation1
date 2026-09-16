package com.codejunk.backend.bookdb;

import com.codejunk.backend.bookdb.dto.BookDto;
import com.codejunk.backend.bookdb.dto.BookRequest;
import com.codejunk.backend.bookdb.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/bookdb/books")
@Validated
public class BookController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<BookDto> listByAuthor(
            @RequestParam Long authorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(200) int size) {
        return service.listByAuthor(authorId, PageRequest.of(page, size, Sort.by("name", "id")));
    }

    @GetMapping("/{id}")
    public BookDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<BookDto> create(@Valid @RequestBody BookRequest request) {
        BookDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/bookdb/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookDto update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return service.update(id, request);
    }
}
