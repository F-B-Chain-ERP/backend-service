package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.VoucherUsageMapper;
import com.erp.backend_service.repository.VoucherRepository;
import com.erp.backend_service.repository.VoucherUsageRepository;
import com.erp.backend_service.service.VoucherUsageService;
import com.erp.core.domain.VoucherUsage;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.VoucherUsageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Triển khai {@link VoucherUsageService}: lịch sử sử dụng voucher.
 */
@Service
public class VoucherUsageServiceImpl implements VoucherUsageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherUsageMapper voucherUsageMapper;

    public VoucherUsageServiceImpl(VoucherRepository voucherRepository,
                                   VoucherUsageRepository voucherUsageRepository,
                                   VoucherUsageMapper voucherUsageMapper) {
        this.voucherRepository = voucherRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.voucherUsageMapper = voucherUsageMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VoucherUsageResponse> listByVoucher(int page, int size, UUID voucherId) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new BaseException(ErrorCode.VOUCHER_NOT_FOUND);
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("usedAt").descending());
        Page<VoucherUsage> pageResult = voucherUsageRepository.findByVoucherId(voucherId, pageable);
        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getContent().stream().map(voucherUsageMapper::toResponse).toList());
    }
}