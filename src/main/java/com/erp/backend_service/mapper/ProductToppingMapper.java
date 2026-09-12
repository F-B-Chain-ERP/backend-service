package com.erp.backend_service.mapper;

import com.erp.core.domain.ProductTopping;
import com.erp.core.domain.Topping;
import com.erp.core.dto.response.menu.ProductToppingResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProductToppingMapper {

    public ProductToppingResponse toResponse(ProductTopping pt, Topping topping) {
        if (pt == null) return null;
        return new ProductToppingResponse(
                pt.getId() != null ? pt.getId().toString() : null,
                pt.getProductId() != null ? pt.getProductId().toString() : null,
                pt.getToppingId() != null ? pt.getToppingId().toString() : null,
                topping != null ? topping.getCode() : "",
                topping != null ? topping.getName() : "",
                topping != null ? topping.getPrice() : BigDecimal.ZERO,
                topping != null ? topping.getGroupName() : null,
                pt.isDefault(),
                pt.getMaxQuantity(),
                pt.getStatus()
        );
    }
}
