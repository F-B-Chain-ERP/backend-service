package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.VoucherBranchMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.VoucherBranchRepository;
import com.erp.backend_service.repository.VoucherRepository;
import com.erp.backend_service.service.VoucherBranchService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.VoucherBranch;
import com.erp.core.dto.request.menu.AssignVoucherBranchRequest;
import com.erp.core.dto.response.menu.VoucherBranchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai {@link VoucherBranchService}: gán voucher cho chi nhánh.
 */
@Service
public class VoucherBranchServiceImpl implements VoucherBranchService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final VoucherRepository voucherRepository;
    private final VoucherBranchRepository voucherBranchRepository;
    private final BranchRepository branchRepository;
    private final VoucherBranchMapper voucherBranchMapper;

    public VoucherBranchServiceImpl(VoucherRepository voucherRepository,
                                    VoucherBranchRepository voucherBranchRepository,
                                    BranchRepository branchRepository,
                                    VoucherBranchMapper voucherBranchMapper) {
        this.voucherRepository = voucherRepository;
        this.voucherBranchRepository = voucherBranchRepository;
        this.branchRepository = branchRepository;
        this.voucherBranchMapper = voucherBranchMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherBranchResponse> getBranches(UUID voucherId) {
        ensureVoucherExists(voucherId);
        return toResponses(voucherBranchRepository.findByVoucherId(voucherId));
    }

    @Override
    @Transactional
    public List<VoucherBranchResponse> assign(UUID voucherId, AssignVoucherBranchRequest request) {
        ensureVoucherExists(voucherId);
        List<VoucherBranch> created = new ArrayList<>();
        for (UUID branchId : request.branchIds()) {
            if (!branchRepository.existsById(branchId)) {
                throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
            }
            if (voucherBranchRepository.existsByVoucherIdAndBranchId(voucherId, branchId)) {
                continue;
            }
            VoucherBranch mapping = new VoucherBranch();
            mapping.setStatus(STATUS_ACTIVE);
            mapping.setVoucherId(voucherId);
            mapping.setBranchId(branchId);
            created.add(mapping);
        }
        if (!created.isEmpty()) {
            voucherBranchRepository.saveAll(created);
        }
        return toResponses(voucherBranchRepository.findByVoucherId(voucherId));
    }

    @Override
    @Transactional
    public void remove(UUID voucherId, UUID branchId) {
        ensureVoucherExists(voucherId);
        VoucherBranch mapping = voucherBranchRepository.findByVoucherIdAndBranchId(voucherId, branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Voucher chưa được gán cho chi nhánh này"));
        voucherBranchRepository.delete(mapping);
    }

    private void ensureVoucherExists(UUID voucherId) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new BaseException(ErrorCode.VOUCHER_NOT_FOUND);
        }
    }

    private List<VoucherBranchResponse> toResponses(List<VoucherBranch> mappings) {
        List<UUID> branchIds = mappings.stream()
                .map(VoucherBranch::getBranchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> branchNames = branchIds.isEmpty() ? Map.of()
                : branchRepository.findAllById(branchIds).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Branch::getId, Branch::getName, (a, b) -> a));
        return mappings.stream()
                .map(m -> voucherBranchMapper.toResponse(m, branchNames.get(m.getBranchId())))
                .toList();
    }
}