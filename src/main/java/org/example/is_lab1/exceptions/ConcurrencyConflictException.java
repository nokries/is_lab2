package org.example.is_lab1.exceptions;

// Транзакцию не удалось выполнить из-за конфликта с параллельными транзакциями
// (ошибка сериализации / взаимная блокировка) даже после нескольких повторов. Код ответа - 409.
public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
