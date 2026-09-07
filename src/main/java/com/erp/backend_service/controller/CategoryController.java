package com.erp.backend_service.controller;

import com.erp.backend_service.service.CategoryService;
import com.erp.core.dto.request.menu.CreateCategoryRequest;
import com.erp.core.dto.request.menu.UpdateCategoryRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.CategoryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Quản lý danh mục (master dùng chung INV + MENU).
 */
@RestController
@RequestMapping("/api/v1/menu/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menu:category:view')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.list(page, size, search, categoryType, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:category:view')")
    public ResponseEntity<ApiResponse<CategoryResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menu:category:create')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.created(null).body(ApiResponse.created(categoryService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:category:update')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('menu:category:update')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateStatus(id, request.get("status"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:category:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
