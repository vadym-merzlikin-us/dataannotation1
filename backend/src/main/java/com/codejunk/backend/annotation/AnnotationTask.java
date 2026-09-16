package com.codejunk.backend.annotation;

import java.time.Instant;

/**
 * A single item queued for human annotation.
 */
public record AnnotationTask(
        String id,
        String text,
        String label,
        Instant createdAt,
        Instant labelledAt
) {

    public AnnotationTask withLabel(String newLabel, Instant at) {
        return new AnnotationTask(id, text, newLabel, createdAt, at);
    }
}
