package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.ScheduledReportRequest;
import com.monitoring.alerts.dto.response.ScheduledReportPreviewResponse;
import com.monitoring.alerts.dto.response.ScheduledReportResponse;
import com.monitoring.alerts.dto.response.NotificationTestResult;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.entity.ScheduledReportFrequency;
import com.monitoring.alerts.exception.InvalidScheduledReportException;
import com.monitoring.alerts.exception.ScheduledReportAccessDeniedException;
import com.monitoring.alerts.exception.ScheduledReportLimitExceededException;
import com.monitoring.alerts.exception.ScheduledReportNotFoundException;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.alerts.repository.ScheduledReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CRUD, preview and execution for scheduled reports. All user-facing operations are scoped to the
 * authenticated organization; scheduler execution re-reads the report by id and derives the tenant
 * from the stored row.
 */
@Service
@Slf4j
public class ScheduledReportService {

    public static final int MAX_REPORTS_PER_ORGANIZATION = 10;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private final ScheduledReportRepository reportRepository;
    private final NotificationChannelRepository channelRepository;
    private final ScheduledReportGenerationService generationService;
    private final ScheduledReportRenderer renderer;
    private final NotificationDeliveryService deliveryService;
    private final ZoneId zone;

    public ScheduledReportService(
            ScheduledReportRepository reportRepository,
            NotificationChannelRepository channelRepository,
            ScheduledReportGenerationService generationService,
            ScheduledReportRenderer renderer,
            NotificationDeliveryService deliveryService,
            @Value("${monitoring.alerts.reports.zone:UTC}") String zoneId) {
        this.reportRepository = reportRepository;
        this.channelRepository = channelRepository;
        this.generationService = generationService;
        this.renderer = renderer;
        this.deliveryService = deliveryService;
        this.zone = ZoneId.of(zoneId.trim());
    }

