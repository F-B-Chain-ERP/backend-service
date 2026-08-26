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

    @Query("""
        SELECT m
        FROM Material m
        WHERE (:search IS NULL
            OR LOWER(m.code) LIKE CONCAT('%', :search, '%')
            OR LOWER(m.name) LIKE CONCAT('%', :search, '%'))
    """)
    Page<Material> search(String search, Pageable pageable);
}
