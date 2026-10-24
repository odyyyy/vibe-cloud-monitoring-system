package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.response.NotificationDeliveryResponse;
import com.monitoring.alerts.dto.response.NotificationTestResult;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.NotificationDelivery;
import com.monitoring.alerts.entity.NotificationDeliveryStatus;
import com.monitoring.alerts.entity.NotificationEventType;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.alerts.repository.NotificationDeliveryRepository;
import com.monitoring.alerts.repository.ScheduledReportRepository;
import com.monitoring.common.dto.response.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * Persists and executes notification deliveries.
 *
 * <p>Deliveries are recorded as {@code PENDING} before any network work starts, then updated after
 * every attempt. Sending runs on the dedicated bounded executors, never on the alert evaluation
 * thread. Up to three attempts are made with exponential backoff, but only for network errors,
 * HTTP 429 and HTTP 5xx responses; a 4xx configuration error is final immediately.
 */
@Service
@Slf4j
public class NotificationDeliveryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationChannelRepository channelRepository;
    private final ScheduledReportRepository scheduledReportRepository;
    private final Map<NotificationChannelType, NotificationSender> senders;
    private final ExecutorService webhookExecutor;
    private final ExecutorService emailExecutor;
    private final int maxAttempts;
    private final long retryBaseDelayMs;

    public NotificationDeliveryService(
            NotificationDeliveryRepository deliveryRepository,
            NotificationChannelRepository channelRepository,
            ScheduledReportRepository scheduledReportRepository,
            List<NotificationSender> notificationSenders,
            @Qualifier("alertWebhookExecutor") ExecutorService alertWebhookExecutor,
            @Qualifier("alertEmailExecutor") ExecutorService alertEmailExecutor,
            @Value("${monitoring.alerts.notifications.max-attempts:3}") int maxAttempts,
            @Value("${monitoring.alerts.notifications.retry-base-delay-ms:1000}") long retryBaseDelayMs) {
        this.deliveryRepository = deliveryRepository;
        this.channelRepository = channelRepository;
        this.scheduledReportRepository = scheduledReportRepository;
        this.webhookExecutor = alertWebhookExecutor;
        this.emailExecutor = alertEmailExecutor;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        this.retryBaseDelayMs = Math.max(0, retryBaseDelayMs);
        Map<NotificationChannelType, NotificationSender> byType = new EnumMap<>(NotificationChannelType.class);
        for (NotificationSender sender : notificationSenders) {
            byType.put(sender.type(), sender);
        }
        this.senders = byType;
    }

    /** Creates a delivery row and schedules it on the correct bounded executor. */
    public void enqueue(NotificationChannel channel, AlertEvent event) {
        NotificationDelivery delivery = deliveryRepository.save(newDelivery(channel, event));
        Runnable task = () -> deliverWithRetries(delivery.getId(), channel, event);
        try {
            executorFor(channel.getType()).submit(task);
        } catch (RejectedExecutionException ex) {
            delivery.setStatus(NotificationDeliveryStatus.FAILED);
            delivery.setLastError(truncate("Notification delivery queue is full"));
            delivery.setLastAttemptAt(Instant.now());
            deliveryRepository.save(delivery);
        }
    }

    /**
     * Sends a test event synchronously and returns the outcome to the caller. Only one attempt is
     * made so the HTTP request stays bounded; regular alert deliveries still retry.
     */
    public NotificationTestResult deliverTest(NotificationChannel channel, AlertEvent event) {
        NotificationDelivery delivery = deliveryRepository.save(newDelivery(channel, event));
        NotificationSendResult result = attempt(channel, event);
        delivery.setAttempts(1);
        delivery.setLastAttemptAt(Instant.now());
        delivery.setLastHttpStatus(result.httpStatus());
        if (result.success()) {
            delivery.setStatus(NotificationDeliveryStatus.SUCCESS);
            delivery.setLastError(null);
            deliveryRepository.save(delivery);
            String message = result.httpStatus() == null
                    ? "Test notification delivered."
                    : "Test notification delivered (HTTP " + result.httpStatus() + ").";
            return new NotificationTestResult(
                    NotificationDeliveryStatus.SUCCESS, message, result.httpStatus(), delivery.getId());
        }

        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setLastError(truncate(result.error()));
        deliveryRepository.save(delivery);
        return new NotificationTestResult(
                NotificationDeliveryStatus.FAILED,
                result.error() == null ? "Test notification failed." : result.error(),
                result.httpStatus(),
                delivery.getId());
    }

    /**
     * Creates a delivery row for a scheduled report and schedules it on the type-appropriate
     * bounded executor.
     */
    public void enqueueScheduledReport(
            ScheduledReport report, NotificationChannel channel, ReportMessage message) {
        NotificationDelivery delivery = deliveryRepository.save(newReportDelivery(report, channel));
        Runnable task = () -> deliverReportWithRetries(delivery.getId(), report, channel, message);
        try {
            executorForReport(report).submit(task);
        } catch (RejectedExecutionException ex) {
            delivery.setStatus(NotificationDeliveryStatus.FAILED);
            delivery.setLastError(truncate("Notification delivery queue is full"));
            delivery.setLastAttemptAt(Instant.now());
            deliveryRepository.save(delivery);
        }
    }

    /**
     * Sends a scheduled report synchronously with a single attempt, for the preview/test endpoint.
     */
    public NotificationTestResult deliverScheduledReportTest(
            ScheduledReport report, NotificationChannel channel, ReportMessage message) {
        NotificationDelivery delivery = deliveryRepository.save(newReportDelivery(report, channel));
        NotificationSendResult result = attemptReport(report, channel, message);
        delivery.setAttempts(1);
        delivery.setLastAttemptAt(Instant.now());
        delivery.setLastHttpStatus(result.httpStatus());
        if (result.success()) {
            delivery.setStatus(NotificationDeliveryStatus.SUCCESS);
            delivery.setLastError(null);
            deliveryRepository.save(delivery);
            String successMessage = result.httpStatus() == null
                    ? "Test report delivered."
                    : "Test report delivered (HTTP " + result.httpStatus() + ").";
            return new NotificationTestResult(
                    NotificationDeliveryStatus.SUCCESS, successMessage, result.httpStatus(), delivery.getId());
        }

        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setLastError(truncate(result.error()));
        deliveryRepository.save(delivery);
        return new NotificationTestResult(
                NotificationDeliveryStatus.FAILED,
                result.error() == null ? "Test report failed." : result.error(),
                result.httpStatus(),
                delivery.getId());
    }

    /** Visible for testing. */
    void deliverReportWithRetries(
            Long deliveryId, ScheduledReport report, NotificationChannel channel, ReportMessage message) {
        NotificationDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            delivery.setAttempts(attemptNumber);
            delivery.setLastAttemptAt(Instant.now());
            NotificationSendResult result = attemptReport(report, channel, message);
            delivery.setLastHttpStatus(result.httpStatus());

            if (result.success()) {
                delivery.setStatus(NotificationDeliveryStatus.SUCCESS);
                delivery.setLastError(null);
                deliveryRepository.save(delivery);
                return;
            }

            delivery.setLastError(truncate(result.error()));
            if (!result.retryable() || attemptNumber == maxAttempts) {
                delivery.setStatus(NotificationDeliveryStatus.FAILED);
                deliveryRepository.save(delivery);
                return;
            }
            delivery.setStatus(NotificationDeliveryStatus.PENDING);
            deliveryRepository.save(delivery);

            if (!sleepBeforeRetry(attemptNumber)) {
                delivery.setStatus(NotificationDeliveryStatus.FAILED);
                delivery.setLastError(truncate("Delivery was interrupted before a retry"));
                deliveryRepository.save(delivery);
                return;
            }
        }
    }

    /**
     * Executes the retry loop. Visible for testing; production callers use {@link #enqueue}.
     */
    void deliverWithRetries(Long deliveryId, NotificationChannel channel, AlertEvent event) {
        NotificationDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            delivery.setAttempts(attemptNumber);
            delivery.setLastAttemptAt(Instant.now());
            NotificationSendResult result = attempt(channel, event);
            delivery.setLastHttpStatus(result.httpStatus());

            if (result.success()) {
                delivery.setStatus(NotificationDeliveryStatus.SUCCESS);
                delivery.setLastError(null);
                deliveryRepository.save(delivery);
                return;
            }

            delivery.setLastError(truncate(result.error()));
            if (!result.retryable() || attemptNumber == maxAttempts) {
                delivery.setStatus(NotificationDeliveryStatus.FAILED);
                deliveryRepository.save(delivery);
                return;
            }
            delivery.setStatus(NotificationDeliveryStatus.PENDING);
            deliveryRepository.save(delivery);

            if (!sleepBeforeRetry(attemptNumber)) {
                delivery.setStatus(NotificationDeliveryStatus.FAILED);
                delivery.setLastError(truncate("Delivery was interrupted before a retry"));
                deliveryRepository.save(delivery);
                return;
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDeliveryResponse> list(
            Long organizationId,
            Long channelId,
            Long incidentId,
            NotificationDeliveryStatus status,
            Integer page,
            Integer size) {
        int effectivePage = page == null || page < 0 ? 0 : page;
        int effectiveSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(
                effectivePage,
                effectiveSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<NotificationDelivery> deliveries =
                deliveryRepository.findAll(specification(organizationId, channelId, incidentId, status), pageable);

        Set<Long> channelIds = deliveries.getContent().stream()
                .map(NotificationDelivery::getChannelId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, NotificationChannel> channels = channelRepository.findAllById(channelIds).stream()
                .collect(Collectors.toMap(NotificationChannel::getId, channel -> channel));
        Set<Long> reportIds = deliveries.getContent().stream()
                .map(NotificationDelivery::getScheduledReportId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ScheduledReport> reports = scheduledReportRepository.findAllById(reportIds).stream()
                .collect(Collectors.toMap(ScheduledReport::getId, report -> report));
        return PageResponse.of(deliveries,
                delivery -> NotificationDeliveryResponse.from(
                        delivery,
                        channels.get(delivery.getChannelId()),
                        reports.get(delivery.getScheduledReportId())));
    }

    private Specification<NotificationDelivery> specification(
            Long organizationId, Long channelId, Long incidentId, NotificationDeliveryStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (channelId != null) {
                predicates.add(cb.equal(root.get("channelId"), channelId));
            }
            if (incidentId != null) {
                predicates.add(cb.equal(root.get("incidentId"), incidentId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private NotificationDelivery newDelivery(NotificationChannel channel, AlertEvent event) {
        return NotificationDelivery.builder()
                .organizationId(channel.getOrganizationId())
                .channelId(channel.getId())
                .incidentId(event.incidentId())
                .eventType(NotificationEventType.fromStatus(event.status()))
                .status(NotificationDeliveryStatus.PENDING)
                .attempts(0)
                .build();
    }

    private NotificationDelivery newReportDelivery(ScheduledReport report, NotificationChannel channel) {
        return NotificationDelivery.builder()
                .organizationId(report.getOrganizationId())
                .channelId(channel == null ? null : channel.getId())
                .scheduledReportId(report.getId())
                .eventType(NotificationEventType.SCHEDULED_REPORT)
                .status(NotificationDeliveryStatus.PENDING)
                .attempts(0)
                .build();
    }

    private NotificationSendResult attemptReport(
            ScheduledReport report, NotificationChannel channel, ReportMessage message) {
        NotificationChannelType type = report.getChannelType() == ScheduledReportChannelType.EMAIL
                ? NotificationChannelType.EMAIL
                : NotificationChannelType.SLACK;
        NotificationSender sender = senders.get(type);
        if (sender == null) {
            return NotificationSendResult.permanentFailure(
                    "No notification sender for channel type " + type);
        }
        try {
            NotificationSendResult result = sender.deliverReport(channel, report, message);
            return result == null
                    ? NotificationSendResult.permanentFailure("Notification sender returned no result")
                    : result;
        } catch (IllegalArgumentException | com.monitoring.healthcheck.exception.InvalidTargetUrlException ex) {
            return NotificationSendResult.permanentFailure(
                    ex.getMessage() == null ? "Notification target is not allowed" : ex.getMessage());
        } catch (RuntimeException ex) {
            log.debug("Scheduled report attempt for report {} failed ({})",
                    report.getId(), ex.getClass().getSimpleName());
            return NotificationSendResult.networkFailure(
                    "Delivery failed (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private NotificationSendResult attempt(NotificationChannel channel, AlertEvent event) {
        NotificationSender sender = senders.get(channel.getType());
        if (sender == null) {
            return NotificationSendResult.permanentFailure(
                    "No notification sender for channel type " + channel.getType());
        }
        try {
            NotificationSendResult result = sender.deliver(channel, event);
            return result == null
                    ? NotificationSendResult.permanentFailure("Notification sender returned no result")
                    : result;
        } catch (IllegalArgumentException | com.monitoring.healthcheck.exception.InvalidTargetUrlException ex) {
            return NotificationSendResult.permanentFailure(
                    ex.getMessage() == null ? "Notification target is not allowed" : ex.getMessage());
        } catch (RuntimeException ex) {
            log.debug("Notification attempt for channel {} failed ({})",
                    channel.getId(), ex.getClass().getSimpleName());
            return NotificationSendResult.networkFailure(
                    "Delivery failed (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private ExecutorService executorFor(NotificationChannelType type) {
        return type == NotificationChannelType.EMAIL ? emailExecutor : webhookExecutor;
    }

    private ExecutorService executorForReport(ScheduledReport report) {
        return report.getChannelType() == ScheduledReportChannelType.EMAIL ? emailExecutor : webhookExecutor;
    }

    private boolean sleepBeforeRetry(int attemptNumber) {
        if (retryBaseDelayMs <= 0) {
            return true;
        }
        long delay = retryBaseDelayMs * (1L << Math.min(attemptNumber - 1, 10));
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= NotificationDelivery.MAX_ERROR_LENGTH
                ? singleLine
                : singleLine.substring(0, NotificationDelivery.MAX_ERROR_LENGTH);
    }
}
