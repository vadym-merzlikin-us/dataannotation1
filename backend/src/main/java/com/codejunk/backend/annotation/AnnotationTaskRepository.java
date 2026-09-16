package com.codejunk.backend.annotation;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store. Swap for a JPA repository once a database is added to the stack.
 */
@Repository
public class AnnotationTaskRepository {

    private final Map<String, AnnotationTask> tasks = new ConcurrentHashMap<>();

    public List<AnnotationTask> findAll() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(AnnotationTask::createdAt).reversed())
                .toList();
    }

    public Optional<AnnotationTask> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public AnnotationTask save(AnnotationTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    public boolean deleteById(String id) {
        return tasks.remove(id) != null;
    }
}
