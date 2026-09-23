package com.erp.backend_service.repository;

import com.erp.core.domain.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, String status);

    boolean existsByRefundCode(String refundCode);

    /**
     * Tổng tiền Refund PROCESSED thuộc chi nhánh trong khoảng [fromInstant, toInstant) theo processedAt.
     */
    @Query("""
        select coalesce(sum(r.amount), 0)
        from Refund r
        join Order o on o.id = r.orderId
        where o.branchId = :branchId
          and r.status = 'PROCESSED'
          and r.processedAt >= :fromInstant
          and r.processedAt < :toInstant
        """)
    BigDecimal sumProcessedAmountByBranchBetween(@Param("branchId") UUID branchId,
                                                 @Param("fromInstant") Instant fromInstant,
                                                 @Param("toInstant") Instant toInstant);

    /**
     * Định khoản dữ liệu nguồn Refund PROCESSED để đối soát doanh thu thuần.
     */
    @Query("""
        select r from Refund r
        join Order o on o.id = r.orderId
        where o.branchId = :branchId
          and r.status = 'PROCESSED'
          and r.processedAt >= :fromInstant
          and r.processedAt < :toInstant
        order by r.processedAt asc
        """)
    Page<Refund> findProcessedByBranchBetween(@Param("branchId") UUID branchId,
                                              @Param("fromInstant") Instant fromInstant,
                                              @Param("toInstant") Instant toInstant,
                                              Pageable pageable);
}