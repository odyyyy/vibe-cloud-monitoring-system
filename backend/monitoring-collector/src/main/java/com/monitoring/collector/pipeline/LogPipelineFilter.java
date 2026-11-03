package com.monitoring.collector.pipeline;

import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.exception.InvalidLogPipelineRuleException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parsed filter expression for a log pipeline rule.
 *
 * <p>Supported clauses are {@code level:DEBUG}, {@code message:"healthcheck"},
 * {@code host:server-1}, {@code service:api} and {@code context.order_id:12345}. Multiple clauses
 * are separated by whitespace (or {@code &&}) and combined with AND. An empty expression or
 * {@code *} matches every event.
 */
final class LogPipelineFilter {

    private static final Pattern CONTEXT_KEY =
            Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final int MAX_VALUE_LENGTH = 512;

    private final List<Clause> clauses;

    private LogPipelineFilter(List<Clause> clauses) {
        this.clauses = clauses;
    }

    static LogPipelineFilter parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return new LogPipelineFilter(List.of());
        }
        String trimmed = expression.trim();
        if ("*".equals(trimmed)) {
            return new LogPipelineFilter(List.of());
        }

        List<Clause> clauses = new ArrayList<>();
        int index = 0;
        while (index < trimmed.length()) {
            index = skipSeparators(trimmed, index);
            if (index >= trimmed.length()) {
                break;
            }

            int colon = trimmed.indexOf(':', index);
            if (colon < 0) {
                throw invalid("Each filter clause must use key:value syntax");
            }
            String key = trimmed.substring(index, colon).trim();
            if (key.isEmpty()) {
                throw invalid("Filter clause is missing a key");
            }
            index = colon + 1;
            index = skipWhitespace(trimmed, index);

            ParsedValue parsed = readValue(trimmed, index);
            index = parsed.nextIndex();
            clauses.add(toClause(key, parsed.value()));
        }

        return new LogPipelineFilter(List.copyOf(clauses));
    }

    boolean matches(LogLevel level, String message, LogPipelineContext context) {
        for (Clause clause : clauses) {
            if (!clause.matches(level, message, context)) {
                return false;
            }
        }
        return true;
    }

    private static Clause toClause(String rawKey, String value) {
        if (value.isEmpty()) {
            throw invalid("Filter clause value must not be empty");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw invalid("Filter value must be at most " + MAX_VALUE_LENGTH + " characters");
        }

        String key = rawKey.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "level" -> new LevelClause(value);
            case "message" -> new MessageClause(value);
            case "host" -> new ContextClause("host", value);
            case "service" -> new ContextClause("service", value);
            default -> {
                if (!key.startsWith("context.")) {
                    throw invalid("Unknown filter key '" + rawKey
                            + "'; use level, message, host, service or context.<key>");
                }
                String contextKey = rawKey.substring("context.".length());
                if (!CONTEXT_KEY.matcher(contextKey).matches()) {
                    throw invalid("Invalid context filter key '" + contextKey + "'");
                }
                yield new ContextClause(contextKey, value);
            }
        };
    }

    private static ParsedValue readValue(String expression, int start) {
        if (start >= expression.length()) {
            throw invalid("Filter clause is missing a value");
        }

        if (expression.charAt(start) == '"') {
            StringBuilder value = new StringBuilder();
            int index = start + 1;
            while (index < expression.length()) {
                char c = expression.charAt(index);
                if (c == '"') {
                    index++;
                    if (index < expression.length()
                            && !Character.isWhitespace(expression.charAt(index))
                            && !expression.startsWith("&&", index)) {
                        throw invalid("Unexpected text after a quoted filter value");
                    }
                    return new ParsedValue(value.toString(), index);
                }
                if (c == '\\' && index + 1 < expression.length()) {
                    char escaped = expression.charAt(index + 1);
                    if (escaped == '"' || escaped == '\\') {
                        value.append(escaped);
                    } else {
                        value.append(c).append(escaped);
                    }
                    index += 2;
                    continue;
                }
                value.append(c);
                index++;
            }
            throw invalid("Quoted filter value is not closed");
        }

        int end = start;
        while (end < expression.length()
                && !Character.isWhitespace(expression.charAt(end))
                && !expression.startsWith("&&", end)) {
            end++;
        }
        return new ParsedValue(expression.substring(start, end), end);
    }

    private static int skipSeparators(String expression, int start) {
        int index = skipWhitespace(expression, start);
        while (expression.startsWith("&&", index)) {
            index = skipWhitespace(expression, index + 2);
        }
        return index;
    }

    private static int skipWhitespace(String expression, int start) {
        int index = start;
        while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
            index++;
        }
        return index;
    }

    private static InvalidLogPipelineRuleException invalid(String message) {
        return new InvalidLogPipelineRuleException(message);
    }

    private record ParsedValue(String value, int nextIndex) {
    }

    private interface Clause {
        boolean matches(LogLevel level, String message, LogPipelineContext context);
    }

    private record LevelClause(String value) implements Clause {
        @Override
        public boolean matches(LogLevel level, String message, LogPipelineContext context) {
            return level != null && level.name().equalsIgnoreCase(value);
        }
    }

    private record MessageClause(String value) implements Clause {
        @Override
        public boolean matches(LogLevel level, String message, LogPipelineContext context) {
            return message != null
                    && message.toLowerCase(Locale.ROOT)
                    .contains(value.toLowerCase(Locale.ROOT));
        }
    }

    private record ContextClause(String key, String value) implements Clause {
        @Override
        public boolean matches(LogLevel level, String message, LogPipelineContext context) {
            if (context == null || !context.isValid()) {
                return false;
            }
            return value.equals(context.stringValue(key));
        }
    }
}
