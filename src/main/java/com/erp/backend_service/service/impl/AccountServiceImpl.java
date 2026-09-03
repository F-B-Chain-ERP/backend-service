package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.AccountMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.AccountService;
import com.erp.backend_service.service.PermissionService;
import com.erp.core.domain.Account;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.AccountBranchResponse;
import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.CreateAccountRequest;
import com.erp.core.dto.auth.ResetPasswordRequest;
import com.erp.core.dto.auth.UpdateAccountRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.AuthProvider;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import com.erp.core.enums.ScopeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link AccountService}: quản lý tài khoản nội bộ do admin thực hiện
 * (tạo, xem, tìm kiếm, cập nhật, vô hiệu hóa, đặt lại mật khẩu).
 * Hỗ trợ gán chi nhánh bắt buộc, đồng bộ phạm vi (Scope) và lọc dữ liệu nhân sự theo chi nhánh.
 */
@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final BranchRepository branchRepository;
    private final ScopeRepository scopeRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountRevocationService revocationService;
    private final PermissionService permissionService;
    private final DataScopeHelper dataScopeHelper;
    private final Duration accessTokenLifetime;

    public AccountServiceImpl(AccountRepository accountRepository,
                              BranchRepository branchRepository,
                              ScopeRepository scopeRepository,
                              RoleRepository roleRepository,
                              AccountRoleRepository accountRoleRepository,
                              AccountMapper accountMapper,
                              PasswordEncoder passwordEncoder,
                              AccountRevocationService revocationService,
                              PermissionService permissionService,
                              DataScopeHelper dataScopeHelper,
                              @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry) {
        this.accountRepository = accountRepository;
        this.branchRepository = branchRepository;
        this.scopeRepository = scopeRepository;
        this.roleRepository = roleRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.revocationService = revocationService;
        this.permissionService = permissionService;
        this.dataScopeHelper = dataScopeHelper;
        this.accessTokenLifetime = Duration.ofSeconds(accessTokenExpiry);
    }

    /** {@inheritDoc} */
    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {
        assertInternalAdmin();
        if (accountRepository.findByUsername(request.username()).isPresent()) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }
        if (request.email() != null && accountRepository.findByEmail(request.email()).isPresent()) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }
        if (request.phone() != null && accountRepository.existsByPhone(request.phone())) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }

        // Bắt buộc chọn chi nhánh và chi nhánh phải tồn tại
        if (request.primaryBranchId() == null || !branchRepository.existsById(request.primaryBranchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Account account = new Account();
        account.setUsername(request.username());
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setFullName(request.fullName());
        account.setEmail(request.email());
        account.setPhone(request.phone());
        account.setPrimaryBranchId(request.primaryBranchId());
        account.setAuthProvider(request.authProvider() != null ? request.authProvider() : AuthProvider.LOCAL);
        account.setHasLocalPassword(true);
        account.setSystemProtected(false);
        account.setStatus(EntityStatus.ACTIVE);
        account = accountRepository.save(account);

        // Nếu có truyền roleIds, tự động đồng bộ Scope và gán quyền
        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            syncRolesForBranch(account, request.primaryBranchId(), request.roleIds());
        }

        return toResponse(account);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        assertInternalAdmin();
        Account account = findById(id);
        if (!dataScopeHelper.isAllSystem() && account.getPrimaryBranchId() != null) {
            dataScopeHelper.enforceBranchAccess(account.getPrimaryBranchId());
        }
        return toResponse(account);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(int page, int size, String search) {
        assertInternalAdmin();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());

        // Nếu không phải ALL_SYSTEM, tự động lọc chỉ hiển thị nhân sự thuộc chi nhánh hiện tại
        UUID effectiveBranchId = dataScopeHelper.isAllSystem()
                ? null
                : dataScopeHelper.getCurrentBranchId().orElse(null);

        Page<Account> accountPage = accountRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, effectiveBranchId, pageable);
        return new PageResponse<>(
                accountPage.getNumber(),
                accountPage.getSize(),
                accountPage.getTotalElements(),
                accountPage.getTotalPages(),
                accountPage.getContent().stream().map(this::toResponse).toList()
        );
    }

    /** {@inheritDoc} */
    @Override
    public AccountResponse updateAccount(UUID id, UpdateAccountRequest request) {
        assertInternalAdmin();
        Account account = findById(id);
        verifyCanModify(account);

        if (request.fullName() != null) {
            account.setFullName(request.fullName());
        }
        if (request.email() != null && !Objects.equals(account.getEmail(), request.email())) {
            if (accountRepository.existsByEmailAndIdNot(request.email(), id)) {
                throw new BaseException(ErrorCode.USER_EXISTED);
            }
            account.setEmail(request.email());
        }
        if (request.phone() != null && !Objects.equals(account.getPhone(), request.phone())) {
            if (accountRepository.existsByPhoneAndIdNot(request.phone(), id)) {
                throw new BaseException(ErrorCode.USER_EXISTED);
            }
            account.setPhone(request.phone());
        }
        if (request.avatarUrl() != null) {
            account.setAvatarUrl(request.avatarUrl());
        }

        boolean branchChanged = false;
        if (request.primaryBranchId() != null && !Objects.equals(account.getPrimaryBranchId(), request.primaryBranchId())) {
            if (!branchRepository.existsById(request.primaryBranchId())) {
                throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            account.setPrimaryBranchId(request.primaryBranchId());
            branchChanged = true;
        }

        if (request.status() != null) {
            account.setStatus(request.status());
        }

        Account saved = accountRepository.save(account);

        // Đồng bộ vai trò nếu có truyền roleIds hoặc khi đổi chi nhánh
        if (request.roleIds() != null || (branchChanged && saved.getPrimaryBranchId() != null)) {
            UUID targetBranchId = saved.getPrimaryBranchId();
            if (targetBranchId != null) {
                List<UUID> targetRoles = request.roleIds() != null
                        ? request.roleIds()
                        : accountRoleRepository.findByAccountId(id).stream()
                            .filter(ar -> ar.getStatus() == EntityStatus.ACTIVE)
                            .map(AccountRole::getRoleId)
                            .distinct()
                            .toList();
                syncRolesForBranch(saved, targetBranchId, targetRoles);
            }
            revocationService.revokeAccount(id, accessTokenLifetime);
            permissionService.evictSnapshot(id);
        }

        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(UUID id) {
        assertInternalAdmin();
        Account account = findById(id);
        verifyCanModify(account);
        account.setStatus(EntityStatus.INACTIVE);
        accountRepository.save(account);
        permissionService.evictSnapshot(id);
        revocationService.revokeAccount(id, accessTokenLifetime);
    }

    /** {@inheritDoc} */
    @Override
    public AccountResponse resetPassword(UUID id, ResetPasswordRequest request) {
        assertInternalAdmin();
        Account account = findById(id);
        verifyCanModify(account);
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setHasLocalPassword(true);
        Account saved = accountRepository.save(account);
        permissionService.evictSnapshot(id);
        revocationService.revokeAccount(id, accessTokenLifetime);
        return toResponse(saved);
    }

    /** Trả về account kèm toàn bộ chi nhánh từ các scope được gán hiệu lực. */
    private AccountResponse toResponse(Account account) {
        List<AccountRole> assignments = accountRoleRepository.findEffectiveByAccountId(
                account.getId(), EntityStatus.ACTIVE, Instant.now());

        List<UUID> scopeIds = assignments.stream()
                .map(AccountRole::getScopeId)
                .distinct()
                .toList();
        Map<UUID, Scope> scopes = scopeRepository.findAllById(scopeIds).stream()
                .filter(scope -> scope.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Scope::getId, Function.identity()));

        boolean allSystem = scopes.values().stream()
                .anyMatch(scope -> scope.getScopeType() == ScopeType.ALL_SYSTEM);

        Set<UUID> branchIds = scopes.values().stream()
                .map(Scope::getBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Luôn giữ chi nhánh chính trong response, kể cả dữ liệu cũ chưa có assignment.
        if (account.getPrimaryBranchId() != null) {
            branchIds.add(account.getPrimaryBranchId());
        }

        List<Branch> branchEntities = allSystem
                ? branchRepository.findAll()
                : branchRepository.findAllById(branchIds);

        List<AccountBranchResponse> branches = branchEntities.stream()
                .sorted((left, right) -> {
                    boolean leftPrimary = Objects.equals(left.getId(), account.getPrimaryBranchId());
                    boolean rightPrimary = Objects.equals(right.getId(), account.getPrimaryBranchId());
                    if (leftPrimary != rightPrimary) return leftPrimary ? -1 : 1;
                    return left.getName().compareToIgnoreCase(right.getName());
                })
                .map(branch -> new AccountBranchResponse(
                        branch.getId(),
                        branch.getCode(),
                        branch.getName(),
                        Objects.equals(branch.getId(), account.getPrimaryBranchId())
                ))
                .toList();

        return accountMapper.toResponse(account, branches);
    }

    /**
     * Đồng bộ gán vai trò gắn với Scope của chi nhánh tương ứng.
     */
    private void syncRolesForBranch(Account account, UUID branchId, List<UUID> roleIds) {
        Scope scope = scopeRepository.findByScopeTypeAndBranchId(ScopeType.STORE, branchId)
                .orElseGet(() -> {
                    Scope newScope = new Scope();
                    newScope.setScopeType(ScopeType.STORE);
                    newScope.setBranchId(branchId);
                    newScope.setStatus(EntityStatus.ACTIVE);
                    return scopeRepository.save(newScope);
                });

        List<AccountRole> existingAssignments = accountRoleRepository.findByAccountId(account.getId());

        for (UUID roleId : roleIds) {
            if (!roleRepository.existsById(roleId)) {
                throw new BaseException(ErrorCode.ROLE_NOT_FOUND);
            }
            Optional<AccountRole> match = existingAssignments.stream()
                    .filter(ar -> ar.getRoleId().equals(roleId) && ar.getScopeId().equals(scope.getId()))
                    .findFirst();
            if (match.isPresent()) {
                AccountRole ar = match.get();
                if (ar.getStatus() != EntityStatus.ACTIVE) {
                    ar.setStatus(EntityStatus.ACTIVE);
                    accountRoleRepository.save(ar);
                }
            } else {
                AccountRole ar = new AccountRole();
                ar.setAccountId(account.getId());
                ar.setRoleId(roleId);
                ar.setScopeId(scope.getId());
                ar.setStatus(EntityStatus.ACTIVE);
                ar.setAssignedAt(Instant.now());
                ar.setAssignedBy(SecurityUtils.getCurrentPrincipalId().map(UUID::toString).orElse("SYSTEM"));
                accountRoleRepository.save(ar);
            }
        }
    }

    /** Lấy tài khoản theo id, ném lỗi nếu không tồn tại. */
    private Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /** Ngăn thay đổi/xóa tài khoản được bảo vệ của hệ thống (ví dụ: root admin). */
    private void verifyCanModify(Account account) {
        if (account.isSystemProtected()) {
            throw new BaseException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
    }

    /** Chỉ tài khoản nội bộ (ACCOUNT) mới được quản lý tài khoản. */
    private void assertInternalAdmin() {
        if (SecurityUtils.getCurrentPrincipalType().orElse(null) != PrincipalType.ACCOUNT) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }
}
