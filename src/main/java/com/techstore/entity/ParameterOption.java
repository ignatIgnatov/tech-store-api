package com.techstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "parameter_options")
@Getter
@Setter
public class ParameterOption extends BaseEntity {

    @Column(name = "external_id")
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id")
    private Parameter parameter;

    @FullTextField
    @Column(name = "name_bg")
    private String nameBg;

    @FullTextField
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "sort_order")
    private Integer order;

    @OneToMany(mappedBy = "parameterOption", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ProductParameter> productParameters = new HashSet<>();
}