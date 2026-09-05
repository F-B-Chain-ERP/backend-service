package com.erp.backend_service.service;

import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.core.domain.Role;
import com.erp.core.enums.EntityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationResolverServiceTest {

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    private NotificationResolverService resolverService;

    @BeforeEach
    void setUp() {
        resolverService = new NotificationResolverService(accountRoleRepository, roleRepository);
    }

    @Test
    @DisplayName("Should resolve branch managers and ALL_SYSTEM admins, excluding current user")
    void testResolveManagersAndAdmins() {
        UUID branchId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID manager1Id = UUID.randomUUID();
        UUID admin1Id = UUID.randomUUID();

        Role storeManagerRole = new Role();
        storeManagerRole.setCode("STORE_MANAGER");
        Role productManagerRole = new Role();
        productManagerRole.setCode("PRODUCT_MANAGER");

        when(roleRepository.findByCodeIn(NotificationResolverService.BRANCH_MANAGER_ROLE_CODES))
                .thenReturn(List.of(storeManagerRole, productManagerRole));

        when(accountRoleRepository.findAccountIdsByRoleIdInAndBranchId(
                any(), eq(branchId), eq(EntityStatus.ACTIVE), any(Instant.class)
        )).thenReturn(List.of(manager1Id, currentUserId));

        when(accountRoleRepository.findAccountIdsByAllSystemScope(
                eq(EntityStatus.ACTIVE), any(Instant.class)
        )).thenReturn(List.of(admin1Id, currentUserId));

        Set<UUID> recipients = resolverService.resolveManagersAndAdmins(branchId, currentUserId);

        assertEquals(2, recipients.size());
        assertTrue(recipients.contains(manager1Id));
        assertTrue(recipients.contains(admin1Id));
        assertFalse(recipients.contains(currentUserId), "Acting user must be excluded");
    }
}
