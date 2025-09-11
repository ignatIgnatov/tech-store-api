package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "product_specifications")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"product", "template"})
public class ProductSpecification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String specValue;

    @Column(length = 1000)
    private String specValueSecondary; // For ranges (min-max)

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private CategorySpecificationTemplate template;

    // Computed properties
    public String getSpecName() {
        return template != null ? template.getSpecName() : null;
    }

    public String getSpecUnit() {
        return template != null ? template.getSpecUnit() : null;
    }

    public String getSpecGroup() {
        return template != null ? template.getSpecGroup() : null;
    }

    public String getFormattedValue() {
        if (template == null) return specValue;

        switch (template.getType()) {
            case RANGE:
                if (specValueSecondary != null) {
                    return specValue + " - " + specValueSecondary +
                            (template.getSpecUnit() != null ? " " + template.getSpecUnit() : "");
                }
                break;
            case BOOLEAN:
                return Boolean.parseBoolean(specValue) ? "Yes" : "No";
            case NUMBER:
            case DECIMAL:
                return specValue + (template.getSpecUnit() != null ? " " + template.getSpecUnit() : "");
        }

        return specValue;
    }
}