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
import com.erp.core.dto.response.inv.WarehouseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    private static final int MAX_PAGE_SIZE = 100;

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
    public PageResponse<WarehouseResponse> list(int page, int size, String search, UUID branchId, String warehouseType, String status) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<Warehouse> pageResult = warehouseRepository.search(
                StringUtils.hasText(search) ? search.trim() : null,
                branchId,
                StringUtils.hasText(warehouseType) ? warehouseType.trim().toUpperCase() : null,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
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
        List<Warehouse> list = StringUtils.hasText(status)
                ? warehouseRepository.findByStatus(status.trim().toUpperCase())
                : warehouseRepository.findAll(Sort.by("name").ascending());

        Map<UUID, String> branchNames = resolveBranchNames(list);
        return list.stream().map(w -> warehouseMapper.toResponse(w, branchNames)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        Warehouse warehouse = findById(id);
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

        if (request.branchId() != null && !branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);
        Warehouse saved = warehouseRepository.save(warehouse);
        String branchName = resolveSingleBranchName(saved.getBranchId());
        return warehouseMapper.toResponse(saved, branchName);
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findById(id);
        String normalizedCode = request.code().trim().toUpperCase();

        if (warehouseRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new BaseException(ErrorCode.INV_409_WAREHOUSE_CODE_EXISTED);
        }

        if (request.branchId() != null && !branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
        }

        warehouseMapper.updateEntity(warehouse, request);
        Warehouse saved = warehouseRepository.save(warehouse);
        String branchName = resolveSingleBranchName(saved.getBranchId());
        return warehouseMapper.toResponse(saved, branchName);
    }

    @Override
    @Transactional
    public WarehouseResponse updateStatus(UUID id, String status) {
        Warehouse warehouse = findById(id);

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
        Warehouse warehouse = findById(id);

        if (purchaseOrderRepository.existsByWarehouseId(id)) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_IN_USE);
        }

        warehouseRepository.deleteById(id);
    }

    private Warehouse findById(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_WAREHOUSE_NOT_FOUND));
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
