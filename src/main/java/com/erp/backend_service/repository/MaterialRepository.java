package com.erp.backend_service.repository;

import com.erp.core.domain.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByBaseUnitId(UUID baseUnitId);

    long countByCategoryId(UUID categoryId);

    @Query("""
        SELECT m
        FROM Material m
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(m.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(m.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:categoryId IS NULL OR m.categoryId = :categoryId)
        AND (:status IS NULL OR m.status = :status)
        AND (:isPerishable IS NULL OR m.isPerishable = :isPerishable)
    """)
    Page<Material> search(String search, UUID categoryId, String status, Boolean isPerishable, Pageable pageable);
}
