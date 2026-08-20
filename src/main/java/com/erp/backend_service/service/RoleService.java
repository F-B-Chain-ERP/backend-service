package com.erp.backend_service.service;

import com.erp.core.dto.auth.RoleResponse;
import com.erp.core.dto.request.role.CreateRoleRequest;
import com.erp.core.dto.request.role.UpdateRoleRequest;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

public interface RoleService {

    RoleResponse create(CreateRoleRequest request);

    RoleResponse getById(UUID id);

    RoleResponse getByCode(String code);

    PageResponse<RoleResponse> getAll(int page, int size, String search);

    RoleResponse update(UUID id, UpdateRoleRequest request);

    void delete(UUID id);
}
