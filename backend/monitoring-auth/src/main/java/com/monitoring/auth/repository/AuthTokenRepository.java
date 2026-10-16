package com.monitoring.auth.repository;

import com.monitoring.auth.entity.AuthToken;
import com.monitoring.auth.entity.AuthTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    @Transactional
    void deleteByUserIdAndType(Long userId, AuthTokenType type);
}
