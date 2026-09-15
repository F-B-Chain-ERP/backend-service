package com.erp.backend_service.controller;

import com.erp.backend_service.service.DeliveryService;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.pos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos/deliveries")
public class DeliveryController {
    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<DeliveryResponse>> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok(
            ApiResponse.success(service.getByOrderId(orderId), "Lấy thông tin giao hàng thành công"));
    }

    @PutMapping("/{orderId}/assign")
    public ResponseEntity<ApiResponse<DeliveryResponse>> assign(@PathVariable UUID orderId,
                                                                @Valid @RequestBody AssignDeliveryRequest request) {
        return ResponseEntity.ok(
            ApiResponse.success(service.assign(orderId, request), "Phân công giao hàng thành công"));
    }

    @PostMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<DeliveryStatusResponse>> status(@PathVariable UUID orderId,
                                                                      @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return ResponseEntity.ok(
            ApiResponse.success(service.updateStatus(orderId, request), "Cập nhật trạng thái giao hàng thành công"));
    }
}
