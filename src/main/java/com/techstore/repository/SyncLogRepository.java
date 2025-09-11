package com.techstore.repository;

import com.techstore.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Optional<SyncLog> findTopBySyncTypeOrderByCreatedAtDesc(String syncType);

    Page<SyncLog> findBySyncTypeOrderByCreatedAtDesc(String syncType, Pageable pageable);

    @Query("SELECT sl FROM SyncLog sl WHERE sl.syncType = :syncType " +
            "AND sl.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY sl.createdAt DESC")
    Page<SyncLog> findBySyncTypeAndDateRange(@Param("syncType") String syncType,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate,
                                             Pageable pageable);
}