package com.erp.backend_service.repository;

import com.erp.core.domain.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu đơn vị tính (unit). */
@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Query("""
        SELECT u
        FROM Unit u
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(u.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(u.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:unitType IS NULL OR u.unitType = :unitType)
        AND (:status IS NULL OR u.status = :status)
    """)
    Page<Unit> search(String search, String unitType, String status, Pageable pageable);
}
