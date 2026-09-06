package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateStockCountRequest;
import com.erp.core.dto.request.inv.UpdateStockCountRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockCountResponse;

import java.util.UUID;

public interface StockCountService {

    PageResponse<StockCountResponse> list(
            int page,
            int size,
            String search,
            String status,
            UUID warehouseId
    );

    StockCountResponse get(UUID id);

    StockCountResponse create(
            CreateStockCountRequest request
    );

    StockCountResponse update(
            UUID id,
            UpdateStockCountRequest request
    );

    StockCountResponse start(UUID id);

    void delete(UUID id);

    StockCountResponse complete(
            UUID id
    );

    StockCountResponse adjust(
            UUID id
    );
}
