package com.erp.backend_service.repository;

import com.erp.core.domain.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu dòng chi tiết đơn mua hàng (purchase_order_item). */
@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    /** Lấy tất cả dòng chi tiết theo đơn mua hàng. */
    List<PurchaseOrderItem> findByPurchaseOrderId(UUID purchaseOrderId);

    /** Xóa tất cả dòng chi tiết theo đơn mua hàng. */
    void deleteByPurchaseOrderId(UUID purchaseOrderId);
}
