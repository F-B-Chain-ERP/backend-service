package com.erp.backend_service.controller;

import com.erp.backend_service.service.ProductVariantService;
import com.erp.core.dto.request.menu.CreateProductVariantRequest;
import com.erp.core.dto.request.menu.SyncProductVariantsRequest;
import com.erp.core.dto.request.menu.UpdateProductVariantRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.menu.ProductVariantResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý các biến thể / kích cỡ sản phẩm (Size S, M, L...).
 */
@RestController
@RequestMapping("/api/v1/menu")
@Validated
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    public ProductVariantController(ProductVariantService productVariantService) {
        this.productVariantService = productVariantService;
    }

    /**
     * Lấy danh sách toàn bộ biến thể của một sản phẩm.
     */
    @GetMapping("/products/{productId}/variants")
    @PreAuthorize("hasAuthority('menu:variant:view')")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getVariants(
            @PathVariable UUID productId
    ) {
        List<ProductVariantResponse> response = productVariantService.getVariantsByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách biến thể thành công"));
    }

    /**
     * Tạo mới một biến thể / kích cỡ cho sản phẩm.
     */
    @PostMapping("/products/{productId}/variants")
    @PreAuthorize("hasAuthority('menu:variant:create')")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> createVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateProductVariantRequest request
    ) {
        ProductVariantResponse created = productVariantService.create(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Tạo biến thể sản phẩm thành công"));
    }

    /**
     * Cập nhật thông tin một biến thể.
     */
    @PutMapping("/products/{productId}/variants/{id}")
    @PreAuthorize("hasAuthority('menu:variant:update')")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductVariantRequest request
    ) {
        ProductVariantResponse updated = productVariantService.update(productId, id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật biến thể sản phẩm thành công"));
    }

    /**
     * Xóa một biến thể khỏi sản phẩm.
     */
    @DeleteMapping("/products/{productId}/variants/{id}")
    @PreAuthorize("hasAuthority('menu:variant:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable UUID productId,
            @PathVariable UUID id
    ) {
        productVariantService.delete(productId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa biến thể sản phẩm thành công"));
    }

    /**
     * Đồng bộ toàn bộ danh sách biến thể của sản phẩm (thêm mới, cập nhật, xóa bỏ).
     */
    @PutMapping("/products/{productId}/variants/sync")
    @PreAuthorize("hasAuthority('menu:variant:update')")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> syncVariants(
            @PathVariable UUID productId,
            @Valid @RequestBody SyncProductVariantsRequest request
    ) {
        List<ProductVariantResponse> synced = productVariantService.syncVariants(productId, request);
        return ResponseEntity.ok(ApiResponse.success(synced, "Đồng bộ biến thể sản phẩm thành công"));
    }
}
