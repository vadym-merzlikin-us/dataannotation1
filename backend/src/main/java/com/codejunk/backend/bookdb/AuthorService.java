package com.codejunk.backend.bookdb;

import com.codejunk.backend.bookdb.dto.AuthorDto;
import com.codejunk.backend.bookdb.dto.AuthorRequest;
import com.codejunk.backend.bookdb.dto.PageResponse;
import com.codejunk.backend.web.NotFoundException;
import com.codejunk.backend.web.VersionConflictException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorService {

    private final AuthorRepository authors;

    public AuthorService(AuthorRepository authors) {
        this.authors = authors;
    }

    public PageResponse<AuthorDto> list(Pageable pageable) {
        Page<Author> page = authors.findAll(pageable);
        return PageResponse.of(page, AuthorDto::of);
    }

    public AuthorDto get(Long id) {
        return authors.findById(id)
                .map(AuthorDto::of)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public AuthorDto create(AuthorRequest request) {
        if (request.version() != 0L) {
            throw new VersionConflictException("New author must have version 0, got " + request.version());
        }
        Author author = new Author(
                request.firstName().trim(),
                request.secondName().trim(),
                trimToNull(request.description()));
        return AuthorDto.of(authors.save(author));
    }

    @Transactional
    public AuthorDto update(Long id, AuthorRequest request) {
        Author author = authors.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!author.getVersion().equals(request.version())) {
            throw new VersionConflictException(
                    "Author version conflict: expected " + request.version() + ", but current is " + author.getVersion());
        }
        author.setFirstName(request.firstName().trim());
        author.setSecondName(request.secondName().trim());
        author.setDescription(trimToNull(request.description()));
        author.incrementVersion();
        return AuthorDto.of(author);
    }

    Author reference(Long id) {
        return authors.findById(id).orElseThrow(() -> notFound(id));
    }

    /** For callers that are about to change the author, not just point at it. */
    Author referenceForUpdate(Long id) {
        return authors.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("No author with id " + id);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
