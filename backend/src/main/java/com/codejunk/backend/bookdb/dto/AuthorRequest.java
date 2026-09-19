package com.codejunk.backend.bookdb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorRequest(
        @NotBlank @Size(max = 200) String firstName,
        @NotBlank @Size(max = 200) String secondName,
        @Size(max = 10_000) String description,
        @NotNull Long version
) {
}
