package com.erp.backend_service.repository;

import com.erp.core.domain.KdsTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KdsTicketRepository extends JpaRepository<KdsTicket, UUID>, JpaSpecificationExecutor<KdsTicket> {

    @Query(value = "select pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireTransactionLock(@Param("lockId") long lockId);

    Optional<KdsTicket> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from KdsTicket t where t.id = :id")
    Optional<KdsTicket> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from KdsTicket t where t.orderId = :orderId")
    Optional<KdsTicket> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    List<KdsTicket> findByOrderIdIn(List<UUID> orderIds);

    @Query("""
        select coalesce(max(t.queueNo), 0) from KdsTicket t
        where t.branchId = :branchId
          and t.createdAt >= :dayStart
          and t.createdAt < :dayEnd
        """)
    int maxQueueNoToday(@Param("branchId") UUID branchId,
                        @Param("dayStart") Instant dayStart,
                        @Param("dayEnd") Instant dayEnd);
}
