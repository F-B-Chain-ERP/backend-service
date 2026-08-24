package com.erp.backend_service.mapper;

import com.erp.core.domain.PurchaseOrderItem;
import com.erp.core.dto.response.proc.PurchaseOrderItemResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi PurchaseOrderItem sang response (mã/tên nguyên liệu và tên đơn vị truyền từ service). */
@Component
public class PurchaseOrderItemMapper {

    /** Ánh xạ dòng chi tiết đơn mua hàng sang response, dùng mã/tên đã giải quyết. */
    public PurchaseOrderItemResponse toResponse(PurchaseOrderItem item, String materialCode, String materialName, String unitName) {
        return new PurchaseOrderItemResponse(
                item.getId() != null ? item.getId().toString() : null,
                item.getStatus(),
                item.getPurchaseOrderId() != null ? item.getPurchaseOrderId().toString() : null,
                item.getMaterialId() != null ? item.getMaterialId().toString() : null,
                materialCode,
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
