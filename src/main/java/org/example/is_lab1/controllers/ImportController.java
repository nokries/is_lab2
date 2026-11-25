package org.example.is_lab1.controllers;

import org.example.is_lab1.models.dto.ImportOperationDto;
import org.example.is_lab1.models.entity.User;
import org.example.is_lab1.services.ImportOperationService;
import org.example.is_lab1.services.ImportService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportController {
    private final ImportService importService;
    private final ImportOperationService operationService;

    public ImportController(ImportService importService, ImportOperationService operationService) {
        this.importService = importService;
        this.operationService = operationService;
    }

    // Загрузка YAML-файла и массовое добавление объектов (всё или ничего)
    @PostMapping("/upload")
    public ResponseEntity<ImportOperationDto> upload(@RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal User user) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пуст");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.endsWith(".yaml") || name.endsWith(".yml"))) {
            throw new IllegalArgumentException("Допускаются только файлы формата .yaml или .yml");
        }
        return ResponseEntity.ok(importService.importFromYaml(file, user));
    }

    // История импорта: обычный пользователь видит свои операции, администратор - все
    @GetMapping("/history")
    public ResponseEntity<Page<ImportOperationDto>> history(@RequestParam(defaultValue = "0") int page,
                                                            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(operationService.getHistory(user, page));
    }
}
