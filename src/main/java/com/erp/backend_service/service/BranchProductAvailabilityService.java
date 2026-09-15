package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.UpdateBranchProductRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BranchProductAvailabilityResponse;

import java.util.UUID;

public interface BranchProductAvailabilityService {

    PageResponse<BranchProductAvailabilityResponse> list(
            UUID branchId, int page, int size, String search, String status, UUID categoryId);

    BranchProductAvailabilityResponse updateAvailability(
            UUID branchId, UUID productId, UpdateBranchProductRequest request);
}
