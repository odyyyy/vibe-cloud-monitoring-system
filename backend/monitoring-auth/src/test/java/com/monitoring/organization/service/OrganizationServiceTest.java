package com.monitoring.organization.service;

import com.monitoring.auth.entity.User;
import com.monitoring.organization.dto.response.MemberResponse;
import com.monitoring.organization.dto.response.OrganizationResponse;
import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.exception.InviteTargetNotFoundException;
import com.monitoring.organization.exception.LastOwnerException;
import com.monitoring.organization.exception.MemberAlreadyExistsException;
import com.monitoring.organization.exception.MemberNotFoundException;
import com.monitoring.organization.exception.OrganizationNotFoundException;
import com.monitoring.organization.exception.TenantAccessDeniedException;
import com.monitoring.organization.repository.MembershipRepository;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    private static final long ORG_ID = 10L;
    private static final long REQUESTER_ID = 1L;
    private static final long TARGET_ID = 2L;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, membershipRepository, userRepository);
    }

    @Test
    void createForUser_persistsOrganizationWithOwnerMembership() {
        given(organizationRepository.save(any(Organization.class))).willAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            organization.setId(ORG_ID);
            return organization;
        });
        given(membershipRepository.save(any(Membership.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrganizationResponse created = service.createForUser(REQUESTER_ID, "  Acme  ");

        assertThat(created.id()).isEqualTo(ORG_ID);
        assertThat(created.name()).isEqualTo("Acme");
        assertThat(created.role()).isEqualTo(MembershipRole.OWNER);

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        then(membershipRepository).should().save(membership.capture());
        assertThat(membership.getValue().getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(membership.getValue().getUserId()).isEqualTo(REQUESTER_ID);
        assertThat(membership.getValue().getRole()).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void createPersonal_usesUsernameAndFallsBackForBlankName() {
        given(organizationRepository.save(any(Organization.class))).willAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            organization.setId(ORG_ID);
            return organization;
        });
        given(membershipRepository.save(any(Membership.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createPersonal(REQUESTER_ID, "ada").name()).isEqualTo("ada organization");
        assertThat(service.createPersonal(REQUESTER_ID, "  ").name()).isEqualTo("My organization");
    }

    @Test
    void listForUser_returnsOnlyOwnMemberships() {
        Membership membership = membership(ORG_ID, REQUESTER_ID, MembershipRole.ADMIN);
        given(membershipRepository.findByUserIdOrderByIdAsc(REQUESTER_ID)).willReturn(List.of(membership));
        given(organizationRepository.findById(ORG_ID))
                .willReturn(Optional.of(Organization.builder().id(ORG_ID).name("Acme").build()));

        List<OrganizationResponse> organizations = service.listForUser(REQUESTER_ID);

        assertThat(organizations).containsExactly(new OrganizationResponse(ORG_ID, "Acme", MembershipRole.ADMIN));
    }

    @Test
    void listForUser_surfacesMissingOrganization() {
        Membership stale = membership(ORG_ID, REQUESTER_ID, MembershipRole.OWNER);
        given(membershipRepository.findByUserIdOrderByIdAsc(REQUESTER_ID)).willReturn(List.of(stale));
        given(organizationRepository.findById(ORG_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForUser(REQUESTER_ID))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void listMembers_requiresMembershipAndResolvesUserDetails() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.VIEWER)));
        Membership viewer = membership(ORG_ID, TARGET_ID, MembershipRole.VIEWER);
        given(membershipRepository.findByOrganizationIdOrderByIdAsc(ORG_ID)).willReturn(List.of(viewer));
        given(userRepository.findAllById(List.of(TARGET_ID))).willReturn(List.of(user(TARGET_ID, "grace")));

        List<MemberResponse> members = service.listMembers(REQUESTER_ID, ORG_ID);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).userId()).isEqualTo(TARGET_ID);
        assertThat(members.get(0).username()).isEqualTo("grace");
        assertThat(members.get(0).role()).isEqualTo(MembershipRole.VIEWER);
    }

    @Test
    void listMembers_rejectsNonMember() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(REQUESTER_ID, ORG_ID))
                .isInstanceOf(TenantAccessDeniedException.class);

        then(membershipRepository).should(never()).findByOrganizationIdOrderByIdAsc(ORG_ID);
    }

    @Test
    void listMembers_rejectsUnknownOrganization() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(false);

        assertThatThrownBy(() -> service.listMembers(REQUESTER_ID, ORG_ID))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void invite_createsViewerMembershipForAdmin() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.ADMIN)));
        User target = user(TARGET_ID, "grace");
        given(userRepository.findByEmail("grace@example.com")).willReturn(Optional.of(target));
        given(membershipRepository.existsByOrganizationIdAndUserId(ORG_ID, TARGET_ID)).willReturn(false);
        given(membershipRepository.save(any(Membership.class))).willAnswer(invocation -> invocation.getArgument(0));

        MemberResponse member = service.invite(REQUESTER_ID, ORG_ID, " grace@example.com ");

        assertThat(member.userId()).isEqualTo(TARGET_ID);
        assertThat(member.role()).isEqualTo(MembershipRole.VIEWER);
    }

    @Test
    void invite_rejectsViewer() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.VIEWER)));

        assertThatThrownBy(() -> service.invite(REQUESTER_ID, ORG_ID, "grace@example.com"))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void invite_rejectsUnknownTargetAndExistingMember() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.OWNER)));
        given(userRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(REQUESTER_ID, ORG_ID, "missing@example.com"))
                .isInstanceOf(InviteTargetNotFoundException.class);

        given(userRepository.findByEmail("grace@example.com")).willReturn(Optional.of(user(TARGET_ID, "grace")));
        given(membershipRepository.existsByOrganizationIdAndUserId(ORG_ID, TARGET_ID)).willReturn(true);

        assertThatThrownBy(() -> service.invite(REQUESTER_ID, ORG_ID, "grace@example.com"))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    void updateRole_adminCannotGrantOrChangeOwner() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.ADMIN)));
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID))
                .willReturn(Optional.of(membership(ORG_ID, TARGET_ID, MembershipRole.VIEWER)));

        assertThatThrownBy(() -> service.updateRole(REQUESTER_ID, ORG_ID, TARGET_ID, MembershipRole.OWNER))
                .isInstanceOf(TenantAccessDeniedException.class);

        Membership targetOwner = membership(ORG_ID, TARGET_ID, MembershipRole.OWNER);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID))
                .willReturn(Optional.of(targetOwner));

        assertThatThrownBy(() -> service.updateRole(REQUESTER_ID, ORG_ID, TARGET_ID, MembershipRole.ADMIN))
                .isInstanceOf(TenantAccessDeniedException.class);
        then(membershipRepository).should(never()).save(any(Membership.class));
    }

    @Test
    void updateRole_ownerCannotLeaveOrganizationWithoutOwner() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.OWNER)));
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID))
                .willReturn(Optional.of(membership(ORG_ID, TARGET_ID, MembershipRole.OWNER)));
        given(membershipRepository.countByOrganizationIdAndRole(ORG_ID, MembershipRole.OWNER)).willReturn(1L);

        assertThatThrownBy(() -> service.updateRole(REQUESTER_ID, ORG_ID, TARGET_ID, MembershipRole.ADMIN))
                .isInstanceOf(LastOwnerException.class);
    }

    @Test
    void updateRole_changesViewerToAdmin() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.OWNER)));
        Membership target = membership(ORG_ID, TARGET_ID, MembershipRole.VIEWER);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID)).willReturn(Optional.of(target));
        given(membershipRepository.save(any(Membership.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(user(TARGET_ID, "grace")));

        MemberResponse updated = service.updateRole(REQUESTER_ID, ORG_ID, TARGET_ID, MembershipRole.ADMIN);

        assertThat(updated.role()).isEqualTo(MembershipRole.ADMIN);
    }

    @Test
    void removeMember_enforcesRolesAndLastOwner() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.ADMIN)));
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID))
                .willReturn(Optional.of(membership(ORG_ID, TARGET_ID, MembershipRole.VIEWER)));

        service.removeMember(REQUESTER_ID, ORG_ID, TARGET_ID);
        then(membershipRepository).should().delete(any(Membership.class));

        Membership owner = membership(ORG_ID, TARGET_ID, MembershipRole.OWNER);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.removeMember(REQUESTER_ID, ORG_ID, TARGET_ID))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void removeMember_rejectsUnknownMember() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.OWNER)));
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, TARGET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(REQUESTER_ID, ORG_ID, TARGET_ID))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void deleteOrganization_requiresOwnerAndRemovesMemberships() {
        given(organizationRepository.existsById(ORG_ID)).willReturn(true);
        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.OWNER)));

        service.deleteOrganization(REQUESTER_ID, ORG_ID);

        then(membershipRepository).should().deleteByOrganizationId(ORG_ID);
        then(organizationRepository).should().deleteById(ORG_ID);

        given(membershipRepository.findByOrganizationIdAndUserId(ORG_ID, REQUESTER_ID))
                .willReturn(Optional.of(membership(ORG_ID, REQUESTER_ID, MembershipRole.ADMIN)));

        assertThatThrownBy(() -> service.deleteOrganization(REQUESTER_ID, ORG_ID))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    private static Membership membership(Long organizationId, Long userId, MembershipRole role) {
        return Membership.builder()
                .id(userId)
                .organizationId(organizationId)
                .userId(userId)
                .role(role)
                .build();
    }

    private static User user(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .build();
    }
}
