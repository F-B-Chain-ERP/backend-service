package com.erp.backend_service.service;

import com.erp.core.dto.request.proc.SupplierMaterial.CreateSupplierMaterialRequest;
import com.erp.core.dto.request.proc.SupplierMaterial.UpdateSupplierMaterialRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.SupplierMaterial.SupplierMaterialResponse;

import java.util.UUID;

public interface SupplierMaterialService {

    PageResponse<SupplierMaterialResponse> list(int page, int size, UUID supplierId, UUID materialId, String search);

    SupplierMaterialResponse get(UUID id);

    SupplierMaterialResponse create(CreateSupplierMaterialRequest request);

    SupplierMaterialResponse update(UUID id, UpdateSupplierMaterialRequest request);

    void delete(UUID id);
}
