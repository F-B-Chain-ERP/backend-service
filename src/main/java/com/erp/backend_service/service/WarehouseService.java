package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateWarehouseRequest;
import com.erp.core.dto.request.inv.UpdateWarehouseRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.WarehouseResponse;

import java.util.List;
import java.util.UUID;

public interface WarehouseService {

    PageResponse<WarehouseResponse> list(int page, int size, String search, UUID branchId, String warehouseType, String status);

    List<WarehouseResponse> listAll(String status);

    WarehouseResponse get(UUID id);

    WarehouseResponse create(CreateWarehouseRequest request);

    WarehouseResponse update(UUID id, UpdateWarehouseRequest request);

    WarehouseResponse updateStatus(UUID id, String status);

    void delete(UUID id);
}
