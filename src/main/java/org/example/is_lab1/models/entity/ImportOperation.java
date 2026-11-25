package org.example.is_lab1.models.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Запись истории импорта: одна загрузка файла = одна операция
@Entity
@Table(name = "import_operation")
public class ImportOperation {
    @Id
    @SequenceGenerator(name = "import_operation_gen", sequenceName = "import_operation_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "import_operation_gen")
    private Long id;

    // Пользователь, который запустил операцию
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportStatus status;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    // Сколько объектов добавлено. Заполняется только у успешно завершённых операций
    @Column(name = "added_count")
    private Integer addedCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public ImportOperation() {}

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Integer getAddedCount() {
        return addedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setAddedCount(Integer addedCount) {
        this.addedCount = addedCount;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
