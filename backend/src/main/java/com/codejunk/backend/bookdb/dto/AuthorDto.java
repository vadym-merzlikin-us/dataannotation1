package com.codejunk.backend.bookdb.dto;

import com.codejunk.backend.bookdb.Author;

public record AuthorDto(Long id, String firstName, String secondName, String description) {

    public static AuthorDto of(Author author) {
        return new AuthorDto(
                author.getId(),
                author.getFirstName(),
                author.getSecondName(),
                author.getDescription());
    }
}
