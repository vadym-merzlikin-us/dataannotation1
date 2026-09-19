package com.codejunk.backend.web;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
        super(message);
    }
}
