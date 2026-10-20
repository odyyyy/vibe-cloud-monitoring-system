package com.monitoring.collector.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CsvExporterTest {

    @Test
    void rendersHeaderAndRowsWithCrlf() {
        String csv = CsvExporter.toCsv(
                List.of("id", "message"),
                List.of(List.of(1L, "hello")));

        assertEquals("\"id\",\"message\"\r\n\"1\",\"hello\"\r\n", csv);
    }

    @Test
    void quotesCommasQuotesAndNewlines() {
        String csv = CsvExporter.toCsv(
                List.of("message"),
                List.of(List.of("a,b \"c\"\nd")));

        assertEquals("\"message\"\r\n\"a,b \"\"c\"\"\nd\"\r\n", csv);
    }

    @Test
    void neutralizesSpreadsheetFormulasInText() {
        String csv = CsvExporter.toCsv(
                List.of("value"),
                List.of(
                        List.of("=1+1"),
                        List.of("+cmd"),
                        List.of("-cmd"),
                        List.of("@cmd"),
                        List.of("\tcmd"),
                        List.of("\rcmd")));

        assertEquals("\"value\"\r\n"
                + "\"'=1+1\"\r\n"
                + "\"'+cmd\"\r\n"
                + "\"'-cmd\"\r\n"
                + "\"'@cmd\"\r\n"
                + "\"'\tcmd\"\r\n"
                + "\"'\rcmd\"\r\n", csv);
    }

    @Test
    void numbersAreQuotedButNotMutated() {
        String csv = CsvExporter.toCsv(
                List.of("value"),
                List.of(List.of(-12.5), List.of(0L)));

        assertEquals("\"value\"\r\n\"-12.5\"\r\n\"0\"\r\n", csv);
    }

    @Test
    void nullCellBecomesEmptyQuotedCell() {
        String csv = CsvExporter.toCsv(List.of("group"), List.of(java.util.Arrays.asList((Object) null)));

        assertEquals("\"group\"\r\n\"\"\r\n", csv);
        assertFalse(csv.contains("null"));
    }
}
