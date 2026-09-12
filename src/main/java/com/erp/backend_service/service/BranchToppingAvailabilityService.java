package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.UpdateBranchToppingRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BranchToppingAvailabilityResponse;

import java.util.UUID;

public interface BranchToppingAvailabilityService {

    PageResponse<BranchToppingAvailabilityResponse> list(UUID branchId, int page, int size, String search, String status);

    BranchToppingAvailabilityResponse updateAvailability(UUID branchId, UUID toppingId, UpdateBranchToppingRequest request);
}
