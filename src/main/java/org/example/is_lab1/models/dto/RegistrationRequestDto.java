package org.example.is_lab1.models.dto;

import jakarta.validation.constraints.NotBlank;
import org.example.is_lab1.models.entity.Role;

public record RegistrationRequestDto(
        @NotBlank(message = "Имя пользователя не может быть пустым") String username,
        @NotBlank(message = "Пароль не может быть пустым") String password,
        Role role) {}
