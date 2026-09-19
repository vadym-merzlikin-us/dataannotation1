package com.codejunk.backend.bookdb;

import com.codejunk.backend.bookdb.dto.BookDto;
import com.codejunk.backend.bookdb.dto.BookRequest;
import com.codejunk.backend.bookdb.dto.PageResponse;
import com.codejunk.backend.web.NotFoundException;
import com.codejunk.backend.web.VersionConflictException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository books;
    private final AuthorService authorService;

    public BookService(BookRepository books, AuthorService authorService) {
        this.books = books;
        this.authorService = authorService;
    }

    public PageResponse<BookDto> listByAuthor(Long authorId, Pageable pageable) {
        Page<Book> page = books.findByAuthorId(authorId, pageable);
        return PageResponse.of(page, BookDto::of);
    }

    public List<BookDto> allByAuthor(Long authorId) {
        return books.findByAuthorIdOrderByNameAsc(authorId).stream()
                .map(BookDto::of)
                .toList();
    }

    public BookDto get(Long id) {
        return books.findById(id)
                .map(BookDto::of)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public BookDto create(BookRequest request) {
        if (request.version() != 0L) {
            throw new VersionConflictException("New book must have version 0, got " + request.version());
        }
        Author author = authorService.reference(request.authorId());
        Book book = new Book(
                request.name().trim(),
                trimToNull(request.description()),
                author);
        books.save(book);
        author.incrementVersion();
        return BookDto.of(book);
    }

    @Transactional
    public BookDto update(Long id, BookRequest request) {
        Book book = books.findById(id).orElseThrow(() -> notFound(id));
        if (!book.getVersion().equals(request.version())) {
            throw new VersionConflictException(
                    "Book version conflict: expected " + request.version() + ", but current is " + book.getVersion());
        }
        book.setName(request.name().trim());
        book.setDescription(trimToNull(request.description()));
        Author author = book.getAuthor();
        if (!author.getId().equals(request.authorId())) {
            author = authorService.reference(request.authorId());
            book.setAuthor(author);
        }
        book.incrementVersion();
        author.incrementVersion();
        return BookDto.of(book);
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("No book with id " + id);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
