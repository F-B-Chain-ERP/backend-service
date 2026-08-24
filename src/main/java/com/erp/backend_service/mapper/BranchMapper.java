package com.erp.backend_service.mapper;

import com.erp.core.domain.Branch;
import com.erp.core.dto.response.branch.BranchResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Chuyển đổi entity Branch sang BranchResponse (tên chi nhánh cha truyền từ service). */
@Component
public class BranchMapper {

    /** Ánh xạ chi nhánh sang response, điền sẵn tên chi nhánh cha nếu có. */
    public BranchResponse toResponse(Branch branch, Map<UUID, String> parentNames) {
        String parentName = branch.getParentId() != null ? parentNames.get(branch.getParentId()) : null;
        return new BranchResponse(
                branch.getId().toString(),
                branch.getCode(),
                branch.getName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getEmail(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getTimezone(),
                branch.isSupportsPickup(),
                branch.isSupportsDelivery(),
                branch.getAveragePreparationMinutes(),
                branch.getStatus(),
                branch.getParentId() != null ? branch.getParentId().toString() : null,
                parentName
        );
    }
}
