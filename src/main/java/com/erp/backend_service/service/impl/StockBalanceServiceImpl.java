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

        List<MaterialStockBalance> balances;

        if (allowedWarehouseIds == null) {
            balances = balanceRepository.search(
                    warehouseId,
                    materialId
            );
        } else {
            balances = balanceRepository
                    .findByWarehouseIdIn(allowedWarehouseIds)
                    .stream()
                    .filter(b ->
                            warehouseId == null
                                    || warehouseId.equals(b.getWarehouseId()))
                    .filter(b ->
                            materialId == null
                                    || materialId.equals(b.getMaterialId()))
                    .toList();
        }

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

        String keyword =
                search == null
                        ? ""
                        : search.trim().toLowerCase();

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
                        .filter(response ->
                                keyword.isBlank()
                                        || contains(
                                        response.materialCode(),
                                        keyword
                                )
                                        || contains(
                                        response.materialName(),
                                        keyword
                                )
                                        || contains(
                                        response.warehouseCode(),
                                        keyword
                                )
                                        || contains(
                                        response.warehouseName(),
                                        keyword
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

        long totalElements = result.size();

        int fromIndex = Math.min(
                page * size,
                result.size()
        );

        int toIndex = Math.min(
                fromIndex + size,
                result.size()
        );

        List<StockBalanceResponse> content =
                result.subList(
                        fromIndex,
                        toIndex
                );

        int totalPages =
                (int) Math.ceil(
                        (double) totalElements / size
                );

        return new PageResponse<>(
                page,
                size,
                totalElements,
                totalPages,
                content
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