package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateMaterialRequest;
import com.erp.core.dto.request.inv.UpdateMaterialRequest;
import com.erp.core.dto.response.Material.MaterialResponse;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

public interface MaterialService {

    PageResponse<MaterialResponse> list(int page, int size, String search, UUID categoryId, String status);

    MaterialResponse get(UUID id);

    MaterialResponse create(CreateMaterialRequest request);

    MaterialResponse update(UUID id, UpdateMaterialRequest request);

    MaterialResponse updateStatus(UUID id, String status);

    void delete(UUID id);
}
