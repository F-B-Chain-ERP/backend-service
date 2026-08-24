package com.erp.backend_service.mapper;

import com.erp.core.domain.PurchaseOrder;
import com.erp.core.dto.response.proc.ApprovedByResponse;
import com.erp.core.dto.response.proc.PurchaseOrderItemResponse;
import com.erp.core.dto.response.proc.PurchaseOrderResponse;
import com.erp.core.dto.response.proc.PurchaseOrderSupplierResponse;
import com.erp.core.dto.response.proc.PurchaseOrderWarehouseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Chuyển đổi PurchaseOrder sang response (thông tin nhà cung cấp/kho/người duyệt truyền từ service). */
@Component
public class PurchaseOrderMapper {

    /** Ánh xạ đơn mua hàng sang response, dùng đối tượng supplier/warehouse/approvedBy và danh sách dòng đã giải quyết. */
    public PurchaseOrderResponse toResponse(PurchaseOrder po,
                                            PurchaseOrderSupplierResponse supplier,
                                            PurchaseOrderWarehouseResponse warehouse,
                                            ApprovedByResponse approvedBy,
                                            List<PurchaseOrderItemResponse> items) {
        return new PurchaseOrderResponse(
                po.getId() != null ? po.getId().toString() : null,
                po.getPoCode(),
                po.getStatus(),
                po.getOrderDate(),
                po.getExpectedDate(),
                supplier,
                warehouse,
                po.getSubtotalAmount(),
                po.getTotalAmount(),
                po.getNote(),
                po.getSubmittedAt(),
                approvedBy,
                po.getApprovedAt(),
                po.getCancelledAt(),
                po.getCancelReason(),
                items,
                po.getCreatedBy(),
                po.getCreatedAt()
        );
    }
}
