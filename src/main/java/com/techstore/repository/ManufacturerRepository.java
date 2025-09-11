package com.techstore.repository;

import com.techstore.entity.Manufacturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {

    Optional<Manufacturer> findByExternalId(Long externalId);

    List<Manufacturer> findAllByOrderByNameAsc();

    @Query("SELECT DISTINCT m FROM Manufacturer m " +
            "JOIN m.products p " +
            "WHERE p.show = true AND p.status != 'NOT_AVAILABLE'")
    List<Manufacturer> findManufacturersWithAvailableProducts();
}