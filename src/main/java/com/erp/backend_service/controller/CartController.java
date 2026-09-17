package com.erp.backend_service.controller;

import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.CartService;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.pos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> get(@RequestParam UUID branchId,
                                                         @RequestParam(required = false) String sessionToken) {
        return ResponseEntity.ok(
            ApiResponse.success(service.getCart(branchId, sessionToken), "Lấy giỏ hàng thành công"));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartMutationResponse>> add(@Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(201).body(
            ApiResponse.created(service.addItem(request), "Thêm sản phẩm vào giỏ hàng thành công"));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartMutationResponse>> update(@PathVariable UUID id,
                                                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateItem(id, request), "Cập nhật giỏ hàng thành công"));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartMutationResponse>> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.deleteItem(id), "Xóa sản phẩm khỏi giỏ hàng thành công"));
    }
}
