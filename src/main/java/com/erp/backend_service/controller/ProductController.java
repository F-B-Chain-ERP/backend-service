package com.erp.backend_service.controller;

import com.erp.backend_service.service.ProductService;
import com.erp.backend_service.service.StorageService;
import com.erp.core.dto.request.menu.CreateProductRequest;
import com.erp.core.dto.request.menu.UpdateProductRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.CreateProductResponse;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Controller quản lý sản phẩm thực đơn phía Admin ERP.
 */
@RestController
@RequestMapping("/api/v1/menu/products")
@Validated
public class ProductController {

    private final ProductService productService;
    private final StorageService storageService;

    public ProductController(ProductService productService, StorageService storageService) {
        this.productService = productService;
        this.storageService = storageService;
    }

    /**
     * Lấy danh sách sản phẩm phân trang cho quản trị viên.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('menu:product:view')")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isFeatured,
            @RequestParam(required = false) Boolean isBestSeller
    ) {
        PageResponse<ProductResponse> response = productService.list(
                page, size, search, categoryId, status, isFeatured, isBestSeller
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy thông tin chi tiết một sản phẩm theo ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:product:view')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> get(@PathVariable UUID id) {
        ProductDetailResponse response = productService.get(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết sản phẩm thành công"));
    }

    /**
     * Tạo sản phẩm mới — nhận JSON request body (theo chuẩn Restful API).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('menu:product:create')")
    public ResponseEntity<ApiResponse<CreateProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request
    ) {
        CreateProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created, "Tạo sản phẩm thành công"));
    }

    /**
     * Tạo sản phẩm mới kèm upload file ảnh trực tiếp (multipart/form-data).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('menu:product:create')")
    public ResponseEntity<ApiResponse<CreateProductResponse>> createMultipart(
            @RequestParam @NotNull UUID categoryId,
            @RequestParam @NotBlank String code,
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam @NotNull @DecimalMin("0") BigDecimal basePrice,
            @RequestParam(required = false) Boolean isFeatured,
            @RequestParam(required = false) Boolean isBestSeller,
            @RequestParam(required = false) Boolean isCombo,
            @RequestParam(name = "image", required = false) MultipartFile image
    ) {
        String finalImageUrl = imageUrl;
        if (image != null && !image.isEmpty()) {
            finalImageUrl = storageService.upload(image, "products");
        }

        CreateProductRequest request = new CreateProductRequest(
                categoryId, code, name, description, finalImageUrl,
                basePrice, isFeatured, isBestSeller, isCombo
        );

        CreateProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created, "Tạo sản phẩm thành công"));
    }

    /**
     * Cập nhật sản phẩm — nhận JSON request body (chuẩn Restful API).
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('menu:product:update')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse updated = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật sản phẩm thành công"));
    }

    /**
     * Cập nhật sản phẩm kèm upload ảnh trực tiếp (multipart/form-data).
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('menu:product:update')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateMultipart(
            @PathVariable UUID id,
            @RequestParam @NotNull UUID categoryId,
            @RequestParam @NotBlank String code,
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam @NotNull @DecimalMin("0") BigDecimal basePrice,
            @RequestParam(required = false) Integer preparationMinutes,
            @RequestParam(required = false) Boolean isFeatured,
            @RequestParam(required = false) Boolean isBestSeller,
            @RequestParam(required = false) Boolean isCombo,
            @RequestParam(required = false) String availableIceLevels,
            @RequestParam(required = false) String availableSugarLevels,
            @RequestParam(required = false) String status,
            @RequestParam(name = "image", required = false) MultipartFile image
    ) {
        String finalImageUrl = imageUrl;
        if (image != null && !image.isEmpty()) {
            finalImageUrl = storageService.upload(image, "products");
        }

        UpdateProductRequest request = new UpdateProductRequest(
                categoryId, code, name, description, finalImageUrl,
                basePrice, preparationMinutes, isFeatured, isBestSeller, isCombo,
                availableIceLevels, availableSugarLevels, status
        );

        ProductResponse updated = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật sản phẩm thành công"));
    }

    /**
     * Xóa mềm sản phẩm (chuyển trạng thái sang DELETED).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:product:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa sản phẩm thành công"));
    }

    /**
     * Endpoint upload ảnh sản phẩm riêng biệt lên MinIO storage.
     */
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('menu:product:create')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("image") MultipartFile image
    ) {
        String imageUrl = storageService.upload(image, "products");
        return ResponseEntity.ok(ApiResponse.success(Map.of("imageUrl", imageUrl), "Upload ảnh thành công"));
    }
}
