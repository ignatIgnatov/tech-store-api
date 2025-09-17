package com.techstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "manufacturers")
@Indexed
@Getter
@Setter
public class Manufacturer extends BaseEntity {

    @Column(name = "external_id", unique = true, nullable = false)
    private Long externalId;

    @FullTextField
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "information_name")
    private String informationName;

    @Column(name = "information_email")
    private String informationEmail;

    @Column(name = "information_address", columnDefinition = "TEXT")
    private String informationAddress;

    @Column(name = "eu_representative_name")
    private String euRepresentativeName;

    @Column(name = "eu_representative_email")
    private String euRepresentativeEmail;

    @Column(name = "eu_representative_address", columnDefinition = "TEXT")
    private String euRepresentativeAddress;

    @OneToMany(mappedBy = "manufacturer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Product> products = new HashSet<>();
}