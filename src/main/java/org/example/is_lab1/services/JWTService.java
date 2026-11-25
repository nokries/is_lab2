package org.example.is_lab1.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.example.is_lab1.models.entity.User;
import org.example.is_lab1.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JWTService {

    @Value("${security.jwt.secret_key}")
    private String secretKey;

    @Value("${security.jwt.access_token_expiration}")
    private long accessTokenExpiration;

    @Value("${security.jwt.refresh_token_expiration}")
    private long refreshTokenExpiration;

    private final TokenRepository tokenRepository;

    public JWTService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64URL.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // В токен кладётся уникальный jti: два токена одного пользователя, выданные в одну миллисекунду,
    // всё равно получаются разными
    private String generateToken(User user, long expiryTime) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryTime))
                .claim("jti", UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    public String generateAccessToken(User user) {
        return generateToken(user, accessTokenExpiration);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, refreshTokenExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private boolean isNotExpired(String token) {
        return !extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // Access-токен действителен, если он есть в БД и не отозван (выход / повторный вход),
    // принадлежит этому пользователю и не истёк
    public boolean isValid(String token, UserDetails user) {
        try {
            if (tokenRepository.findFirstByAccessTokenAndLoggedOutFalse(token).isEmpty()) {
                return false;
            }
            String username = extractUsername(token);
            return username.equals(user.getUsername()) && isNotExpired(token);
        } catch (Exception e) {
            // Подпись не сошлась, токен повреждён и т.п.
            return false;
        }
    }

    // То же для refresh-токена
    public boolean isValidRefresh(String token, User user) {
        String username = extractUsername(token);
        boolean known = tokenRepository.findFirstByRefreshTokenAndLoggedOutFalse(token).isPresent();
        return username.equals(user.getUsername()) && isNotExpired(token) && known;
    }
}
