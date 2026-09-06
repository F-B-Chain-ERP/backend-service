package com.erp.backend_service.repository;

import com.erp.core.domain.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockCountItemRepository extends JpaRepository<StockCountItem, UUID> {
    List<StockCountItem> findByStockCountId(UUID stockCountId);
    boolean existsByStockCountIdAndMaterialId(UUID stockCountId, UUID materialId);
    void deleteByStockCountId(UUID stockCountId);
}
