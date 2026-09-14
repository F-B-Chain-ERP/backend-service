package com.erp.backend_service.repository;

import com.erp.core.domain.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComboItemRepository extends JpaRepository<ComboItem, UUID> {

    List<ComboItem> findByComboProductIdAndStatusOrderByCreatedAtAsc(UUID comboProductId, String status);

    List<ComboItem> findByComboProductId(UUID comboProductId);

    Optional<ComboItem> findByComboProductIdAndVariantId(UUID comboProductId, UUID variantId);

    boolean existsByComboProductIdAndVariantIdAndStatus(UUID comboProductId, UUID variantId, String status);
}
