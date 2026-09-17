package com.erp.backend_service.repository;

import com.erp.core.domain.CartItemTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CartItemToppingRepository extends JpaRepository<CartItemTopping, UUID> {
    List<CartItemTopping> findByCartItemIdAndStatus(UUID cartItemId, String status);

    /** Bulk load topping 1 lần cho cả giỏ (tránh N+1 khi so khớp merge). */
    List<CartItemTopping> findByCartItemIdInAndStatus(Collection<UUID> cartItemIds, String status);

    void deleteByCartItemId(UUID cartItemId);
}
