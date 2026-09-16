package com.codejunk.backend.bookdb;

import com.codejunk.backend.bookdb.dto.BookDto;
import com.codejunk.backend.bookdb.dto.BookRequest;
import com.codejunk.backend.bookdb.dto.PageResponse;
import com.codejunk.backend.web.NotFoundException;
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
        Book book = new Book(
                request.name().trim(),
                trimToNull(request.description()),
                authorService.reference(request.authorId()));
        return BookDto.of(books.save(book));
    }

    @Transactional
    public BookDto update(Long id, BookRequest request) {
        Book book = books.findById(id).orElseThrow(() -> notFound(id));
        book.setName(request.name().trim());
        book.setDescription(trimToNull(request.description()));
        if (!book.getAuthor().getId().equals(request.authorId())) {
            book.setAuthor(authorService.reference(request.authorId()));
        }
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
