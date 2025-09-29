package com.techstore.entity;

import com.techstore.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
@Data
@EqualsAndHashCode(callSuper = false)
@Indexed // Main search index
public class Product extends BaseEntity {

    @Column(name = "tekra_id")
    private String tekraId;

    @KeywordField(name = "sku", projectable = Projectable.YES, aggregable = Aggregable.YES)
    private String sku;

    @FullTextField(name = "nameBg", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameBg_sort", normalizer = "lowercase", sortable = Sortable.YES)
    @Column(name = "name_bg", columnDefinition = "TEXT")
    private String nameBg;

    @FullTextField(name = "nameEn", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "nameEn_sort", normalizer = "lowercase", sortable = Sortable.YES)
    @Column(name = "name_en", columnDefinition = "TEXT")
    private String nameEn;

    @FullTextField(name = "descriptionBg", searchable = Searchable.YES)
    @Column(name = "description_bg", columnDefinition = "TEXT")
    private String descriptionBg;

    @FullTextField(name = "descriptionEn", searchable = Searchable.YES)
    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @GenericField(projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "external_id", unique = true)
    private Long externalId;

    @GenericField(projectable = Projectable.YES)
    @Column(name = "workflow_id")
    private Long workflowId;

    @FullTextField(name = "referenceNumber", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "referenceNumber_exact", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "reference_number", unique = true)
    private String referenceNumber;

    @FullTextField(name = "model", searchable = Searchable.YES, projectable = Projectable.YES)
    @KeywordField(name = "model_exact", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "model")
    private String model;

    @KeywordField(name = "barcode", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "barcode")
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id")
    @IndexedEmbedded(name = "manufacturer", includeDepth = 1)
    private Manufacturer manufacturer;

    @GenericField(name = "status", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status;

    @GenericField(name = "priceClient", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(name = "price_client", precision = 10, scale = 2)
    private BigDecimal priceClient;

    @GenericField(name = "pricePartner", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(name = "price_partner", precision = 10, scale = 2)
    private BigDecimal pricePartner;

    @GenericField(name = "pricePromo", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(name = "price_promo", precision = 10, scale = 2)
    private BigDecimal pricePromo;

    @GenericField(name = "priceClientPromo", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(name = "price_client_promo", precision = 10, scale = 2)
    private BigDecimal priceClientPromo;

    @GenericField(name = "markupPercentage", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "markup_percentage", precision = 5, scale = 2)
    private BigDecimal markupPercentage = BigDecimal.valueOf(20.0);

    @GenericField(name = "finalPrice", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(name = "final_price", precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @GenericField(name = "show", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Column(name = "show_flag")
    private Boolean show = true;

    @GenericField(name = "warranty", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    private Integer warranty;

    @GenericField(name = "discount", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    @Column(precision = 8, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @GenericField(name = "active", projectable = Projectable.YES, aggregable = Aggregable.YES)
    private Boolean active = true;

    @GenericField(name = "featured", projectable = Projectable.YES, aggregable = Aggregable.YES)
    private Boolean featured = false;

    @KeywordField(name = "primaryImageUrl", projectable = Projectable.YES)
    @Column(name = "image_url", length = 1000)
    private String primaryImageUrl;

    @ElementCollection
    @CollectionTable(name = "additional_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "additional_urls", length = 1000)
    private List<String> additionalImages = new ArrayList<>();

    @GenericField(name = "weight", projectable = Projectable.YES, sortable = Sortable.YES, aggregable = Aggregable.YES)
    private BigDecimal weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @IndexedEmbedded(name = "category", includeDepth = 2)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @IndexedEmbedded(name = "productParameters", includeDepth = 2)
    private Set<ProductParameter> productParameters = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @IndexedEmbedded(name = "productFlags", includeDepth = 1)
    private Set<ProductFlag> productFlags = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserFavorite> favorites = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<CartItem> cartItems = new HashSet<>();

    // Calculated fields for search
    @GenericField(name = "onSale", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Transient
    public boolean isOnSaleIndexed() {
        return discount != null && discount.compareTo(BigDecimal.ZERO) > 0;
    }

    @GenericField(name = "hasImages", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Transient
    public boolean hasImages() {
        return primaryImageUrl != null || (additionalImages != null && !additionalImages.isEmpty());
    }

    @GenericField(name = "totalImages", projectable = Projectable.YES, aggregable = Aggregable.YES)
    @Transient
    public int getTotalImagesCount() {
        int count = primaryImageUrl != null ? 1 : 0;
        if (additionalImages != null) {
            count += additionalImages.size();
        }
        return count;
    }

    @FullTextField(name = "searchableText", searchable = Searchable.YES)
    @Transient
    public String getSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (nameEn != null) sb.append(nameEn).append(" ");
        if (nameBg != null) sb.append(nameBg).append(" ");
        if (model != null) sb.append(model).append(" ");
        if (referenceNumber != null) sb.append(referenceNumber).append(" ");
        if (barcode != null) sb.append(barcode).append(" ");
        if (manufacturer != null && manufacturer.getName() != null) {
            sb.append(manufacturer.getName()).append(" ");
        }
        if (category != null) {
            if (category.getNameEn() != null) sb.append(category.getNameEn()).append(" ");
            if (category.getNameBg() != null) sb.append(category.getNameBg()).append(" ");
        }
        return sb.toString().trim();
    }

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