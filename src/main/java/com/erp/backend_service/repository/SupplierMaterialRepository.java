package com.erp.backend_service.repository;

import com.erp.core.domain.SupplierMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SupplierMaterialRepository extends JpaRepository<SupplierMaterial, UUID> {
    boolean existsBySupplierIdAndMaterialId(UUID supplierID, UUID materialID);
    boolean existsBySupplierIdAndMaterialIdAndIdNot(UUID supplierID, UUID materialID, UUID id);

    /**
     * Gỡ cờ ưu tiên của tất cả NCC khác cùng NVL.
     * Dùng khi bật isPreferred=true để đảm bảo mỗi NVL chỉ có 1 NCC ưu tiên.
     */
    @Modifying
    @Query(value = "UPDATE supplier_material SET is_preferred = false WHERE material_id = :materialId", nativeQuery = true)
    void clearPreferredByMaterialId(@Param("materialId") UUID materialId);

    @Modifying
    @Query(value = "UPDATE supplier_material SET is_preferred = false WHERE material_id = :materialId AND id <> :excludeId", nativeQuery = true)
    void clearPreferredByMaterialIdAndIdNot(@Param("materialId") UUID materialId, @Param("excludeId") UUID excludeId);

    @Query("""
        SELECT sm 
        FROM SupplierMaterial sm 
        WHERE (:supplierId IS NULL OR sm.supplierId = :supplierId)
        AND (:materialId IS NULL OR sm.materialId = :materialId)
        AND (
                :search IS NULL 
                OR :search = '' 
                OR LOWER(COALESCE(sm.supplierSku, '')) LIKE CONCAT('%', LOWER(:search), '%')
        )
""")
    Page search(
            UUID supplierId,
            UUID materialId,
            String search,
            Pageable pageable
    );
}
