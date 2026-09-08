package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.core.domain.MaterialStockBalance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class StockBalanceMutationService {

    private final MaterialStockBalanceRepository balanceRepository;
    private final MaterialRepository materialRepository;
    private final DataScopeHelper dataScopeHelper;

    public StockBalanceMutationService(
            MaterialStockBalanceRepository balanceRepository,
            MaterialRepository materialRepository,
            DataScopeHelper dataScopeHelper
    ) {
        this.balanceRepository = balanceRepository;
        this.materialRepository = materialRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    public MaterialStockBalance lockOrCreate(
            UUID warehouseId,
            UUID materialId
    ) {
        dataScopeHelper.enforceWarehouseAccess(
                warehouseId
        );

        materialRepository.findById(materialId)
                .orElseThrow(() ->
                        new BaseException(
                                ErrorCode.MATERIAL_NOT_FOUND
                        )
                );

        balanceRepository.ensureExists(warehouseId, materialId);

        return balanceRepository
                .findForUpdate(
                        warehouseId,
                        materialId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Stock balance was not created or found"
                ));
    }

    public void increase(
            UUID warehouseId,
            UUID materialId,
            BigDecimal quantity
    ) {
        if (quantity == null
                || quantity.signum() <= 0) {
            throw new BaseException(
                    ErrorCode.INVALID_QUANTITY
            );
        }

        MaterialStockBalance balance =
                lockOrCreate(
                        warehouseId,
                        materialId
                );

        BigDecimal current =
                balance.getQuantityOnHand() == null
                        ? BigDecimal.ZERO
                        : balance.getQuantityOnHand();

        balance.setQuantityOnHand(
                current.add(quantity)
        );

        balanceRepository.save(balance);
    }

    public void decrease(
            UUID warehouseId,
            UUID materialId,
            BigDecimal quantity
    ) {
        if (quantity == null
                || quantity.signum() <= 0) {
            throw new BaseException(
                    ErrorCode.INVALID_QUANTITY
            );
        }

        MaterialStockBalance balance =
                lockOrCreate(
                        warehouseId,
                        materialId
                );

        BigDecimal onHand =
                balance.getQuantityOnHand() == null
                        ? BigDecimal.ZERO
                        : balance.getQuantityOnHand();

        BigDecimal reserved =
                balance.getQuantityReserved() == null
                        ? BigDecimal.ZERO
                        : balance.getQuantityReserved();

        BigDecimal available =
                onHand.subtract(reserved);

        if (available.compareTo(quantity) < 0) {
            throw new BaseException(
                    ErrorCode.INV_400_INSUFFICIENT_STOCK
            );
        }

        balance.setQuantityOnHand(
                onHand.subtract(quantity)
        );

        balanceRepository.save(balance);
    }
}
