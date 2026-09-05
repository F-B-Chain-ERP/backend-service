package com.erp.backend_service.repository;

import com.erp.core.domain.MaterialStockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialStockBalanceRepository
        extends JpaRepository<MaterialStockBalance, UUID> {

    @Query("""
        SELECT b
        FROM MaterialStockBalance b
        WHERE (:warehouseId IS NULL
               OR b.warehouseId = :warehouseId)
          AND (:materialId IS NULL
               OR b.materialId = :materialId)
    """)
    List<MaterialStockBalance> search(
            UUID warehouseId,
            UUID materialId
    );

    List<MaterialStockBalance> findByWarehouseId(
            UUID warehouseId
    );

    List<MaterialStockBalance> findByWarehouseIdIn(
            Collection<UUID> warehouseIds
    );

    Optional<MaterialStockBalance>
    findByWarehouseIdAndMaterialId(
            UUID warehouseId,
            UUID materialId
    );

    @Modifying
    @Query(value = """
        INSERT INTO material_stock_balance (
            id, warehouse_id, material_id,
            quantity_on_hand, quantity_reserved,
            created_at, updated_at
        ) VALUES (
            gen_random_uuid(), :warehouseId, :materialId,
            0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        ON CONFLICT (warehouse_id, material_id) DO NOTHING
        """, nativeQuery = true)
    int ensureExists(UUID warehouseId, UUID materialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b
        FROM MaterialStockBalance b
        WHERE b.warehouseId = :warehouseId
          AND b.materialId = :materialId
    """)
    Optional<MaterialStockBalance> findForUpdate(
            UUID warehouseId,
            UUID materialId
    );
}
