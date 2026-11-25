package org.example.is_lab1.models.dto;

import org.example.is_lab1.models.entity.ImportStatus;

import java.time.LocalDateTime;

// Строка истории импорта: id операции, статус, пользователь, число добавленных объектов (только у SUCCESS)
public record ImportOperationDto(
        Long id,
        String username,
        ImportStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String fileName,
        Integer addedCount,
        String errorMessage
) {}
