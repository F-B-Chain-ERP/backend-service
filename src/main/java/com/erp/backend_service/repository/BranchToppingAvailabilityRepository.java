package com.erp.backend_service.repository;

import com.erp.core.domain.BranchToppingAvailability;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchToppingAvailabilityRepository extends JpaRepository<BranchToppingAvailability, UUID> {

    Optional<BranchToppingAvailability> findByBranchIdAndToppingId(UUID branchId, UUID toppingId);

    @Query("""
        SELECT bta FROM BranchToppingAvailability bta
        JOIN Topping t ON t.id = bta.toppingId
        WHERE bta.branchId = :branchId
        AND (:search IS NULL OR :search = ''
            OR LOWER(t.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(t.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:status IS NULL OR bta.status = :status)
        ORDER BY bta.createdAt DESC
    """)
    Page<BranchToppingAvailability> search(
            @Param("branchId") UUID branchId,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );
}
