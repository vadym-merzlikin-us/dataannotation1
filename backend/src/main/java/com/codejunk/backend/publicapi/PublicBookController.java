package com.codejunk.backend.publicapi;

import com.codejunk.backend.bookdb.BookService;
import com.codejunk.backend.bookdb.dto.BookDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public read-only surface, deliberately limited to one method for now.
 * Kept separate from the /api/bookdb controllers so the editing endpoints and
 * the published contract can evolve independently.
 */
@RestController
@RequestMapping("/api/public")
public class PublicBookController {

    private final BookService books;

    public PublicBookController(BookService books) {
        this.books = books;
    }

    @GetMapping("/books")
    public List<BookDto> booksByAuthor(@RequestParam Long authorId) {
        return books.allByAuthor(authorId);
    }
}
