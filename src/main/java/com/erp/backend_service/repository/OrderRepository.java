package com.erp.backend_service.repository;

import com.erp.core.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    boolean existsByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Optional<Order> findTopByOrderCodeStartsWithOrderByOrderCodeDesc(String prefix);

    /**
     * Giữ cho tương thích ngược, không dùng cho list mới (xem {@link OrderSpecifications}).
     * Query cũ "? is null" sập 500 trên Postgres khi param Instant null.
     */
    @Deprecated
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

    @Query("""
        select o from Order o
        where o.branchId = :branchId
          and o.createdAt >= :fromInstant
          and o.createdAt <= :toInstant
          and o.status != 'CANCELLED'
          and not (o.status = 'REJECTED' and o.paymentStatus != 'PAID')
        order by o.createdAt asc
        """)
    java.util.List<Order> findOrdersInShiftWindow(@Param("branchId") UUID branchId,
                                                  @Param("fromInstant") Instant fromInstant,
                                                  @Param("toInstant") Instant toInstant);

    boolean existsByBranchId(UUID branchId);

    boolean existsByPickupTimeSlotId(UUID pickupTimeSlotId);

    @Query("""
        select o.pickupTimeSlotId, count(o) from Order o
        where o.branchId = :branchId
          and o.pickupTimeSlotId in :slotIds
          and o.createdAt >= :startOfDay
          and o.createdAt < :endOfDay
          and o.status not in ('CANCELLED', 'REJECTED')
        group by o.pickupTimeSlotId
        """)
    java.util.List<Object[]> countActiveOrdersBySlotIdsOnDate(@Param("branchId") UUID branchId,
                                                             @Param("slotIds") java.util.Collection<UUID> slotIds,
                                                             @Param("startOfDay") Instant startOfDay,
                                                             @Param("endOfDay") Instant endOfDay);

    @Query("""
        select count(o) from Order o
        where o.branchId = :branchId
          and o.pickupTimeSlotId = :slotId
          and o.createdAt >= :startOfDay
          and o.createdAt < :endOfDay
          and o.status not in ('CANCELLED', 'REJECTED')
        """)
    long countActiveOrdersInSlotOnDate(@Param("branchId") UUID branchId,
                                      @Param("slotId") UUID slotId,
                                      @Param("startOfDay") Instant startOfDay,
                                      @Param("endOfDay") Instant endOfDay);
}
