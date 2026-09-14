package com.erp.backend_service.mapper;

import com.erp.core.domain.Product;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.ProductBomOverviewResponse;
import com.erp.core.dto.response.menu.ProductRecipeItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mapper chuyển đổi giữa entity công thức định lượng (BOM) và các DTO tương ứng.
 */
@Component
public class BomMapper {

    /**
     * Ánh xạ từ ProductRecipeItem entity sang ProductRecipeItemResponse DTO.
     */
    public ProductRecipeItemResponse toItemResponse(
            ProductRecipeItem item,
            String materialName,
            String materialCode,
            String unitCode
    ) {
        if (item == null) return null;
        return new ProductRecipeItemResponse(
                item.getId() != null ? item.getId().toString() : null,
                item.getVariantId() != null ? item.getVariantId().toString() : null,
                item.getMaterialId() != null ? item.getMaterialId().toString() : null,
                materialName != null ? materialName : "",
                materialCode != null ? materialCode : "",
                item.getQuantity(),
                item.getUnitId() != null ? item.getUnitId().toString() : null,
                unitCode != null ? unitCode : "",
                item.getWastagePercent(),
                item.getStatus()
        );
    }

    /**
     * Ánh xạ sang BomResponse đầy đủ của biến thể.
     */
    public BomResponse toBomResponse(
            ProductVariant variant,
            String productName,
            List<ProductRecipeItemResponse> items
    ) {
        String displayName = productName != null && !productName.isBlank()
                ? productName + " - " + variant.getVariantName()
                : variant.getVariantName();

        return new BomResponse(
                variant.getId() != null ? variant.getId().toString() : null,
                displayName,
                items != null ? items : List.of()
        );
    }

    /**
     * Ánh xạ sang ProductBomOverviewResponse cho danh sách BOM tổng quan.
     */
    public ProductBomOverviewResponse toOverviewResponse(
            ProductVariant variant,
            Product product,
            String categoryName,
            int itemCount
    ) {
        BigDecimal basePrice = product.getBasePrice() != null ? product.getBasePrice() : BigDecimal.ZERO;
        BigDecimal delta = variant.getPriceDelta() != null ? variant.getPriceDelta() : BigDecimal.ZERO;
        BigDecimal sellingPrice = basePrice.add(delta);

        return new ProductBomOverviewResponse(
                variant.getId() != null ? variant.getId().toString() : null,
                variant.getVariantCode(),
                variant.getVariantName(),
                product.getId() != null ? product.getId().toString() : null,
                product.getCode(),
                product.getName(),
                categoryName != null ? categoryName : "",
                sellingPrice,
                itemCount,
                variant.getStatus(),
                variant.getUpdatedAt()
        );
    }
}
