package com.erp.backend_service.repository;

import com.erp.core.domain.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/** Truy vấn dữ liệu đơn mua hàng (purchase_order). */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    /** Kiểm tra mã đơn mua hàng đã tồn tại hay chưa (dùng cho tạo). */
    boolean existsByPoCode(String poCode);

    /**
     * Lấy đơn có mã poCode lớn nhất với tiền tố cho trước (dùng sinh mã PO-YYYYMM-XXXX).
     */
    org.springframework.data.domain.Page<PurchaseOrder> findFirstByPoCodeStartingWithOrderByPoCodeDesc(String prefix, Pageable pageable);

    /**
     * Tìm kiếm phân trang theo mã đơn (poCode), trạng thái, nhà cung cấp, kho và khoảng ngày đặt,
     * đồng thời hỗ trợ lọc theo danh sách kho được phân quyền (allowedWarehouseIds).
     * Các tham số {@code null} tương ứng không lọc.
     */
    @Query("""
            select po from PurchaseOrder po
            where (:search is null or :search = ''
                   or lower(po.poCode) like lower(concat('%', :search, '%')))
              and (:status is null or po.status = :status)
              and (:supplierId is null or po.supplierId = :supplierId)
              and (:warehouseId is null or po.warehouseId = :warehouseId)
              and (:allowedWarehouseIds is null or po.warehouseId in :allowedWarehouseIds)
              and (:fromDate is null or po.orderDate >= :fromDate)
              and (:toDate is null or po.orderDate <= :toDate)
            """)
    Page<PurchaseOrder> search(@Param("search") String search,
                               @Param("status") String status,
                               @Param("supplierId") UUID supplierId,
                               @Param("warehouseId") UUID warehouseId,
                               @Param("allowedWarehouseIds") java.util.Collection<UUID> allowedWarehouseIds,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               Pageable pageable);
}
