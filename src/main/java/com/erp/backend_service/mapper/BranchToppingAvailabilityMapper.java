package com.erp.backend_service.mapper;

import com.erp.core.domain.BranchToppingAvailability;
import com.erp.core.domain.Topping;
import com.erp.core.dto.response.menu.BranchToppingAvailabilityResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BranchToppingAvailabilityMapper {

    public BranchToppingAvailabilityResponse toResponse(BranchToppingAvailability bta, Topping topping) {
        if (bta == null) return null;
        return new BranchToppingAvailabilityResponse(
                bta.getId() != null ? bta.getId().toString() : null,
                bta.getBranchId() != null ? bta.getBranchId().toString() : null,
                bta.getToppingId() != null ? bta.getToppingId().toString() : null,
                topping != null ? topping.getCode() : "",
                topping != null ? topping.getName() : "",
                topping != null ? topping.getPrice() : BigDecimal.ZERO,
                topping != null ? topping.getGroupName() : null,
                bta.isAvailable(),
                bta.getStatus()
        );
    }
}
