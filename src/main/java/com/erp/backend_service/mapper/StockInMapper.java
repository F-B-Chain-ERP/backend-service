package com.erp.backend_service.mapper;

import com.erp.core.domain.StockIn;
import com.erp.core.dto.response.inv.StockInItemResponse;
import com.erp.core.dto.response.inv.StockInResponse;
import com.erp.core.dto.response.inv.StockInUserResponse;
import com.erp.core.dto.response.inv.StockInWarehouseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Chuyển đổi StockIn sang response (thông tin kho/người ghi nhận và danh sách dòng truyền từ service). */
@Component
public class StockInMapper {

    /** Ánh xạ phiếu nhập kho sang response, dùng warehouse/receivedBy và danh sách dòng đã giải quyết. */
    public StockInResponse toResponse(StockIn si,
                                      StockInWarehouseResponse warehouse,
                                      StockInUserResponse receivedBy,
                                      String purchaseOrderStatus,
                                      List<StockInItemResponse> items) {
        return new StockInResponse(
                si.getId() != null ? si.getId().toString() : null,
                si.getCode(),
                si.getWarehouseId() != null ? si.getWarehouseId().toString() : null,
                si.getStatus(),
                warehouse,
                si.getSourceType(),
                si.getSourceReferenceId(),
                purchaseOrderStatus,
                si.getInDate(),
                si.getNote(),
                receivedBy,
                si.getPostedAt(),
                items,
                si.getCreatedBy(),
                si.getCreatedAt()
        );
    }
}