package com.codejunk.backend.bookdb;

import com.codejunk.backend.bookdb.dto.AuthorDto;
import com.codejunk.backend.bookdb.dto.AuthorRequest;
import com.codejunk.backend.bookdb.dto.PageResponse;
import com.codejunk.backend.web.NotFoundException;
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
        Author author = new Author(
                request.firstName().trim(),
                request.secondName().trim(),
                trimToNull(request.description()));
        return AuthorDto.of(authors.save(author));
    }

    @Transactional
    public AuthorDto update(Long id, AuthorRequest request) {
        Author author = authors.findById(id).orElseThrow(() -> notFound(id));
        author.setFirstName(request.firstName().trim());
        author.setSecondName(request.secondName().trim());
        author.setDescription(trimToNull(request.description()));
        return AuthorDto.of(author);
    }

    Author reference(Long id) {
        return authors.findById(id).orElseThrow(() -> notFound(id));
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
