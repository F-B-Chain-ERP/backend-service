package com.erp.backend_service.repository;

import com.erp.core.domain.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, UUID> {
    List<StockTransferItem> findByStockTransferId(UUID stockTransferId);
    List<StockTransferItem> findByStockTransferIdIn(java.util.Collection<UUID> stockTransferIds);
    void deleteByStockTransferId(UUID stockTransferId);
    boolean existsByMaterialId(UUID materialId);
}
