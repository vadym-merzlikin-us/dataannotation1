package com.codejunk.backend.bookdb.dto;

import com.codejunk.backend.bookdb.Book;

public record BookDto(Long id, String name, String description, Long authorId, Long version) {

    public static BookDto of(Book book) {
        return new BookDto(
                book.getId(),
                book.getName(),
                book.getDescription(),
                book.getAuthor().getId(),
                book.getVersion());
    }
}
