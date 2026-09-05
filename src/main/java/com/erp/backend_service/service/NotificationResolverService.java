package com.erp.backend_service.service;

import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.core.domain.Role;
import com.erp.core.enums.EntityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Dịch vụ giải quyết danh sách người nhận thông báo (recipient accountIds)
 * theo vai trò và phạm vi (chi nhánh, hệ thống).
 */
@Service
public class NotificationResolverService {

    private static final Logger log = LoggerFactory.getLogger(NotificationResolverService.class);

    /** Các mã vai trò cấp quản lý chi nhánh cần nhận thông báo */
    public static final Set<String> BRANCH_MANAGER_ROLE_CODES = Set.of("STORE_MANAGER", "PRODUCT_MANAGER");

    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;

    public NotificationResolverService(
            AccountRoleRepository accountRoleRepository,
            RoleRepository roleRepository
    ) {
        this.accountRoleRepository = accountRoleRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Tìm tất cả tài khoản quản lý của chi nhánh (STORE_MANAGER, PRODUCT_MANAGER)
     * và tất cả tài khoản quản trị hệ thống (ALL_SYSTEM).
     *
     * @param branchId ID của chi nhánh (nếu có)
     * @param excludeAccountId ID tài khoản cần loại trừ (ví dụ người thực hiện thao tác)
     * @return Tập hợp các accountId hợp lệ cần nhận thông báo
     */
    @Transactional(readOnly = true)
    public Set<UUID> resolveManagersAndAdmins(UUID branchId, UUID excludeAccountId) {
        Set<UUID> recipientIds = new HashSet<>();
        Instant now = Instant.now();

        // 1. Quản lý chi nhánh
        if (branchId != null) {
            List<Role> managerRoles = roleRepository.findByCodeIn(BRANCH_MANAGER_ROLE_CODES);
            if (!managerRoles.isEmpty()) {
                List<UUID> roleIds = managerRoles.stream().map(Role::getId).toList();
                List<UUID> managerAccountIds = accountRoleRepository.findAccountIdsByRoleIdInAndBranchId(
                        roleIds, branchId, EntityStatus.ACTIVE, now
                );
                recipientIds.addAll(managerAccountIds);
            }
        }

        // 2. Quản trị toàn hệ thống (ALL_SYSTEM)
        List<UUID> adminAccountIds = accountRoleRepository.findAccountIdsByAllSystemScope(EntityStatus.ACTIVE, now);
        recipientIds.addAll(adminAccountIds);

        // 3. Loại trừ người thao tác
        if (excludeAccountId != null) {
            recipientIds.remove(excludeAccountId);
        }

        recipientIds.removeIf(Objects::isNull);
        log.debug("Resolved {} recipients for branchId={}: {}", recipientIds.size(), branchId, recipientIds);
        return recipientIds;
    }
}
