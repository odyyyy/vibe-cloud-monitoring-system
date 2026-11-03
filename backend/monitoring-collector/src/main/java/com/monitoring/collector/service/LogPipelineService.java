package com.monitoring.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.exception.ApplicationNotFoundException;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.collector.dto.request.CreateLogPipelineRuleRequest;
import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.dto.request.ReorderLogPipelineRulesRequest;
import com.monitoring.collector.dto.request.TestLogPipelineRequest;
import com.monitoring.collector.dto.request.UpdateLogPipelineRuleRequest;
import com.monitoring.collector.dto.response.LogPipelineMatchedRuleResponse;
import com.monitoring.collector.dto.response.LogPipelineRuleResponse;
import com.monitoring.collector.dto.response.LogPipelineTestOutput;
import com.monitoring.collector.dto.response.LogPipelineTestResponse;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.exception.InvalidLogPipelineRuleException;
import com.monitoring.collector.exception.LogPipelineRuleAccessDeniedException;
import com.monitoring.collector.exception.LogPipelineRuleLimitExceededException;
import com.monitoring.collector.exception.LogPipelineRuleNotFoundException;
import com.monitoring.collector.pipeline.LogPipelineEngine;
import com.monitoring.collector.pipeline.LogPipelineRuleSupport;
import com.monitoring.collector.repository.LogPipelineRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tenant-scoped CRUD and evaluation of log processing pipeline rules. */
@Service
@RequiredArgsConstructor
public class LogPipelineService {

    private final LogPipelineRuleRepository ruleRepository;
    private final ApplicationService applicationService;
    private final LogPipelineEngine engine;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<LogPipelineRuleResponse> list(Long organizationId) {
        return ruleRepository.findByOrganizationIdOrderByOrderIndexAscIdAsc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LogPipelineRuleResponse create(Long organizationId,
                                          CreateLogPipelineRuleRequest request) {
        if (ruleRepository.countByOrganizationId(organizationId)
                >= LogPipelineRule.MAX_RULES_PER_ORGANIZATION) {
            throw new LogPipelineRuleLimitExceededException(
                    LogPipelineRule.MAX_RULES_PER_ORGANIZATION);
        }

        Application application = resolveApplication(organizationId, request.getApplicationId());
        LogPipelineRuleSupport.RuleMetadata metadata = LogPipelineRuleSupport.validate(
                request.getAction(), request.getFilter(), request.getActionConfig(), objectMapper);

        LogPipelineRule rule = LogPipelineRule.builder()
                .organizationId(organizationId)
                .application(application)
                .name(request.getName().trim())
                .filterExpression(request.getFilter().trim())
                .action(request.getAction())
                .actionConfig(metadata.canonicalConfig())
                .orderIndex(ruleRepository.findMaxOrderIndex(organizationId) + 1)
                .build();
        return toResponse(ruleRepository.save(rule), metadata.extractedFields());
    }

    @Transactional
    public LogPipelineRuleResponse update(Long organizationId,
                                          Long id,
                                          UpdateLogPipelineRuleRequest request) {
        LogPipelineRule rule = getOwnedRule(organizationId, id);
        Application application = resolveApplication(organizationId, request.getApplicationId());
        LogPipelineRuleSupport.RuleMetadata metadata = LogPipelineRuleSupport.validate(
                request.getAction(), request.getFilter(), request.getActionConfig(), objectMapper);

        rule.setApplication(application);
        rule.setName(request.getName().trim());
        rule.setFilterExpression(request.getFilter().trim());
        rule.setAction(request.getAction());
        rule.setActionConfig(metadata.canonicalConfig());
        return toResponse(ruleRepository.save(rule), metadata.extractedFields());
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        ruleRepository.delete(getOwnedRule(organizationId, id));
    }

    @Transactional
    public List<LogPipelineRuleResponse> reorder(Long organizationId,
                                                 ReorderLogPipelineRulesRequest request) {
        List<Long> ids = request.getIds();
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new InvalidLogPipelineRuleException("ids must not contain duplicates");
        }

        List<LogPipelineRule> rules =
                ruleRepository.findByOrganizationIdOrderByOrderIndexAscIdAsc(organizationId);
        Set<Long> owned = new HashSet<>();
        for (LogPipelineRule rule : rules) {
            owned.add(rule.getId());
        }
        if (!owned.equals(unique)) {
            throw new InvalidLogPipelineRuleException(
                    "ids must contain every log pipeline rule of the organization exactly once");
        }

        Map<Long, LogPipelineRule> byId = new LinkedHashMap<>();
        for (LogPipelineRule rule : rules) {
            byId.put(rule.getId(), rule);
        }
        List<LogPipelineRule> ordered = new ArrayList<>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            LogPipelineRule rule = byId.get(ids.get(index));
            rule.setOrderIndex(index);
            ordered.add(rule);
        }
        return ruleRepository.saveAll(ordered).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LogPipelineTestResponse test(Long organizationId, TestLogPipelineRequest request) {
        Application application = resolveApplication(organizationId, request.getApplicationId());
        List<LogPipelineRule> rules = ruleRepository.findApplicableRules(
                organizationId, application.getId());
        LogPipelineEngine.PipelineResult result = engine.apply(
                request.getLevel(), request.getMessage(), request.getContext(), rules);

        List<LogPipelineMatchedRuleResponse> matched = result.matchedRules().stream()
                .map(rule -> new LogPipelineMatchedRuleResponse(
                        rule.id(), rule.name(), rule.action()))
                .toList();
        LogPipelineTestOutput output = result.dropped()
                ? null
                : new LogPipelineTestOutput(
                        request.getLevel(), result.message(), result.context());
        return new LogPipelineTestResponse(result.dropped(), matched, output);
    }

