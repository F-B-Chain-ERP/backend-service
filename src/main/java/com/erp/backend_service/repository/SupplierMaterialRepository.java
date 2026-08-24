package com.erp.backend_service.repository;

import com.erp.core.domain.SupplierMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierMaterialRepository extends JpaRepository<SupplierMaterial, UUID> {
    boolean existsBySupplierIdAndMaterialId(UUID supplierID, UUID materialID);
    boolean existsBySupplierIdAndMaterialIdAndIdNot(UUID supplierID, UUID materialID, UUID id);
    Page<SupplierMaterial> findBySupplierIdAndMaterialId(UUID supplierID, UUID materialID, Pageable pageable);
    Page<SupplierMaterial> findBySupplierId(UUID supplierId, Pageable pageable);
    Page<SupplierMaterial> findByMaterialId(UUID materialId, Pageable pageable);
    Page<SupplierMaterial> findBySupplierSkuContainingIgnoreCase(String supplierSku, Pageable pageable);
}
