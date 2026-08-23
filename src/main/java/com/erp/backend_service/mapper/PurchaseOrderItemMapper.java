package com.erp.backend_service.mapper;

import com.erp.core.domain.PurchaseOrderItem;
import com.erp.core.dto.response.PurchaseOrderItemResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi PurchaseOrderItem sang response (tên nguyên liệu/đơn vị truyền từ service). */
@Component
public class PurchaseOrderItemMapper {

    /** Ánh xạ dòng chi tiết đơn mua hàng sang response, dùng tên đã giải quyết. */
    public PurchaseOrderItemResponse toResponse(PurchaseOrderItem item, String materialName, String unitName) {
        return new PurchaseOrderItemResponse(
                item.getId().toString(),
                item.getStatus(),
                item.getPurchaseOrderId() != null ? item.getPurchaseOrderId().toString() : null,
                item.getMaterialId() != null ? item.getMaterialId().toString() : null,
                materialName,
                item.getQuantity(),
                item.getUnitId() != null ? item.getUnitId().toString() : null,
                unitName,
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getReceivedQuantity(),
                item.getCreatedBy(),
                item.getCreatedAt()
        );
    }
}
