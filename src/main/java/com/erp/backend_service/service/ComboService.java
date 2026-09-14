package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.AddComboItemRequest;
import com.erp.core.dto.request.menu.BulkSyncComboItemsRequest;
import com.erp.core.dto.request.menu.CalculateComboPriceRequest;
import com.erp.core.dto.response.menu.ComboDetailResponse;
import com.erp.core.dto.response.menu.CalculateComboPriceResponse;

import java.util.UUID;

public interface ComboService {

    ComboDetailResponse getDetail(UUID comboId);

    ComboDetailResponse addItem(UUID comboId, AddComboItemRequest request);

    ComboDetailResponse removeItem(UUID comboId, UUID itemId);

    ComboDetailResponse syncItems(UUID comboId, BulkSyncComboItemsRequest request);

    CalculateComboPriceResponse calculatePrice(CalculateComboPriceRequest request);
}
