package com.erp.backend_service.repository;

import com.erp.core.domain.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByCategoryTypeAndCode(String categoryType, String code);

    boolean existsByCategoryTypeAndCodeAndIdNot(String categoryType, String code, UUID id);

    @Query("""
        SELECT c
        FROM Category c
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(c.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(c.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:categoryType IS NULL OR c.categoryType = :categoryType)
        AND (:status IS NULL OR c.status = :status)
    """)
    Page<Category> search(String search, String categoryType, String status, Pageable pageable);
}
