package com.monitoring.collector.repository;

import com.monitoring.collector.entity.DailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyUsageRepository extends JpaRepository<DailyUsage, Long> {

    /** The single row for an organization and day, if the rollup has already run. */
    Optional<DailyUsage> findByOrganizationIdAndUsageDate(Long organizationId, LocalDate usageDate);

    /** Rows of an organization within an inclusive day range, oldest first. */
    List<DailyUsage> findByOrganizationIdAndUsageDateBetweenOrderByUsageDateAsc(
            Long organizationId, LocalDate from, LocalDate to);
}
