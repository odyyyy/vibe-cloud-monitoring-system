package com.monitoring.auth.repository;

import com.monitoring.auth.entity.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findByUserIdAndOrganizationIdAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, Long organizationId, Instant now);

    Optional<PersonalAccessToken> findByIdAndUserIdAndOrganizationId(
            Long id, Long userId, Long organizationId);
}
