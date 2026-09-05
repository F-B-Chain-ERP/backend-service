package com.erp.backend_service.repository;

import com.erp.core.domain.StockOut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/** Truy vấn dữ liệu phiếu xuất kho (stock_out). */
@Repository
public interface StockOutRepository extends JpaRepository<StockOut, UUID> {

    /** Lấy phiếu xuất có mã lớn nhất theo tiền tố cho trước (dùng sinh mã SO-yyyyMM-XXXX). */
    Page<StockOut> findFirstByCodeStartingWithOrderByCodeDesc(String prefix, Pageable pageable);

    /**
     * Tìm kiếm phân trang theo mã phiếu, trạng thái, kho và loại đích xuất, khoảng ngày xuất,
     * đồng thời hỗ trợ lọc theo danh sách kho được phân quyền (allowedWarehouseIds).
     */
    @Query("""
            select so from StockOut so
            where (:search is null or :search = ''
                   or lower(so.code) like lower(concat('%', :search, '%')))
              and (:status is null or so.status = :status)
              and (:warehouseId is null or so.warehouseId = :warehouseId)
              and (:allowedWarehouseIds is null or so.warehouseId in :allowedWarehouseIds)
              and (:destinationType is null or so.destinationType = :destinationType)
              and (:fromDate is null or so.outDate >= :fromDate)
              and (:toDate is null or so.outDate <= :toDate)
            """)
    Page<StockOut> search(@Param("search") String search,
                          @Param("status") String status,
                          @Param("warehouseId") UUID warehouseId,
                          @Param("allowedWarehouseIds") Collection<UUID> allowedWarehouseIds,
                          @Param("destinationType") String destinationType,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          Pageable pageable);
}