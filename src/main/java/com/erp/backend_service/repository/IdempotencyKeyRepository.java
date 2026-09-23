package com.erp.backend_service.repository;

import com.erp.core.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    List<IdempotencyKey> findAllByIdempotencyKeyOrderByCreatedAtDesc(String idempotencyKey);

    /** Mutex transaction-scoped, dùng được giữa nhiều instance mà không đổi schema. */
    @Query(value = "select pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireTransactionLock(@Param("lockId") long lockId);
}
