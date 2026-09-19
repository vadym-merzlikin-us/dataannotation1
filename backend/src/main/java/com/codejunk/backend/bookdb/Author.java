package com.codejunk.backend.bookdb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "authors")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 200)
    private String firstName;

    @Column(name = "second_name", nullable = false, length = 200)
    private String secondName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected Author() {
        // for JPA
    }

    public Author(String firstName, String secondName, String description) {
        this.firstName = firstName;
        this.secondName = secondName;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSecondName() {
        return secondName;
    }

    public void setSecondName(String secondName) {
        this.secondName = secondName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public void incrementVersion() {
        this.version++;
    }
}
