package com.erp.backend_service.service;

import com.erp.core.dto.auth.RoleResponse;
import com.erp.core.dto.request.role.CreateRoleRequest;
import com.erp.core.dto.request.role.UpdateRoleRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.auth.RoleAssignmentRequest;
import com.erp.core.dto.auth.RoleAssignmentResponse;
import com.erp.core.dto.auth.RoleMemberResponse;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý việc gán và thu hồi vai trò (role) cho tài khoản trong một phạm vi (scope).
 */
public interface RoleService {

    /**
     * Gán một vai trò cho tài khoản tại phạm vi chỉ định.
     * Nếu đã tồn tại bản ghi thì cập nhật thành active, đồng thời thu hồi token cũ.
     *
     * @param request thông tin gán vai trò (tài khoản, vai trò, phạm vi)
     * @return thông tin bản ghi gán vai trò vừa tạo/cập nhật
     */
    RoleAssignmentResponse assign(RoleAssignmentRequest request);

    /**
     * Thu hồi (vô hiệu hóa) một bản ghi gán vai trò theo id.
     *
     * @param assignmentId id bản ghi gán vai trò cần thu hồi
     */
    void revoke(UUID assignmentId);

    /**
     * Lấy danh sách các vai trò đã gán cho một tài khoản.
     *
     * @param accountId id của tài khoản
     * @return danh sách thông tin gán vai trò (kể cả đã inactive)
     */
    List<RoleAssignmentResponse> findByAccount(UUID accountId);

    RoleResponse create(CreateRoleRequest request);

    RoleResponse getById(UUID id);

    RoleResponse getByCode(String code);

    PageResponse<RoleResponse> getAll(int page, int size, String search);

    RoleResponse update(UUID id, UpdateRoleRequest request);

    void delete(UUID id);

    /**
     * Lấy danh sách id quyền đã gán cho một vai trò.
     *
     * @param roleId id vai trò
     * @return danh sách permission id
     */
    List<UUID> getPermissionsByRole(UUID roleId);

    /**
     * Lấy danh sách tài khoản là thành viên của một vai trò (đã gán effective).
     *
     * @param roleId id vai trò
     * @return danh sách thành viên (RoleMemberResponse)
     */
    List<RoleMemberResponse> getMembers(UUID roleId);

    /**
     * Thay thế toàn bộ quyền của một vai trò bằng danh sách mới.
     *
     * @param roleId         id vai trò
     * @param permissionIds  danh sách permission id (có thể rỗng để gỡ hết)
     */
    void setPermissionsForRole(UUID roleId, List<UUID> permissionIds);
}
