package com.techstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

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

    @Column(name = "external_id")
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "tekra_key")
    private String tekraKey;

    @FullTextField
    @Column(name = "name_bg")
    private String nameBg;

    @FullTextField
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "sort_order")
    private Integer order;

    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<ParameterOption> options = new HashSet<>();
}
