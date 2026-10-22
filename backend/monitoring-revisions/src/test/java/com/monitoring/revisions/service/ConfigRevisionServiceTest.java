package com.monitoring.revisions.service;

import com.monitoring.auth.repository.UserRepository;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.revisions.entity.ConfigChangeType;
import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import com.monitoring.revisions.repository.ConfigRevisionCounterRepository;
import com.monitoring.revisions.repository.ConfigRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigRevisionServiceTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long RESOURCE_ID = 7L;
    private static final int SCHEMA_VERSION = 1;

    private ConfigRevisionRepository revisionRepository;
    private ConfigRevisionCounterRepository counterRepository;
    private ConfigRevisionService service;

    @BeforeEach
    void setUp() {
        revisionRepository = Mockito.mock(ConfigRevisionRepository.class);
        counterRepository = Mockito.mock(ConfigRevisionCounterRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        CurrentTenantService currentTenantService = Mockito.mock(CurrentTenantService.class);
        service = new ConfigRevisionService(revisionRepository, counterRepository,
                userRepository, currentTenantService);
    }

    /** The counter hands out the given numbers, one per increment. */
    private void givenCounterNumbers(int... numbers) {
        Integer[] values = new Integer[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            values[i] = numbers[i];
        }
        Mockito.when(counterRepository.increment(anyLong(), any(), anyLong()))
                .thenReturn(1, java.util.Arrays.copyOfRange(values, 1, values.length));
        Mockito.when(counterRepository.findLastNumber(anyLong(), any(), anyLong()))
                .thenReturn(values[0], java.util.Arrays.copyOfRange(values, 1, values.length));
    }

    private void givenLatestRevision(ConfigRevision revision) {
        when(revisionRepository.findTopByOrganizationIdAndResourceTypeAndResourceIdOrderByRevisionNumberDescIdDesc(
                anyLong(), any(), anyLong())).thenReturn(Optional.ofNullable(revision));
    }

    private ConfigRevision revision(long id, int number, String snapshot) {
        ConfigRevision revision = ConfigRevision.builder()
                .organizationId(ORGANIZATION_ID)
                .resourceType(ConfigResourceType.DASHBOARD)
                .resourceId(RESOURCE_ID)
                .revisionNumber(number)
                .changeType(ConfigChangeType.UPDATE)
                .schemaVersion(SCHEMA_VERSION)
                .snapshot(snapshot)
                .build();
        ReflectionTestUtils.setField(revision, "id", id);
        return revision;
    }

    @Test
    void recordCreateStoresTheFirstRevision() {
        givenCounterNumbers(1);
        givenLatestRevision(null);

        service.recordCreate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"a\"}");

        ArgumentCaptor<ConfigRevision> captor = ArgumentCaptor.forClass(ConfigRevision.class);
        verify(revisionRepository).save(captor.capture());
        assertThat(captor.getValue().getRevisionNumber()).isEqualTo(1);
        assertThat(captor.getValue().getChangeType()).isEqualTo(ConfigChangeType.CREATE);
    }

    @Test
    void recordCreateContinuesTheRevisionNumbering() {
        givenCounterNumbers(4);
        givenLatestRevision(revision(10L, 3, "{\"name\":\"b\"}"));

        service.recordCreate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"c\"}");

        ArgumentCaptor<ConfigRevision> captor = ArgumentCaptor.forClass(ConfigRevision.class);
        verify(revisionRepository).save(captor.capture());
        assertThat(captor.getValue().getRevisionNumber()).isEqualTo(4);
    }

    @Test
    void aMissingCounterIsCreatedOnDemand() {
        when(counterRepository.increment(anyLong(), any(), anyLong())).thenReturn(0, 1);
        when(counterRepository.findLastNumber(anyLong(), any(), anyLong())).thenReturn(1);
        when(revisionRepository.findMaxRevisionNumber(anyLong(), any(), anyLong())).thenReturn(5);
        givenLatestRevision(revision(10L, 5, "{\"name\":\"old\"}"));

        service.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"old\"}", "{\"name\":\"new\"}");

        verify(counterRepository).insertCounter(ORGANIZATION_ID, "DASHBOARD", RESOURCE_ID);
        // The counter continues after the newest revision the resource already has.
        verify(counterRepository).raiseTo(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID, 5);
        ArgumentCaptor<ConfigRevision> captor = ArgumentCaptor.forClass(ConfigRevision.class);
        verify(revisionRepository).save(captor.capture());
        assertThat(captor.getValue().getRevisionNumber()).isEqualTo(1);
    }

    @Test
    void recordCreateCreatesTheCounterOfTheResource() {
        givenCounterNumbers(1);
        givenLatestRevision(null);

        service.recordCreate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"a\"}");

        verify(counterRepository).insertCounter(ORGANIZATION_ID, "DASHBOARD", RESOURCE_ID);
    }

    @Test
    void recordUpdateStoresABaselineForAResourceWithoutHistory() {
        givenCounterNumbers(1, 2);
        givenLatestRevision(null);

        service.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"before\"}", "{\"name\":\"after\"}");

        ArgumentCaptor<ConfigRevision> captor = ArgumentCaptor.forClass(ConfigRevision.class);
        verify(revisionRepository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ConfigRevision::getChangeType)
                .containsExactly(ConfigChangeType.BASELINE, ConfigChangeType.UPDATE);
        assertThat(captor.getAllValues()).extracting(ConfigRevision::getRevisionNumber)
                .containsExactly(1, 2);
    }

    @Test
    void recordUpdateSkipsAnUnchangedSnapshot() {
        givenCounterNumbers(2);
        givenLatestRevision(revision(10L, 1, "{\"name\":\"a\"}"));

        service.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"a\"}", "{\"name\":\"a\"}");

        verify(revisionRepository, never()).save(any());
        verify(counterRepository, never()).increment(anyLong(), any(), anyLong());
    }

    @Test
    void recordUpdateSkipsASnapshotThatMatchesTheNewestRevision() {
        givenCounterNumbers(2);
        givenLatestRevision(revision(10L, 2, "{\"name\":\"a\"}"));

        service.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"other\"}", "{\"name\":\"a\"}");

        verify(revisionRepository, never()).save(any());
        verify(counterRepository, never()).increment(anyLong(), any(), anyLong());
    }

    @Test
    void recordUpdatePrunesTheHistoryToTheRetentionLimit() {
        givenCounterNumbers(11);
        givenLatestRevision(revision(10L, 10, "{\"name\":\"old\"}"));

        service.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"old\"}", "{\"name\":\"new\"}");

        verify(revisionRepository).deleteResourceHistoryUpTo(ORGANIZATION_ID, ConfigResourceType.DASHBOARD,
                RESOURCE_ID, 11 - ConfigRevisionService.MAX_REVISIONS_PER_RESOURCE);
    }

    @Test
    void recordUpdateKeepsTheHistoryWhileItFitsTheRetentionLimit() {
        givenCounterNumbers(10);
        givenLatestRevision(revision(10L, 9, "{\"name\":\"old\"}"));

        service.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "{\"name\":\"old\"}", "{\"name\":\"new\"}");

        verify(revisionRepository, never()).deleteResourceHistoryUpTo(anyLong(), any(), anyLong(), anyInt());
    }

    @Test
    void deleteAllRemovesTheWholeHistoryAndItsCounter() {
        service.deleteAll(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID);

        verify(revisionRepository).deleteResourceHistory(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID);
        verify(counterRepository).deleteCounter(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID);
    }

    @Test
    void anOversizedSnapshotIsNotRecorded() {
        givenCounterNumbers(1);

        service.recordCreate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, RESOURCE_ID,
                SCHEMA_VERSION, "x".repeat(ConfigRevisionService.MAX_SNAPSHOT_BYTES + 1));

        verify(revisionRepository, never()).save(any());
        verify(counterRepository, never()).insertCounter(anyLong(), anyString(), anyLong());
    }
}
