package com.erp.backend_service.security;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.PrincipalType;
import com.erp.core.enums.ScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataScopeHelperTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    private DataScopeHelper dataScopeHelper;

    private final UUID branchA = UUID.randomUUID();
    private final UUID branchB = UUID.randomUUID();
    private final UUID warehouseA = UUID.randomUUID();
    private final UUID warehouseB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dataScopeHelper = new DataScopeHelper(warehouseRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuth(ScopeType scopeType, UUID branchId) {
        List<ScopeResponse> scopes = Collections.singletonList(
                new ScopeResponse(UUID.randomUUID(), scopeType, branchId));
        CustomUserDetails userDetails = new CustomUserDetails(
                PrincipalType.ACCOUNT,
                UUID.randomUUID(),
                "testuser",
                "password",
                true,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                scopes,
                branchId,
                Instant.now()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    @DisplayName("isAllSystem() should return true for ALL_SYSTEM scope and false for STORE scope")
    void testIsAllSystem() {
        mockAuth(ScopeType.ALL_SYSTEM, null);
        assertTrue(dataScopeHelper.isAllSystem());

        mockAuth(ScopeType.STORE, branchA);
        assertFalse(dataScopeHelper.isAllSystem());
    }

    @Test
    @DisplayName("getCurrentBranchId() should return current branchId from session")
    void testGetCurrentBranchId() {
        mockAuth(ScopeType.STORE, branchA);
        Optional<UUID> branchId = dataScopeHelper.getCurrentBranchId();
        assertTrue(branchId.isPresent());
        assertEquals(branchA, branchId.get());
    }

    @Test
    @DisplayName("resolveEffectiveBranchId() should allow requestedBranchId for ALL_SYSTEM and force currentBranchId for STORE")
    void testResolveEffectiveBranchId() {
        mockAuth(ScopeType.ALL_SYSTEM, null);
        assertEquals(branchB, dataScopeHelper.resolveEffectiveBranchId(branchB));
        assertNull(dataScopeHelper.resolveEffectiveBranchId(null));

        mockAuth(ScopeType.STORE, branchA);
        assertEquals(branchA, dataScopeHelper.resolveEffectiveBranchId(branchB));
    }

    @Test
    @DisplayName("enforceBranchAccess() should pass for own branch and throw CROSS_SCOPE_DENIED for other branch")
    void testEnforceBranchAccess() {
        mockAuth(ScopeType.STORE, branchA);

        assertDoesNotThrow(() -> dataScopeHelper.enforceBranchAccess(branchA));

        BaseException ex = assertThrows(BaseException.class,
                () -> dataScopeHelper.enforceBranchAccess(branchB));
        assertEquals(ErrorCode.CROSS_SCOPE_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("enforceBranchAccess() should always pass for ALL_SYSTEM")
    void testEnforceBranchAccessAllSystem() {
        mockAuth(ScopeType.ALL_SYSTEM, null);
        assertDoesNotThrow(() -> dataScopeHelper.enforceBranchAccess(branchA));
        assertDoesNotThrow(() -> dataScopeHelper.enforceBranchAccess(branchB));
    }

    @Test
    @DisplayName("enforceWarehouseAccess() should validate warehouse belongs to user branch")
    void testEnforceWarehouseAccess() {
        mockAuth(ScopeType.STORE, branchA);

        Warehouse whA = new Warehouse();
        whA.setId(warehouseA);
        whA.setBranchId(branchA);
        when(warehouseRepository.findById(warehouseA)).thenReturn(Optional.of(whA));

        Warehouse whB = new Warehouse();
        whB.setId(warehouseB);
        whB.setBranchId(branchB);
        when(warehouseRepository.findById(warehouseB)).thenReturn(Optional.of(whB));

        assertDoesNotThrow(() -> dataScopeHelper.enforceWarehouseAccess(warehouseA));

        BaseException ex = assertThrows(BaseException.class,
                () -> dataScopeHelper.enforceWarehouseAccess(warehouseB));
        assertEquals(ErrorCode.CROSS_SCOPE_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("getAllowedWarehouseIds() should return branch warehouses for STORE scope")
    void testGetAllowedWarehouseIds() {
        mockAuth(ScopeType.STORE, branchA);

        Warehouse whA = new Warehouse();
        whA.setId(warehouseA);
        whA.setBranchId(branchA);
        when(warehouseRepository.findByBranchId(branchA)).thenReturn(List.of(whA));

        Collection<UUID> allowed = dataScopeHelper.getAllowedWarehouseIds(null);
        assertNotNull(allowed);
        assertEquals(1, allowed.size());
        assertTrue(allowed.contains(warehouseA));
    }
}
