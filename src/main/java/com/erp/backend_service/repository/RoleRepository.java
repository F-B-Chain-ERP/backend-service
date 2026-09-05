package com.erp.backend_service.repository;

import com.erp.core.domain.Role;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu vai trò (Role). */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** Tìm vai trò theo mã (code). */
    Optional<Role> findByCode(String code);
    List<Role> findByCodeIn(Collection<String> codes);
    List<Role> findAllByStatus(EntityStatus status);
    Page<Role> findAll(Pageable pageable);
    Page<Role> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}

