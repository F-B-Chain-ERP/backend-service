package com.erp.backend_service.mapper;

import com.erp.core.domain.SupplierMaterial;
import com.erp.core.dto.response.SupplierMaterialResponse;
import org.springframework.stereotype.Component;


@Component
public class SupplierMaterialMapper {

    public SupplierMaterialResponse toResponse(SupplierMaterial e, String supplierName, String materialName) {
        return new SupplierMaterialResponse(
                e.getId().toString(),
                e.getSupplierId().toString(),
                supplierName,
                e.getMaterialId().toString(),
                materialName,
                e.getSupplierSku(),
                e.getPurchasePrice(),
                e.getLeadTimeDays(),
                e.isPreferred(),
                e.getStatus(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }
}
