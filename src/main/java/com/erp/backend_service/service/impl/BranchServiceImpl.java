package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.BranchMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.BranchService;
import com.erp.core.domain.Branch;
import com.erp.core.dto.request.branch.CreateBranchRequest;
import com.erp.core.dto.request.branch.UpdateBranchRequest;
import com.erp.core.dto.response.branch.BranchResponse;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.ScopeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link BranchService}: quản lý chi nhánh, giải quyết tên chi nhánh
 * cha và lọc danh sách theo phạm vi (scope) của tài khoản đang đăng nhập.
 */
@Service
public class BranchServiceImpl implements BranchService {

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final String DEFAULT_STATUS = "ACTIVE";

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

    public BranchServiceImpl(BranchRepository branchRepository, BranchMapper branchMapper) {
        this.branchRepository = branchRepository;
        this.branchMapper = branchMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> findAll() {
        List<Branch> branches = branchRepository.findAll();
        Map<UUID, String> parentNames = resolveParentNames(branches);
        return branches.stream()
                .map(branch -> branchMapper.toResponse(branch, parentNames))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> findMine() {
        CustomUserDetails current = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        if (current.getPrincipalType() != com.erp.core.enums.PrincipalType.ACCOUNT) {
            return List.of();
        }

        List<ScopeResponse> scopes = current.getScopes();
        boolean allSystem = scopes.stream().anyMatch(s -> s.scopeType() == ScopeType.ALL_SYSTEM);
        List<Branch> branches;
        if (allSystem) {
            branches = branchRepository.findAll();
        } else {
            List<UUID> allowedIds = scopes.stream()
                    .map(ScopeResponse::branchId)
                    .filter(Objects::nonNull)
                    .toList();
            if (allowedIds.isEmpty()) {
                return List.of();
            }
            branches = branchRepository.findAllById(allowedIds);
        }
        Map<UUID, String> parentNames = resolveParentNames(branches);
        return branches.stream()
                .map(branch -> branchMapper.toResponse(branch, parentNames))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public BranchResponse findById(UUID id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        Map<UUID, String> parentNames = resolveParentNames(List.of(branch));
        return branchMapper.toResponse(branch, parentNames);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BranchResponse create(CreateBranchRequest request) {
        if (branchRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Branch branch = new Branch();
        applyRequest(branch, request.code(), request.name(), request.address(), request.phone(),
                request.email(), request.latitude(), request.longitude(), request.timezone(),
                request.supportsPickup(), request.supportsDelivery(),
                request.averagePreparationMinutes(), request.status(), request.parentId());
        return branchMapper.toResponse(branchRepository.save(branch), Map.of());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BranchResponse update(UUID id, UpdateBranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!branch.getCode().equals(request.code()) && branchRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
        if (request.parentId() != null && request.parentId().equals(id)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        validateParentExists(request.parentId());
        applyRequest(branch, request.code(), request.name(), request.address(), request.phone(),
                request.email(), request.latitude(), request.longitude(), request.timezone(),
                request.supportsPickup(), request.supportsDelivery(),
                request.averagePreparationMinutes(), request.status(), request.parentId());
        return branchMapper.toResponse(branchRepository.save(branch), Map.of());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        if (!branchRepository.existsById(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        branchRepository.deleteById(id);
    }

    /** Áp dụng dữ liệu từ request vào thực thể, dùng giá trị mặc định nếu null. */
    private void applyRequest(Branch branch, String code, String name, String address, String phone,
                              String email, java.math.BigDecimal latitude, java.math.BigDecimal longitude,
                              String timezone, Boolean supportsPickup, Boolean supportsDelivery,
                              Integer averagePreparationMinutes, String status, UUID parentId) {
        validateParentExists(parentId);
        branch.setCode(code);
        branch.setName(name);
        branch.setAddress(address);
        branch.setPhone(phone);
        branch.setEmail(email);
        branch.setLatitude(latitude);
        branch.setLongitude(longitude);
        branch.setTimezone(timezone != null ? timezone : DEFAULT_TIMEZONE);
        if (supportsPickup != null) {
            branch.setSupportsPickup(supportsPickup);
        }
        if (supportsDelivery != null) {
            branch.setSupportsDelivery(supportsDelivery);
        }
        if (averagePreparationMinutes != null) {
            branch.setAveragePreparationMinutes(averagePreparationMinutes);
        }
        branch.setStatus(status != null && !status.isBlank() ? status : DEFAULT_STATUS);
        branch.setParentId(parentId);
    }

    /** Kiểm tra chi nhánh cha tồn tại nếu được cung cấp. */
    private void validateParentExists(UUID parentId) {
        if (parentId != null && !branchRepository.existsById(parentId)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /** Xây dựng map id -> tên chi nhánh cha từ tập chi nhánh đã biết. */
    private Map<UUID, String> resolveParentNames(List<Branch> branches) {
        List<UUID> parentIds = branches.stream()
                .map(Branch::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
    }
}
