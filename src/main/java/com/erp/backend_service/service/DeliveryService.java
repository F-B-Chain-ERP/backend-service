package com.erp.backend_service.service;

import com.erp.core.dto.request.pos.AssignDeliveryRequest;
import com.erp.core.dto.request.pos.UpdateDeliveryStatusRequest;
import com.erp.core.dto.response.pos.DeliveryResponse;
import com.erp.core.dto.response.pos.DeliveryStatusResponse;

import java.util.List;
import java.util.UUID;

public interface DeliveryService {
    DeliveryResponse getByOrderId(UUID orderId);

    /** Tải giao hàng cho nhiều đơn trong 1 lần (màn Giao hàng), vẫn lọc branch-scope từng đơn. */
    List<DeliveryResponse> listByOrderIds(List<UUID> orderIds);

    DeliveryResponse assign(UUID orderId, AssignDeliveryRequest request);

    DeliveryStatusResponse updateStatus(UUID orderId, UpdateDeliveryStatusRequest request);
}
