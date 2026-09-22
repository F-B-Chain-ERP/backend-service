package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.WarehouseMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.StockCountRepository;
import com.erp.backend_service.repository.StockInRepository;
import com.erp.backend_service.repository.StockOutRepository;
import com.erp.backend_service.repository.StockTransferRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.WarehouseService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateWarehouseRequest;
import com.erp.core.dto.request.inv.UpdateWarehouseRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.WarehouseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(WarehouseServiceImpl.class);

    private static final Set<String> ALLOWED_WAREHOUSE_TYPES = Set.of("CENTRAL", "BRANCH");
    private static final Set<String> ALLOWED_WAREHOUSE_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final WarehouseRepository warehouseRepository;
    private final BranchRepository branchRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockInRepository stockInRepository;
    private final StockOutRepository stockOutRepository;
    private final StockTransferRepository stockTransferRepository;
    private final StockCountRepository stockCountRepository;
    private final MaterialStockBalanceRepository balanceRepository;
    private final WarehouseMapper warehouseMapper;
    private final DataScopeHelper dataScopeHelper;

    public WarehouseServiceImpl(WarehouseRepository warehouseRepository,
                                BranchRepository branchRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                StockInRepository stockInRepository,
                                StockOutRepository stockOutRepository,
                                StockTransferRepository stockTransferRepository,
                                StockCountRepository stockCountRepository,
                                MaterialStockBalanceRepository balanceRepository,
                                WarehouseMapper warehouseMapper,
                                DataScopeHelper dataScopeHelper) {
        this.warehouseRepository = warehouseRepository;
        this.branchRepository = branchRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stockInRepository = stockInRepository;
        this.stockOutRepository = stockOutRepository;
        this.stockTransferRepository = stockTransferRepository;
        this.stockCountRepository = stockCountRepository;
        this.balanceRepository = balanceRepository;
        this.warehouseMapper = warehouseMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(int page, int size, String search, UUID branchId, String warehouseType, String status) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        log.info("Get list warehouses: keyword={}, page={}, size={}", search, page, size);
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        String normalizedWarehouseType = StringUtils.hasText(warehouseType) ? warehouseType.trim().toUpperCase() : null;
        validateWarehouseType(normalizedWarehouseType);
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        validateWarehouseStatus(normalizedStatus);

        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<Warehouse> pageResult = warehouseRepository.search(
                StringUtils.hasText(search) ? search.trim() : null,
                effectiveBranchId,
                normalizedWarehouseType,
                normalizedStatus,
                pageable
        );

        Map<UUID, String> branchNames = resolveBranchNames(pageResult.getContent());

        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getContent().stream().map(w -> warehouseMapper.toResponse(w, branchNames)).toList()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listAll(String status) {
        log.info("Get all warehouses: status={}", status);
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        validateWarehouseStatus(normalizedStatus);
        // Dropdown dùng ở mọi form kho: user chi nhánh chỉ thấy kho CN đang làm
        // (+ kho CENTRAL), khớp với enforce ở đường ghi để khỏi "thấy mà không làm được".
        // ALL_SYSTEM giữ nguyên toàn bộ. Chưa chọn CN thì chặn như list() phân trang.
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(null);
        List<Warehouse> list = effectiveBranchId == null
                ? (normalizedStatus != null
                        ? warehouseRepository.findByStatus(normalizedStatus)
                        : warehouseRepository.findAll(Sort.by("name").ascending()))
                : warehouseRepository.findVisibleForBranch(effectiveBranchId, normalizedStatus);

        Map<UUID, String> branchNames = resolveBranchNames(list);
        return list.stream().map(w -> warehouseMapper.toResponse(w, branchNames)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        log.info("Get warehouse {}", id);
        Warehouse warehouse = findById(id);
        dataScopeHelper.enforceBranchAccess(warehouse.getBranchId());
        String branchName = resolveSingleBranchName(warehouse.getBranchId());
        return warehouseMapper.toResponse(warehouse, branchName);
    }

    @Override
    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        String normalizedCode = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCode(normalizedCode)) {
            throw new BaseException(ErrorCode.INV_409_WAREHOUSE_CODE_EXISTED);
        }

        String normalizedType = request.warehouseType().trim().toUpperCase();
        validateWarehouseType(normalizedType);
        validateRequestStatus(request.status());

        validateBranchBinding(request.warehouseType(), request.branchId());

        Warehouse warehouse = warehouseMapper.toEntity(request);
        if ("CENTRAL".equals(request.warehouseType().trim().toUpperCase())) {
            warehouse.setBranchId(null);
        }
        log.info("Create warehouse: code={}, warehouseType={}", normalizedCode, normalizedType);
        Warehouse saved = warehouseRepository.save(warehouse);
        String branchName = resolveSingleBranchName(saved.getBranchId());
        return warehouseMapper.toResponse(saved, branchName);
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        log.info("Update warehouse id={}", id);
        Warehouse warehouse = findById(id);
        dataScopeHelper.enforceBranchAccess(warehouse.getBranchId());

        String normalizedType = request.warehouseType().trim().toUpperCase();
        validateWarehouseType(normalizedType);
        validateRequestStatus(request.status());

        validateBranchBinding(request.warehouseType(), request.branchId());

        String normalizedCode = request.code().trim().toUpperCase();

        if (warehouseRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new BaseException(ErrorCode.INV_409_WAREHOUSE_CODE_EXISTED);
        }

        warehouseMapper.updateEntity(warehouse, request);
        if ("CENTRAL".equals(request.warehouseType().trim().toUpperCase())) {
            warehouse.setBranchId(null);
        }
        Warehouse saved = warehouseRepository.save(warehouse);
        String branchName = resolveSingleBranchName(saved.getBranchId());
        return warehouseMapper.toResponse(saved, branchName);
    }

    @Override
    @Transactional
    public WarehouseResponse updateStatus(UUID id, String status) {
        log.info("Update warehouse status id={}, status={}", id, status);
        Warehouse warehouse = findById(id);
        dataScopeHelper.enforceBranchAccess(warehouse.getBranchId());

        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_INVALID_STATUS);
        }

        warehouse.setStatus(normalizedStatus);
        Warehouse saved = warehouseRepository.save(warehouse);
        String branchName = resolveSingleBranchName(saved.getBranchId());
        return warehouseMapper.toResponse(saved, branchName);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Delete warehouse id={}", id);
        Warehouse warehouse = findById(id);
        dataScopeHelper.enforceBranchAccess(warehouse.getBranchId());

        if (purchaseOrderRepository.existsByWarehouseId(id)
                || stockInRepository.existsByWarehouseId(id)
                || stockOutRepository.existsByWarehouseId(id)
                || stockTransferRepository.existsByWarehouseId(id)
                || stockCountRepository.existsByWarehouseId(id)
                || balanceRepository.existsByWarehouseId(id)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_IN_USE);
        }

        warehouseRepository.deleteById(id);
    }

    private void validateWarehouseType(String warehouseType) {
        if (warehouseType != null && !ALLOWED_WAREHOUSE_TYPES.contains(warehouseType)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_INVALID_TYPE);
        }
    }

    private void validateWarehouseStatus(String status) {
        if (status != null && !ALLOWED_WAREHOUSE_STATUSES.contains(status)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_INVALID_STATUS);
        }
    }

    /** Chỉ chấp nhận trạng thái ACTIVE/INACTIVE ở create/update (kho mới mặc định ACTIVE). */
    private void validateRequestStatus(String status) {
        if (StringUtils.hasText(status) && !ALLOWED_WAREHOUSE_STATUSES.contains(status.trim().toUpperCase())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_INVALID_STATUS);
        }
    }

    private Warehouse findById(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_WAREHOUSE_NOT_FOUND));
    }

    /** Kho bắt buộc thuộc một chi nhánh đang hoạt động (chống kho mồ côi). */
    private void validateBranch(UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_BRANCH_INACTIVE);
        }
    }

    /**
     * Ràng buộc giữa loại kho và chi nhánh:
     * - CENTRAL: không được gắn branchId.
     * - BRANCH: bắt buộc branchId thuộc chi nhánh đang hoạt động.
     */
    private void validateBranchBinding(String warehouseType, UUID branchId) {
        boolean isCentral = "CENTRAL".equals(warehouseType.trim().toUpperCase());
        if (isCentral) {
            if (branchId != null) {
                throw new BaseException(ErrorCode.INV_400_WAREHOUSE_CENTRAL_NO_BRANCH);
            }
            return;
        }
        if (branchId == null) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_BRANCH_REQUIRED);
        }
        dataScopeHelper.enforceBranchAccess(branchId);
        validateBranch(branchId);
    }

    private String resolveSingleBranchName(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findById(branchId).map(Branch::getName).orElse(null);
    }

    private Map<UUID, String> resolveBranchNames(List<Warehouse> warehouses) {
        Set<UUID> branchIds = warehouses.stream()
                .map(Warehouse::getBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (branchIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (existing, replacement) -> existing));
    }
}
