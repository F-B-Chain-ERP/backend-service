package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.backend_service.service.ScopeService;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.ScopeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ScopeServiceImpl(ScopeRepository scopeRepository) {
        this.scopeRepository = scopeRepository;
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
}
