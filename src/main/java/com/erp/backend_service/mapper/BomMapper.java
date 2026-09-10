package com.erp.backend_service.mapper;

import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.response.menu.BomItemResponse;
import com.erp.core.dto.response.menu.BomResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ánh xạ thủ công giữa {@link ProductRecipeItem} entity và các DTO BOM.
 */
@Component
public class BomMapper {

    /**
     * Ánh xạ một dòng BOM sang {@link BomItemResponse}.
     *
     * @param e            entity product_recipe_item
     * @param materialName tên nguyên vật liệu (đã resolve từ service)
     * @param unitCode     mã đơn vị tính (đã resolve từ service)
     */
    public BomItemResponse toItemResponse(ProductRecipeItem e, String materialName, String unitCode) {
        return new BomItemResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getMaterialId() != null ? e.getMaterialId().toString() : null,
                materialName,
                e.getQuantity(),
                e.getUnitId() != null ? e.getUnitId().toString() : null,
                unitCode,
                e.getWastagePercent()
        );
    }

    /**
     * Ánh xạ thông tin_variant và danh sách dòng BOM sang {@link BomResponse}.
     *
     * @param v     entity product_variant
     * @param items danh sách BomItemResponse đã map sẵn
     */
    public BomResponse toBomResponse(ProductVariant v, List<BomItemResponse> items) {
        return new BomResponse(
                v.getId() != null ? v.getId().toString() : null,
                v.getVariantName(),
                items != null ? items : List.of()
        );
    }
}
