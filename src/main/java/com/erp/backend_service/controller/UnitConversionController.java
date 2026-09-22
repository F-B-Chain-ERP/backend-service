package com.erp.backend_service.controller;

import com.erp.backend_service.service.UnitConversionService;
import com.erp.core.dto.request.inv.CreateUnitConversionRequest;
import com.erp.core.dto.request.inv.UpdateUnitConversionRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.inv.UnitConversionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Quản lý bảng quy đổi đơn vị (1 chiều, chiều ngược tự đảo 1/factor). */
@RestController
@RequestMapping("/api/v1/inv/unit-conversions")
public class UnitConversionController {

    private final UnitConversionService service;

    public UnitConversionController(UnitConversionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:material:view')")
    public ResponseEntity<ApiResponse<List<UnitConversionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(), "Lấy bảng quy đổi đơn vị thành công"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:view')")
    public ResponseEntity<ApiResponse<UnitConversionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id), "Lấy dòng quy đổi thành công"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inv:material:create')")
    public ResponseEntity<ApiResponse<UnitConversionResponse>> create(
        @Valid @RequestBody CreateUnitConversionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(request), "Tạo dòng quy đổi thành công"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:update')")
    public ResponseEntity<ApiResponse<UnitConversionResponse>> update(
        @PathVariable UUID id, @Valid @RequestBody UpdateUnitConversionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request), "Cập nhật dòng quy đổi thành công"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa dòng quy đổi thành công"));
    }
}
