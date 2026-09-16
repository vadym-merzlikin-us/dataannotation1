package com.codejunk.backend.bookdb;

import com.codejunk.backend.bookdb.dto.AuthorDto;
import com.codejunk.backend.bookdb.dto.AuthorRequest;
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
@RequestMapping("/api/bookdb/authors")
@Validated
public class AuthorController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AuthorService service;

    public AuthorController(AuthorService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuthorDto> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(200) int size) {
        return service.list(PageRequest.of(page, size, Sort.by("secondName", "firstName", "id")));
    }

    @GetMapping("/{id}")
    public AuthorDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<AuthorDto> create(@Valid @RequestBody AuthorRequest request) {
        AuthorDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/bookdb/authors/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AuthorDto update(@PathVariable Long id, @Valid @RequestBody AuthorRequest request) {
        return service.update(id, request);
    }
}
