package com.erp.backend_service.mapper;

import com.erp.core.domain.BranchProductAvailability;
import com.erp.core.domain.Product;
import com.erp.core.dto.response.menu.BranchProductAvailabilityResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BranchProductAvailabilityMapper {

    public BranchProductAvailabilityResponse toResponse(
            BranchProductAvailability bpa, Product product, String categoryName) {
        if (bpa == null) return null;
        return new BranchProductAvailabilityResponse(
                bpa.getId() != null ? bpa.getId().toString() : null,
                bpa.getBranchId() != null ? bpa.getBranchId().toString() : null,
                bpa.getProductId() != null ? bpa.getProductId().toString() : null,
                product != null ? product.getCode() : "",
                product != null ? product.getName() : "",
                product != null ? product.getBasePrice() : BigDecimal.ZERO,
                categoryName != null ? categoryName : "",
                bpa.getSalePrice(),
                bpa.isAvailable(),
                bpa.getStatus()
        );
    }

    public BranchProductAvailabilityResponse toResponseAvailableByDefault(
            Product product, String categoryName) {
        return new BranchProductAvailabilityResponse(
                null,
                null,
                product.getId().toString(),
                product.getCode(),
                product.getName(),
                product.getBasePrice(),
                categoryName != null ? categoryName : "",
                null,
                true,
                "ACTIVE"
        );
    }
}
