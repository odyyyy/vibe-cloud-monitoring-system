package com.monitoring.api;

import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import com.monitoring.revisions.repository.ConfigRevisionRepository;
import com.monitoring.revisions.service.ConfigRevisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that concurrent saves of the same configuration resource are serialized.
 *
 * <p>Two saves of the same dashboard used to read the same "latest revision", allocate the same
 * revision number and prune the same row. The losing transaction failed with an optimistic
 * locking error, so the user saw HTTP 500 instead of a saved dashboard.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:revisions-it;MODE=MySQL;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.healthcheck.scheduler.enabled=false",
        "monitoring.alerts.scheduler.enabled=false",
        "monitoring.platform-metrics.enabled=false",
        "monitoring.retention.enabled=false"
})
class ConfigRevisionConcurrencyIntegrationTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final int SCHEMA_VERSION = 1;

    @Autowired
    private ConfigRevisionService revisionService;

    @Autowired
    private ConfigRevisionRepository revisionRepository;

    @Test
    void concurrentUpdatesAllocateUniqueRevisionNumbers() throws Exception {
        Long resourceId = 4242L;
        revisionService.recordCreate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, resourceId,
                SCHEMA_VERSION, "{\"version\":0}");

        int writers = 4;
        writeConcurrently(resourceId, writers);

        List<ConfigRevision> revisions = history(resourceId);
        // Revision 1 is the creation, the four concurrent updates follow it.
        assertThat(revisions).extracting(ConfigRevision::getRevisionNumber)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    @Test
    void concurrentUpdatesBeyondTheRetentionLimitKeepTheNewestRevisions() throws Exception {
        Long resourceId = 4343L;
        revisionService.recordCreate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, resourceId,
                SCHEMA_VERSION, "{\"version\":0}");

        int writers = ConfigRevisionService.MAX_REVISIONS_PER_RESOURCE + 6;
        writeConcurrently(resourceId, writers);

        List<ConfigRevision> revisions = history(resourceId);
        assertThat(revisions).hasSize(ConfigRevisionService.MAX_REVISIONS_PER_RESOURCE);
        assertThat(revisions).extracting(ConfigRevision::getRevisionNumber).doesNotHaveDuplicates();
    }

    /**
     * Runs {@code writers} saves of the same resource in parallel and rethrows the first
     * failure, so a lost update or a failed prune fails the test.
     */
    private void writeConcurrently(Long resourceId, int writers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < writers; i++) {
                int version = i + 1;
                results.add(pool.submit(() -> {
                    start.await();
                    revisionService.recordUpdate(ORGANIZATION_ID, ConfigResourceType.DASHBOARD, resourceId,
                            SCHEMA_VERSION, "{\"version\":0}", "{\"version\":" + version + "}");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private List<ConfigRevision> history(Long resourceId) {
        return revisionRepository.findByOrganizationIdAndResourceTypeAndResourceId(
                ORGANIZATION_ID, ConfigResourceType.DASHBOARD, resourceId,
                PageRequest.of(0, 100)).getContent();
    }
}
