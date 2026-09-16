package com.codejunk.backend.annotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class AnnotationTaskController {

    private final AnnotationTaskService service;

    public AnnotationTaskController(AnnotationTaskService service) {
        this.service = service;
    }

    public record CreateTaskRequest(@NotBlank @Size(max = 2000) String text) {
    }

    public record LabelRequest(@NotBlank @Size(max = 64) String label) {
    }

    @GetMapping
    public List<AnnotationTask> list() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<AnnotationTask> create(@Valid @RequestBody CreateTaskRequest request) {
        AnnotationTask created = service.create(request.text());
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @PutMapping("/{id}/label")
    public AnnotationTask label(@PathVariable String id, @Valid @RequestBody LabelRequest request) {
        return service.label(id, request.label());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
