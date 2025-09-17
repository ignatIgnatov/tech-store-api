package com.techstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"parent", "children", "products"})
public class Category extends BaseEntity {

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

    @Column(name = "show_flag", nullable = false)
    private Boolean show = true;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Category> children = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    public boolean isParentCategory() {
        return parent == null;
    }
}