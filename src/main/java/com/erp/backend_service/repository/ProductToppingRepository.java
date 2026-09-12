package com.erp.backend_service.repository;

import com.erp.core.domain.ProductTopping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductToppingRepository extends JpaRepository<ProductTopping, UUID> {

    List<ProductTopping> findByProductIdOrderByCreatedAtAsc(UUID productId);

    Optional<ProductTopping> findByProductIdAndToppingId(UUID productId, UUID toppingId);

    boolean existsByProductIdAndToppingId(UUID productId, UUID toppingId);

    boolean existsByToppingId(UUID toppingId);
}
