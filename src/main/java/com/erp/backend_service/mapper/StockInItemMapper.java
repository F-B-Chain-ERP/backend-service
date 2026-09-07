package com.erp.backend_service.mapper;

import com.erp.core.domain.StockInItem;
import com.erp.core.dto.response.inv.StockInItemResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi StockInItem sang response (mã/tên nguyên liệu truyền từ service). */
@Component
public class StockInItemMapper {

    /** Ánh xạ dòng chi tiết phiếu nhập kho sang response, dùng mã/tên đã giải quyết. */
    public StockInItemResponse toResponse(StockInItem item, String materialCode, String materialName) {
        return new StockInItemResponse(
                item.getId() != null ? item.getId() : null,
                item.getStatus(),
                item.getStockInId() != null ? item.getStockInId() : null,
                item.getPurchaseOrderItemId() != null ? item.getPurchaseOrderItemId() : null,
                item.getMaterialId() != null ? item.getMaterialId() : null,
                materialCode,
                materialName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getBatchNo(),
                item.getExpiryDate()
        );
    }
}