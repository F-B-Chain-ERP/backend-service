package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.AccountMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.PermissionService;
import com.erp.backend_service.service.impl.AccountServiceImpl;
import com.erp.core.domain.Account;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Role;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.CreateAccountRequest;
import com.erp.core.dto.auth.UpdateAccountRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.AuthProvider;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import com.erp.core.enums.ScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ScopeRepository scopeRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AccountRoleRepository accountRoleRepository;
    @Mock private AccountMapper accountMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountRevocationService revocationService;
    @Mock private PermissionService permissionService;
    @Mock private DataScopeHelper dataScopeHelper;

    private AccountServiceImpl accountService;

    private final UUID branchA = UUID.randomUUID();
    private final UUID branchB = UUID.randomUUID();
    private final UUID roleId1 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(
                accountRepository,
                branchRepository,
                scopeRepository,
                roleRepository,
                accountRoleRepository,
                accountMapper,
                passwordEncoder,
                revocationService,
                permissionService,
                dataScopeHelper,
                3600
        );

        // Mock internal admin caller
        CustomUserDetails adminUser = new CustomUserDetails(
                PrincipalType.ACCOUNT,
                UUID.randomUUID(),
                "admin",
                "pwd",
                true,
                Collections.emptyList(),
                List.of("ROLE_ADMIN"),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Instant.now()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createAccount should throw RESOURCE_NOT_FOUND when primaryBranchId does not exist")
    void testCreateAccountBranchNotFound() {
        CreateAccountRequest req = new CreateAccountRequest(
                "user1", "Pass123456", "User One", "user1@example.com", "0901234567",
                branchA, AuthProvider.LOCAL, null
        );
        when(branchRepository.existsById(branchA)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class, () -> accountService.createAccount(req));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("createAccount with roleIds should create account and auto sync Scope + AccountRole")
    void testCreateAccountWithRoles() {
        CreateAccountRequest req = new CreateAccountRequest(
                "user1", "Pass123456", "User One", "user1@example.com", "0901234567",
                branchA, AuthProvider.LOCAL, List.of(roleId1)
        );
        when(branchRepository.existsById(branchA)).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("hashed_pwd");

        Account savedAcc = new Account();
        savedAcc.setId(UUID.randomUUID());
        savedAcc.setUsername("user1");
        savedAcc.setPrimaryBranchId(branchA);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAcc);

        Scope scope = new Scope();
        scope.setId(UUID.randomUUID());
        scope.setScopeType(ScopeType.STORE);
        scope.setBranchId(branchA);
        when(scopeRepository.findByScopeTypeAndBranchId(ScopeType.STORE, branchA))
                .thenReturn(Optional.of(scope));

        when(roleRepository.existsById(roleId1)).thenReturn(true);
        when(accountRoleRepository.findByAccountId(savedAcc.getId())).thenReturn(List.of());

        AccountResponse resp = new AccountResponse(
                savedAcc.getId(), "user1", "user1@example.com", "User One", "0901234567",
                null, AuthProvider.LOCAL, true, EntityStatus.ACTIVE, branchA, null, Instant.now(), Instant.now()
        );
        when(accountMapper.toResponse(savedAcc)).thenReturn(resp);

        AccountResponse result = accountService.createAccount(req);
        assertNotNull(result);
        assertEquals(branchA, result.primaryBranchId());

        verify(accountRoleRepository, times(1)).save(any(AccountRole.class));
    }

    @Test
    @DisplayName("listAccounts should filter by currentBranchId for branch manager")
    void testListAccountsBranchScoped() {
        when(dataScopeHelper.isAllSystem()).thenReturn(false);
        when(dataScopeHelper.getCurrentBranchId()).thenReturn(Optional.of(branchA));

        Page<Account> page = new PageImpl<>(List.of());
        when(accountRepository.search(isNull(), eq(branchA), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<AccountResponse> result = accountService.listAccounts(0, 10, null);
        assertNotNull(result);
        verify(accountRepository).search(isNull(), eq(branchA), any(Pageable.class));
    }

    @Test
    @DisplayName("listAccounts should query with null branchId for ALL_SYSTEM admin")
    void testListAccountsAllSystem() {
        when(dataScopeHelper.isAllSystem()).thenReturn(true);

        Page<Account> page = new PageImpl<>(List.of());
        when(accountRepository.search(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<AccountResponse> result = accountService.listAccounts(0, 10, null);
        assertNotNull(result);
        verify(accountRepository).search(isNull(), isNull(), any(Pageable.class));
    }
}
