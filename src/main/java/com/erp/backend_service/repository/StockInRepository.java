package com.erp.backend_service.repository;

import com.erp.core.domain.StockIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/** Truy vấn dữ liệu phiếu nhập kho (stock_in). */
@Repository
public interface StockInRepository extends JpaRepository<StockIn, UUID> {

    /** Lấy phiếu nhập có mã lớn nhất theo tiền tố cho trước (dùng sinh mã SI-yyyyMM-XXXX). */
    Page<StockIn> findFirstByCodeStartingWithOrderByCodeDesc(String prefix, Pageable pageable);

    /**
     * Tìm kiếm phân trang theo mã phiếu, trạng thái, kho và loại nguồn nhập, khoảng ngày nhập,
     * đồng thời hỗ trợ lọc theo danh sách kho được phân quyền (allowedWarehouseIds).
     */
    @Query("""
            select si from StockIn si
            where (:search is null or :search = ''
                   or lower(si.code) like lower(concat('%', :search, '%')))
              and (:status is null or si.status = :status)
              and (:warehouseId is null or si.warehouseId = :warehouseId)
              and (:allowedWarehouseIds is null or si.warehouseId in :allowedWarehouseIds)
              and (:sourceType is null or si.sourceType = :sourceType)
              and (:fromDate is null or si.inDate >= :fromDate)
              and (:toDate is null or si.inDate <= :toDate)
            """)
    Page<StockIn> search(@Param("search") String search,
                         @Param("status") String status,
                         @Param("warehouseId") UUID warehouseId,
                         @Param("allowedWarehouseIds") Collection<UUID> allowedWarehouseIds,
                         @Param("sourceType") String sourceType,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate,
                         Pageable pageable);
}