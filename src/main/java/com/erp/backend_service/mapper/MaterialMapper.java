package com.erp.backend_service.mapper;

import com.erp.core.domain.Material;
import com.erp.core.dto.response.Material.MaterialResponse;
import org.springframework.stereotype.Component;

@Component
public class MaterialMapper {

    public MaterialResponse toResponse(Material e) {
        return new MaterialResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCode(),
                e.getName(),
                e.getCategoryId() != null ? e.getCategoryId().toString() : null,
                e.getBaseUnitId() != null ? e.getBaseUnitId().toString() : null,
                e.getStatus());
    }
}
