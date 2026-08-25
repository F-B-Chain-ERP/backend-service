package com.erp.backend_service.mapper;

import com.erp.core.domain.Supplier;
import com.erp.core.dto.response.proc.SupplierResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi entity Supplier sang SupplierResponse. */
@Component
public class SupplierMapper {

    /** Ánh xạ thông tin nhà cung cấp sang response. */
    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId().toString(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getTaxCode(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getPaymentTermDays(),
                supplier.getStatus(),
                supplier.getCreatedBy(),
                supplier.getCreatedAt()
        );
    }
}
