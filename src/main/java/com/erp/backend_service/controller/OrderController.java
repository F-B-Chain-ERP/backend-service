package com.erp.backend_service.controller;

import com.erp.backend_service.service.OrderService;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(service.create(request), "Tạo đơn hàng thành công"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
        @RequestParam(required = false) UUID branchId, @RequestParam(required = false) String orderType,
        @RequestParam(required = false) String status, @RequestParam(required = false) LocalDate fromDate,
        @RequestParam(required = false) LocalDate toDate, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
            ApiResponse.success(service.list(branchId, orderType, status, fromDate, toDate, page, size),
                                "Lấy danh sách đơn hàng thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id), "Lấy chi tiết đơn hàng thành công"));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderStatusResponse>> status(@PathVariable UUID id,
                                                                   @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(
            ApiResponse.success(service.updateStatus(id, request), "Cập nhật trạng thái đơn hàng thành công"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(@PathVariable UUID id,
                                                             @Valid @RequestBody CancelOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.cancel(id, request), "Hủy đơn hàng thành công"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> complete(@PathVariable UUID id,
                                                               @Valid @RequestBody CompleteOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.complete(id, request), "Hoàn tất đơn hàng thành công"));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<OrderHistoryResponse>>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(
            ApiResponse.success(service.history(id), "Lấy nhật ký trạng thái đơn hàng thành công"));
    }
}
