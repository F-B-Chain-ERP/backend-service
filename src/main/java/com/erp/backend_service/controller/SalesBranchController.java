package com.erp.backend_service.controller;

import com.erp.backend_service.repository.BranchRepository;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.branch.BranchSalesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Chi nhánh cho kênh bán hàng (khách chọn nơi nhận/giao).
 * Endpoint công khai không yêu cầu JWT (nằm trong /api/v1/sales/**).
 * Chỉ trả chi nhánh ACTIVE, không lộ cấu trúc nội bộ (parent, scope).
 */
@RestController
@RequestMapping("/api/v1/sales/branches")
public class SalesBranchController {

    private final BranchRepository branchRepository;
    private static final Logger log = LoggerFactory.getLogger(SalesBranchController.class);

    public SalesBranchController(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchSalesResponse>>> list() {
        log.info("Get list branches (sales)");
        List<BranchSalesResponse> branches = branchRepository.findByStatusOrderByCodeAsc("ACTIVE").stream()
            .map(b -> new BranchSalesResponse(b.getId().toString(), b.getCode(), b.getName(), b.getAddress(),
                b.getPhone(), b.isSupportsPickup(), b.isSupportsDelivery()))
            .toList();
        return ResponseEntity.ok(ApiResponse.success(branches, "Lấy danh sách chi nhánh thành công"));
    }
}
