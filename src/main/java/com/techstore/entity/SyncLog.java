package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sync_logs")
@Getter
@Setter
public class SyncLog extends BaseEntity {

    @Column(name = "sync_type", nullable = false)
    private String syncType; // CATEGORIES, MANUFACTURERS, PRODUCTS

    @Column(name = "status", nullable = false)
    private String status; // SUCCESS, FAILED, IN_PROGRESS

    @Column(name = "records_processed")
    private Long recordsProcessed;

    @Column(name = "records_created")
    private Long recordsCreated;

    @Column(name = "records_updated")
    private Long recordsUpdated;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;
}