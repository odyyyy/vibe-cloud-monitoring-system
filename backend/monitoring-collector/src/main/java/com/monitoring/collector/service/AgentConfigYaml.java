package com.monitoring.collector.service;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Merges organization-default and application-specific YAML overrides into the document served
 * to agents, and derives a stable SHA-256 version from the canonical result.
 *
 * <p>Mappings are merged recursively, lists and scalars replace the base value. If either
 * template contains invalid YAML or a non-mapping document, the raw invalid document is served
 * so the agent can reject it and report an error while keeping its last known good config.
 */
final class AgentConfigYaml {

    private static final int MAX_ALIASES = 50;

    private AgentConfigYaml() {
    }

    record Resolved(String configYaml, String configVersion) {
    }

    static Resolved resolve(String defaultYaml, String overrideYaml) {
        Map<String, Object> defaults = parse(defaultYaml);
        Map<String, Object> override = parse(overrideYaml);

        if (defaults == null && override == null) {
            // The failing document is more useful to the agent than the other valid one: it
            // contains the invalid text the user must correct.
            String invalid = overrideYaml != null && !overrideYaml.isBlank() ? overrideYaml : defaultYaml;
            String raw = invalid == null ? "" : invalid;
            return new Resolved(raw, hash("invalid\n" + raw));
        }
        if (override == null) {
            String raw = overrideYaml == null ? "" : overrideYaml;
            return new Resolved(raw, hash("invalid\n" + raw));
        }
        if (defaults == null) {
            String raw = defaultYaml == null ? "" : defaultYaml;
            return new Resolved(raw, hash("invalid\n" + raw));
        }

        Map<String, Object> merged = deepMerge(defaults, override);
        String yaml = dump(merged);
        return new Resolved(yaml, hash(yaml));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(MAX_ALIASES);
            options.setCodePointLimit(256 * 1024);
            Object loaded = new Yaml(new SafeConstructor(options)).load(yaml);
            if (loaded == null) {
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    return null;
                }
                result.put(key, entry.getValue());
            }
            return result;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object existing = result.get(entry.getKey());
            Object incoming = entry.getValue();
            if (existing instanceof Map<?, ?> existingMap && incoming instanceof Map<?, ?> incomingMap) {
                result.put(entry.getKey(), deepMerge(
                        (Map<String, Object>) existingMap,
                        (Map<String, Object>) incomingMap));
            } else {
                result.put(entry.getKey(), incoming);
            }
        }
        return result;
    }

    private static String dump(Map<String, Object> value) {
        if (value.isEmpty()) {
            return "{}\n";
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setWidth(120);
        options.setSplitLines(false);
        return new Yaml(options).dump(canonicalize(value));
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sorted.put(key, canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(canonicalize(item));
            }
            return copy;
        }
        return value;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
