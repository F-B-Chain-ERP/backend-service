package com.erp.backend_service.repository;

import com.erp.core.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu kho (warehouse). */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    /** Tìm danh sách kho thuộc một chi nhánh cụ thể. */
    List<Warehouse> findByBranchId(UUID branchId);

    /** Kiểm tra mã kho đã tồn tại chưa. */
    boolean existsByCode(String code);

    /** Kiểm tra mã kho đã tồn tại cho bản ghi khác chưa (dùng khi cập nhật). */
    boolean existsByCodeAndIdNot(String code, UUID id);

    /** Lấy danh sách kho theo trạng thái (ví dụ ACTIVE để nạp dropdown). */
    List<Warehouse> findByStatus(String status);

    /**
     * Tìm kiếm phân trang theo mã/tên kho, chi nhánh, loại kho và trạng thái.
     */
    @Query("""
        SELECT w
        FROM Warehouse w
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(w.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(w.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:branchId IS NULL OR w.branchId = :branchId)
        AND (:warehouseType IS NULL OR w.warehouseType = :warehouseType)
        AND (:status IS NULL OR w.status = :status)
    """)
    Page<Warehouse> search(
            @Param("search") String search,
            @Param("branchId") UUID branchId,
            @Param("warehouseType") String warehouseType,
            @Param("status") String status,
            Pageable pageable
    );
}

