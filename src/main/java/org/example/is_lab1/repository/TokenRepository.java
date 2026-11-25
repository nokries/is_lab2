package org.example.is_lab1.repository;

import org.example.is_lab1.models.entity.Token;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    @Query("SELECT t FROM Token t WHERE t.user.id = :userId AND t.loggedOut = false")
    List<Token> findAllAccessTokenByUser(@Param("userId") Long userId);

    @Query("SELECT t FROM Token t WHERE t.accessToken = :accessToken AND t.loggedOut = false ORDER BY t.id DESC")
    List<Token> findByAccessTokenAndLoggedOutFalse(@Param("accessToken") String accessToken, Pageable pageable);

    default Optional<Token> findFirstByAccessTokenAndLoggedOutFalse(String accessToken) {
        List<Token> tokens = findByAccessTokenAndLoggedOutFalse(accessToken, Pageable.ofSize(1));
        return tokens.isEmpty() ? Optional.empty() : Optional.of(tokens.get(0));
    }

    @Query("SELECT t FROM Token t WHERE t.refreshToken = :refreshToken AND t.loggedOut = false ORDER BY t.id DESC")
    List<Token> findByRefreshTokenAndLoggedOutFalse(@Param("refreshToken") String refreshToken, Pageable pageable);

    default Optional<Token> findFirstByRefreshTokenAndLoggedOutFalse(String refreshToken) {
        List<Token> tokens = findByRefreshTokenAndLoggedOutFalse(refreshToken, Pageable.ofSize(1));
        return tokens.isEmpty() ? Optional.empty() : Optional.of(tokens.get(0));
    }
}
