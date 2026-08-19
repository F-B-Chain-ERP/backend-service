package com.erp.backend_service.repository;

import com.erp.core.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {
    List<RolePermission> findByRoleIdIn(Collection<UUID> roleIds);
    List<RolePermission> findByRoleId(UUID roleId);
}
