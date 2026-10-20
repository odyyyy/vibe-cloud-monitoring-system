package com.monitoring.collector.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds downloadable HTTP responses for CSV and JSON exports.
 *
 * <p>Responses are attachment downloads and expose the export safety metadata in headers:
 * {@code X-Export-Limit}, {@code X-Export-Total} and {@code X-Export-Truncated}. The frontend
 * uses the last one to warn the user when more rows matched than the export limit allows.
 */
@Component
@RequiredArgsConstructor
public class ExportResponseWriter {

    /** Hard upper bound on rows returned by a single synchronous export. */
    public static final int MAX_EXPORT_ROWS = 10_000;

    public static final String TRUNCATED_HEADER = "X-Export-Truncated";
    public static final String TOTAL_HEADER = "X-Export-Total";
    public static final String LIMIT_HEADER = "X-Export-Limit";

    private static final MediaType CSV_MEDIA_TYPE =
            MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final String CSV_FILENAME = "export.csv";
    private static final String JSON_FILENAME = "export.json";

    private final ObjectMapper objectMapper;

    public ResponseEntity<byte[]> csv(ExportData<?> data, List<String> headers,
                                      List<? extends List<?>> rows) {
        byte[] body = CsvExporter.toCsv(headers, rows).getBytes(StandardCharsets.UTF_8);
        return download(body, CSV_MEDIA_TYPE, CSV_FILENAME, data);
    }

    public ResponseEntity<byte[]> json(ExportData<?> data) {
        byte[] body;
        try {
            body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data.rows());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize export response", ex);
        }
        return download(body, MediaType.APPLICATION_JSON, JSON_FILENAME, data);
    }

    private ResponseEntity<byte[]> download(byte[] body, MediaType contentType,
                                            String filename, ExportData<?> data) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(LIMIT_HEADER, String.valueOf(MAX_EXPORT_ROWS))
                .header(TOTAL_HEADER, String.valueOf(data.totalRows()))
                .header(TRUNCATED_HEADER, Boolean.toString(data.isTruncated()))
                .body(body);
    }
}
