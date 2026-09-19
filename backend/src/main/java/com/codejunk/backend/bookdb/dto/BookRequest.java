package com.codejunk.backend.bookdb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank @Size(max = 500) String name,
        @Size(max = 10_000) String description,
        @NotNull Long authorId,
        @NotNull Long version
) {
}
