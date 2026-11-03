package com.monitoring.healthcheck.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.healthcheck.dto.StepExecutionResult;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Serializes workflow definitions and per-step diagnostics to JSON.
 *
 * <p>Parsing stored definitions is defensive: a row that cannot be decoded (for example after a
 * manual database edit) degrades to an empty list instead of breaking the whole health-check
 * listing. The executor treats an empty definition as a failed check.
 */
@Component
public class SyntheticCheckCodec {

    private static final Logger log = LoggerFactory.getLogger(SyntheticCheckCodec.class);

    private static final TypeReference<List<SyntheticApiStep>> STEPS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<StepExecutionResult>> STEP_RESULTS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public SyntheticCheckCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeSteps(List<SyntheticApiStep> steps) {
        return write(steps);
    }

    public List<SyntheticApiStep> readSteps(String json) {
        return read(json, STEPS_TYPE);
    }

    public String writeStepResults(List<StepExecutionResult> stepResults) {
        return write(stepResults);
    }

    public List<StepExecutionResult> readStepResults(String json) {
        return read(json, STEP_RESULTS_TYPE);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize synthetic check definition", ex);
        }
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> value = objectMapper.readValue(json, type);
            return value == null ? List.of() : value;
        } catch (JsonProcessingException ex) {
            log.warn("Ignoring unreadable synthetic check JSON: {}", ex.getOriginalMessage());
            return List.of();
        }
    }
}
