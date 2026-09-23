package com.erp.backend_service.repository;

import com.erp.core.domain.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartIdAndStatusOrderByCreatedAtAsc(UUID cartId, String status);

    Optional<CartItem> findByIdAndCartIdAndStatus(UUID id, UUID cartId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CartItem i where i.id = :id and i.cartId = :cartId and i.status = :status")
    Optional<CartItem> findByIdAndCartIdAndStatusForUpdate(@Param("id") UUID id,
                                                           @Param("cartId") UUID cartId,
                                                           @Param("status") String status);
}
