package com.erp.backend_service.repository;

import com.erp.core.domain.Topping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ToppingRepository extends JpaRepository<Topping, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Query("""
        SELECT t FROM Topping t
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(t.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(t.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:groupName IS NULL OR :groupName = ''
            OR LOWER(t.groupName) = LOWER(:groupName))
        AND (:status IS NULL OR t.status = :status)
        ORDER BY t.createdAt DESC
    """)
    Page<Topping> search(
            @Param("search") String search,
            @Param("groupName") String groupName,
            @Param("status") String status,
            Pageable pageable
    );
}
