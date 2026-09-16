package com.codejunk.backend.bookdb.dto;

import com.codejunk.backend.bookdb.Book;

public record BookDto(Long id, String name, String description, Long authorId) {

    public static BookDto of(Book book) {
        return new BookDto(
                book.getId(),
                book.getName(),
                book.getDescription(),
                book.getAuthor().getId());
    }
}
