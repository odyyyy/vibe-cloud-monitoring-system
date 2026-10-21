package com.monitoring.api.platform;

import com.monitoring.apps.entity.Application;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the self-monitoring tenant, application and built-in dashboard exist on startup.
 * The operation is idempotent, so restarts do not duplicate resources.
 */
@Component
@RequiredArgsConstructor
public class PlatformBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final PlatformResourceService platformResourceService;
    private final PlatformAdminService platformAdminService;

    @Override
    public void run(ApplicationArguments args) {
        Application application = platformResourceService.ensureSystemApplication();
        platformResourceService.ensurePlatformDashboard(application);
        platformAdminService.ensurePlatformAdmin();
        log.info("Platform self-monitoring initialized (system application id={})", application.getId());
    }
}
