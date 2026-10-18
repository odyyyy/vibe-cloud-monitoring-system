package com.monitoring.auth.organization.controller;

import com.monitoring.auth.organization.dto.request.CreateOrganizationRequest;
import com.monitoring.auth.organization.dto.request.InviteMemberRequest;
import com.monitoring.auth.organization.dto.response.MemberResponse;
import com.monitoring.auth.organization.dto.response.OrganizationResponse;
import com.monitoring.auth.organization.dto.request.UpdateMemberRoleRequest;
import com.monitoring.auth.organization.service.OrganizationService;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Organization and membership management for the currently authenticated user.
 *
 * <p>The organization in the path is authoritative for membership checks; the active
 * organization header is irrelevant here, so a user cannot manage a tenant they do not belong
 * to. Roles are enforced inside {@link OrganizationService}.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public List<OrganizationResponse> list() {
        return organizationService.listForUser(currentTenantService.requireUserId());
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse created = organizationService.createForUser(
                currentTenantService.requireUserId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{organizationId}/members")
    public List<MemberResponse> listMembers(@PathVariable Long organizationId) {
        return organizationService.listMembers(currentTenantService.requireUserId(), organizationId);
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<MemberResponse> invite(
            @PathVariable Long organizationId,
            @Valid @RequestBody InviteMemberRequest request) {
        MemberResponse member = organizationService.invite(
                currentTenantService.requireUserId(), organizationId, request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @RequestMapping(
            value = "/{organizationId}/members/{userId}",
            method = {RequestMethod.PATCH, RequestMethod.PUT})
    public MemberResponse updateRole(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return organizationService.updateRole(
                currentTenantService.requireUserId(), organizationId, userId, request.role());
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long organizationId,
            @PathVariable Long userId) {
        organizationService.removeMember(currentTenantService.requireUserId(), organizationId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{organizationId}")
    public ResponseEntity<Void> delete(@PathVariable Long organizationId) {
        organizationService.deleteOrganization(currentTenantService.requireUserId(), organizationId);
        return ResponseEntity.noContent().build();
    }
}
