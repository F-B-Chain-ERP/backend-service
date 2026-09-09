package com.erp.backend_service.controller;

import com.erp.backend_service.service.ProductService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductSalesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller cung cấp danh sách sản phẩm cho kênh bán hàng (Khách xem menu / POS / App bán hàng).
 * Endpoint công khai không yêu cầu token JWT.
 */
@RestController
@RequestMapping("/api/v1/sales/products")
public class SalesProductController {

    private final ProductService productService;

    public SalesProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Lấy danh sách sản phẩm đang bán (ACTIVE) cho kênh bán hàng có phân trang.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSalesResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean isFeatured
    ) {
        PageResponse<ProductSalesResponse> response = productService.listForSales(
                page,
                size,
                search,
                categoryId,
                isFeatured
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy thông tin chi tiết một sản phẩm đang bán kèm danh sách kích cỡ/variants cho kênh bán hàng.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> get(@PathVariable UUID id) {
        ProductDetailResponse response = productService.getDetailForSales(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết sản phẩm thành công"));
    }
}
