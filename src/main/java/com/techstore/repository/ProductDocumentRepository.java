package com.techstore.repository;

import com.techstore.entity.ProductDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductDocumentRepository extends JpaRepository<ProductDocument, Long> {

    Optional<ProductDocument> findByProductIdAndDocumentUrl(Long productId, String documentUrl);
}