package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "product_specifications")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"product"})
public class ProductSpecification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String specName;

    @Column(nullable = false, length = 1000)
    private String specValue;

    @Column(length = 500)
    private String specUnit;

    @Column(length = 100)
    private String specGroup;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    public ProductSpecification() {}

    public ProductSpecification(String specName, String specValue) {
        this.specName = specName;
        this.specValue = specValue;
    }

    public ProductSpecification(String specName, String specValue, String specUnit) {
        this.specName = specName;
        this.specValue = specValue;
        this.specUnit = specUnit;
    }

    public ProductSpecification(String specName, String specValue, String specUnit, String specGroup) {
        this.specName = specName;
        this.specValue = specValue;
        this.specUnit = specUnit;
        this.specGroup = specGroup;
    }

    public String getFormattedValue() {
        if (specUnit != null && !specUnit.trim().isEmpty()) {
            return specValue + " " + specUnit;
        }
        return specValue;
    }
}