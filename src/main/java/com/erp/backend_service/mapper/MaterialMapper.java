package com.erp.backend_service.mapper;

import com.erp.core.domain.Material;
import com.erp.core.dto.request.inv.CreateMaterialRequest;
import com.erp.core.dto.request.inv.UpdateMaterialRequest;
import com.erp.core.dto.response.Material.MaterialResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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

    /** Tạo Material mới từ CreateMaterialRequest. */
    public Material toEntity(CreateMaterialRequest request) {
        Material m = new Material();
        m.setCode(request.code());
        m.setName(request.name());
        m.setCategoryId(request.categoryId());
        m.setBaseUnitId(request.baseUnitId());
        m.setMinStockAlert(request.minStockAlert() != null ? request.minStockAlert() : BigDecimal.ZERO);
        m.setShelfLifeDays(request.shelfLifeDays());
        m.setPerishable(request.isPerishable() != null && request.isPerishable());
        m.setStatus("ACTIVE");
        return m;
    }

    /** Cập nhật Material từ UpdateMaterialRequest (không đổi status). */
    public void updateEntity(Material m, UpdateMaterialRequest request) {
        m.setCode(request.code());
        m.setName(request.name());
        m.setCategoryId(request.categoryId());
        m.setBaseUnitId(request.baseUnitId());
        m.setMinStockAlert(request.minStockAlert() != null ? request.minStockAlert() : BigDecimal.ZERO);
        m.setShelfLifeDays(request.shelfLifeDays());
        m.setPerishable(request.isPerishable() != null && request.isPerishable());
    }
}
