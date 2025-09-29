package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"parent", "children", "products"})
public class Category extends BaseEntity {

    @KeywordField(name = "tekraId", projectable = Projectable.YES)
    @Column(name = "tekra_id")
    private String tekraId;

    @KeywordField(name = "tekraSlug", projectable = Projectable.YES)
    @Column(name = "tekra_slug")
    private String tekraSlug;

    @GenericField(name = "externalId", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "external_id", unique = true)
    private Long externalId;

    @FullTextField(name = "nameEn", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameEn_sort", normalizer = "lowercase", sortable = Sortable.YES)
    @Column(name = "name_en")
    private String nameEn;

    @FullTextField(name = "nameBg", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameBg_sort", normalizer = "lowercase", sortable = Sortable.YES)
    @Column(name = "name_bg")
    private String nameBg;

    @KeywordField(name = "slug", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(length = 200)
    private String slug;

    @GenericField(name = "show", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "show_flag", nullable = false)
    private Boolean show = true;

    @GenericField(name = "sortOrder", projectable = Projectable.YES, sortable = Sortable.YES)
    @Column(nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @IndexedEmbedded(name = "parent", includeDepth = 1)
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Category> children = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    @GenericField(name = "isParent", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Transient
    public boolean isParentCategoryIndexed() {
        return parent == null;
    }

    @GenericField(name = "depth", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Transient
    public int getDepth() {
        int depth = 0;
        Category current = this.parent;
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    public boolean isParentCategory() {
        return parent == null;
    }
}
