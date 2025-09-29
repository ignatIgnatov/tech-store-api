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
@Table(name = "manufacturers")
@Indexed
@Getter
@Setter
public class Manufacturer extends BaseEntity {

    @GenericField(name = "externalId", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "external_id", unique = true)
    private Long externalId;

    @FullTextField(name = "name", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "name_sort", normalizer = "lowercase", sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(name = "name", nullable = false)
    private String name;

    @FullTextField(name = "informationName", searchable = Searchable.YES)
    @Column(name = "information_name")
    private String informationName;

    @KeywordField(name = "informationEmail", projectable = Projectable.YES)
    @Column(name = "information_email")
    private String informationEmail;

    @FullTextField(name = "informationAddress", searchable = Searchable.YES)
    @Column(name = "information_address", columnDefinition = "TEXT")
    private String informationAddress;

    @FullTextField(name = "euRepresentativeName", searchable = Searchable.YES)
    @Column(name = "eu_representative_name")
    private String euRepresentativeName;

    @KeywordField(name = "euRepresentativeEmail", projectable = Projectable.YES)
    @Column(name = "eu_representative_email")
    private String euRepresentativeEmail;

    @FullTextField(name = "euRepresentativeAddress", searchable = Searchable.YES)
    @Column(name = "eu_representative_address", columnDefinition = "TEXT")
    private String euRepresentativeAddress;

    @OneToMany(mappedBy = "manufacturer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Product> products = new HashSet<>();

    @GenericField(name = "productCount", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Transient
    public int getProductCount() {
        return products != null ? products.size() : 0;
    }
}
