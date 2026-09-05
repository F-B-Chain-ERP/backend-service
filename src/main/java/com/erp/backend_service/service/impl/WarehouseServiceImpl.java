package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.WarehouseMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.service.WarehouseService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateWarehouseRequest;
import com.erp.core.dto.request.inv.UpdateWarehouseRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.warehouse.WarehouseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.erp.core.enums.EntityStatus;

import java.util.UUID;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String WAREHOUSE_TYPE_CENTRAL = "CENTRAL";
    private static final String WAREHOUSE_TYPE_BRANCH = "BRANCH";

    private final WarehouseRepository warehouseRepository;
    private final BranchRepository branchRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final WarehouseMapper warehouseMapper;

    public WarehouseServiceImpl(WarehouseRepository warehouseRepository,
                                BranchRepository branchRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                WarehouseMapper warehouseMapper) {
        this.warehouseRepository = warehouseRepository;
        this.branchRepository = branchRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.warehouseMapper = warehouseMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(int page, int size, String search, String warehouseType, UUID branchId, String status) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        String normalizedWarehouseType = null;
        if (StringUtils.hasText(warehouseType)) {
            normalizedWarehouseType = warehouseType.trim().toUpperCase();
            if (!WAREHOUSE_TYPE_CENTRAL.equals(normalizedWarehouseType) && !WAREHOUSE_TYPE_BRANCH.equals(normalizedWarehouseType)) {
                throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_TYPE);
            }
        }
        String normalizedStatus = null;
        if (StringUtils.hasText(status)) {
            normalizedStatus = status.trim().toUpperCase();
            if (!EntityStatus.ACTIVE.name().equals(normalizedStatus) && !EntityStatus.INACTIVE.name().equals(normalizedStatus)) {
                throw new BaseException(ErrorCode.INV_400_WAREHOUSE_INVALID_STATUS);
            }
        }
        if (branchId != null && !branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
        }

        Page<Warehouse> pageResult = warehouseRepository.search(
                normalizedSearch,
                normalizedWarehouseType,
                branchId, normalizedStatus,
                pageable
        );
        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getContent().stream().map(warehouseMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        Warehouse warehouse = findById(id);
        String branchCode = null;
        String branchName = null;
        if (warehouse.getBranchId() != null) {
            Branch branch = branchRepository.findById(warehouse.getBranchId()).orElse(null);
            if (branch != null) {
                branchCode = branch.getCode();
                branchName = branch.getName();
            }
        }
        return warehouseMapper.toDetailResponse(warehouse, branchCode, branchName);
    }

    @Override
    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_CODE_EXISTED);
        }

        String warehouseType = request.warehouseType().trim().toUpperCase();
        if (!WAREHOUSE_TYPE_CENTRAL.equals(warehouseType) && !WAREHOUSE_TYPE_BRANCH.equals(warehouseType)) {
            throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_TYPE);
        }

        if (WAREHOUSE_TYPE_CENTRAL.equals(warehouseType) && request.branchId() != null) {
            throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_BRANCH);
        }
        if (WAREHOUSE_TYPE_BRANCH.equals(warehouseType)) {
            if (request.branchId() == null) {
                throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_BRANCH);
            }
            if (!branchRepository.existsById(request.branchId())) {
                throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
            }
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);
        warehouse.setWarehouseType(warehouseType);
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findById(id);

        if (!warehouse.getCode().equals(request.code()) && warehouseRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_CODE_EXISTED);
        }

        String warehouseType = request.warehouseType().trim().toUpperCase();
        if (!WAREHOUSE_TYPE_CENTRAL.equals(warehouseType) && !WAREHOUSE_TYPE_BRANCH.equals(warehouseType)) {
            throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_TYPE);
        }

        if (WAREHOUSE_TYPE_CENTRAL.equals(warehouseType) && request.branchId() != null) {
            throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_BRANCH);
        }
        if (WAREHOUSE_TYPE_BRANCH.equals(warehouseType)) {
            if (request.branchId() == null) {
                throw new BaseException(ErrorCode.INV_400_INVALID_WAREHOUSE_BRANCH);
            }
            if (!branchRepository.existsById(request.branchId())) {
                throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
            }
        }

        warehouseMapper.updateEntity(warehouse, request);
        warehouse.setWarehouseType(warehouseType);
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Override
    @Transactional
    public WarehouseResponse updateStatus(UUID id, String status) {
        Warehouse warehouse = findById(id);

        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!EntityStatus.ACTIVE.name().equals(normalizedStatus) && !EntityStatus.INACTIVE.name().equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_INVALID_STATUS);
        }

        warehouse.setStatus(normalizedStatus);
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Warehouse warehouse = findById(id);

        if (purchaseOrderRepository.existsByWarehouseId(id)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_IN_USE);
        }
        // TODO S3-13:
        // Bổ sung kiểm tra các dữ liệu liên quan đến kho khi các repository tương ứng sẵn sàng:
        // - material_stock_balance
        // - stock_in
        // - stock_out
        // - stock_transfer (kho nguồn / kho đích)
        // - stock_count
        warehouseRepository.delete(warehouse);
    }

    private Warehouse findById(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_WAREHOUSE_NOT_FOUND));
    }
}
