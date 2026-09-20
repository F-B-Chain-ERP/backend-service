package com.erp.backend_service.controller;

import com.erp.backend_service.service.KdsService;
import com.erp.core.dto.request.pos.KdsTicketItemProgressRequest;
import com.erp.core.dto.request.pos.KdsTicketStatusRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.KdsTicketResponse;
import com.erp.core.dto.response.pos.KdsTicketSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos/kds")
public class KdsController {
    private final KdsService service;

    public KdsController(KdsService service) {
        this.service = service;
    }

    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<PageResponse<KdsTicketSummaryResponse>>> list(
        @RequestParam(required = false) UUID branchId, @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate fromDate, @RequestParam(required = false) LocalDate toDate,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            ApiResponse.success(service.list(branchId, status, fromDate, toDate, search, page, size),
                "Lấy danh sách phiếu bếp thành công"));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id), "Lấy chi tiết phiếu bếp thành công"));
    }

    @GetMapping("/tickets/by-order/{orderId}")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> getByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(
            ApiResponse.success(service.getByOrderId(orderId), "Lấy phiếu bếp theo đơn thành công"));
    }

    @PostMapping("/tickets/{id}/start")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> start(@PathVariable UUID id,
                                                                @Valid @RequestBody(required = false) KdsTicketStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.start(id), "Bếp bắt đầu pha chế"));
    }

    @PostMapping("/tickets/{id}/ready")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> ready(@PathVariable UUID id,
                                                                @Valid @RequestBody(required = false) KdsTicketStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.ready(id), "Bếp báo món sẵn sàng"));
    }

    @PostMapping("/tickets/{id}/serve")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> serve(@PathVariable UUID id,
                                                                @Valid @RequestBody(required = false) KdsTicketStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.serve(id), "Đã phục vụ"));
    }

    @PostMapping("/tickets/ensure/{orderId}")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> ensure(@PathVariable UUID orderId) {
        return ResponseEntity.ok(
            ApiResponse.success(service.createOnOrderConfirmed(orderId), "Đảm bảo phiếu bếp cho đơn thành công"));
    }

    @PostMapping("/items/{itemId}/progress")
    public ResponseEntity<ApiResponse<KdsTicketResponse>> progress(@PathVariable UUID itemId,
                                                                   @Valid @RequestBody KdsTicketItemProgressRequest request) {
        return ResponseEntity.ok(
            ApiResponse.success(service.progressItem(itemId, request), "Cập nhật tiến độ món thành công"));
    }
}
