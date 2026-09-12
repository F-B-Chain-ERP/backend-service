package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.BulkSyncComboItemsRequest;
import com.erp.core.dto.response.menu.ComboDetailResponse;

import java.util.UUID;

public interface ComboService {

    ComboDetailResponse getDetail(UUID comboId);

    ComboDetailResponse syncItems(UUID comboId, BulkSyncComboItemsRequest request);
}
