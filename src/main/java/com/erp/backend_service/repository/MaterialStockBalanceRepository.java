package com.erp.backend_service.repository;

import com.erp.core.domain.MaterialStockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn tồn kho nguyên vật liệu theo kho (material_stock_balance). */
@Repository
public interface MaterialStockBalanceRepository extends JpaRepository<MaterialStockBalance, UUID> {

    /** Lấy số dư tồn của một nguyên vật liệu tại một kho, khóa bi quan để tránh điều kiện tranh chấp. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MaterialStockBalance> findByWarehouseIdAndMaterialId(UUID warehouseId, UUID materialId);
}