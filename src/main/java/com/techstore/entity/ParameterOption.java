package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "parameter_options")
@Getter
@Setter
public class ParameterOption extends BaseEntity {

    @GenericField(name = "externalId", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "external_id")
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id")
    @IndexedEmbedded(name = "parameter", includeDepth = 1)
    private Parameter parameter;

    @FullTextField(name = "nameBg", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameBg_exact", aggregable = Aggregable.YES)
    @Column(name = "name_bg")
    private String nameBg;

    @FullTextField(name = "nameEn", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameEn_exact", aggregable = Aggregable.YES)
    @Column(name = "name_en")
    private String nameEn;

    @GenericField(name = "order", projectable = Projectable.YES, sortable = Sortable.YES)
    @Column(name = "sort_order")
    private Integer order;

    @OneToMany(mappedBy = "parameterOption", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ProductParameter> productParameters = new HashSet<>();
}