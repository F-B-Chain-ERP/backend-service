package com.erp.backend_service.service;

import com.erp.core.dto.request.pos.AssignDeliveryRequest;
import com.erp.core.dto.request.pos.UpdateDeliveryStatusRequest;
import com.erp.core.dto.response.pos.DeliveryResponse;
import com.erp.core.dto.response.pos.DeliveryStatusResponse;

import java.util.UUID;

public interface DeliveryService {
    DeliveryResponse getByOrderId(UUID orderId);

    DeliveryResponse assign(UUID orderId, AssignDeliveryRequest request);

    DeliveryStatusResponse updateStatus(UUID orderId, UpdateDeliveryStatusRequest request);
}
