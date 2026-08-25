package com.erp.backend_service.repository;

import com.erp.core.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu kho (warehouse). */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    /** Tìm danh sách kho thuộc một chi nhánh cụ thể. */
    List<Warehouse> findByBranchId(UUID branchId);
}
