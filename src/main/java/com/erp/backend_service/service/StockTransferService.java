package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateStockTransferRequest;
import com.erp.core.dto.request.inv.ReceiveStockTransferRequest;
import com.erp.core.dto.request.inv.UpdateStockTransferRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockTransferResponse;

import java.util.UUID;

public interface StockTransferService {

    PageResponse<StockTransferResponse> list(
            int page,
            int size,
            String search,
            String status,
            UUID warehouseId
    );

    StockTransferResponse get(UUID id);

    StockTransferResponse create(
            CreateStockTransferRequest request
    );

    StockTransferResponse update(
            UUID id,
            UpdateStockTransferRequest request
    );

    StockTransferResponse dispatch(
            UUID id
    );

    StockTransferResponse receive(
            UUID id,
            ReceiveStockTransferRequest request
    );

    StockTransferResponse cancel(
            UUID id
    );
}