    @Transactional(readOnly = true)
    public List<ScheduledReportResponse> list(Long organizationId) {
        Instant now = Instant.now();
        return reportRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(organizationId).stream()
                .map(report -> ScheduledReportResponse.from(report, nextRunAt(report, now), zone.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduledReportResponse get(Long organizationId, Long id) {
        ScheduledReport report = getOwnedReport(id, organizationId);
        return ScheduledReportResponse.from(report, nextRunAt(report, Instant.now()), zone.getId());
    }

    @Transactional
    public ScheduledReportResponse create(Long organizationId, ScheduledReportRequest request) {
        if (reportRepository.countByOrganizationId(organizationId) >= MAX_REPORTS_PER_ORGANIZATION) {
            throw new ScheduledReportLimitExceededException(MAX_REPORTS_PER_ORGANIZATION);
        }
        ScheduledReport report = new ScheduledReport();
        report.setOrganizationId(organizationId);
        apply(report, organizationId, request);
        ScheduledReport saved = reportRepository.save(report);
        return ScheduledReportResponse.from(saved, nextRunAt(saved, Instant.now()), zone.getId());
    }

    @Transactional
    public ScheduledReportResponse update(Long organizationId, Long id, ScheduledReportRequest request) {
        ScheduledReport report = getOwnedReport(id, organizationId);
        apply(report, organizationId, request);
        ScheduledReport saved = reportRepository.save(report);
        return ScheduledReportResponse.from(saved, nextRunAt(saved, Instant.now()), zone.getId());
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        ScheduledReport report = getOwnedReport(id, organizationId);
        reportRepository.delete(report);
    }

    /**
     * Generates a report, attempts one synchronous delivery, and returns both the delivery outcome
     * and the exact rendered payload. A delivery failure is reported in the response rather than
     * preventing the preview.
     */
    public ScheduledReportPreviewResponse sendTest(Long organizationId, Long id) {
        ScheduledReport report = getOwnedReport(id, organizationId);
        Instant now = Instant.now();
        ScheduledReportContent content = generationService.generate(organizationId, report, now);
        ReportMessage message = renderer.render(report, content);
        NotificationChannel channel = findReportChannel(report);
        NotificationTestResult result = deliveryService.deliverScheduledReportTest(report, channel, message);
        return ScheduledReportPreviewResponse.builder()
                .reportId(report.getId())
                .reportName(report.getName())
                .channelType(report.getChannelType())
                .deliveryStatus(result.status())
                .deliveryMessage(result.message())
                .deliveryId(result.deliveryId())
                .lastHttpStatus(result.httpStatus())
                .subject(message.subject())
                .html(message.html())
                .slackMessage(message.slackMessage())
                .content(content)
                .build();
    }

    /**
     * Executes one scheduler occurrence. Deliberately not wrapped in a long transaction: generation
     * uses its own read-only transaction, and the delivery row is committed before the async send
     * task starts. {@code lastRunAt} prevents duplicate delivery after a restart within the hour.
     */
    public void runScheduledReport(Long reportId, Instant occurrence) {
        ScheduledReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            return;
        }
        if (report.getLastRunAt() != null && !report.getLastRunAt().isBefore(occurrence)) {
            return;
        }
        try {
            ScheduledReportContent content =
                    generationService.generate(report.getOrganizationId(), report, occurrence);
            ReportMessage message = renderer.render(report, content);
            deliveryService.enqueueScheduledReport(report, findReportChannel(report), message);
            report.setLastRunAt(occurrence);
            reportRepository.save(report);
        } catch (RuntimeException ex) {
            log.warn("Scheduled report {} failed to run: {}", reportId, ex.getClass().getSimpleName());
        }
    }

    /** Visible for testing. Computes the next occurrence strictly after {@code now}. */
    Instant nextRunAt(ScheduledReport report, Instant now) {
        ZonedDateTime zonedNow = now.atZone(zone);
        LocalDate today = zonedNow.toLocalDate();
        LocalDate candidateDate = switch (report.getFrequency()) {
            case DAILY -> today;
            case WEEKLY -> today.plusDays(daysUntil(today, report.getDayOfWeek()));
            case MONTHLY -> today.withDayOfMonth(1);
        };
        ZonedDateTime candidate = candidateDate.atTime(report.getTimeOfDay()).atZone(zone);
        if (!candidate.toInstant().isAfter(now)) {
            candidate = switch (report.getFrequency()) {
                case DAILY -> candidate.plusDays(1);
                case WEEKLY -> candidate.plusWeeks(1);
                case MONTHLY -> candidate.plusMonths(1).withDayOfMonth(1);
            };
        }
        return candidate.toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private long daysUntil(LocalDate today, Integer targetDayOfWeek) {
        int target = targetDayOfWeek == null ? today.getDayOfWeek().getValue() : targetDayOfWeek;
        int delta = (target - today.getDayOfWeek().getValue() + 7) % 7;
        return delta;
    }

    private void apply(ScheduledReport report, Long organizationId, ScheduledReportRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new InvalidScheduledReportException("Report name is required");
        }
        if (name.length() > ScheduledReport.MAX_NAME_LENGTH) {
            throw new InvalidScheduledReportException(
                    "Report name must be at most " + ScheduledReport.MAX_NAME_LENGTH + " characters");
        }
        if (request.getFrequency() == null || request.getTimeOfDay() == null
                || request.getChannelType() == null) {
            throw new InvalidScheduledReportException("Frequency, time and channel type are required");
        }

        Integer dayOfWeek = null;
        if (request.getFrequency() == ScheduledReportFrequency.WEEKLY) {
            dayOfWeek = request.getDayOfWeek();
            if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
                throw new InvalidScheduledReportException("Weekly reports require a day of week from 1 to 7");
            }
        }

        List<String> recipients = normalizeRecipients(request.getRecipients());
        Long channelId = null;
        if (request.getChannelType() == ScheduledReportChannelType.EMAIL) {
            if (recipients.isEmpty()) {
                throw new InvalidScheduledReportException("Email reports require at least one recipient");
            }
        } else {
            recipients = List.of();
            channelId = request.getChannelId();
            if (channelId == null) {
                throw new InvalidScheduledReportException("Slack reports require a Slack channel");
            }
            NotificationChannel channel = channelRepository.findByIdAndOrganizationId(channelId, organizationId)
                    .orElseThrow(() -> new InvalidScheduledReportException(
                            "Slack channel was not found in this organization"));
            if (channel.getType() != NotificationChannelType.SLACK) {
                throw new InvalidScheduledReportException("Selected channel is not a Slack channel");
            }
        }

        report.setName(name);
        report.setFrequency(request.getFrequency());
        report.setDayOfWeek(dayOfWeek);
        report.setTimeOfDay(request.getTimeOfDay().truncatedTo(ChronoUnit.MINUTES));
        report.setChannelType(request.getChannelType());
        report.setChannelId(channelId);
        report.setRecipients(new ArrayList<>(recipients));
        report.setIncludeMetrics(Boolean.TRUE.equals(request.getIncludeMetrics()));
        report.setIncludeTopErrors(Boolean.TRUE.equals(request.getIncludeTopErrors()));
        report.setIncludeUptime(Boolean.TRUE.equals(request.getIncludeUptime()));
    }

    private List<String> normalizeRecipients(List<String> rawRecipients) {
        if (rawRecipients == null || rawRecipients.isEmpty()) {
            return List.of();
        }
        if (rawRecipients.size() > ScheduledReport.MAX_RECIPIENTS) {
            throw new InvalidScheduledReportException(
                    "At most " + ScheduledReport.MAX_RECIPIENTS + " recipients are allowed");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : rawRecipients) {
            String email = raw == null ? "" : raw.trim();
            if (email.isEmpty()) {
                throw new InvalidScheduledReportException("Recipient email addresses must not be blank");
            }
            if (email.length() > ScheduledReport.MAX_RECIPIENT_LENGTH
                    || !EMAIL_PATTERN.matcher(email).matches()) {
                throw new InvalidScheduledReportException("Enter a valid email address: " + email);
            }
            unique.add(email.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(unique);
    }

    private ScheduledReport getOwnedReport(Long id, Long organizationId) {
        ScheduledReport report = reportRepository.findById(id)
                .orElseThrow(() -> new ScheduledReportNotFoundException(id));
        if (!report.getOrganizationId().equals(organizationId)) {
            throw new ScheduledReportAccessDeniedException();
        }
        return report;
    }

    private NotificationChannel findReportChannel(ScheduledReport report) {
        if (report.getChannelId() == null) {
            return null;
        }
        return channelRepository.findByIdAndOrganizationId(report.getChannelId(), report.getOrganizationId())
                .orElse(null);
    }
}
