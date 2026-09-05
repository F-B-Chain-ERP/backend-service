package com.erp.backend_service.mapper;

import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateWarehouseRequest;
import com.erp.core.dto.request.inv.UpdateWarehouseRequest;
import com.erp.core.dto.response.warehouse.WarehouseResponse;
import com.erp.core.enums.EntityStatus;
import org.springframework.stereotype.Component;

@Component
public class WarehouseMapper {

    public WarehouseResponse toResponse(Warehouse e) {
        return new WarehouseResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCode(),
                e.getName(),
                e.getWarehouseType(),
                e.getBranchId() != null ? e.getBranchId().toString() : null,
                null,
                null,
                e.getAddress(),
                e.getStatus(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }

    public WarehouseResponse toDetailResponse(Warehouse e, String branchCode, String branchName) {
        return new WarehouseResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCode(),
                e.getName(),
                e.getWarehouseType(),
                e.getBranchId() != null ? e.getBranchId().toString() : null,
                branchCode,
                branchName,
                e.getAddress(),
                e.getStatus(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }

    public Warehouse toEntity(CreateWarehouseRequest request) {
        Warehouse w = new Warehouse();
        w.setCode(request.code());
        w.setName(request.name());
        w.setWarehouseType(request.warehouseType());
        w.setBranchId(request.branchId());
        w.setAddress(request.address());
        w.setStatus(EntityStatus.ACTIVE.name());
        return w;
    }

    public void updateEntity(Warehouse w, UpdateWarehouseRequest request) {
        w.setCode(request.code());
        w.setName(request.name());
        w.setWarehouseType(request.warehouseType());
        w.setBranchId(request.branchId());
        w.setAddress(request.address());
    }
}
