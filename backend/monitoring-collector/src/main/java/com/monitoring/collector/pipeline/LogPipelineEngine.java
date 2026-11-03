package com.monitoring.collector.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.exception.InvalidLogPipelineRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the compiled rules in order. The same engine is used by ingestion (fallback for direct
 * API senders) and by the pipeline test endpoint, which keeps the simulation faithful.
 */
@Component
public final class LogPipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(LogPipelineEngine.class);
    private static final int MAX_MESSAGE_CHARS = 8192;
    private static final int MAX_CAPTURED_VALUE_CHARS = 256;

    private final ObjectMapper objectMapper;

    public LogPipelineEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PipelineResult apply(LogLevel level,
                                String message,
                                String context,
                                List<LogPipelineRule> rules) {
        List<CompiledLogPipelineRule> compiled = compile(rules);
        return applyCompiled(level, message, context, compiled);
    }

    /**
     * Applies the rules to a batch, compiling the regexes once. This is the ingestion path:
     * compiling every rule for every event would be prohibitively expensive for large batches.
     */
    public List<PipelineResult> applyBatch(List<LogEventPayload> payloads,
                                           List<LogPipelineRule> rules) {
        List<CompiledLogPipelineRule> compiled = compile(rules);
        List<PipelineResult> results = new ArrayList<>(payloads.size());
        for (LogEventPayload payload : payloads) {
            results.add(applyCompiled(
                    payload.getLevel(), payload.getMessage(), payload.getContext(), compiled));
        }
        return List.copyOf(results);
    }

    List<CompiledLogPipelineRule> compile(List<LogPipelineRule> rules) {
        List<CompiledLogPipelineRule> compiled = new ArrayList<>(rules.size());
        for (LogPipelineRule rule : rules) {
            try {
                compiled.add(LogPipelineRuleCompiler.compile(rule, objectMapper));
            } catch (InvalidLogPipelineRuleException e) {
                // Invalid persisted configuration must not block the whole ingestion batch.
                log.warn("Skipping invalid log pipeline rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
        return compiled;
    }

    private PipelineResult applyCompiled(LogLevel level,
                                         String message,
                                         String context,
                                         List<CompiledLogPipelineRule> rules) {
        String currentMessage = message;
        LogPipelineContext document = new LogPipelineContext(objectMapper, context);
        List<MatchedRule> matched = new ArrayList<>();

        for (CompiledLogPipelineRule rule : rules) {
            if (!rule.matches(level, currentMessage, document)) {
                continue;
            }
            matched.add(new MatchedRule(rule.id(), rule.name(), rule.action()));

            if (rule.isDrop()) {
                return new PipelineResult(true, List.copyOf(matched), null, null);
            }

            try {
                if (rule.isParse()) {
                    if (!document.isValid()) {
                        continue;
                    }
                    java.util.Map<String, String> additions = new java.util.LinkedHashMap<>();
                    rule.parse(currentMessage).forEach((key, value) ->
                            additions.put(key, truncate(value, MAX_CAPTURED_VALUE_CHARS)));
                    document.putAll(additions);
                } else if (rule.isAddAttribute()) {
                    document.put(rule.attributeKey(), rule.attributeValue());
                } else if (rule.isMask()) {
                    if (rule.masksMessage()) {
                        String masked = rule.replaceAll(currentMessage);
                        if (masked.length() <= MAX_MESSAGE_CHARS) {
                            currentMessage = masked;
                        }
                    } else {
                        document.mask(rule.maskField(), rule.regex());
                    }
                }
            } catch (RegexTimeoutException e) {
                // A timed-out regex does not match; the event continues through the remaining
                // rules and the log is never lost because of a costly pattern.
            }
        }

        return new PipelineResult(false, List.copyOf(matched), currentMessage, document.serialize());
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    public record MatchedRule(Long id, String name,
                              com.monitoring.collector.entity.LogPipelineAction action) {
    }

    public record PipelineResult(boolean dropped, List<MatchedRule> matchedRules,
                                 String message, String context) {
    }
}
