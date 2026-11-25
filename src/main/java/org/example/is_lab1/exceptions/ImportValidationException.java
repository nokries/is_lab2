package org.example.is_lab1.exceptions;

import java.util.List;

// Файл импорта не прошёл проверку (нарушены ограничения предметной области или неверный формат).
// Хранит список всех найденных ошибок, чтобы пользователь исправил файл за один раз. Код ответа - 400.
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
