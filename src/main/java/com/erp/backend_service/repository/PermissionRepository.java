package com.erp.backend_service.repository;

import com.erp.core.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu quyền (Permission). */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    /** Tìm quyền theo mã (code). */
    Optional<Permission> findByCode(String code);
}

