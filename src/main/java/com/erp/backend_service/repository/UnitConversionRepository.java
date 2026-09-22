package com.erp.backend_service.repository;

import com.erp.core.domain.UnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitConversionRepository extends JpaRepository<UnitConversion, UUID> {

    Optional<UnitConversion> findByFromUnitIdAndToUnitIdAndStatus(UUID fromUnitId, UUID toUnitId, String status);

    boolean existsByFromUnitIdAndToUnitId(UUID fromUnitId, UUID toUnitId);

    List<UnitConversion> findByStatusOrderByCreatedAtAsc(String status);
}
