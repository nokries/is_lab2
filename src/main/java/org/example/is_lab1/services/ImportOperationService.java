package org.example.is_lab1.services;

import jakarta.persistence.EntityNotFoundException;
import org.example.is_lab1.models.dto.ImportOperationDto;
import org.example.is_lab1.models.entity.ImportOperation;
import org.example.is_lab1.models.entity.ImportStatus;
import org.example.is_lab1.models.entity.Role;
import org.example.is_lab1.models.entity.User;
import org.example.is_lab1.repository.ImportOperationRepository;
import org.example.is_lab1.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class ImportOperationService {
    private static final int PAGE_SIZE = 10;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final ImportOperationRepository repository;
    private final UserRepository userRepository;

    public ImportOperationService(ImportOperationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }


    @Transactional
    public Long start(User user, String fileName) {
        User owner = userRepository.findById(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + user.getId()));

        ImportOperation operation = new ImportOperation();
        operation.setUser(owner);
        operation.setStatus(ImportStatus.IN_PROGRESS);
        operation.setFileName(fileName);
        operation.setStartTime(LocalDateTime.now());
        repository.save(operation);
        return operation.getId();
    }

    @Transactional
    public void markFailed(Long operationId, String message) {
        ImportOperation operation = repository.findById(operationId).orElse(null);
        if (operation == null || operation.getStatus() != ImportStatus.IN_PROGRESS) {
            return;
        }
        operation.setStatus(ImportStatus.FAILED);
        operation.setEndTime(LocalDateTime.now());
        operation.setAddedCount(null);
        operation.setErrorMessage(truncate(message));
        repository.save(operation);
    }


    @Transactional(readOnly = true)
    public Page<ImportOperationDto> getHistory(User user, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
        Page<ImportOperation> operations;
        if (user.getRole() == Role.ADMIN) {
            operations = repository.findAll(pageable);
        } else {
            operations = repository.findByUserId(user.getId(), pageable);
        }
        return operations.map(ImportOperationService::toDto);
    }

    @Transactional(readOnly = true)
    public ImportOperationDto getOperation(Long id) {
        ImportOperation operation = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Import operation not found with id " + id));
        return toDto(operation);
    }

    public static String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() > MAX_ERROR_LENGTH) {
            return message.substring(0, MAX_ERROR_LENGTH) + "...";
        }
        return message;
    }

    private static ImportOperationDto toDto(ImportOperation operation) {
        Integer added = null;
        if (operation.getStatus() == ImportStatus.SUCCESS) {
            added = operation.getAddedCount();
        }
        return new ImportOperationDto(
                operation.getId(),
                operation.getUser().getUsername(),
                operation.getStatus(),
                operation.getStartTime(),
                operation.getEndTime(),
                operation.getFileName(),
                added,
                operation.getErrorMessage()
        );
    }
}
