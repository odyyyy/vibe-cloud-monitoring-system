package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, Long> {

    List<ScheduledReport> findByOrganizationIdOrderByCreatedAtDescIdDesc(Long organizationId);

    Optional<ScheduledReport> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByOrganizationId(Long organizationId);

    List<ScheduledReport> findByTimeOfDayGreaterThanEqualAndTimeOfDayLessThanOrderByIdAsc(
            LocalTime from, LocalTime to);
}
