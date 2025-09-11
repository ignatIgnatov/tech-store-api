package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "category_specification_templates")
@Data
@EqualsAndHashCode(callSuper = false)
public class CategorySpecificationTemplate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String specName;

    @Column(length = 500)
    private String specUnit;

    @Column(length = 100)
    private String specGroup;

    @Column(nullable = false)
    private Boolean required = false;

    @Column(nullable = false)
    private Boolean filterable = false;

    @Column(nullable = false)
    private Boolean searchable = false;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecificationType type = SpecificationType.TEXT;

    @Column(columnDefinition = "TEXT")
    private String allowedValues; // JSON array for dropdown/checkbox options

    @Column(length = 1000)
    private String description; // Help text for users

    @Column(length = 500)
    private String placeholder; // Input placeholder

    @Column(nullable = false)
    private Boolean showInListing = false; // Show in product cards

    @Column(nullable = false)
    private Boolean showInComparison = true; // Show in product comparison

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public enum SpecificationType {
        TEXT,           // Free text input
        NUMBER,         // Numeric input with validation
        DECIMAL,        // Decimal number
        DROPDOWN,       // Single selection dropdown
        MULTI_SELECT,   // Multiple selection
        BOOLEAN,        // Yes/No checkbox
        RANGE,          // Min-Max range (e.g., price range)
        COLOR,          // Color picker
        URL,            // URL validation
        EMAIL,          // Email validation
        DATE            // Date picker
    }
}