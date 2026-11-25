package org.example.is_lab1.repository;

import jakarta.persistence.LockModeType;
import org.example.is_lab1.models.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    // Пользователь с блокировкой строки (SELECT ... FOR UPDATE).
    // Нужна, чтобы два одновременных входа одного пользователя не перемешали выдачу и отзыв токенов.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameWithLock(@Param("username") String username);
}
