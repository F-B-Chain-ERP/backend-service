package com.erp.backend_service.mapper;

import com.erp.core.domain.StockOut;
import com.erp.core.dto.response.inv.StockOutItemResponse;
import com.erp.core.dto.response.inv.StockOutResponse;
import com.erp.core.dto.response.inv.StockOutUserResponse;
import com.erp.core.dto.response.inv.StockOutWarehouseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Chuyển đổi StockOut sang response (thông tin kho/người xuất và danh sách dòng truyền từ service). */
@Component
public class StockOutMapper {

    /** Ánh xạ phiếu xuất kho sang response, dùng warehouse/issuedBy và danh sách dòng đã giải quyết. */
    public StockOutResponse toResponse(StockOut so,
                                       StockOutWarehouseResponse warehouse,
                                       StockOutUserResponse issuedBy,
                                       List<StockOutItemResponse> items) {
        return new StockOutResponse(
                so.getId() != null ? so.getId().toString() : null,
                so.getCode(),
                so.getWarehouseId() != null ? so.getWarehouseId().toString() : null,
                so.getStatus(),
                warehouse,
                so.getDestinationType(),
                so.getDestinationReferenceId(),
                so.getOutDate(),
                so.getNote(),
                issuedBy,
                so.getPostedAt(),
                items,
                so.getCreatedBy(),
                so.getCreatedAt()
        );
    }
}