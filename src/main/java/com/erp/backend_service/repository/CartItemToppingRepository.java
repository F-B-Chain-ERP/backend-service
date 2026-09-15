package com.erp.backend_service.repository;

import com.erp.core.domain.CartItemTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartItemToppingRepository extends JpaRepository<CartItemTopping, UUID> {
    List<CartItemTopping> findByCartItemIdAndStatus(UUID cartItemId, String status);

    void deleteByCartItemId(UUID cartItemId);
}
