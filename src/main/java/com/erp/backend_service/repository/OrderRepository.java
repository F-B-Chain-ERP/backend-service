package com.erp.backend_service.repository;

import com.erp.core.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    boolean existsByOrderCode(String orderCode);

    Optional<Order> findTopByOrderCodeStartsWithOrderByOrderCodeDesc(String prefix);

    @Query("""
        select o from Order o
        where (:branchId is null or o.branchId = :branchId)
          and (:customerId is null or o.customerId = :customerId)
          and (:orderType is null or o.orderType = :orderType)
          and (:status is null or o.status = :status)
          and (:fromDate is null or o.createdAt >= :fromDate)
          and (:toDate is null or o.createdAt < :toDate)
        order by o.createdAt desc
        """)
    Page<Order> search(@Param("branchId") UUID branchId, @Param("customerId") UUID customerId,
                       @Param("orderType") String orderType, @Param("status") String status,
                       @Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate,
                       Pageable pageable);
}
