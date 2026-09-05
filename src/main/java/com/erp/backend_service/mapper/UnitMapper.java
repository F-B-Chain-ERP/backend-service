package com.erp.backend_service.mapper;

import com.erp.core.domain.Unit;
import com.erp.core.dto.request.menu.CreateUnitRequest;
import com.erp.core.dto.request.menu.UpdateUnitRequest;
import com.erp.core.dto.response.menu.UnitResponse;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ thủ công giữa {@link Unit} và các DTO đơn vị tính.
 */
@Component
public class UnitMapper {

    /** Ánh xạ entity sang response. */
    public UnitResponse toResponse(Unit e) {
        return new UnitResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCode(),
                e.getName(),
                e.getUnitType(),
                e.getStatus(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }

    /** Tạo Unit mới từ CreateUnitRequest (mặc định ACTIVE). */
    public Unit toEntity(CreateUnitRequest request) {
        Unit u = new Unit();
        u.setCode(request.code());
        u.setName(request.name());
        u.setUnitType(request.unitType());
        u.setStatus("ACTIVE");
        return u;
    }

    /** Cập nhật Unit từ UpdateUnitRequest (không đổi status). */
    public void updateEntity(Unit u, UpdateUnitRequest request) {
        u.setCode(request.code());
        u.setName(request.name());
        u.setUnitType(request.unitType());
    }
}
