package com.erp.backend_service.repository;

import com.erp.core.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Truy vấn ánh xạ giữa vai trò và quyền (RolePermission). */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {

    /** Lấy tất cả ánh xạ thuộc danh sách vai trò cho trước. */
    List<RolePermission> findByRoleIdIn(Collection<UUID> roleIds);

    /** Lấy các quyền của một vai trò. */
    /** Lấy toàn bộ ánh xạ của một vai trò. */
    List<RolePermission> findByRoleId(UUID roleId);

    /** Lấy toàn bộ ánh xạ trỏ tới một quyền (các vai trò đang sở hữu quyền này). */
    List<RolePermission> findByPermissionId(UUID permissionId);
}
