package com.techstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "categories")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"parent", "children", "products", "specificationTemplates"})
public class Category extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true, nullable = false)
    private Long externalId;

    @FullTextField
    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @FullTextField
    @Column(name = "name_bg")
    private String nameBg;

    @Column(length = 200)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 1000)
    private String imageUrl;

    @Column(name = "show_flag", nullable = false)
    private Boolean show = true;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Category> children = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ProductCategory> productCategories = new HashSet<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CategorySpecificationTemplate> specificationTemplates = new ArrayList<>();

    public List<CategorySpecificationTemplate> getRequiredSpecifications() {
        return specificationTemplates.stream()
                .filter(CategorySpecificationTemplate::getRequired)
                .sorted(Comparator.comparing(CategorySpecificationTemplate::getSortOrder))
                .toList();
    }

    public List<CategorySpecificationTemplate> getFilterableSpecifications() {
        return specificationTemplates.stream()
                .filter(CategorySpecificationTemplate::getFilterable)
                .sorted(Comparator.comparing(CategorySpecificationTemplate::getSortOrder))
                .toList();
    }

    public boolean isParentCategory() {
        return parent == null;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public String getFullPath() {
        if (parent == null) {
            return nameEn;
        }
        return parent.getFullPath() + " / " + nameEn;
    }
}