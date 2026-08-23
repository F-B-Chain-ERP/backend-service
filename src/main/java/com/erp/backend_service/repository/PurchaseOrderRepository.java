package com.erp.backend_service.repository;

import com.erp.core.domain.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu đơn mua hàng (purchase_order). */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    /** Kiểm tra mã đơn mua hàng đã tồn tại hay chưa (dùng cho tạo). */
    boolean existsByPoCode(String poCode);
    /**
     * Tìm kiếm phân trang theo mã đơn (poCode) và trạng thái.
     * Khi {@code search} rỗng/null thì không lọc theo mã; khi {@code status} null thì không lọc trạng thái.
     */
    @Query("""
            select po from PurchaseOrder po
            where (:search is null or :search = ''
                   or lower(po.poCode) like lower(concat('%', :search, '%')))
              and (:status is null or po.status = :status)
            """)
    Page<PurchaseOrder> search(@Param("search") String search, @Param("status") String status, Pageable pageable);
}
