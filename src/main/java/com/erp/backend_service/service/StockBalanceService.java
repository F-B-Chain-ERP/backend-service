package com.erp.backend_service.service;

import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockBalanceResponse;

import java.util.UUID;

public interface StockBalanceService {

    PageResponse<StockBalanceResponse> list(
            int page,
            int size,
            UUID warehouseId,
            UUID materialId,
            String search
    );

    StockBalanceResponse get(
            UUID warehouseId,
            UUID materialId
    );
}