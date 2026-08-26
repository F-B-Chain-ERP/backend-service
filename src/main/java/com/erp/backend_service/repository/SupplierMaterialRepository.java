package com.erp.backend_service.repository;

import com.erp.core.domain.SupplierMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SupplierMaterialRepository extends JpaRepository<SupplierMaterial, UUID> {
    boolean existsBySupplierIdAndMaterialId(UUID supplierID, UUID materialID);
    boolean existsBySupplierIdAndMaterialIdAndIdNot(UUID supplierID, UUID materialID, UUID id);

    @Query("""
        SELECT sm 
        FROM SupplierMaterial sm 
        WHERE (:supplierId IS NULL OR sm.supplierId = :supplierId)
        AND (:materialId IS NULL OR sm.materialId = :supplierId)
        AND (
                :search IS NULL 
                OR LOWER(sm.supplierSku) LIKE CONCAT('%', :search, '%')
        )
""")
    Page search(
            UUID supplierId,
            UUID materialId,
            String search,
            Pageable pageable
    );
}
