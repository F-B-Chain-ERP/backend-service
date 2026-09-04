package com.erp.backend_service.mapper;

import com.erp.core.domain.Material;
import com.erp.core.dto.response.Material.MaterialResponse;
import org.springframework.stereotype.Component;

@Component
public class MaterialMapper {

    /** Ánh xạ cho danh sách (list) - không join tên category/unit. */
    public MaterialResponse toResponse(Material e) {
        return new MaterialResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCode(),
                e.getName(),
                e.getCategoryId() != null ? e.getCategoryId().toString() : null,
                null,
                e.getBaseUnitId() != null ? e.getBaseUnitId().toString() : null,
                null,
                e.getMinStockAlert(),
                e.getShelfLifeDays(),
                e.isPerishable(),
                e.getStatus(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }

    /** Ánh xạ cho chi tiết (detail) - có tên category/unit. */
    public MaterialResponse toDetailResponse(Material e, String categoryName, String unitName) {
        return new MaterialResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCode(),
                e.getName(),
                e.getCategoryId() != null ? e.getCategoryId().toString() : null,
                categoryName,
                e.getBaseUnitId() != null ? e.getBaseUnitId().toString() : null,
                unitName,
                e.getMinStockAlert(),
                e.getShelfLifeDays(),
                e.isPerishable(),
                e.getStatus(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }
}
