package com.erp.backend_service.repository;

import com.erp.core.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartIdAndStatusOrderByCreatedAtAsc(UUID cartId, String status);

    Optional<CartItem> findByIdAndCartIdAndStatus(UUID id, UUID cartId, String status);
}
