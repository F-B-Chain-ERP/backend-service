package com.erp.backend_service.repository;

import com.erp.core.domain.BranchVariantDailyStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchVariantDailyStockRepository extends JpaRepository<BranchVariantDailyStock, UUID> {
    @Query(value = "select pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireTransactionLock(@Param("lockId") long lockId);
    Optional<BranchVariantDailyStock> findByBranchIdAndVariantIdAndBusinessDateAndStatus(UUID branchId, UUID variantId,
                                                                                         LocalDate date, String status);

    /** Mọi dòng tồn của chi nhánh trong 1 ngày kinh doanh (màn Tồn sản phẩm). */
    List<BranchVariantDailyStock> findByBranchIdAndBusinessDateAndStatus(UUID branchId, LocalDate date,
                                                                         String status);

    /** Lịch sử bán theo ngày của chi nhánh (ước lượng tiêu thụ NVL bình quân). */
    List<BranchVariantDailyStock> findByBranchIdAndBusinessDateBetweenAndStatus(UUID branchId, LocalDate from,
                                                                                LocalDate to, String status);

    /** Dòng tồn mới nhất của variant tại chi nhánh (để carryover số dư sang ngày mới). */
    Optional<BranchVariantDailyStock> findFirstByBranchIdAndVariantIdAndStatusOrderByBusinessDateDesc(
        UUID branchId, UUID variantId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from BranchVariantDailyStock s
        where s.branchId = :branchId and s.variantId = :variantId
          and s.businessDate = :date and s.status = :status
        """)
    Optional<BranchVariantDailyStock> lockByBranchVariantDate(@Param("branchId") UUID branchId,
                                                              @Param("variantId") UUID variantId,
                                                              @Param("date") LocalDate date,
                                                              @Param("status") String status);
}
