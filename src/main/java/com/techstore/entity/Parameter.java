package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "parameters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "external_id"})
)
@Indexed
@Data
public class Parameter extends BaseEntity {

    @GenericField(name = "externalId", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "external_id")
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @IndexedEmbedded(name = "category", includeDepth = 1)
    private Category category;

    @KeywordField(name = "tekraKey", projectable = Projectable.YES)
    @Column(name = "tekra_key")
    private String tekraKey;

    @FullTextField(name = "nameBg", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameBg_sort", normalizer = "lowercase", sortable = Sortable.YES)
    @Column(name = "name_bg")
    private String nameBg;

    @FullTextField(name = "nameEn", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameEn_sort", normalizer = "lowercase", sortable = Sortable.YES)
    @Column(name = "name_en")
    private String nameEn;

    @GenericField(name = "order", projectable = Projectable.YES, sortable = Sortable.YES)
    @Column(name = "sort_order")
    private Integer order;

    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @IndexedEmbedded(name = "options", includeDepth = 1)
    private Set<ParameterOption> options = new HashSet<>();
}