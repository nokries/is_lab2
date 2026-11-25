package org.example.is_lab1.models.entity;

import jakarta.persistence.*;

// Выданная пара JWT-токенов. Токен считается действующим, пока is_logged_out = false
@Entity
@Table(name = "token_table")
public class Token {
    @Id
    @SequenceGenerator(name = "token_gen", sequenceName = "token_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "token_gen")
    @Column(name = "id")
    private Long id;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "is_logged_out", nullable = false)
    private boolean loggedOut;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Token() {}

    public Long getId() {
        return id;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean isLoggedOut() {
        return loggedOut;
    }

    public User getUser() {
        return user;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setLoggedOut(boolean loggedOut) {
        this.loggedOut = loggedOut;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
