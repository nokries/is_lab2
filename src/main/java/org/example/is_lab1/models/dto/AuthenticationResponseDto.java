package org.example.is_lab1.models.dto;

import org.example.is_lab1.models.entity.Role;

// Ответ на вход / регистрацию: токены и данные пользователя (фронтенду нужны имя и роль)
public record AuthenticationResponseDto(String accessToken, String refreshToken, String username, Role role) {}
