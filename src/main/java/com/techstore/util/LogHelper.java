package com.techstore.util;

import com.techstore.entity.SyncLog;
import com.techstore.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogHelper {

    public static final String LOG_STATUS_SUCCESS = "SUCCESS";
    public static final String LOG_STATUS_FAILED = "FAILED";
    public static final String LOG_STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final SyncLogRepository syncLogRepository;

    public SyncLog createSyncLogSimple(String syncType) {
        SyncLog syncLog = new SyncLog();
        syncLog.setSyncType(syncType);
        syncLog.setStatus(LOG_STATUS_IN_PROGRESS);
        return syncLogRepository.save(syncLog);
    }

    public void updateSyncLogSimple(SyncLog syncLog, String status, long totalRecords,
                                     long created, long updated, long errors,
                                     String message, long startTime) {
        syncLog.setStatus(status);
        syncLog.setRecordsProcessed(totalRecords);
        syncLog.setRecordsCreated(created);
        syncLog.setRecordsUpdated(updated);
        syncLog.setErrorMessage(message);
        syncLog.setCreatedAt(java.time.LocalDateTime.now());
        syncLog.setDurationMs(System.currentTimeMillis() - startTime);
        syncLogRepository.save(syncLog);
    }
}
