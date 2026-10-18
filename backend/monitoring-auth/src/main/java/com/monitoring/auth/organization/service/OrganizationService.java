package com.monitoring.auth.organization.service;

import com.monitoring.auth.entity.User;
import com.monitoring.auth.organization.dto.MemberResponse;
import com.monitoring.auth.organization.dto.OrganizationResponse;
import com.monitoring.auth.organization.entity.Membership;
import com.monitoring.auth.organization.entity.MembershipRole;
import com.monitoring.auth.organization.entity.Organization;
import com.monitoring.auth.organization.exception.InviteTargetNotFoundException;
import com.monitoring.auth.organization.exception.LastOwnerException;
import com.monitoring.auth.organization.exception.MemberAlreadyExistsException;
import com.monitoring.auth.organization.exception.MemberNotFoundException;
import com.monitoring.auth.organization.exception.OrganizationNotFoundException;
import com.monitoring.auth.organization.exception.TenantAccessDeniedException;
import com.monitoring.auth.organization.repository.MembershipRepository;
import com.monitoring.auth.organization.repository.OrganizationRepository;
import com.monitoring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Organization and membership management.
 *
 * <p>Authorization is checked against the caller's membership in the organization named by the
 * request path (not the active header organization), so membership management can never leak
 * across tenants. Only an OWNER may delete an organization or grant the OWNER role, and an
 * organization can never be left without an owner.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final int MAX_NAME_LENGTH = 100;

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForUser(Long userId) {
        return membershipRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(membership -> new OrganizationResponse(
                        membership.getOrganizationId(),
                        organizationName(membership.getOrganizationId()),
                        membership.getRole()))
                .toList();
    }

    @Transactional
    public OrganizationResponse createForUser(Long userId, String name) {
        Organization organization = organizationRepository.save(Organization.builder()
                .name(normalizeName(name))
                .build());
        Membership membership = membershipRepository.save(Membership.builder()
                .organizationId(organization.getId())
                .userId(userId)
                .role(MembershipRole.OWNER)
                .build());
        return new OrganizationResponse(organization.getId(), organization.getName(), membership.getRole());
    }

    /**
     * Creates the personal organization every registered user starts with. Existing users are
     * backfilled by the V15 migration; new users go through this method at registration.
     */
    @Transactional
    public OrganizationResponse createPersonal(Long userId, String username) {
        String base = username == null || username.isBlank() ? "My" : username;
        String name = truncate(base + " organization", MAX_NAME_LENGTH);
        return createForUser(userId, name);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long requesterId, Long organizationId) {
        requireMember(requesterId, organizationId);

        List<Membership> memberships = membershipRepository.findByOrganizationIdOrderByIdAsc(organizationId);
        List<Long> userIds = memberships.stream().map(Membership::getUserId).toList();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return memberships.stream()
                .map(membership -> toMemberResponse(membership, users.get(membership.getUserId())))
                .toList();
    }

    @Transactional
    public MemberResponse invite(Long requesterId, Long organizationId, String email) {
        requireRole(requesterId, organizationId, MembershipRole.ADMIN, MembershipRole.OWNER);

        User target = userRepository.findByEmail(email.trim())
                .orElseThrow(InviteTargetNotFoundException::new);

        if (membershipRepository.existsByOrganizationIdAndUserId(organizationId, target.getId())) {
            throw new MemberAlreadyExistsException();
        }

        Membership membership = membershipRepository.save(Membership.builder()
                .organizationId(organizationId)
                .userId(target.getId())
                .role(MembershipRole.VIEWER)
                .build());
        return toMemberResponse(membership, target);
    }

    @Transactional
    public MemberResponse updateRole(Long requesterId, Long organizationId, Long memberUserId, MembershipRole newRole) {
        Membership requester = requireRole(requesterId, organizationId, MembershipRole.ADMIN, MembershipRole.OWNER);
        Membership target = requireTarget(organizationId, memberUserId);

        // Only an OWNER may grant OWNER, and only an OWNER may change another OWNER's role.
        if ((newRole == MembershipRole.OWNER || target.getRole() == MembershipRole.OWNER)
                && requester.getRole() != MembershipRole.OWNER) {
            throw new TenantAccessDeniedException("Only an owner can manage owner roles");
        }

        if (target.getRole() == MembershipRole.OWNER && newRole != MembershipRole.OWNER) {
            assertNotLastOwner(organizationId);
        }

        target.setRole(newRole);
        Membership saved = membershipRepository.save(target);
        return toMemberResponse(saved, userRepository.findById(saved.getUserId()).orElse(null));
    }

    @Transactional
    public void removeMember(Long requesterId, Long organizationId, Long memberUserId) {
        Membership requester = requireRole(requesterId, organizationId, MembershipRole.ADMIN, MembershipRole.OWNER);
        Membership target = requireTarget(organizationId, memberUserId);

        if (target.getRole() == MembershipRole.OWNER) {
            if (requester.getRole() != MembershipRole.OWNER) {
                throw new TenantAccessDeniedException("Only an owner can remove an owner");
            }
            assertNotLastOwner(organizationId);
        }

        membershipRepository.delete(target);
    }

    @Transactional
    public void deleteOrganization(Long requesterId, Long organizationId) {
        Membership requester = requireMember(requesterId, organizationId);
        if (requester.getRole() != MembershipRole.OWNER) {
            throw new TenantAccessDeniedException("Only an owner can delete the organization");
        }
        membershipRepository.deleteByOrganizationId(organizationId);
        organizationRepository.deleteById(organizationId);
    }

    private Membership requireMember(Long userId, Long organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new OrganizationNotFoundException(organizationId);
        }
        return membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(TenantAccessDeniedException::new);
    }

    private Membership requireRole(Long userId, Long organizationId, MembershipRole... allowed) {
        Membership membership = requireMember(userId, organizationId);
        for (MembershipRole role : allowed) {
            if (membership.getRole() == role) {
                return membership;
            }
        }
        throw new TenantAccessDeniedException("Your role does not permit this action");
    }

    private Membership requireTarget(Long organizationId, Long memberUserId) {
        return membershipRepository.findByOrganizationIdAndUserId(organizationId, memberUserId)
                .orElseThrow(MemberNotFoundException::new);
    }

    private void assertNotLastOwner(Long organizationId) {
        if (membershipRepository.countByOrganizationIdAndRole(organizationId, MembershipRole.OWNER) <= 1) {
            throw new LastOwnerException();
        }
    }

    private String organizationName(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getName)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    private MemberResponse toMemberResponse(Membership membership, User user) {
        return new MemberResponse(
                membership.getId(),
                membership.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getEmail() : null,
                membership.getRole());
    }

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Organization name is required");
        }
        return truncate(trimmed, MAX_NAME_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
