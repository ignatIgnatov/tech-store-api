package com.techstore.entity;

import com.techstore.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
@Data
@EqualsAndHashCode(callSuper = false)
public class Product extends BaseEntity {

    @FullTextField
    @Column(name = "name_bg", columnDefinition = "TEXT")
    private String nameBg;

    @FullTextField
    @Column(name = "name_en", columnDefinition = "TEXT")
    private String nameEn;

    @FullTextField
    @Column(name = "description_bg", columnDefinition = "TEXT")
    private String descriptionBg;

    @FullTextField
    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "external_id", unique = true)
    private Long externalId;

    @Column(name = "workflow_id")
    private Long workflowId;

    @FullTextField
    @Column(name = "reference_number", unique = true)
    private String referenceNumber;

    @FullTextField
    @Column(name = "model")
    private String model;

    @Column(name = "barcode")
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id")
    @IndexedEmbedded
    private Manufacturer manufacturer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status;

    @Column(name = "price_client", precision = 10, scale = 2)
    private BigDecimal priceClient;

    @Column(name = "price_partner", precision = 10, scale = 2)
    private BigDecimal pricePartner;

    @Column(name = "price_promo", precision = 10, scale = 2)
    private BigDecimal pricePromo;

    @Column(name = "price_client_promo", precision = 10, scale = 2)
    private BigDecimal priceClientPromo;

    @Column(name = "markup_percentage", precision = 5, scale = 2)
    private BigDecimal markupPercentage = BigDecimal.valueOf(20.0);

    @Column(name = "final_price", precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "show_flag")
    private Boolean show = true;

    private Integer warranty;

    @Column(precision = 8, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    private Boolean active = true;

    private Boolean featured = false;

    @Column(name = "image_url", length = 1000)
    private String primaryImageUrl;

    @ElementCollection
    @CollectionTable(name = "additional_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "additional_urls", length = 1000)
    private List<String> additionalImages = new ArrayList<>();

    @Column(precision = 3, scale = 2)
    private BigDecimal weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<ProductParameter> productParameters = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ProductFlag> productFlags = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserFavorite> favorites = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<CartItem> cartItems = new HashSet<>();

    public void calculateFinalPrice() {
        if (priceClient != null && markupPercentage != null) {
            BigDecimal markup = priceClient.multiply(markupPercentage.divide(BigDecimal.valueOf(100)));
            this.finalPrice = priceClient.add(markup);
        }
    }

    public boolean isOnSale() {
        return discount != null && discount.compareTo(BigDecimal.ZERO) != 0;
    }
}