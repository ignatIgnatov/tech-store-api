package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;

@Entity
@Table(name = "product_flags")
@Getter
@Setter
public class ProductFlag extends BaseEntity {

    @GenericField(name = "externalId", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @KeywordField(name = "imageUrl", projectable = Projectable.YES)
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @FullTextField(name = "nameBg", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameBg_exact", aggregable = Aggregable.YES)
    @Column(name = "name_bg")
    private String nameBg;

    @FullTextField(name = "nameEn", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameEn_exact", aggregable = Aggregable.YES)
    @Column(name = "name_en")
    private String nameEn;
}