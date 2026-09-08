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

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM StockTransfer s WHERE s.fromWarehouseId = :warehouseId OR s.toWarehouseId = :warehouseId")
    boolean existsByWarehouseId(UUID warehouseId);

    /**
     * Còn phiếu chuyển đang đi đường liên quan kho (đi hoặc đến) hay không.
     * Dùng để chặn chốt kiểm kê khi hàng chưa về đủ.
     */
    @Query("""
                SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
                FROM StockTransfer s
                WHERE s.status = 'IN_TRANSIT'
                  AND (s.fromWarehouseId = :warehouseId OR s.toWarehouseId = :warehouseId)
            """)
    boolean existsOpenTransfer(UUID warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockTransfer s WHERE s.id = :id")
    java.util.Optional<StockTransfer> findByIdForUpdate(UUID id);

    @Query("""
                SELECT s
                FROM StockTransfer s
                WHERE (:search IS NULL
                       OR :search = ''
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
