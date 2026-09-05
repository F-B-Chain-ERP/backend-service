package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.CreateUnitRequest;
import com.erp.core.dto.request.menu.UpdateUnitRequest;
import com.erp.core.dto.response.menu.UnitResponse;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

public interface UnitService {

    PageResponse<UnitResponse> list(int page, int size, String search, String unitType, String status);

    UnitResponse get(UUID id);

    UnitResponse create(CreateUnitRequest request);

    UnitResponse update(UUID id, UpdateUnitRequest request);

    UnitResponse updateStatus(UUID id, String status);

    void delete(UUID id);
}
