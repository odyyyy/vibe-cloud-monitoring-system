package com.monitoring.audit.repository;

import com.monitoring.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Read/write access to the append-only audit trail.
 *
 * <p>Only {@code save} (insert) and read methods are used. No delete or update operation is
 * exposed to callers, keeping the trail immutable at the persistence boundary.
 */
public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
}
