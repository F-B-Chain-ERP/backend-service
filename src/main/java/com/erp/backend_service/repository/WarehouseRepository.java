package com.erp.backend_service.repository;

import com.erp.core.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu kho (warehouse). */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    boolean existsByCode(String code);

    List<Warehouse> findByBranchId(UUID branchId);

    @Query("""
        SELECT w FROM Warehouse w
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(w.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(w.name) LIKE CONCAT('%', LOWER(:search), '%'))
          AND (:warehouseType IS NULL OR w.warehouseType = :warehouseType)
          AND (:branchId IS NULL OR w.branchId = :branchId)
          AND (:status IS NULL OR w.status = :status)
    """)
    Page<Warehouse> search(String search, String warehouseType, UUID branchId, String status, Pageable pageable);
}
