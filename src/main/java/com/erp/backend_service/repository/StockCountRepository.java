package com.erp.backend_service.repository;

import com.erp.core.domain.StockCount;
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
public interface StockCountRepository
        extends JpaRepository<StockCount, UUID> {

    boolean existsByCode(String code);

    boolean existsByWarehouseId(UUID warehouseId);

    /**
     * Kho có phiếu kiểm kê đang thực hiện (IN_PROGRESS) hay không.
     * Dùng để chặn nhập/xuất/chuyển làm thay đổi tồn giữa lúc kiểm.
     */
    @Query("""
                SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
                FROM StockCount s
                WHERE s.status = 'IN_PROGRESS'
                  AND s.warehouseId = :warehouseId
            """)
    boolean existsCounting(UUID warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockCount s WHERE s.id = :id")
    java.util.Optional<StockCount> findByIdForUpdate(UUID id);

    @Query("""
                SELECT s
                FROM StockCount s
        WHERE (:search IS NULL
                OR :search = ''
                OR LOWER(s.code)
                LIKE LOWER(CONCAT('%', :search, '%')))
                  AND (:status IS NULL
                       OR s.status = :status)
                  AND (:warehouseId IS NULL
                       OR s.warehouseId = :warehouseId)
                  AND (
                        :allowedWarehouseIds IS NULL
                        OR s.warehouseId IN :allowedWarehouseIds
                      )
            """)
    Page<StockCount> search(
            String search,
            String status,
            UUID warehouseId,
            Collection<UUID> allowedWarehouseIds,
            Pageable pageable
    );
}
