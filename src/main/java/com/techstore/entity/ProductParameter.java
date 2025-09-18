package com.techstore.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_parameters")
@Getter
@Setter
public class ProductParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parameter_option_id", nullable = false)
    private ParameterOption parameterOption;
}