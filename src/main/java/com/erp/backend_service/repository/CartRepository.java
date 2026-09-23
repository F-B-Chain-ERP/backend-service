package com.erp.backend_service.repository;

import com.erp.core.domain.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerIdAndBranchIdAndStatus(UUID customerId, UUID branchId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customerId = :customerId and c.branchId = :branchId and c.status = :status")
    Optional<Cart> findActiveForUpdate(@Param("customerId") UUID customerId,
                                       @Param("branchId") UUID branchId,
                                       @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id = :id")
    Optional<Cart> findByIdForUpdate(@Param("id") UUID id);
}
