package com.monitoring.collector.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.AgentNodeRepository;
import com.monitoring.apps.service.AgentService;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.collector.dto.request.AgentConfigStatusRequest;
import com.monitoring.collector.dto.request.AssignAgentConfigTemplateRequest;
import com.monitoring.collector.dto.request.CreateAgentConfigTemplateRequest;
import com.monitoring.collector.dto.request.UpdateAgentConfigTemplateRequest;
import com.monitoring.collector.dto.response.ActiveAgentConfigResponse;
import com.monitoring.collector.dto.response.AgentConfigTemplateResponse;
import com.monitoring.collector.dto.response.ApplicationAgentConfigResponse;
import com.monitoring.collector.entity.AgentConfigAssignment;
import com.monitoring.collector.entity.AgentConfigStatus;
import com.monitoring.collector.entity.AgentConfigTemplate;
import com.monitoring.collector.exception.AgentConfigTemplateInUseException;
import com.monitoring.collector.exception.AgentConfigTemplateLimitExceededException;
import com.monitoring.collector.exception.AgentConfigTemplateNameConflictException;
import com.monitoring.collector.exception.AgentConfigTemplateNotFoundException;
import com.monitoring.collector.repository.AgentConfigAssignmentRepository;
import com.monitoring.collector.repository.AgentConfigTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped management and resolution of centralized agent configuration templates. */
@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private final AgentConfigTemplateRepository templateRepository;
    private final AgentConfigAssignmentRepository assignmentRepository;
    private final AgentNodeRepository agentNodeRepository;
    private final ApplicationService applicationService;
    private final AgentService agentService;

    @Transactional(readOnly = true)
    public List<AgentConfigTemplateResponse> list(Long organizationId) {
        return templateRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(AgentConfigTemplateResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AgentConfigTemplateResponse create(Long organizationId,
                                              CreateAgentConfigTemplateRequest request) {
        if (templateRepository.countByOrganizationId(organizationId)
                >= AgentConfigTemplate.MAX_TEMPLATES_PER_ORGANIZATION) {
            throw new AgentConfigTemplateLimitExceededException(
                    AgentConfigTemplate.MAX_TEMPLATES_PER_ORGANIZATION);
        }

        String name = request.getName().trim();
        if (templateRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw new AgentConfigTemplateNameConflictException(name);
        }

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        AgentConfigTemplate template = AgentConfigTemplate.builder()
                .organizationId(organizationId)
                .name(name)
                .configYaml(normalizeYaml(request.getConfigYaml()))
                .isDefault(makeDefault)
                .build();
        AgentConfigTemplate saved = templateRepository.save(template);

        if (makeDefault) {
            templateRepository.clearDefault(organizationId, saved.getId());
            agentNodeRepository.markConfigPendingByOrganizationId(organizationId);
        }
        return AgentConfigTemplateResponse.fromEntity(saved);
    }

    @Transactional
    public AgentConfigTemplateResponse update(Long organizationId,
                                              Long id,
                                              UpdateAgentConfigTemplateRequest request) {
        AgentConfigTemplate template = getOwnedTemplate(organizationId, id);
        String name = request.getName().trim();
        if (templateRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, id)) {
            throw new AgentConfigTemplateNameConflictException(name);
        }

        boolean wasDefault = template.isDefault();
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        template.setName(name);
        template.setConfigYaml(normalizeYaml(request.getConfigYaml()));
        template.setDefault(makeDefault);

        if (makeDefault) {
            templateRepository.clearDefault(organizationId, id);
        }
        AgentConfigTemplate saved = templateRepository.save(template);

        if (wasDefault || makeDefault) {
            agentNodeRepository.markConfigPendingByOrganizationId(organizationId);
        } else {
            markAssignedApplicationsPending(id);
        }
        return AgentConfigTemplateResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        AgentConfigTemplate template = getOwnedTemplate(organizationId, id);
        if (!assignmentRepository.findByTemplateId(id).isEmpty()) {
            throw new AgentConfigTemplateInUseException(id);
        }
        boolean wasDefault = template.isDefault();
        templateRepository.delete(template);
        if (wasDefault) {
            agentNodeRepository.markConfigPendingByOrganizationId(organizationId);
        }
    }

    @Transactional
    public ApplicationAgentConfigResponse assign(Long organizationId,
                                                 Long applicationId,
                                                 AssignAgentConfigTemplateRequest request) {
        Application application = applicationService.getOwnedApplication(applicationId, organizationId);
        Long templateId = request.getTemplateId();

        AgentConfigTemplate selected = null;
        if (templateId == null) {
            assignmentRepository.deleteByApplicationId(applicationId);
        } else {
            selected = getOwnedTemplate(organizationId, templateId);
            AgentConfigAssignment assignment = assignmentRepository.findByApplicationId(applicationId)
                    .orElseGet(() -> AgentConfigAssignment.builder()
                            .application(application)
                            .build());
            assignment.setTemplate(selected);
            assignmentRepository.save(assignment);
        }

        agentNodeRepository.markConfigPendingByApplicationId(applicationId);
        return applicationConfigResponse(organizationId, applicationId, selected);
    }

    @Transactional(readOnly = true)
    public ApplicationAgentConfigResponse applicationConfig(Long organizationId, Long applicationId) {
        applicationService.getOwnedApplication(applicationId, organizationId);
        Optional<AgentConfigAssignment> assignment = assignmentRepository.findByApplicationId(applicationId);
        return applicationConfigResponse(
                organizationId,
                applicationId,
                assignment.map(AgentConfigAssignment::getTemplate).orElse(null));
    }

    private ApplicationAgentConfigResponse applicationConfigResponse(
            Long organizationId,
            Long applicationId,
            AgentConfigTemplate assignedTemplate) {
        Long defaultTemplateId = templateRepository
                .findFirstByOrganizationIdAndIsDefaultTrue(organizationId)
                .map(AgentConfigTemplate::getId)
                .orElse(null);
        AgentConfigYaml.Resolved resolved = resolve(organizationId, applicationId);
        return new ApplicationAgentConfigResponse(
                applicationId,
                assignedTemplate == null ? null : assignedTemplate.getId(),
                assignedTemplate == null ? null : assignedTemplate.getName(),
                defaultTemplateId,
                resolved.configYaml(),
                resolved.configVersion());
    }

    /** Returns the merged config for the authenticated agent's application. */
    @Transactional(readOnly = true)
    public ActiveAgentConfigResponse activeConfig(Application application) {
        AgentConfigYaml.Resolved resolved = resolve(application.getOrganizationId(), application.getId());
        return new ActiveAgentConfigResponse(resolved.configVersion(), resolved.configYaml());
    }

    /**
     * Persists the agent's sync result. A "synced" report for an outdated version is downgraded
     * to pending so a stale agent cannot make the UI claim the current config is applied.
     */
    @Transactional
    public void reportStatus(Application application, AgentConfigStatusRequest request) {
        String activeVersion = resolve(application.getOrganizationId(), application.getId()).configVersion();
        String status = request.getStatus().name();
        if (request.getStatus() == AgentConfigStatus.SYNCED
                && !activeVersion.equals(request.getConfigVersion())) {
            status = AgentConfigStatus.PENDING.name();
        }

        agentService.reportConfigStatus(
                application,
                request.getHostname(),
                request.getConfigVersion(),
                status,
                request.getErrorMessage(),
                Instant.now());
    }

    private AgentConfigYaml.Resolved resolve(Long organizationId, Long applicationId) {
        String defaultYaml = templateRepository
                .findFirstByOrganizationIdAndIsDefaultTrue(organizationId)
                .map(AgentConfigTemplate::getConfigYaml)
                .orElse(null);
        String assignedYaml = assignmentRepository.findByApplicationId(applicationId)
                .map(assignment -> assignment.getTemplate().getConfigYaml())
                .orElse(null);
        return AgentConfigYaml.resolve(defaultYaml, assignedYaml);
    }

    private void markAssignedApplicationsPending(Long templateId) {
        assignmentRepository.findByTemplateId(templateId)
                .forEach(assignment ->
                        agentNodeRepository.markConfigPendingByApplicationId(
                                assignment.getApplication().getId()));
    }

    private AgentConfigTemplate getOwnedTemplate(Long organizationId, Long id) {
        return templateRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new AgentConfigTemplateNotFoundException(id));
    }

    private static String normalizeYaml(String value) {
        return value == null || value.isBlank() ? "{}\n" : value;
    }
}
