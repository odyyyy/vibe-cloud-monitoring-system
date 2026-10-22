package com.monitoring.retention.service;

import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.retention.config.RetentionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetentionPurgeJobTest {

    private RetentionProperties properties;
    private OrganizationRepository organizationRepository;
    private RetentionPurgeService purgeService;
    private RetentionPurgeJob job;

    @BeforeEach
    void setUp() {
        properties = new RetentionProperties();
        organizationRepository = Mockito.mock(OrganizationRepository.class);
        purgeService = Mockito.mock(RetentionPurgeService.class);
        job = new RetentionPurgeJob(properties, organizationRepository, purgeService);
    }

    @Test
    void disabledJobDoesNothing() {
        properties.setEnabled(false);

        job.purgeExpiredData();

        verifyNoInteractions(organizationRepository, purgeService);
    }

    @Test
    void enabledJobPurgesEveryOrganizationAndContinuesAfterAFailure() {
        when(organizationRepository.findAll()).thenReturn(List.of(organization(1L), organization(2L)));
        doThrow(new IllegalStateException("tenant one failed"))
                .when(purgeService).purgeOrganization(eq(1L), any(Instant.class));

        job.purgeExpiredData();

        verify(purgeService).purgeOrganization(eq(1L), any(Instant.class));
        verify(purgeService).purgeOrganization(eq(2L), any(Instant.class));
    }

    private Organization organization(Long id) {
        Organization organization = Organization.builder().name("org-" + id).build();
        organization.setId(id);
        return organization;
    }
}
