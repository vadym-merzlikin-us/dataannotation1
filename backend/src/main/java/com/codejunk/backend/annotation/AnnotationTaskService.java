package com.codejunk.backend.annotation;

import com.codejunk.backend.web.NotFoundException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AnnotationTaskService {

    private final AnnotationTaskRepository repository;
    private final Clock clock;

    public AnnotationTaskService(AnnotationTaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @PostConstruct
    void seed() {
        if (repository.findAll().isEmpty()) {
            create("The delivery arrived two days late, but the item is perfect.");
            create("Absolutely no complaints - would order again.");
            create("Support never answered my third email.");
        }
    }

    public List<AnnotationTask> findAll() {
        return repository.findAll();
    }

    public AnnotationTask create(String text) {
        AnnotationTask task = new AnnotationTask(
                UUID.randomUUID().toString(),
                text.trim(),
                null,
                clock.instant(),
                null);
        return repository.save(task);
    }

    public AnnotationTask label(String id, String label) {
        AnnotationTask task = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No annotation task with id " + id));
        return repository.save(task.withLabel(label, clock.instant()));
    }

    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new NotFoundException("No annotation task with id " + id);
        }
    }
}
