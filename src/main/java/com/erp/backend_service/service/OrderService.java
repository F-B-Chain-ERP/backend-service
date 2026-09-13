package com.erp.backend_service.service;

import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse create(CreateOrderRequest request);

    PageResponse<OrderSummaryResponse> list(UUID branchId, String orderType, String status, LocalDate fromDate,
                                            LocalDate toDate, int page, int size);

    OrderResponse get(UUID id);

    OrderStatusResponse updateStatus(UUID id, UpdateOrderStatusRequest request);

    OrderResponse cancel(UUID id, CancelOrderRequest request);

    OrderResponse complete(UUID id, CompleteOrderRequest request);

    List<OrderHistoryResponse> history(UUID id);
}
