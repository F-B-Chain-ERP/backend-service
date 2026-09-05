package com.erp.backend_service.repository;

import com.erp.core.domain.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface StockTransferRepository
        extends JpaRepository<StockTransfer, UUID> {

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockTransfer s WHERE s.id = :id")
    java.util.Optional<StockTransfer> findByIdForUpdate(UUID id);

    @Query("""
        SELECT s
        FROM StockTransfer s
        WHERE (:search IS NULL
               OR LOWER(s.code)
               LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL
               OR s.status = :status)
          AND (
                :warehouseId IS NULL
                OR s.fromWarehouseId = :warehouseId
                OR s.toWarehouseId = :warehouseId
              )
          AND (
                :allowedWarehouseIds IS NULL
                OR s.fromWarehouseId IN :allowedWarehouseIds
                OR s.toWarehouseId IN :allowedWarehouseIds
              )
    """)
    Page<StockTransfer> search(
            String search,
            String status,
            UUID warehouseId,
            Collection<UUID> allowedWarehouseIds,
            Pageable pageable
    );
}
