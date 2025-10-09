package com.techstore.service;

import com.techstore.service.sync.TekraSyncService;
import com.techstore.service.sync.ValiSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CronJobService {

    @Value("${app.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${app.sync.tekra.enabled:true}")
    private boolean tekraSyncEnabled;

    private final ValiSyncService valiSyncService;
    private final TekraSyncService tekraSyncService;

    @Scheduled(cron = "${app.sync.cron}")
    public void syncVali() {
        if (!syncEnabled) {
            log.info("Synchronization is disabled");
            return;
        }

        log.info("Starting scheduled Vali synchronization at {}", LocalDateTime.now());
        try {
            valiSyncService.syncCategories();
            log.info("Scheduled category synchronization completed at {}", LocalDateTime.now());

            valiSyncService.syncManufacturers();
            log.info("Scheduled manufacturers synchronization completed at {}", LocalDateTime.now());

            valiSyncService.syncParameters();
            log.info("Scheduled parameters synchronization completed at {}", LocalDateTime.now());

            valiSyncService.syncProducts();
            log.info("Scheduled products synchronization completed at {}", LocalDateTime.now());

        } catch (Exception e) {
            log.error("CRITICAL: Scheduled Vali synchronization failed", e);
        }
    }

    @Scheduled(cron = "${app.sync.tekra.sync-cron}")
    public void syncTekra() {
        if (!tekraSyncEnabled) {
            log.info("Synchronization is disabled");
            return;
        }

        log.info("Starting scheduled Tekra synchronization at {}", LocalDateTime.now());
        try {
            tekraSyncService.syncTekraCategories();
            log.info("Scheduled Tekra category synchronization completed at {}", LocalDateTime.now());

            tekraSyncService.syncTekraManufacturers();
            log.info("Scheduled Tekra manufacturers synchronization completed at {}", LocalDateTime.now());

            tekraSyncService.syncTekraParameters();
            log.info("Scheduled Tekra parameters synchronization completed at {}", LocalDateTime.now());

            tekraSyncService.syncTekraProducts();
            log.info("Scheduled Tekra products synchronization completed at {}", LocalDateTime.now());

        } catch (Exception e) {
            log.error("CRITICAL: Scheduled Tekra synchronization failed", e);
        }
    }
}
