package com.erp.backend_service.service;

import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.ScopeAdminResponse;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.dto.request.scope.CreateScopeRequest;
import com.erp.core.dto.request.scope.UpdateScopeRequest;
import com.erp.core.enums.ScopeType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quản lý phạm vi (scope) của tài khoản: truy vấn, tạo mới và kiểm tra
 * một phạm vi có bao phủ một chi nhánh cụ thể hay không.
 */
public interface ScopeService {

    /**
     * Lấy phạm vi đang active theo id, ném lỗi nếu không tồn tại.
     *
     * @param scopeId id phạm vi
     * @return bản ghi phạm vi đang hoạt động
     */
    Scope getActive(UUID scopeId);

    /**
     * Lấy đồng thời nhiều phạm vi đang active theo danh sách id.
     *
     * @param scopeIds danh sách id phạm vi
     * @return map id -&gt; phạm vi (chỉ các phạm vi active)
     */
    Map<UUID, Scope> findAllById(Iterable<UUID> scopeIds);

    /**
     * Kiểm tra phạm vi (DTO) có bao phủ chi nhánh chỉ định hay không.
     *
     * @param scope    phạm vi dạng response
     * @param branchId id chi nhánh
     * @return {@code true} nếu là phạm vi toàn hệ thống hoặc trùng chi nhánh
     */
    boolean covers(ScopeResponse scope, UUID branchId);

    /**
     * Danh sách toàn bộ phạm vi phục vụ màn quản trị, kèm tên chi nhánh liên quan.
     *
     * @return danh sách phạm vi
     */
    List<ScopeAdminResponse> findAll();

    /**
     * Chi tiết một phạm vi.
     *
     * @param id id phạm vi
     * @return thông tin phạm vi
     */
    ScopeAdminResponse getById(UUID id);

    /**
     * Tạo mới phạm vi. ALL_SYSTEM không gắn chi nhánh; STORE/WAREHOUSE bắt buộc gắn.
     *
     * @param request dữ liệu phạm vi cần tạo
     * @return phạm vi vừa tạo
     */
    ScopeAdminResponse create(CreateScopeRequest request);

    /**
     * Cập nhật một phần thông tin phạm vi. Truyền {@code null} để giữ nguyên trường tương ứng.
     *
     * @param id      id phạm vi
     * @param request dữ liệu cần cập nhật
     * @return phạm vi sau khi cập nhật
     */
    ScopeAdminResponse update(UUID id, UpdateScopeRequest request);

    /**
     * Xóa vĩnh viễn một phạm vi.
     *
     * @param id id phạm vi
     */
    void delete(UUID id);
}
