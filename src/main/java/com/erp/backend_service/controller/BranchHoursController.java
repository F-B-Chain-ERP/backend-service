package com.erp.backend_service.controller;

import com.erp.backend_service.service.BranchHoursService;
import com.erp.core.dto.request.branch.BatchUpdateBranchHoursRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.branch.BranchHoursResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý cấu hình lịch hoạt động tuần của chi nhánh.
 */
@RestController
@RequestMapping("/api/v1/branches/{branchId}/hours")
public class BranchHoursController {

    private final BranchHoursService branchHoursService;
    private static final Logger log = LoggerFactory.getLogger(BranchHoursController.class);

    public BranchHoursController(BranchHoursService branchHoursService) {
        this.branchHoursService = branchHoursService;
    }

    /**
     * Lấy lịch hoạt động tuần của chi nhánh (từ Thứ Hai đến Chủ Nhật).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:branch_hours:view')")
    public ResponseEntity<ApiResponse<List<BranchHoursResponse>>> getHours(@PathVariable UUID branchId) {
        log.info("Get branch hours: branchId={}", branchId);
        return ResponseEntity.ok(ApiResponse.success(branchHoursService.getHours(branchId)));
    }

    /**
     * Cập nhật hàng loạt (Batch Update) lịch hoạt động 7 ngày trong tuần của chi nhánh.
     */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:branch_hours:update')")
    public ResponseEntity<ApiResponse<List<BranchHoursResponse>>> updateHours(
            @PathVariable UUID branchId,
            @Valid @RequestBody BatchUpdateBranchHoursRequest request) {
        log.info("Update branch hours: branchId={}", branchId);
        return ResponseEntity.ok(ApiResponse.success(branchHoursService.updateHours(branchId, request)));
    }
}
