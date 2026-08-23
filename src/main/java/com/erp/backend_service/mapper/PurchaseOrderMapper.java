package com.erp.backend_service.mapper;

import com.erp.core.domain.PurchaseOrder;
import com.erp.core.dto.response.PurchaseOrderItemResponse;
import com.erp.core.dto.response.PurchaseOrderResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Chuyển đổi PurchaseOrder sang response (tên nhà cung cấp/kho và dòng chi tiết truyền từ service). */
@Component
public class PurchaseOrderMapper {

    /** Ánh xạ đơn mua hàng sang response, dùng tên và danh sách dòng đã giải quyết. */
    public PurchaseOrderResponse toResponse(PurchaseOrder po, String supplierName, String warehouseName,
                                            List<PurchaseOrderItemResponse> items) {
        return new PurchaseOrderResponse(
                po.getId().toString(),
                po.getPoCode(),
                po.getSupplierId() != null ? po.getSupplierId().toString() : null,
                supplierName,
                po.getWarehouseId() != null ? po.getWarehouseId().toString() : null,
                warehouseName,
                po.getStatus(),
                po.getOrderDate(),
                po.getExpectedDate(),
                po.getSubtotalAmount(),
                po.getTotalAmount(),
                po.getNote(),
                po.getSubmittedAt(),
                po.getApprovedBy() != null ? po.getApprovedBy().toString() : null,
                po.getApprovedAt(),
                po.getCancelledAt(),
                po.getCancelReason(),
                items,
                po.getCreatedBy(),
                po.getCreatedAt()
        );
    }
}
