package com.erp.backend_service.repository;

import com.erp.core.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu vai trò (Role). */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** Tìm vai trò theo mã (code). */
    Optional<Role> findByCode(String code);
}

