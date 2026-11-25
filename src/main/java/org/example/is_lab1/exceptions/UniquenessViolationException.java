package org.example.is_lab1.exceptions;

// Нарушено ограничение уникальности предметной области (например, имя существа уже занято).
// Ограничение проверяется в бизнес-логике, в БД оно не задано. Обработчик ошибок отвечает кодом 409.
public class UniquenessViolationException extends RuntimeException {
    public UniquenessViolationException(String message) {
        super(message);
    }
}
