package org.example.is_lab1.exceptions;

import java.util.List;

public class ImportValidationException extends RuntimeException {
    private final List<String> errors;

    public ImportValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public ImportValidationException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
