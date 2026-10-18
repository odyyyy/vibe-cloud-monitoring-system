package com.monitoring.organization.repository;

import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.entity.MembershipRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    /** All memberships of a user, oldest first; the first one is the default organization. */
    List<Membership> findByUserIdOrderByIdAsc(Long userId);

    List<Membership> findByOrganizationIdOrderByIdAsc(Long organizationId);

    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

    long countByOrganizationIdAndRole(Long organizationId, MembershipRole role);

    void deleteByOrganizationId(Long organizationId);
}
