package com.erp.backend_service.mapper;

import com.erp.core.domain.Permission;
import com.erp.core.dto.response.PermissionResponse;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public PermissionResponse toResponse(Permission permission) {
        if (permission == null) {
            return null;
        }
        return new PermissionResponse(
                permission.getId() == null ? null : permission.getId().toString(),
                permission.getCode(),
                permission.getName(),
                permission.getModule(),
                permission.getDescription(),
                permission.getStatus()
        );
    }
}
