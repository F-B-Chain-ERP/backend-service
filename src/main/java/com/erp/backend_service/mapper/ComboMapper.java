package com.erp.backend_service.mapper;

import com.erp.core.domain.ComboItem;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.response.menu.ComboDetailResponse;
import com.erp.core.dto.response.menu.ComboItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ComboMapper {

    public ComboDetailResponse toComboDetailResponse(Product combo, List<ComboItemResponse> items) {
        if (combo == null) return null;
        BigDecimal calculatedPrice = items != null
                ? items.stream()
                    .map(ComboItemResponse::lineTotal)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;
        return new ComboDetailResponse(
                combo.getId() != null ? combo.getId().toString() : null,
                combo.getCode(),
                combo.getName(),
                combo.getBasePrice(),
                calculatedPrice,
                combo.isCombo(),
                combo.getStatus(),
                items != null ? items : List.of()
        );
    }

    public ComboItemResponse toComboItemResponse(
            ComboItem ci, ProductVariant variant, Product variantProduct) {
        if (ci == null) return null;
        BigDecimal variantPrice = null;
        if (variantProduct != null && variant != null) {
            variantPrice = variantProduct.getBasePrice().add(variant.getPriceDelta());
        }
        BigDecimal lineTotal = variantPrice != null
                ? variantPrice.multiply(BigDecimal.valueOf(ci.getQuantity()))
                : null;
        return new ComboItemResponse(
                ci.getId() != null ? ci.getId().toString() : null,
                variant != null ? variant.getId().toString() : null,
                variant != null ? variant.getVariantCode() : "",
                variant != null ? variant.getVariantName() : "",
                variant != null ? variant.getSizeLabel() : "",
                variantProduct != null ? variantProduct.getId().toString() : null,
                variantProduct != null ? variantProduct.getCode() : "",
                variantProduct != null ? variantProduct.getName() : "",
                variantPrice,
                ci.getQuantity(),
                ci.isSubstitutable(),
                ci.getStatus(),
                lineTotal
        );
    }
}
