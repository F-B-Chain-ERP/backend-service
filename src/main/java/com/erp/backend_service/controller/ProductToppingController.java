package com.erp.backend_service.controller;

import com.erp.backend_service.service.ProductToppingService;
import com.erp.core.dto.request.menu.AddProductToppingRequest;
import com.erp.core.dto.request.menu.UpdateProductToppingRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.menu.ProductToppingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý liên kết topping–sản phẩm: gán, cập nhật cấu hình (isDefault, maxQuantity), gỡ topping khỏi sản phẩm.
 */
@RestController
@Validated
public class ProductToppingController {

    private final ProductToppingService service;

    public ProductToppingController(ProductToppingService service) {
        this.service = service;
    }

    /** Danh sách topping đã gán cho sản phẩm. */
    @GetMapping("/api/v1/menu/products/{productId}/toppings")
    @PreAuthorize("hasAuthority('menu:product_topping:view')")
    public ResponseEntity<ApiResponse<List<ProductToppingResponse>>> listByProduct(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(service.listByProduct(productId)));
    }

    /** Gán topping vào sản phẩm (yêu cầu sản phẩm phải ACTIVE). */
    @PostMapping("/api/v1/menu/products/{productId}/toppings")
    @PreAuthorize("hasAuthority('menu:product_topping:create')")
    public ResponseEntity<ApiResponse<ProductToppingResponse>> add(
            @PathVariable UUID productId,
            @Valid @RequestBody AddProductToppingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.add(productId, request), "Thêm topping vào sản phẩm thành công"));
    }

    /** Cập nhật cấu hình topping trên sản phẩm (isDefault, maxQuantity). */
    @PutMapping("/api/v1/menu/product-toppings/{id}")
    @PreAuthorize("hasAuthority('menu:product_topping:update')")
    public ResponseEntity<ApiResponse<ProductToppingResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductToppingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    /** Gỡ topping khỏi sản phẩm (hard delete, hoạt động cả khi sản phẩm INACTIVE). */
    @DeleteMapping("/api/v1/menu/product-toppings/{id}")
    @PreAuthorize("hasAuthority('menu:product_topping:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
