package org.example.is_lab1.services;

import jakarta.servlet.http.HttpServletRequest;
import org.example.is_lab1.exceptions.UniquenessViolationException;
import org.example.is_lab1.models.dto.AuthenticationResponseDto;
import org.example.is_lab1.models.dto.RegistrationRequestDto;
import org.example.is_lab1.models.entity.Role;
import org.example.is_lab1.models.entity.Token;
import org.example.is_lab1.models.entity.User;
import org.example.is_lab1.repository.TokenRepository;
import org.example.is_lab1.repository.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenRepository tokenRepository;

    public AuthenticationService(UserRepository userRepository,
                                 JWTService jwtService,
                                 PasswordEncoder passwordEncoder,
                                 AuthenticationManager authenticationManager,
                                 TokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenRepository = tokenRepository;
    }


    @Transactional
    public AuthenticationResponseDto register(RegistrationRequestDto request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new UniquenessViolationException("Пользователь с таким именем уже существует");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role() != null ? request.role() : Role.USER);
        userRepository.save(user);

        return issueTokens(user);
    }


    @Transactional
    public AuthenticationResponseDto authenticate(RegistrationRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsernameWithLock(request.username())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        revokeAllTokens(user);
        return issueTokens(user);
    }

    @Transactional
    public Optional<AuthenticationResponseDto> refreshToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring(7);
        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            return Optional.empty();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        if (!jwtService.isValidRefresh(token, user)) {
            return Optional.empty();
        }

        revokeAllTokens(user);
        return Optional.of(issueTokens(user));
    }

    private void revokeAllTokens(User user) {
        List<Token> validTokens = tokenRepository.findAllAccessTokenByUser(user.getId());
        validTokens.forEach(t -> t.setLoggedOut(true));
        tokenRepository.saveAll(validTokens);
    }

    private AuthenticationResponseDto issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        Token token = new Token();
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setLoggedOut(false);
        token.setUser(user);
        tokenRepository.save(token);

        return new AuthenticationResponseDto(accessToken, refreshToken, user.getUsername(), user.getRole());
    }
}
