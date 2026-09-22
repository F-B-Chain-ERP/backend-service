package com.erp.backend_service.controller;

import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.CartService;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.pos.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos/cart")
public class CartController {
    private final CartService service;

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> get(@RequestParam UUID branchId,
                                                         @RequestParam(required = false) String sessionToken) {
        log.info("Get cart branch={}", branchId);
        return ResponseEntity.ok(
            ApiResponse.success(service.getCart(branchId, sessionToken), "Lấy giỏ hàng thành công"));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartMutationResponse>> add(@Valid @RequestBody AddCartItemRequest request) {
        log.info("Add cart item: branch={}, product={}", request.branchId(), request.productId());
        return ResponseEntity.status(201).body(
            ApiResponse.created(service.addItem(request), "Thêm sản phẩm vào giỏ hàng thành công"));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartMutationResponse>> update(@PathVariable UUID id,
                                                                    @Valid @RequestBody UpdateCartItemRequest request) {
        log.info("Update cart item id={}", id);
        return ResponseEntity.ok(ApiResponse.success(service.updateItem(id, request), "Cập nhật giỏ hàng thành công"));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartMutationResponse>> delete(@PathVariable UUID id) {
        log.info("Delete cart item id={}", id);
        return ResponseEntity.ok(ApiResponse.success(service.deleteItem(id), "Xóa sản phẩm khỏi giỏ hàng thành công"));
    }
}
