package org.example.is_lab1.repository;

import org.example.is_lab1.models.entity.ImportOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportOperationRepository extends JpaRepository<ImportOperation, Long> {
    // Операции одного пользователя (для обычного пользователя). Администратор использует findAll
    Page<ImportOperation> findByUserId(Long userId, Pageable pageable);
}
