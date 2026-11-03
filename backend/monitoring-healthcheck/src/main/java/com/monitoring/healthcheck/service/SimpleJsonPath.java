package com.monitoring.healthcheck.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Resolves a deliberately small JSONPath subset against a parsed JSON document.
 *
 * <p>Supported syntax: a leading {@code $}, dot-separated object members ({@code $.a.b}) and
 * array indexes ({@code $.items[0].id}). Quoted member notation ({@code $['a-b']}) is accepted.
 * Wildcards, recursive descent, filters, slices and expressions are <em>not</em> supported: the
 * extraction language must stay side-effect free and cheap to evaluate on untrusted payloads.
 */
public final class SimpleJsonPath {

    private SimpleJsonPath() {
    }

    public static boolean isValid(String path) {
        return parse(path) != null;
    }

    public static Optional<JsonNode> resolve(JsonNode root, String path) {
        if (root == null) {
            return Optional.empty();
        }
        String[] tokens = parse(path);
        if (tokens == null) {
            return Optional.empty();
        }
        JsonNode current = root;
        for (String token : tokens) {
            if (token.startsWith("#")) {
                int index;
                try {
                    index = Integer.parseInt(token.substring(1));
                } catch (NumberFormatException ex) {
                    return Optional.empty();
                }
                if (!current.isArray() || index < 0 || index >= current.size()) {
                    return Optional.empty();
                }
                current = current.get(index);
            } else {
                if (!current.isObject() || !current.has(token)) {
                    return Optional.empty();
                }
                current = current.get(token);
            }
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    /**
     * @return member tokens (array indexes prefixed with {@code #}) or {@code null} when invalid
     */
    private static String[] parse(String path) {
        if (path == null) {
            return null;
        }
        String value = path.trim();
        if (value.isEmpty() || value.charAt(0) != '$') {
            return null;
        }
        java.util.List<String> tokens = new java.util.ArrayList<>();
        int index = 1;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (current == '.') {
                int start = ++index;
                while (index < value.length() && value.charAt(index) != '.' && value.charAt(index) != '[') {
                    index++;
                }
                String member = value.substring(start, index);
                if (!isMemberName(member)) {
                    return null;
                }
                tokens.add(member);
            } else if (current == '[') {
                index++;
                if (index >= value.length()) {
                    return null;
                }
                char quote = value.charAt(index);
                if (quote == '\'' || quote == '"') {
                    index++;
                    int start = index;
                    while (index < value.length() && value.charAt(index) != quote) {
                        index++;
                    }
                    if (index >= value.length()) {
                        return null;
                    }
                    String member = value.substring(start, index);
                    index++;
                    if (!isMemberName(member)) {
                        return null;
                    }
                    tokens.add(member);
                } else {
                    int start = index;
                    while (index < value.length() && Character.isDigit(value.charAt(index))) {
                        index++;
                    }
                    if (start == index) {
                        return null;
                    }
                    tokens.add("#" + value.substring(start, index));
                }
                if (index >= value.length() || value.charAt(index) != ']') {
                    return null;
                }
                index++;
            } else {
                return null;
            }
        }
        return tokens.toArray(String[]::new);
    }

    private static boolean isMemberName(String member) {
        if (member == null || member.isEmpty() || member.length() > 128) {
            return false;
        }
        for (int i = 0; i < member.length(); i++) {
            char c = member.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
