package com.erp.backend_service.repository;

import com.erp.core.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerIdAndBranchIdAndStatus(UUID customerId, UUID branchId, String status);

    Optional<Cart> findBySessionTokenAndBranchIdAndStatus(String sessionToken, UUID branchId, String status);
}
