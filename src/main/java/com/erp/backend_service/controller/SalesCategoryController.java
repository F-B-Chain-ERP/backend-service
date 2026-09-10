package com.erp.backend_service.controller;

import com.erp.backend_service.service.CategoryService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.CategoryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cung cấp danh mục sản phẩm cho kênh bán hàng (Storefront / POS / App đặt món).
 * Endpoint công khai thuộc /api/v1/sales/** không yêu cầu đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/sales/categories")
public class SalesCategoryController {

    private final CategoryService categoryService;

    public SalesCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Lấy danh sách các danh mục sản phẩm đang hoạt động (ACTIVE) sắp xếp theo thứ tự hiển thị.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryType
    ) {
        PageResponse<CategoryResponse> response = categoryService.list(
                page,
                size,
                search,
                categoryType != null && !categoryType.isBlank() ? categoryType : "PRODUCT",
                "ACTIVE"
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách danh mục cho kênh bán hàng thành công"));
    }
}
