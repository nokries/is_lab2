package org.example.is_lab1.models.dto;

import org.example.is_lab1.models.entity.Role;


public record AuthenticationResponseDto(String accessToken, String refreshToken, String username, Role role) {}
