package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.pos.PosStockService;
import com.erp.core.dto.request.pos.RestockDailyStockRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.pos.DailyStockResponse;
import com.erp.core.domain.BranchVariantDailyStock;
import com.erp.core.enums.PrincipalType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Tồn bán trong ngày của POS. Chỉ nhân viên quản lý bán hàng, CUSTOMER không được gọi.
 * Dòng tồn ngày mới tự carryover ở lần check đầu tiên nên không cần job/cron.
 */
@RestController
@RequestMapping("/api/v1/pos/stocks")
public class PosStockController {

    private final PosStockService stockService;

    public PosStockController(PosStockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/restock")
    public ResponseEntity<ApiResponse<DailyStockResponse>> restock(
        @Valid @RequestBody RestockDailyStockRequest request) {
        if (SecurityUtils.getCurrentPrincipalType().orElse(null) == PrincipalType.CUSTOMER) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        if (!SecurityUtils.hasPermission("pos:order:update")) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        BranchVariantDailyStock saved = stockService.restock(request.branchId(), request.variantId(),
            request.openingQuantity(), request.note());
        return ResponseEntity.ok(ApiResponse.success(new DailyStockResponse(saved.getBranchId(),
            saved.getVariantId(), saved.getBusinessDate(), saved.getOpeningQuantity(),
            saved.getRemainingQuantity(), saved.getSoldQuantity()), "Chốt tồn mở bán thành công"));
    }
}
