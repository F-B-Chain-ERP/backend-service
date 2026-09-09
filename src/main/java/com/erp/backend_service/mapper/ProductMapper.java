package com.erp.backend_service.mapper;

import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductResponse;
import com.erp.core.dto.response.menu.ProductSalesResponse;
import com.erp.core.dto.response.menu.ProductVariantResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ánh xạ thủ công giữa {@link Product} entity và các DTO sản phẩm.
 */
@Component
public class ProductMapper {

    /**
     * Ánh xạ sang ProductResponse dùng cho Admin (kèm thông tin audit).
     */
    public ProductResponse toAdminResponse(Product p, String categoryName) {
        return new ProductResponse(
                p.getId() != null ? p.getId().toString() : null,
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getImageUrl(),
                p.getCategoryId() != null ? p.getCategoryId().toString() : null,
                categoryName,
                p.getBasePrice(),
                p.getPreparationMinutes(),
                p.isFeatured(),
                p.isBestSeller(),
                p.isCombo(),
                p.getAvailableIceLevels(),
                p.getAvailableSugarLevels(),
                p.getStatus(),
                p.getCreatedBy(),
                p.getCreatedAt(),
                p.getUpdatedBy(),
                p.getUpdatedAt()
        );
    }

    /**
     * Ánh xạ sang ProductSalesResponse dùng cho kênh bán hàng (public, thông tin món chi tiết).
     */
    public ProductSalesResponse toSalesResponse(Product p, String categoryName) {
        return new ProductSalesResponse(
                p.getId() != null ? p.getId().toString() : null,
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getImageUrl(),
                p.getCategoryId() != null ? p.getCategoryId().toString() : null,
                categoryName,
                p.getBasePrice(),
                p.getPreparationMinutes(),
                p.isFeatured(),
                p.isBestSeller(),
                p.isCombo(),
                p.getAvailableIceLevels(),
                p.getAvailableSugarLevels(),
                p.getStatus()
        );
    }

    /**
     * Ánh xạ từ ProductVariant entity sang ProductVariantResponse DTO.
     */
    public ProductVariantResponse toVariantResponse(ProductVariant v) {
        if (v == null) return null;
        return new ProductVariantResponse(
                v.getId() != null ? v.getId().toString() : null,
                v.getVariantCode(),
                v.getVariantName(),
                v.getSizeLabel(),
                v.getPriceDelta(),
                v.getDisplayOrder(),
                v.getStatus()
        );
    }

    /**
     * Ánh xạ sang ProductDetailResponse chi tiết đầy đủ kèm variants.
     */
    public ProductDetailResponse toDetailResponse(Product p, String categoryName, List<ProductVariantResponse> variants) {
        return new ProductDetailResponse(
                p.getId() != null ? p.getId().toString() : null,
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getImageUrl(),
                p.getCategoryId() != null ? p.getCategoryId().toString() : null,
                categoryName,
                p.getBasePrice(),
                p.getPreparationMinutes(),
                p.isFeatured(),
                p.isBestSeller(),
                p.isCombo(),
                p.getAvailableIceLevels(),
                p.getAvailableSugarLevels(),
                p.getStatus(),
                variants != null ? variants : List.of(),
                p.getCreatedBy(),
                p.getCreatedAt(),
                p.getUpdatedBy(),
                p.getUpdatedAt()
        );
    }
}
