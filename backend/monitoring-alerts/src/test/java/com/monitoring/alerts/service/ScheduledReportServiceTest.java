package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.ScheduledReportRequest;
import com.monitoring.alerts.dto.response.ScheduledReportResponse;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.entity.ScheduledReportFrequency;
import com.monitoring.alerts.exception.InvalidScheduledReportException;
import com.monitoring.alerts.exception.ScheduledReportAccessDeniedException;
import com.monitoring.alerts.exception.ScheduledReportLimitExceededException;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.alerts.repository.ScheduledReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ScheduledReportServiceTest {

    private ScheduledReportRepository reportRepository;
    private NotificationChannelRepository channelRepository;
    private ScheduledReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = Mockito.mock(ScheduledReportRepository.class);
        channelRepository = Mockito.mock(NotificationChannelRepository.class);
        service = new ScheduledReportService(
                reportRepository,
                channelRepository,
                Mockito.mock(ScheduledReportGenerationService.class),
                new ScheduledReportRenderer(),
                Mockito.mock(NotificationDeliveryService.class),
                "UTC");
    }

    @Test
    void nextDailyRunRollsOverAfterTheConfiguredTime() {
        ScheduledReport report = report(ScheduledReportFrequency.DAILY, 9, null);

        assertEquals(Instant.parse("2026-10-24T09:00:00Z"),
                service.nextRunAt(report, Instant.parse("2026-10-24T08:30:00Z")));
        assertEquals(Instant.parse("2026-10-25T09:00:00Z"),
                service.nextRunAt(report, Instant.parse("2026-10-24T10:00:00Z")));
    }

    @Test
    void nextWeeklyAndMonthlyRunsUseTheirCalendarRules() {
        ScheduledReport weekly = report(ScheduledReportFrequency.WEEKLY, 9, 1);
        ScheduledReport monthly = report(ScheduledReportFrequency.MONTHLY, 9, null);
        Instant saturday = Instant.parse("2026-10-24T10:00:00Z");

        assertEquals(Instant.parse("2026-10-26T09:00:00Z"), service.nextRunAt(weekly, saturday));
        assertEquals(Instant.parse("2026-11-01T09:00:00Z"), service.nextRunAt(monthly, saturday));
    }

    @Test
    void emailRecipientsAreValidatedNormalizedAndDeduplicated() {
        when(reportRepository.countByOrganizationId(7L)).thenReturn(0L);
        when(reportRepository.save(any(ScheduledReport.class))).thenAnswer(invocation -> {
            ScheduledReport saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        ScheduledReportRequest request = request(ScheduledReportChannelType.EMAIL);
        request.setRecipients(List.of(" Ops@Example.com ", "ops@example.com", "oncall@example.com"));

        ScheduledReportResponse response = service.create(7L, request);

        assertEquals(List.of("ops@example.com", "oncall@example.com"), response.getRecipients());
        assertEquals(LocalTime.of(9, 0), response.getTimeOfDay());
    }

    @Test
    void weeklyReportRequiresDayOfWeek() {
        ScheduledReportRequest request = request(ScheduledReportChannelType.EMAIL);
        request.setFrequency(ScheduledReportFrequency.WEEKLY);

        assertThrows(InvalidScheduledReportException.class, () -> service.create(7L, request));
    }

    @Test
    void emailReportRequiresValidRecipientsAndLimit() {
        ScheduledReportRequest noRecipients = request(ScheduledReportChannelType.EMAIL);
        noRecipients.setRecipients(List.of());
        assertThrows(InvalidScheduledReportException.class, () -> service.create(7L, noRecipients));

        ScheduledReportRequest invalid = request(ScheduledReportChannelType.EMAIL);
        invalid.setRecipients(List.of("not-an-email"));
        assertThrows(InvalidScheduledReportException.class, () -> service.create(7L, invalid));

        ScheduledReportRequest tooMany = request(ScheduledReportChannelType.EMAIL);
        tooMany.setRecipients(java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> "ops" + index + "@example.com")
                .toList());
        assertThrows(InvalidScheduledReportException.class, () -> service.create(7L, tooMany));
    }

    @Test
    void slackReportRequiresOwnedSlackChannel() {
        NotificationChannel webhook = NotificationChannel.builder()
                .id(3L)
                .organizationId(7L)
                .type(NotificationChannelType.WEBHOOK)
                .active(true)
                .build();
        when(channelRepository.findByIdAndOrganizationId(3L, 7L)).thenReturn(Optional.of(webhook));
        ScheduledReportRequest request = request(ScheduledReportChannelType.SLACK);
        request.setChannelId(3L);

        assertThrows(InvalidScheduledReportException.class, () -> service.create(7L, request));
    }

    @Test
    void organizationReportLimitIsEnforced() {
        when(reportRepository.countByOrganizationId(7L)).thenReturn(10L);

        assertThrows(ScheduledReportLimitExceededException.class,
                () -> service.create(7L, request(ScheduledReportChannelType.EMAIL)));
    }

    @Test
    void reportBelongingToAnotherOrganizationIsRejected() {
        ScheduledReport report = report(ScheduledReportFrequency.DAILY, 9, null);
        report.setOrganizationId(7L);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThrows(ScheduledReportAccessDeniedException.class, () -> service.get(8L, 1L));
    }

    private ScheduledReportRequest request(ScheduledReportChannelType type) {
        return ScheduledReportRequest.builder()
                .name("Daily digest")
                .frequency(ScheduledReportFrequency.DAILY)
                .timeOfDay(LocalTime.of(9, 0))
                .channelType(type)
                .recipients(type == ScheduledReportChannelType.EMAIL
                        ? List.of("ops@example.com")
                        : List.of())
                .channelId(type == ScheduledReportChannelType.SLACK ? 3L : null)
                .includeMetrics(true)
                .includeTopErrors(true)
                .includeUptime(true)
                .build();
    }

    private ScheduledReport report(ScheduledReportFrequency frequency, int hour, Integer dayOfWeek) {
        return ScheduledReport.builder()
                .id(1L)
                .organizationId(7L)
                .name("Digest")
                .frequency(frequency)
                .dayOfWeek(dayOfWeek)
                .timeOfDay(LocalTime.of(hour, 0))
                .channelType(ScheduledReportChannelType.EMAIL)
                .recipients(List.of("ops@example.com"))
                .includeMetrics(true)
                .includeTopErrors(true)
                .includeUptime(true)
                .build();
    }
}
