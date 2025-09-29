package com.techstore.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;

@Entity
@Table(name = "product_parameters")
@Getter
@Setter
@EqualsAndHashCode(callSuper = false, exclude = {"product"})
@ToString(exclude = {"product"})
public class ProductParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parameter_id", nullable = false)
    @IndexedEmbedded(name = "parameter", includeDepth = 1)
    private Parameter parameter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parameter_option_id", nullable = false)
    @IndexedEmbedded(name = "parameterOption", includeDepth = 1)
    private ParameterOption parameterOption;

    @FullTextField(name = "searchableParameterText", projectable = Projectable.YES)
    @Transient
    public String getSearchableParameterText() {
        StringBuilder sb = new StringBuilder();
        if (parameter != null) {
            if (parameter.getNameEn() != null) sb.append(parameter.getNameEn()).append(" ");
            if (parameter.getNameBg() != null) sb.append(parameter.getNameBg()).append(" ");
        }
        if (parameterOption != null) {
            if (parameterOption.getNameEn() != null) sb.append(parameterOption.getNameEn()).append(" ");
            if (parameterOption.getNameBg() != null) sb.append(parameterOption.getNameBg()).append(" ");
        }
        return sb.toString().trim();
    }
}