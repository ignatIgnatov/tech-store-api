package com.techstore.service;

import com.techstore.dto.request.ProductSearchRequestDto;
import com.techstore.dto.response.ProductSummaryDto;
import com.techstore.entity.Product;
import com.techstore.mapper.ProductMapper;
import com.techstore.repository.UserFavoriteRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final EntityManager entityManager;
    private final ProductMapper productMapper;
    private final UserFavoriteRepository userFavoriteRepository;

    public Page<ProductSummaryDto> searchProducts(ProductSearchRequestDto searchRequest, Long userId, String language) {
        SearchSession searchSession = Search.session(entityManager);

        SearchResult<Product> searchQuery = searchSession.search(Product.class)
                .where(f -> f.bool(b -> {
                    // Must be visible and available
                    b.must(f.match().field("show").matching(true));
                    b.mustNot(f.match().field("status").matching("NOT_AVAILABLE"));

                    // Text search in multiple fields
                    if (searchRequest.getQuery() != null && !searchRequest.getQuery().trim().isEmpty()) {
                        String query = searchRequest.getQuery().trim();
                        b.must(f.bool(textSearch -> {
                            if ("bg".equals(language)) {
                                textSearch.should(f.match().field("nameBg").matching(query).boost(2.0f));
                                textSearch.should(f.match().field("descriptionBg").matching(query));
                            } else {
                                textSearch.should(f.match().field("nameEn").matching(query).boost(2.0f));
                                textSearch.should(f.match().field("descriptionEn").matching(query));
                            }
                            textSearch.should(f.match().field("referenceNumber").matching(query).boost(3.0f));
                            textSearch.should(f.match().field("model").matching(query).boost(1.5f));
                            textSearch.should(f.match().field("manufacturer.name").matching(query).boost(1.5f));
                        }));
                    }

                    // Additional filters
                    if (searchRequest.getCategoryId() != null) {
                        b.must(f.match().field("productCategories.category.id").matching(searchRequest.getCategoryId()));
                    }

                    if (searchRequest.getManufacturerId() != null) {
                        b.must(f.match().field("manufacturer.id").matching(searchRequest.getManufacturerId()));
                    }

                    if (searchRequest.getMinPrice() != null) {
                        b.must(f.range().field("finalPrice").atLeast(searchRequest.getMinPrice()));
                    }

                    if (searchRequest.getMaxPrice() != null) {
                        b.must(f.range().field("finalPrice").atMost(searchRequest.getMaxPrice()));
                    }
                }))
                .sort(f -> {
                    switch (searchRequest.getSortBy().toLowerCase()) {
                        case "price":
                            return searchRequest.getSortDirection().equalsIgnoreCase("desc") ?
                                    f.field("finalPrice").desc() : f.field("finalPrice").asc();
                        case "createdat":
                            return searchRequest.getSortDirection().equalsIgnoreCase("desc") ?
                                    f.field("createdAt").desc() : f.field("createdAt").asc();
                        default:
                            return f.score().desc(); // Relevance-based sorting for text search
                    }
                })
                .fetch(searchRequest.getPage() * searchRequest.getSize(), searchRequest.getSize());

        assert false;
        List<Product> products = ((SearchResult<Product>) searchQuery).hits();
        long totalHits = ((SearchResult<Product>) searchQuery).total().hitCount();

        List<ProductSummaryDto> dtos = products.stream()
                .map(product -> {
                    ProductSummaryDto dto = productMapper.toSummaryDto(product, language);
                    if (userId != null) {
                        dto.setIsFavorite(userFavoriteRepository.existsByUserIdAndProductId(userId, product.getId()));
                    }
                    return dto;
                })
                .toList();

        PageRequest pageRequest = PageRequest.of(searchRequest.getPage(), searchRequest.getSize());
        return new PageImpl<>(dtos, pageRequest, totalHits);
    }
}