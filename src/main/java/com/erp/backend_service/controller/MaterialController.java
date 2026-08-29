package com.erp.backend_service.controller;

import com.erp.backend_service.service.MaterialService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.Material.MaterialResponse;
import com.erp.core.dto.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inv/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:material:view')")
    public ResponseEntity<ApiResponse<PageResponse<MaterialResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(materialService.list(page, size, search)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:view')")
    public ResponseEntity<ApiResponse<MaterialResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(materialService.get(id)));
    }
}
