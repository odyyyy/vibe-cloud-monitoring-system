package com.monitoring.collector.export;

import java.util.List;

/**
 * Minimal CSV writer for exports.
 *
 * <p>Every cell is quoted and embedded quotes are doubled, so commas, quotes and newlines
 * inside log messages never break the file. Text cells are additionally neutralized to
 * prevent spreadsheet formula injection: a leading {@code =}, {@code +}, {@code -},
 * {@code @}, tab or carriage return is prefixed with a single quote. Numeric cells (ids,
 * metric values) are quoted but not prefixed, so negative numbers stay numbers.
 */
public final class CsvExporter {

    private static final String LINE_SEPARATOR = "\r\n";

    private CsvExporter() {
    }

    /**
     * Renders a header row followed by data rows as CSV with CRLF line endings.
     *
     * @param headers column names; rendered as text cells
     * @param rows    cell values; {@link Number} instances are rendered as numbers and
     *                everything else as sanitized text
     */
    public static String toCsv(List<String> headers, List<? extends List<?>> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, headers);
        for (List<?> row : rows) {
            appendRow(csv, row);
        }
        return csv.toString();
    }

    public static String textCell(String value) {
        return quote(neutralizeFormula(value));
    }

    private static void appendRow(StringBuilder csv, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(formatCell(cells.get(i)));
        }
        csv.append(LINE_SEPARATOR);
    }

    private static String formatCell(Object value) {
        if (value == null) {
            return quote("");
        }
        if (value instanceof Number number) {
            return quote(number.toString());
        }
        return textCell(String.valueOf(value));
    }

    /**
     * Prefixes a leading formula trigger with a single quote. The surrounding quotes added by
     * {@link #quote(String)} are not enough on their own: spreadsheet applications still
     * evaluate a quoted cell that starts with {@code =}, {@code +}, {@code -} or {@code @}.
     */
    private static String neutralizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
