package org.example.is_lab1.models.entity;

// Статус операции импорта
public enum ImportStatus {
    IN_PROGRESS, // импорт выполняется
    SUCCESS,     // транзакция зафиксирована, все объекты из файла добавлены
    FAILED       // ошибка: транзакция откатена, ни один объект не создан
}
