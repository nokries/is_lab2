package org.example.is_lab1.exceptions;

public class UniquenessViolationException extends RuntimeException {
    public UniquenessViolationException(String message) {
        super(message);
    }
}
