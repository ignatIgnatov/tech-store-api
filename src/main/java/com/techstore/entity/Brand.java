package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "brands")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"products"})
public class Brand extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 200)
    private String name;

    @Column(unique = true, nullable = false, length = 200)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 1000)
    private String logoUrl;

    @Column(length = 1000)
    private String websiteUrl;

    @Column(length = 100)
    private String country;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean featured = false;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    public int getProductCount() {
        return products != null ? products.size() : 0;
    }

    public int getActiveProductCount() {
        return products != null ? (int) products.stream()
                .filter(product -> product.getActive())
                .count() : 0;
    }
}