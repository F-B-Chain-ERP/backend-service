package com.erp.backend_service.controller;

import com.erp.backend_service.service.RoleService;
import com.erp.core.dto.auth.RoleResponse;
import com.erp.core.dto.request.role.CreateRoleRequest;
import com.erp.core.dto.request.role.UpdateRoleRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.CacheRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/vi/role")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('sys:role:create')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.create(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/getId")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public ResponseEntity<ApiResponse<RoleResponse>> getId(@Valid @PathVariable UUID id) {
        RoleResponse response = roleService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/getAll")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAll(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam String search) {
        PageResponse<RoleResponse> response = roleService.getAll(page, size, search);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('sys:role:update')")
    public ResponseEntity<ApiResponse<RoleResponse>> update(@RequestParam UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = roleService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('sys:role:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
