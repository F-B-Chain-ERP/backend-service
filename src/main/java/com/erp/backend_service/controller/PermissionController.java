package com.erp.backend_service.controller;

import com.erp.backend_service.service.PermissionService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.PermissionResponse;
import com.erp.core.enums.EntityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller danh mục quyền (Permission).
 * Theo nghiệp vụ: permission chỉ có quyền xem. Thêm/sửa/xóa được thực hiện
 * trực tiếp ở tầng DB (seed / set default), không expose qua API.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@Validated
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** Lấy thông tin một quyền theo id. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public ResponseEntity<ApiResponse<PermissionResponse>> getById(
            @NotNull(message = "Permission id must not be null")
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getById(id)));
    }

    /**
     * Danh sách quyền phân trang, hỗ trợ tìm kiếm (code/name/module) và lọc theo module/status.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public ResponseEntity<ApiResponse<PageResponse<PermissionResponse>>> getAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must not be negative") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) EntityStatus status
    ) {
        PageResponse<PermissionResponse> response =
                permissionService.getAll(page, size, search, module, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Danh sách các module hiện có trong danh mục quyền (phục vụ bộ lọc). */
    @GetMapping("/modules")
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public ResponseEntity<ApiResponse<List<String>>> getModules() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getModules()));
    }
}
