package com.erp.backend_service.security;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.ScopeType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tiện ích hỗ trợ kiểm soát phạm vi dữ liệu (Data Scoping & Isolation) theo Chi nhánh và Kho.
 * Cung cấp các phương thức kiểm tra ranh giới dữ liệu cho tầng Service và Repository.
 */
@Component
public class DataScopeHelper {

    private final WarehouseRepository warehouseRepository;

    public DataScopeHelper(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Lấy branchId đang làm việc trong phiên hiện tại từ SecurityContext (được trích xuất từ JWT token).
     */
    public Optional<UUID> getCurrentBranchId() {
        return SecurityUtils.getCurrentUserDetails().map(CustomUserDetails::getBranchId);
    }

    /**
     * Kiểm tra xem người dùng hiện tại có quyền Quản trị toàn hệ thống (Scope ALL_SYSTEM) hay không.
     */
    public boolean isAllSystem() {
        return SecurityUtils.getCurrentUserDetails()
                .map(u -> u.getScopes().stream().anyMatch(s -> s.scopeType() == ScopeType.ALL_SYSTEM))
                .orElse(false);
    }

    /**
     * Lấy danh sách tất cả branchId mà người dùng được phân quyền trong danh sách Scopes của họ.
     */
    public List<UUID> getAllowedBranchIds() {
        return SecurityUtils.getCurrentUserDetails()
                .map(u -> u.getScopes().stream()
                        .map(ScopeResponse::branchId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .orElse(Collections.emptyList());
    }

    /**
     * Xác định branchId hiệu lực dùng cho câu truy vấn tìm kiếm / danh sách:
     * - Nếu là ALL_SYSTEM: Trả về {@code requestedBranchId} (nếu null -> truy vấn toàn bộ).
     * - Nếu là nhân sự Chi nhánh: Bắt buộc trả về {@code currentBranchId} để chặn bypass tham số.
     */
    public UUID resolveEffectiveBranchId(UUID requestedBranchId) {
        if (isAllSystem()) {
            return requestedBranchId;
        }
        return getCurrentBranchId()
                .orElseThrow(() -> new BaseException(ErrorCode.CROSS_SCOPE_DENIED));
    }

    /**
     * Kiểm tra quyền truy cập trực tiếp vào một Chi nhánh.
     * Ném ngoại lệ {@link ErrorCode#CROSS_SCOPE_DENIED} nếu vi phạm.
     */
    public void enforceBranchAccess(UUID targetBranchId) {
        if (isAllSystem()) {
            return;
        }
        UUID current = getCurrentBranchId().orElse(null);
        if (current == null || targetBranchId == null || !current.equals(targetBranchId)) {
            throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
        }
    }

    /**
     * Kiểm tra và trả về thực thể {@link Warehouse} nếu kho đó thuộc chi nhánh mà người dùng có quyền.
     * Ném ngoại lệ {@link ErrorCode#CROSS_SCOPE_DENIED} nếu vi phạm.
     */
    public Warehouse enforceWarehouseAccess(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new BaseException(ErrorCode.PROC_404_WAREHOUSE_NOT_FOUND));
        if (isAllSystem()) {
            return warehouse;
        }
        UUID currentBranch = getCurrentBranchId().orElse(null);
        if (currentBranch == null || warehouse.getBranchId() == null || !warehouse.getBranchId().equals(currentBranch)) {
            throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
        }
        return warehouse;
    }

    /**
     * Xác định danh sách ID các kho mà người dùng được phép truy vấn dữ liệu (PO, Stock):
     * - Nếu ALL_SYSTEM: Trả về {@code requestedWarehouseId} (nếu có) hoặc null (không giới hạn kho).
     * - Nếu là nhân sự Chi nhánh: Trả về danh sách tất cả các kho thuộc chi nhánh hiện tại của họ.
     *   Nếu client truyền {@code requestedWarehouseId}, kiểm tra kho đó có thuộc chi nhánh không.
     */
    public Collection<UUID> getAllowedWarehouseIds(UUID requestedWarehouseId) {
        if (isAllSystem()) {
            return requestedWarehouseId != null ? List.of(requestedWarehouseId) : null;
        }
        UUID userBranchId = getCurrentBranchId()
                .orElseThrow(() -> new BaseException(ErrorCode.CROSS_SCOPE_DENIED));
        List<UUID> branchWarehouseIds = warehouseRepository.findByBranchId(userBranchId)
                .stream()
                .map(Warehouse::getId)
                .toList();

        if (requestedWarehouseId != null) {
            if (!branchWarehouseIds.contains(requestedWarehouseId)) {
                throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
            }
            return List.of(requestedWarehouseId);
        }
        return branchWarehouseIds;
    }
}
