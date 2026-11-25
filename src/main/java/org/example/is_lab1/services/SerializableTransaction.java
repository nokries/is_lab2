package org.example.is_lab1.services;

import org.example.is_lab1.exceptions.ConcurrencyConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

// Выполняет действие в транзакции с уровнем изоляции SERIALIZABLE и повторяет её, если PostgreSQL
// отклонил транзакцию из-за конфликта с параллельной (SQLSTATE 40001 / 40P01).
//
// Зачем: ограничения уникальности реализованы в бизнес-логике (сначала SELECT "есть ли такое имя?",
// потом INSERT). На уровне READ COMMITTED две параллельные транзакции обе не увидят друг друга
// (фантомное чтение) и обе создадут объект с одним именем. SERIALIZABLE этого не допускает:
// одна из транзакций получает ошибку сериализации, повторяется, видит уже зафиксированный объект
// и отвечает пользователю понятным "имя уже занято".
//
// Повтор нужно делать СНАРУЖИ транзакции, поэтому здесь TransactionTemplate, а не @Transactional:
// метод, помеченный @Transactional, не может сам себя перезапустить.
// Вызывать execute() нужно из кода, который сам не выполняется внутри другой транзакции.
@Component
public class SerializableTransaction {
    private static final Logger log = LoggerFactory.getLogger(SerializableTransaction.class);

    private static final int MAX_ATTEMPTS = 6;

    private final TransactionTemplate template;

    public SerializableTransaction(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public <T> T execute(Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return template.execute(status -> action.get());
            } catch (RuntimeException e) {
                if (!isConcurrencyFailure(e)) {
                    throw e;
                }
                last = e;
                log.info("Конфликт параллельных транзакций, попытка {} из {}", attempt, MAX_ATTEMPTS);
                pause(attempt);
            }
        }
        throw new ConcurrencyConflictException(
                "Не удалось выполнить операцию из-за одновременных изменений другими пользователями. Повторите запрос", last);
    }

    public void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    // Небольшая случайная пауза, чтобы повторяющиеся транзакции не сталкивались снова и снова
    private void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10L * attempt, 40L * attempt + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Ошибка вызвана конфликтом транзакций? Смотрим всю цепочку причин:
    // EclipseLink и Spring заворачивают исходный SQLException в несколько слоёв исключений
    static boolean isConcurrencyFailure(Throwable error) {
        Throwable t = error;
        int depth = 0;
        while (t != null && depth < 20) {
            if (t instanceof ConcurrencyFailureException) {
                return true;
            }
            if (t instanceof SQLException sql && hasConflictState(sql)) {
                return true;
            }
            t = t.getCause();
            depth++;
        }
        return false;
    }

    private static boolean hasConflictState(SQLException sql) {
        SQLException current = sql;
        int depth = 0;
        while (current != null && depth < 20) {
            String state = current.getSQLState();
            // 40001 - serialization_failure, 40P01 - deadlock_detected
            if ("40001".equals(state) || "40P01".equals(state)) {
                return true;
            }
            current = current.getNextException();
            depth++;
        }
        return false;
    }
}
