package com.erp.backend_service.service;

import com.erp.core.dto.response.Material.MaterialResponse;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

public interface MaterialService {

    PageResponse<MaterialResponse> list(int page, int size, String search);

    MaterialResponse get(UUID id);
}
