package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.backend_service.service.ScopeService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.ScopeAdminResponse;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.dto.request.scope.CreateScopeRequest;
import com.erp.core.dto.request.scope.UpdateScopeRequest;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.ScopeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link ScopeService}: truy vấn/lưu phạm vi trên cơ sở dữ liệu,
 * tự động tạo phạm vi khi cần và kiểm tra bao phủ chi nhánh.
 */
@Service
public class ScopeServiceImpl implements ScopeService {
    private final ScopeRepository scopeRepository;
    private final BranchRepository branchRepository;
    private final AccountRoleRepository accountRoleRepository;

    public ScopeServiceImpl(ScopeRepository scopeRepository, BranchRepository branchRepository,
            AccountRoleRepository accountRoleRepository) {
        this.scopeRepository = scopeRepository;
        this.branchRepository = branchRepository;
        this.accountRoleRepository = accountRoleRepository;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Scope getActive(UUID scopeId) {
        return scopeRepository.findById(scopeId)
                .filter(scope -> scope.getStatus() == EntityStatus.ACTIVE)
                .orElseThrow(() -> new BaseException(ErrorCode.SCOPE_NOT_FOUND));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Scope> findAllById(Iterable<UUID> scopeIds) {
        return scopeRepository.findAllById(scopeIds).stream()
                .filter(scope -> scope.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Scope::getId, Function.identity()));
    }
    /** {@inheritDoc} */
    @Override
    public boolean covers(ScopeResponse scope, UUID branchId) {
        if (scope == null) {
            return false;
        }
        return scope.scopeType() == ScopeType.ALL_SYSTEM || Objects.equals(scope.branchId(), branchId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ScopeAdminResponse> findAll() {
        List<Scope> scopes = scopeRepository.findAll();
        Map<UUID, String> branchNames = resolveBranchNames(
                scopes.stream().map(Scope::getBranchId).filter(Objects::nonNull).distinct().toList());
        return scopes.stream()
                .map(scope -> toResponse(scope, branchNames))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ScopeAdminResponse getById(UUID id) {
        Scope scope = scopeRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SCOPE_NOT_FOUND));
        return toResponse(scope, resolveBranchNames(
                scope.getBranchId() != null ? List.of(scope.getBranchId()) : List.of()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ScopeAdminResponse create(CreateScopeRequest request) {
        validateBranchRequirement(request.scopeType(), request.branchId());
        validateBranchExists(request.branchId());
        ensureNotDuplicated(request.scopeType(), request.branchId(), null);

        Scope scope = new Scope();
        applyRequest(scope, request.scopeType(), request.branchId(),
                request.status() != null ? request.status() : EntityStatus.ACTIVE);
        return toResponse(saveSafely(scope), resolveBranchNames(
                scope.getBranchId() != null ? List.of(scope.getBranchId()) : List.of()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ScopeAdminResponse update(UUID id, UpdateScopeRequest request) {
        Scope scope = scopeRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SCOPE_NOT_FOUND));

        ScopeType targetType = request.scopeType() != null ? request.scopeType() : scope.getScopeType();
        UUID targetBranchId = request.branchId() != null ? request.branchId() : scope.getBranchId();
        validateBranchRequirement(targetType, targetBranchId);
        validateBranchExists(request.branchId());
        ensureNotDuplicated(targetType, targetBranchId, id);

        applyRequest(scope, targetType, targetBranchId,
                request.status() != null ? request.status() : scope.getStatus());
        return toResponse(saveSafely(scope), resolveBranchNames(
                scope.getBranchId() != null ? List.of(scope.getBranchId()) : List.of()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        if (!scopeRepository.existsById(id)) {
            throw new BaseException(ErrorCode.SCOPE_NOT_FOUND);
        }
        if (accountRoleRepository.existsByScopeId(id)) {
            throw new BaseException(ErrorCode.SCOPE_IN_USE);
        }
        scopeRepository.deleteById(id);
    }

    /** Áp dụng dữ liệu từ request vào thực thể phạm vi. */
    private void applyRequest(Scope scope, ScopeType scopeType, UUID branchId, EntityStatus status) {
        scope.setScopeType(scopeType);
        scope.setBranchId(ScopeType.ALL_SYSTEM.equals(scopeType) ? null : branchId);
        scope.setStatus(status != null ? status : EntityStatus.ACTIVE);
    }

    /** ALL_SYSTEM không gắn chi nhánh; các loại khác bắt buộc phải có chi nhánh. */
    private void validateBranchRequirement(ScopeType scopeType, UUID branchId) {
        if (scopeType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST);
        }
        if (ScopeType.ALL_SYSTEM.equals(scopeType) && branchId != null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        if (!ScopeType.ALL_SYSTEM.equals(scopeType) && branchId == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** Kiểm tra chi nhánh tồn tại nếu phạm vi được gắn vào chi nhánh. */
    private void validateBranchExists(UUID branchId) {
        if (branchId != null && !branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /** Chống tạo trùng cặp (loại phạm vi + chi nhánh). */
    private void ensureNotDuplicated(ScopeType scopeType, UUID branchId, UUID excludeId) {
        scopeRepository.findByScopeTypeAndBranchId(scopeType, branchId)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
                });
    }

    /** Lưu phạm vi, chuyển lỗi ràng buộc CSDL thành lỗi nghiệp vụ. */
    private Scope saveSafely(Scope scope) {
        try {
            return scopeRepository.save(scope);
        } catch (DataIntegrityViolationException ex) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    /** Xây dựng map id -&gt; tên chi nhánh từ danh sách id cho trước. */
    private Map<UUID, String> resolveBranchNames(List<UUID> branchIds) {
        if (branchIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
    }

    /** Chuyển đổi thực thể sang response kèm tên chi nhánh (nếu có). */
    private ScopeAdminResponse toResponse(Scope scope, Map<UUID, String> branchNames) {
        return new ScopeAdminResponse(
                scope.getId(),
                scope.getScopeType(),
                scope.getBranchId(),
                scope.getBranchId() != null ? branchNames.get(scope.getBranchId()) : null,
                scope.getStatus()
        );
    }
}
