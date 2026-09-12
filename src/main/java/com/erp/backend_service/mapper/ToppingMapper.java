package com.erp.backend_service.mapper;

import com.erp.core.domain.Topping;
import com.erp.core.dto.response.menu.ToppingResponse;
import org.springframework.stereotype.Component;

@Component
public class ToppingMapper {

    public ToppingResponse toResponse(Topping t) {
        if (t == null) return null;
        return new ToppingResponse(
                t.getId() != null ? t.getId().toString() : null,
                t.getCode(),
                t.getName(),
                t.getPrice(),
                t.getImageUrl(),
                t.getGroupName(),
                t.getMaterialId() != null ? t.getMaterialId().toString() : null,
                t.getMaterialQuantity(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
