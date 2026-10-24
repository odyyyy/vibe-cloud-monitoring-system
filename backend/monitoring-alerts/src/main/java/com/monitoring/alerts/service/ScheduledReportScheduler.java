package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.repository.ScheduledReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs scheduled reports at their configured minute. Generation happens on a bounded executor,
 * and each report occurrence is guarded by its stored {@code lastRunAt} to avoid duplicate sends
 * after a restart within the same minute.
 *
 * <p>Report times use the configured report time zone (UTC by default). MONTHLY reports run on the
 * first day of the month because the task model has no separate day-of-month field.
 */
@Component
@Slf4j
public class ScheduledReportScheduler {

    private final ScheduledReportRepository reportRepository;
    private final ScheduledReportService reportService;
    private final ExecutorService reportExecutor;
    private final ZoneId zone;

    public ScheduledReportScheduler(
            ScheduledReportRepository reportRepository,
            ScheduledReportService reportService,
            @Qualifier("alertReportExecutor") ExecutorService reportExecutor,
            @Value("${monitoring.alerts.reports.zone:UTC}") String zoneId) {
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.reportExecutor = reportExecutor;
        this.zone = ZoneId.of(zoneId.trim());
    }

    @Scheduled(
            cron = "${monitoring.alerts.reports.cron:0 * * * * *}",
            zone = "${monitoring.alerts.reports.zone:UTC}")
    public void runDueReports() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalTime start = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime end = start.equals(LocalTime.of(23, 59)) ? LocalTime.MAX : start.plusMinutes(1);
        List<ScheduledReport> candidates = reportRepository
                .findByTimeOfDayGreaterThanEqualAndTimeOfDayLessThanOrderByIdAsc(start, end);
        if (candidates.isEmpty()) {
            return;
        }

        for (ScheduledReport report : candidates) {
            Instant occurrence = now.toLocalDate().atTime(report.getTimeOfDay()).atZone(zone).toInstant();
            if (!isDueOn(report, now) || alreadyRan(report, occurrence)) {
                continue;
            }
            try {
                reportExecutor.submit(() -> reportService.runScheduledReport(report.getId(), occurrence));
            } catch (RejectedExecutionException ex) {
                log.warn("Scheduled report {} was skipped because the report queue is full", report.getId());
            }
        }
    }

    private boolean isDueOn(ScheduledReport report, ZonedDateTime now) {
        return switch (report.getFrequency()) {
            case DAILY -> true;
            case WEEKLY -> report.getDayOfWeek() != null
                    && report.getDayOfWeek() == now.getDayOfWeek().getValue();
            case MONTHLY -> now.getDayOfMonth() == 1;
        };
    }

    private boolean alreadyRan(ScheduledReport report, Instant occurrence) {
        return report.getLastRunAt() != null && !report.getLastRunAt().isBefore(occurrence);
    }
}
