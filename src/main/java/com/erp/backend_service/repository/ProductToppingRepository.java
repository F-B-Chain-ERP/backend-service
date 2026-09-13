package com.erp.backend_service.repository;

import com.erp.core.domain.ProductTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductToppingRepository extends JpaRepository<ProductTopping, UUID> {
    Optional<ProductTopping> findByProductIdAndToppingIdAndStatus(UUID productId, UUID toppingId, String status);
}
