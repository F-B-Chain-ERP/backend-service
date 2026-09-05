package com.erp.backend_service.mapper;

import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateWarehouseRequest;
import com.erp.core.dto.request.inv.UpdateWarehouseRequest;
import com.erp.core.dto.response.inv.WarehouseResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class WarehouseMapper {

    public WarehouseResponse toResponse(Warehouse warehouse, String branchName) {
        return new WarehouseResponse(
                warehouse.getId() != null ? warehouse.getId().toString() : null,
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getWarehouseType(),
                warehouse.getBranchId() != null ? warehouse.getBranchId().toString() : null,
                branchName,
                warehouse.getAddress(),
                warehouse.getStatus(),
                warehouse.getCreatedBy(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedBy(),
                warehouse.getUpdatedAt()
        );
    }

    public WarehouseResponse toResponse(Warehouse warehouse, Map<UUID, String> branchNames) {
        String branchName = warehouse.getBranchId() != null ? branchNames.get(warehouse.getBranchId()) : null;
        return toResponse(warehouse, branchName);
    }

    public Warehouse toEntity(CreateWarehouseRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(request.code().trim().toUpperCase());
        warehouse.setName(request.name().trim());
        warehouse.setWarehouseType(request.warehouseType());
        warehouse.setBranchId(request.branchId());
        warehouse.setAddress(request.address() != null ? request.address().trim() : null);
        warehouse.setStatus(request.status() != null && !request.status().isBlank() ? request.status().trim().toUpperCase() : "ACTIVE");
        return warehouse;
    }

    public void updateEntity(Warehouse warehouse, UpdateWarehouseRequest request) {
        warehouse.setCode(request.code().trim().toUpperCase());
        warehouse.setName(request.name().trim());
        warehouse.setWarehouseType(request.warehouseType());
        warehouse.setBranchId(request.branchId());
        warehouse.setAddress(request.address() != null ? request.address().trim() : null);
        if (request.status() != null && !request.status().isBlank()) {
            warehouse.setStatus(request.status().trim().toUpperCase());
        }
    }
}
