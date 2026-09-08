package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.StockBalanceService;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockBalanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockBalanceServiceImpl implements StockBalanceService {

    private final MaterialStockBalanceRepository balanceRepository;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final DataScopeHelper dataScopeHelper;

    public StockBalanceServiceImpl(
            MaterialStockBalanceRepository balanceRepository,
            MaterialRepository materialRepository,
            WarehouseRepository warehouseRepository,
            DataScopeHelper dataScopeHelper
    ) {
        this.balanceRepository = balanceRepository;
        this.materialRepository = materialRepository;
        this.warehouseRepository = warehouseRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockBalanceResponse> list(
            int page,
            int size,
            UUID warehouseId,
            UUID materialId,
            String search
    ) {
        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 20;
        }

        if (size > 100) {
            size = 100;
        }

        Collection<UUID> allowedWarehouseIds =
                dataScopeHelper.getAllowedWarehouseIds(warehouseId);

        if (allowedWarehouseIds != null
                && allowedWarehouseIds.isEmpty()) {

            return new PageResponse<>(
                    page,
                    size,
                    0,
                    0,
                    List.of()
            );
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("warehouseId").ascending().and(Sort.by("materialId").ascending()));
        String keyword = search == null ? "" : search.trim();
        Page<MaterialStockBalance> balancePage;
        if (keyword.isBlank()) {
            balancePage = balanceRepository.searchPaged(warehouseId, materialId, allowedWarehouseIds, pageable);
        } else {
            // Resolve kho/NVL khớp keyword (giới hạn 200 mỗi loại để tránh full scan balance)
            Pageable lookup = PageRequest.of(0, 200);
            List<UUID> whIds = warehouseRepository.search(keyword, null, null, null, lookup)
                    .getContent().stream().map(Warehouse::getId).toList();
            List<UUID> matIds = materialRepository.search(keyword, null, null, null, lookup)
                    .getContent().stream().map(Material::getId).toList();
            if (whIds.isEmpty() && matIds.isEmpty()) {
                return new PageResponse<>(page, size, 0, 0, List.of());
            }
            // Đảm bảo collection non-empty cho câu IN (tránh lỗi DB khi 1 phía rỗng)
            List<UUID> safeWhIds = whIds.isEmpty() ? List.of(new UUID(0L, 0L)) : whIds;
            List<UUID> safeMatIds = matIds.isEmpty() ? List.of(new UUID(0L, 0L)) : matIds;
            balancePage = balanceRepository.searchPagedWithKeyword(
                    warehouseId, materialId, allowedWarehouseIds, safeWhIds, safeMatIds, pageable);
        }

        List<MaterialStockBalance> balances = balancePage.getContent();

        Map<UUID, Warehouse> warehouseMap =
                warehouseRepository.findAllById(
                                balances.stream()
                                        .map(MaterialStockBalance::getWarehouseId)
                                        .distinct()
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Warehouse::getId,
                                w -> w
                        ));

        Map<UUID, Material> materialMap =
                materialRepository.findAllById(
                                balances.stream()
                                        .map(MaterialStockBalance::getMaterialId)
                                        .distinct()
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Material::getId,
                                m -> m
                        ));

        List<StockBalanceResponse> result =
                balances.stream()
                        .map(balance ->
                                toResponse(
                                        balance,
                                        warehouseMap.get(
                                                balance.getWarehouseId()
                                        ),
                                        materialMap.get(
                                                balance.getMaterialId()
                                        )
                                )
                        )
                        .sorted(
                                Comparator.comparing(
                                        StockBalanceResponse::warehouseCode,
                                        Comparator.nullsLast(
                                                String::compareTo
                                        )
                                ).thenComparing(
                                        StockBalanceResponse::materialCode,
                                        Comparator.nullsLast(
                                                String::compareTo
                                        )
                                )
                        )
                        .toList();

        return new PageResponse<>(
                balancePage.getNumber(),
                balancePage.getSize(),
                balancePage.getTotalElements(),
                balancePage.getTotalPages(),
                result
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StockBalanceResponse get(
            UUID warehouseId,
            UUID materialId
    ) {
        dataScopeHelper.enforceWarehouseAccess(
                warehouseId
        );

        Warehouse warehouse =
                warehouseRepository.findById(warehouseId)
                        .orElseThrow(() ->
                                new BaseException(
                                        ErrorCode.PROC_404_WAREHOUSE_NOT_FOUND
                                )
                        );

        Material material =
                materialRepository.findById(materialId)
                        .orElseThrow(() ->
                                new BaseException(
                                        ErrorCode.MATERIAL_NOT_FOUND
                                )
                        );

        MaterialStockBalance balance =
                balanceRepository
                        .findByWarehouseIdAndMaterialId(
                                warehouseId,
                                materialId
                        )
                        .orElseGet(() -> {
                            MaterialStockBalance empty =
                                    new MaterialStockBalance();

                            empty.setWarehouseId(
                                    warehouseId
                            );

                            empty.setMaterialId(
                                    materialId
                            );

                            empty.setQuantityOnHand(
                                    BigDecimal.ZERO
                            );

                            empty.setQuantityReserved(
                                    BigDecimal.ZERO
                            );

                            return empty;
                        });

        return toResponse(
                balance,
                warehouse,
                material
        );
    }

    private StockBalanceResponse toResponse(
            MaterialStockBalance balance,
            Warehouse warehouse,
            Material material
    ) {
        BigDecimal quantityOnHand =
                Optional.ofNullable(
                        balance.getQuantityOnHand()
                ).orElse(BigDecimal.ZERO);

        BigDecimal quantityReserved =
                Optional.ofNullable(
                        balance.getQuantityReserved()
                ).orElse(BigDecimal.ZERO);

        BigDecimal availableQuantity =
                quantityOnHand.subtract(
                        quantityReserved
                );

        BigDecimal minStockAlert =
                material == null
                        ? BigDecimal.ZERO
                        : Optional.ofNullable(
                        material.getMinStockAlert()
                ).orElse(BigDecimal.ZERO);

        return new StockBalanceResponse(
                balance.getId(),
                balance.getWarehouseId(),
                warehouse == null
                        ? null
                        : warehouse.getCode(),
                warehouse == null
                        ? null
                        : warehouse.getName(),
                balance.getMaterialId(),
                material == null
                        ? null
                        : material.getCode(),
                material == null
                        ? null
                        : material.getName(),
                quantityOnHand,
                quantityReserved,
                availableQuantity,
                minStockAlert
        );
    }

    private boolean contains(
            String value,
            String keyword
    ) {
        return value != null
                && value.toLowerCase()
                .contains(keyword);
    }
}