package com.erp.backend_service.service.impl;

import com.erp.backend_service.util.audit.AuditAction;
import com.erp.backend_service.util.audit.AuditEvent;
import com.erp.backend_service.util.audit.AuditModule;
import com.erp.backend_service.util.audit.AuditTargetType;
import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.PermissionMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.PermissionRepository;
import com.erp.backend_service.repository.RolePermissionRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.PermissionSnapshot;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.service.PermissionService;
import com.erp.backend_service.service.ScopeService;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Permission;
import com.erp.core.domain.Role;
import com.erp.core.domain.RolePermission;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.PermissionResponse;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.ScopeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link PermissionService}: tính toán quyền của tài khoản dựa trên
 * vai trò được gán và phạm vi (scope) tương ứng, đồng thời ghi log khi bị từ chối.
 */
@Service("permissionService")
public class PermissionServiceImpl implements PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(15);
    private static final String SECTION_SEPARATOR = "\n---\n";
    private static final String ITEM_SEPARATOR = ",";
    private static final String SCOPE_FIELD_SEPARATOR = "\\|";
    private static final String SCOPE_VALUE_SEPARATOR = "|";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final PermissionMapper permissionMapper;
    private final StringRedisTemplate redisTemplate;

    public PermissionServiceImpl(AccountRepository accountRepository,
                                  AccountRoleRepository accountRoleRepository,
                                  RolePermissionRepository rolePermissionRepository,
                                  RoleRepository roleRepository,
                                  PermissionRepository permissionRepository,
                                  ScopeService scopeService,
                                  AuditService auditService,
                                  PermissionMapper permissionMapper,
                                  StringRedisTemplate redisTemplate) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.permissionMapper = permissionMapper;
        this.redisTemplate = redisTemplate;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID accountId, String permissionCode) {
        if (!isActive(accountId)) {
            return false;
        }
        return getSnapshot(accountId).permissions().contains(permissionCode);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean isAllowed(UUID accountId, String permissionCode, UUID branchId) {
        Objects.requireNonNull(branchId, "branchId must not be null");
        if (!isActive(accountId)) {
            return false;
        }
        PermissionSnapshot snapshot = getSnapshot(accountId);
        return snapshot.permissions().contains(permissionCode) && snapshot.scopes().stream()
                .anyMatch(scope -> scopeService.covers(scope, branchId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PermissionSnapshot getSnapshot(UUID accountId) {
        PermissionSnapshot cached = readSnapshot(accountId);
        if (cached != null) {
            return cached;
        }
        PermissionSnapshot snapshot = toSnapshot(readGrants(accountId));
        saveSnapshot(accountId, snapshot);
        return snapshot;
    }

    /** {@inheritDoc} */
    @Override
    public void saveSnapshot(UUID accountId, PermissionSnapshot snapshot) {
        if (accountId == null || snapshot == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(RedisKeys.permissionSnapshot(accountId), serialize(snapshot), SNAPSHOT_TTL);
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable while saving permission snapshot: {}", exception.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void evictSnapshot(UUID accountId) {
        if (accountId == null) {
            return;
        }
        try {
            redisTemplate.delete(RedisKeys.permissionSnapshot(accountId));
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable while evicting permission snapshot: {}", exception.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public PermissionSnapshot snapshotFromDetails(CustomUserDetails details) {
        if (details == null) {
            return emptySnapshot();
        }
        return new PermissionSnapshot(details.getRoles(), details.getPermissions(), details.getScopes());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getById(UUID id) {
        return permissionMapper.toResponse(getExisting(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> getAll(int page, int size, String search, String module, EntityStatus status) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Permission> result = permissionRepository.search(
                normalizeSearch(search), normalizeModuleFilter(module), status, pageable);
        return new PageResponse<>(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream().map(permissionMapper::toResponse).toList()
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<String> getModules() {
        return permissionRepository.findDistinctModules(null);
    }

    // ==================== Hàm phụ cho quản trị danh mục quyền ====================

    /** Lấy quyền theo id, ném PERMISSION_NOT_FOUND nếu không tồn tại. */
    private Permission getExisting(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.PERMISSION_NOT_FOUND));
    }

    /** Từ khoá tìm kiếm: trim, rỗng thì trả null (bỏ qua tiêu chí). */
    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    /** Bộ lọc module: trim và về chữ hoa để khớp dữ liệu seed (SYS/POS/...), rỗng thì trả null. */
    private String normalizeModuleFilter(String module) {
        if (module == null || module.isBlank()) {
            return null;
        }
        return module.trim().toUpperCase();
    }

    /** Giới hạn kích thước trang trong [1, 100], mặc định 10 khi không hợp lệ. */
    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    /** Trim chuỗi, chuỗi rỗng trả về null (cột description được phép null). */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Đọc và tính toán các quyền (kèm phạm vi) hiện có của tài khoản từ
     * bản ghi gán vai trò, ánh xạ vai trò-quyền và phạm vi đang active.
     */
    private List<Grant> readGrants(UUID accountId) {
        List<AccountRole> assignments = accountRoleRepository
                .findEffectiveByAccountId(accountId, EntityStatus.ACTIVE, Instant.now());
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<UUID, Role> roles = roleRepository.findAllById(
                        assignments.stream().map(AccountRole::getRoleId).distinct().toList()
                ).stream().filter(role -> role.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        List<RolePermission> mappings = rolePermissionRepository.findByRoleIdIn(
                roles.keySet());
        Map<UUID, Permission> permissions = permissionRepository.findAllById(
                        mappings.stream().map(RolePermission::getPermissionId).distinct().toList()
                ).stream().filter(permission -> permission.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        Map<UUID, Scope> scopes = scopeService.findAllById(assignments.stream().map(AccountRole::getScopeId).distinct().toList());

        return assignments.stream()
                .filter(assignment -> roles.containsKey(assignment.getRoleId()))
                .flatMap(assignment -> mappings.stream()
                        .filter(mapping -> mapping.getRoleId().equals(assignment.getRoleId()))
                        .map(RolePermission::getPermissionId)
                        .filter(permissions::containsKey)
                        .map(permissionId -> new Grant(roles.get(assignment.getRoleId()).getCode(),
                                permissions.get(permissionId).getCode(), scopes.get(assignment.getScopeId()))))
                .filter(grant -> grant.scope() != null)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public void requirePermission(String permissionCode) {
        UUID accountId = currentAccountId();
        if (!hasPermission(accountId, permissionCode)) {
            auditDenied(accountId, AuditTargetType.PERMISSION, null, Map.of("permissionCode", permissionCode));
            throw new BaseException(ErrorCode.PERMISSION_DENIED);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void requireAccess(String permissionCode, UUID branchId) {
        UUID accountId = currentAccountId();
        if (!isAllowed(accountId, permissionCode, branchId)) {
            auditDenied(accountId, AuditTargetType.SCOPE, branchId,
                    Map.of("permissionCode", permissionCode, "branchId", branchId.toString()));
            throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
        }
    }

    /** Kiểm tra tài khoản có tồn tại và đang ở trạng thái active hay không. */
    private boolean isActive(UUID accountId) {
        return accountId != null && accountRepository.findById(accountId)
                .map(account -> account.getStatus() == EntityStatus.ACTIVE)
                .orElse(false);
    }

    /** Lấy accountId của người dùng hiện tại, ném lỗi nếu chưa xác thực. */
    private UUID currentAccountId() {
        return SecurityUtils.getCurrentAccountId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
    }

    /**
     * Ghi nhận một sự kiện bị từ chối truy cập vào audit log.
     */
    private void auditDenied(UUID accountId, AuditTargetType targetType,
                              UUID targetId, Map<String, Object> details) {
        auditService.record(new AuditEvent(accountId, AuditAction.ACCESS_DENIED,
                AuditModule.SYS, targetType, targetId, details));
    }

    /** Đọc ảnh chụp quyền hạn từ cache, trả về snapshot rỗng nếu null hoặc lỗi Redis. */
    private PermissionSnapshot readSnapshot(UUID accountId) {
        if (accountId == null) {
            return emptySnapshot();
        }
        try {
            String value = redisTemplate.opsForValue().get(RedisKeys.permissionSnapshot(accountId));
            if (value == null) {
                return null;
            }
            return deserialize(value);
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable while reading permission snapshot: {}", exception.getMessage());
            return null;
        }
    }

    /** Chuyển danh sách grant thành ảnh chụp quyền hạn (vai trò, quyền, phạm vi). */
    private PermissionSnapshot toSnapshot(List<Grant> grants) {
        return new PermissionSnapshot(
                grants.stream().map(Grant::roleCode).distinct().map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role).toList(),
                grants.stream().map(Grant::permissionCode).distinct().toList(),
                grants.stream().map(Grant::scope).map(scope -> new ScopeResponse(scope.getId(), scope.getScopeType(), scope.getBranchId())).distinct().toList()
        );
    }

    /** Chuyển ảnh chụp quyền hạn thành chuỗi lưu trên Redis (roles|permissions|scopes). */
    private String serialize(PermissionSnapshot snapshot) {
        return String.join(SECTION_SEPARATOR,
                String.join(ITEM_SEPARATOR, snapshot.roles()),
                String.join(ITEM_SEPARATOR, snapshot.permissions()),
                snapshot.scopes().stream().map(this::serializeScope).collect(Collectors.joining(ITEM_SEPARATOR))
        );
    }

    /** Khôi phục ảnh chụp quyền hạn từ chuỗi đã lưu trên Redis. */
    private PermissionSnapshot deserialize(String value) {
        String[] sections = value.split(SECTION_SEPARATOR, -1);
        if (sections.length != 3) {
            return emptySnapshot();
        }
        return new PermissionSnapshot(splitItems(sections[0]), splitItems(sections[1]), splitItems(sections[2]).stream()
                .map(this::deserializeScope)
                .filter(Objects::nonNull)
                .toList());
    }

    /** Tách chuỗi thành danh sách mục, bỏ qua các phần trống. */
    private List<String> splitItems(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(ITEM_SEPARATOR))
                .filter(item -> !item.isBlank())
                .toList();
    }

    /** Chuyển một phạm vi thành chuỗi (id|scopeType|branchId). */
    private String serializeScope(ScopeResponse scope) {
        String branchIdValue;
        if (scope.branchId() == null) {
            branchIdValue = "";
        } else {
            branchIdValue = scope.branchId().toString();
        }
        return String.join(SCOPE_VALUE_SEPARATOR,
                scope.id().toString(),
                scope.scopeType().name(),
                branchIdValue);
    }

    /** Khôi phục một phạm vi từ chuỗi, trả về {@code null} nếu chuỗi không hợp lệ. */
    private ScopeResponse deserializeScope(String value) {
        try {
            String[] parts = value.split(SCOPE_FIELD_SEPARATOR, -1);
            UUID branchId;
            if (parts[2].isBlank()) {
                branchId = null;
            } else {
                branchId = UUID.fromString(parts[2]);
            }
            return new ScopeResponse(UUID.fromString(parts[0]), ScopeType.valueOf(parts[1]), branchId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Tạo ảnh chụp quyền hạn rỗng (không vai trò/quyền/phạm vi). */
    private PermissionSnapshot emptySnapshot() {
        return new PermissionSnapshot(List.of(), List.of(), List.of());
    }

    /** Bản ghi tạm chứa một cặp (vai trò, quyền) kèm phạm vi tương ứng. */
    private record Grant(String roleCode, String permissionCode, Scope scope) {
    }
}
