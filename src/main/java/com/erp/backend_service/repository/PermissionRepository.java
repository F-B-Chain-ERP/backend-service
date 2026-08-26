package com.erp.backend_service.repository;

import com.erp.core.domain.Permission;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu quyền (Permission). */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    /** Tìm quyền theo mã (code). */
    Optional<Permission> findByCode(String code);

    /** Tìm danh sách quyền theo nhiều mã (code). */
    List<Permission> findByCodeIn(Collection<String> codes);

    /** Kiểm tra mã quyền đã tồn tại hay chưa (so khớp chính xác). */
    boolean existsByCode(String code);

    /**
     * Tìm kiếm quyền kết hợp bộ lọc tuỳ chọn: từ khoá trên code/name/module,
     * lọc theo module và theo trạng thái. Truyền {@code null} để bỏ qua một tiêu chí.
     */
    @Query("""
            select p from Permission p
            where (coalesce(:search, '') = ''
                   or lower(p.code) like lower(concat('%', coalesce(:search, ''), '%'))
                   or lower(p.name) like lower(concat('%', coalesce(:search, ''), '%'))
                   or lower(p.module) like lower(concat('%', coalesce(:search, ''), '%')))
              and (coalesce(:module, '') = '' or upper(p.module) = upper(coalesce(:module, '')))
              and (:status is null or p.status = :status)
            """)
    Page<Permission> search(
            @Param("search") String search,
            @Param("module") String module,
            @Param("status") EntityStatus status,
            Pageable pageable
    );

    /** Danh sách các module hiện có (phục vụ bộ lọc phía client). */
    @Query("select distinct p.module from Permission p where (:status is null or p.status = :status) order by p.module")
    java.util.List<String> findDistinctModules(@Param("status") EntityStatus status);
}
