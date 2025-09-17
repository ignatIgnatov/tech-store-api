package com.techstore.repository;

import com.techstore.entity.ProductDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductDocumentRepository extends JpaRepository<ProductDocument, Long> {

    /**
     * Find all documents for a specific product
     */
    List<ProductDocument> findByProductId(Long productId);

    /**
     * Find document by product ID and document URL (for duplicate checking)
     */
    Optional<ProductDocument> findByProductIdAndDocumentUrl(Long productId, String documentUrl);

    /**
     * Find documents by product external ID
     */
    @Query("SELECT pd FROM ProductDocument pd WHERE pd.product.externalId = :externalId")
    List<ProductDocument> findByProductExternalId(@Param("externalId") Long externalId);

    /**
     * Delete all documents for a specific product
     */
    void deleteByProductId(Long productId);

    /**
     * Count documents for a specific product
     */
    long countByProductId(Long productId);

    /**
     * Find documents with empty comments (for data cleanup)
     */
    @Query("SELECT pd FROM ProductDocument pd WHERE (pd.commentBg IS NULL OR pd.commentBg = '') AND (pd.commentEn IS NULL OR pd.commentEn = '')")
    List<ProductDocument> findDocumentsWithoutComments();
}