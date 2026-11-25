package org.example.is_lab1.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.is_lab1.models.dto.AuthenticationResponseDto;
import org.example.is_lab1.models.dto.RegistrationRequestDto;
import org.example.is_lab1.services.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Эндпоинты входа лежат под /api/auth, чтобы проксирование /api (nginx, vite) продолжало работать без изменений.
// Выход (logout) обрабатывает Spring Security: POST /api/auth/logout (см. SecurityConfig)
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    // Пользователь с таким именем уже есть - UniquenessViolationException, ExceptionHandler отвечает кодом 409
    @PostMapping("/registration")
    public ResponseEntity<AuthenticationResponseDto> register(@Valid @RequestBody RegistrationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDto> authenticate(@Valid @RequestBody RegistrationRequestDto request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @PostMapping("/refresh_token")
    public ResponseEntity<AuthenticationResponseDto> refreshToken(HttpServletRequest request) {
        return authenticationService.refreshToken(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