    @Transactional(readOnly = true)
    public List<LogPipelineRule> rulesForAgent(Application application) {
        return ruleRepository.findApplicableRules(
                application.getOrganizationId(), application.getId());
    }

    @Transactional(readOnly = true)
    public LogPipelineEngine.PipelineResult applyForIngest(Application application,
                                                           com.monitoring.collector.entity.LogLevel level,
                                                           String message,
                                                           String context) {
        List<LogPipelineRule> rules = findRulesForApplication(application);
        if (rules.isEmpty()) {
            return new LogPipelineEngine.PipelineResult(false, List.of(), message, context);
        }
        return engine.apply(level, message, context, rules);
    }

    /**
     * Applies the pipeline to a whole batch after loading the rules once. Ingestion can
     * contain up to 1000 events, so per-event rule lookups would create an N+1 query pattern.
     */
    @Transactional(readOnly = true)
    public List<LogPipelineEngine.PipelineResult> applyForIngestBatch(
            Application application,
            List<LogEventPayload> payloads) {
        List<LogPipelineRule> rules = findRulesForApplication(application);
        if (rules.isEmpty()) {
            return payloads.stream()
                    .map(payload -> new LogPipelineEngine.PipelineResult(
                            false, List.of(), payload.getMessage(), payload.getContext()))
                    .toList();
        }
        return engine.applyBatch(payloads, rules);
    }

    private List<LogPipelineRule> findRulesForApplication(Application application) {
        return ruleRepository.findApplicableRules(
                application.getOrganizationId(), application.getId());
    }

    public LogPipelineRuleResponse toResponse(LogPipelineRule rule) {
        List<String> extractedFields = List.of();
        try {
            extractedFields = LogPipelineRuleSupport.metadata(rule, objectMapper).extractedFields();
        } catch (InvalidLogPipelineRuleException ignored) {
            // A stored rule can be invalid only after manual database changes. Keep the list
            // usable and let the engine skip that rule.
        }
        return toResponse(rule, extractedFields);
    }

    private LogPipelineRuleResponse toResponse(LogPipelineRule rule,
                                               List<String> extractedFields) {
        return LogPipelineRuleResponse.fromEntity(
                rule, readConfig(rule.getActionConfig()), extractedFields);
    }

    private JsonNode readConfig(String actionConfig) {
        if (actionConfig == null || actionConfig.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(actionConfig);
        } catch (Exception e) {
            return null;
        }
    }

    private Application resolveApplication(Long organizationId, Long applicationId) {
        if (applicationId == null) {
            return null;
        }
        try {
            return applicationService.getOwnedApplication(applicationId, organizationId);
        } catch (ApplicationNotFoundException | ApplicationAccessDeniedException e) {
            throw new InvalidLogPipelineRuleException(
                    "The referenced application does not exist or is not yours");
        }
    }

    private LogPipelineRule getOwnedRule(Long organizationId, Long id) {
        LogPipelineRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new LogPipelineRuleNotFoundException(id));
        if (!rule.getOrganizationId().equals(organizationId)) {
            throw new LogPipelineRuleAccessDeniedException();
        }
        return rule;
    }
}
