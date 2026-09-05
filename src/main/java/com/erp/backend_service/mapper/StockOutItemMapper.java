package com.erp.backend_service.mapper;

import com.erp.core.domain.StockOutItem;
import com.erp.core.dto.response.inv.StockOutItemResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi StockOutItem sang response (mã/tên nguyên liệu truyền từ service). */
@Component
public class StockOutItemMapper {

    /** Ánh xạ dòng chi tiết phiếu xuất kho sang response, dùng mã/tên đã giải quyết. */
    public StockOutItemResponse toResponse(StockOutItem item, String materialCode, String materialName) {
        return new StockOutItemResponse(
                item.getId() != null ? item.getId() : null,
                item.getStatus(),
                item.getStockOutId() != null ? item.getStockOutId() : null,
                item.getMaterialId() != null ? item.getMaterialId() : null,
                materialCode,
                materialName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getBatchNo()
        );
    }
}