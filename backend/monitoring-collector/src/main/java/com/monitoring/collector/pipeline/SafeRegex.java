package com.monitoring.collector.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A compiled regex with a hard evaluation deadline.
 *
 * <p>Java's {@link Pattern} can backtrack catastrophically, so every evaluation runs against a
 * {@link CharSequence} that aborts once the deadline passes. The pattern itself is compiled once
 * and translated so that Python/Go-style named groups such as {@code (?P<order_id>...)} are
 * accepted even though Java group names do not allow underscores.
 */
final class SafeRegex {

    static final int MAX_PATTERN_LENGTH = 512;
    static final long DEFAULT_TIMEOUT_MILLIS = 10;

    private static final Pattern GROUP_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final Pattern pattern;
    private final Map<Integer, String> namedGroups;
    private final long timeoutMillis;

    private SafeRegex(Pattern pattern, Map<Integer, String> namedGroups, long timeoutMillis) {
        this.pattern = pattern;
        this.namedGroups = Collections.unmodifiableMap(namedGroups);
        this.timeoutMillis = timeoutMillis;
    }

    static SafeRegex compile(String source) {
        return compile(source, DEFAULT_TIMEOUT_MILLIS);
    }

    static SafeRegex compile(String source, long timeoutMillis) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("pattern is required");
        }
        if (source.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException(
                    "pattern must be at most " + MAX_PATTERN_LENGTH + " characters");
        }

        Translation translation = translate(source);
        Pattern compiled;
        try {
            compiled = Pattern.compile(translation.javaPattern());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("pattern is invalid: " + e.getMessage(), e);
        }
        return new SafeRegex(compiled, translation.namedGroups(), timeoutMillis);
    }

    List<String> namedGroupNames() {
        return List.copyOf(namedGroups.values());
    }

    boolean find(String input) {
        Matcher matcher = pattern.matcher(deadlineSequence(input));
        return matcher.find();
    }

    /** First match's named groups, in declaration order. Returns an empty map when there is no match. */
    Map<String, String> findNamedGroups(String input) {
        Matcher matcher = pattern.matcher(deadlineSequence(input));
        if (!matcher.find()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : namedGroups.entrySet()) {
            String value = matcher.group(entry.getKey());
            if (value != null) {
                values.put(entry.getValue(), value);
            }
        }
        return values;
    }

    String replaceAll(String input) {
        Matcher matcher = pattern.matcher(deadlineSequence(input));
        return matcher.replaceAll("[REDACTED]");
    }

    private DeadlineCharSequence deadlineSequence(String input) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        return new DeadlineCharSequence(input, deadline);
    }

    /**
     * Translates named groups to plain capturing groups and records their capture indexes. Java
     * numbers ordinary and named capturing groups in source order, so replacing a named group
     * with {@code (} preserves all existing backreferences.
     */
    private static Translation translate(String source) {
        StringBuilder out = new StringBuilder(source.length());
        Map<Integer, String> names = new LinkedHashMap<>();
        int groupIndex = 0;
        boolean inClass = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);

            if (c == '\\') {
                if (i + 1 < source.length() && source.charAt(i + 1) == 'Q') {
                    int end = source.indexOf("\\E", i + 2);
                    if (end < 0) {
                        out.append(source, i, source.length());
                        break;
                    }
                    out.append(source, i, end + 2);
                    i = end + 1;
                    continue;
                }
                out.append(c);
                if (i + 1 < source.length()) {
                    out.append(source.charAt(++i));
                }
                continue;
            }

            if (inClass) {
                out.append(c);
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }

            if (c == '[') {
                inClass = true;
                out.append(c);
                continue;
            }

            if (c != '(') {
                out.append(c);
                continue;
            }

            int namedStart = namedGroupStart(source, i);
            if (namedStart >= 0) {
                int nameEnd = source.indexOf('>', namedStart);
                if (nameEnd < 0) {
                    throw new IllegalArgumentException("named group is missing '>'");
                }
                String name = source.substring(namedStart, nameEnd);
                if (!GROUP_NAME.matcher(name).matches()) {
                    throw new IllegalArgumentException(
                            "invalid capture group name '" + name
                                    + "'; use letters, digits and underscores");
                }
                if (names.containsValue(name)) {
                    throw new IllegalArgumentException("duplicate capture group name '" + name + "'");
                }
                groupIndex++;
                names.put(groupIndex, name);
                out.append('(');
                i = nameEnd;
                continue;
            }

            if (isCapturingGroup(source, i)) {
                groupIndex++;
            }
            out.append(c);
        }

        return new Translation(out.toString(), names);
    }

    /** Returns the index just after {@code (?P<} or {@code (?<}, or -1. */
    private static int namedGroupStart(String source, int openParen) {
        if (source.startsWith("(?P<", openParen)) {
            return openParen + 4;
        }
        if (source.startsWith("(?<", openParen)
                && openParen + 3 < source.length()
                && source.charAt(openParen + 3) != '='
                && source.charAt(openParen + 3) != '!') {
            return openParen + 3;
        }
        return -1;
    }

    private static boolean isCapturingGroup(String source, int openParen) {
        return openParen + 1 >= source.length() || source.charAt(openParen + 1) != '?';
    }

    private record Translation(String javaPattern, Map<Integer, String> namedGroups) {
    }

    /** CharSequence that aborts backtracking once the regex deadline has passed. */
    private static final class DeadlineCharSequence implements CharSequence {

        private final String value;
        private final long deadline;

        private DeadlineCharSequence(String value, long deadline) {
            this.value = value;
            this.deadline = deadline;
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() - deadline > 0) {
                throw new RegexTimeoutException();
            }
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(value.substring(start, end), deadline);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
