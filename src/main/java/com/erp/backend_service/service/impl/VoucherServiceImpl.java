package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.VoucherBranchMapper;
import com.erp.backend_service.mapper.VoucherMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.VoucherBranchRepository;
import com.erp.backend_service.repository.VoucherRepository;
import com.erp.backend_service.repository.VoucherUsageRepository;
import com.erp.backend_service.service.VoucherService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Voucher;
import com.erp.core.domain.VoucherBranch;
import com.erp.core.domain.VoucherUsage;
import com.erp.core.dto.request.menu.ApplyVoucherRequest;
import com.erp.core.dto.request.menu.CreateVoucherRequest;
import com.erp.core.dto.request.menu.UpdateVoucherRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.VoucherApplyResponse;
import com.erp.core.dto.response.menu.VoucherBranchResponse;
import com.erp.core.dto.response.menu.VoucherDetailResponse;
import com.erp.core.dto.response.menu.VoucherResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai {@link VoucherService}: quản lý voucher với kiểm tra trùng mã,
 * xóa mềm (chuyển INACTIVE) và áp dụng voucher vào đơn hàng.
 */
@Service
public class VoucherServiceImpl implements VoucherService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String DEFAULT_STATUS = "ACTIVE";

    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String STATUS_INACTIVE = "INACTIVE";

    private static final String DISCOUNT_PERCENT = "PERCENT";

    private static final String DISCOUNT_FIXED = "FIXED";

    private final VoucherRepository voucherRepository;
    private final VoucherBranchRepository voucherBranchRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final BranchRepository branchRepository;
    private final VoucherMapper voucherMapper;
    private final VoucherBranchMapper voucherBranchMapper;

    public VoucherServiceImpl(VoucherRepository voucherRepository,
                              VoucherBranchRepository voucherBranchRepository,
                              VoucherUsageRepository voucherUsageRepository,
                              BranchRepository branchRepository,
                              VoucherMapper voucherMapper,
                              VoucherBranchMapper voucherBranchMapper) {
        this.voucherRepository = voucherRepository;
        this.voucherBranchRepository = voucherBranchRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.branchRepository = branchRepository;
        this.voucherMapper = voucherMapper;
        this.voucherBranchMapper = voucherBranchMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> list(int page, int size, String search, String status, String discountType,
                                              Instant startFrom, Instant startTo, Instant endFrom, Instant endTo,
                                              UUID branchId) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());

        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : "";
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        String normalizedDiscountType = StringUtils.hasText(discountType) ? discountType.trim().toUpperCase() : null;
        Page<Voucher> pageResult = voucherRepository.search(
                normalizedSearch, normalizedStatus, normalizedDiscountType,
                startFrom, startTo, endFrom, endTo, branchId, pageable);

        List<VoucherResponse> content = pageResult.getContent().stream()
                .map(voucherMapper::toResponse)
                .toList();
        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                content);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherDetailResponse get(UUID id) {
        Voucher voucher = findById(id);
        List<VoucherBranch> branches = voucherBranchRepository.findByVoucherId(id);
        Map<UUID, String> branchNames = resolveBranchNames(
                branches.stream().map(VoucherBranch::getBranchId).filter(Objects::nonNull).distinct().toList());
        List<VoucherBranchResponse> branchResponses = branches.stream()
                .map(b -> voucherBranchMapper.toResponse(b, branchNames.get(b.getBranchId())))
                .toList();
        return new VoucherDetailResponse(voucherMapper.toResponse(voucher), branchResponses);
    }

    @Override
    @Transactional
    public VoucherResponse create(CreateVoucherRequest request) {
        String code = request.code().trim().toUpperCase();
        if (voucherRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.VOUCHER_CODE_EXISTED);
        }
        Voucher voucher = new Voucher();
        apply(voucher, code, request.name(), request.description(), request.discountType(),
                request.discountValue(), request.maxDiscountAmount(), request.minOrderAmount(),
                request.usageLimit(), request.usageLimitPerCustomer(), request.startAt(), request.endAt(),
                request.status());
        return voucherMapper.toResponse(voucherRepository.save(voucher));
    }

    @Override
    @Transactional
    public VoucherResponse update(UUID id, UpdateVoucherRequest request) {
        Voucher voucher = findById(id);
        String code = request.code().trim().toUpperCase();
        if (!voucher.getCode().equals(code) && voucherRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.VOUCHER_CODE_EXISTED);
        }
        apply(voucher, code, request.name(), request.description(), request.discountType(),
                request.discountValue(), request.maxDiscountAmount(), request.minOrderAmount(),
                request.usageLimit(), request.usageLimitPerCustomer(), request.startAt(), request.endAt(),
                request.status());
        return voucherMapper.toResponse(voucherRepository.save(voucher));
    }

    @Override
    @Transactional
    public VoucherResponse updateStatus(UUID id, String status) {
        Voucher voucher = findById(id);
        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        String normalizedStatus = status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(normalizedStatus) && !STATUS_INACTIVE.equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        voucher.setStatus(normalizedStatus);
        return voucherMapper.toResponse(voucherRepository.save(voucher));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Voucher voucher = findById(id);
        if (STATUS_ACTIVE.equals(voucher.getStatus())) {
            voucher.setStatus(STATUS_INACTIVE);
            voucherRepository.save(voucher);
        }
    }

    @Override
    @Transactional
    public VoucherApplyResponse apply(ApplyVoucherRequest request) {
        Voucher voucher = voucherRepository.findByIdForUpdate(request.voucherId())
                .orElseThrow(() -> new BaseException(ErrorCode.VOUCHER_NOT_FOUND));

        // Idempotency: đơn hàng đã dùng voucher rồi thì trả về kết quả cũ, không ghi usage lần 2.
        Optional<VoucherUsage> existing = voucherUsageRepository.findByVoucherIdAndOrderId(
                request.voucherId(), request.orderId());
        if (existing.isPresent()) {
            VoucherUsage usage = existing.get();
            return new VoucherApplyResponse(
                    voucher.getId().toString(),
                    request.orderId().toString(),
                    usage.getDiscountAmount(),
                    request.orderAmount().subtract(usage.getDiscountAmount()));
        }

        if (!STATUS_ACTIVE.equals(voucher.getStatus())) {
            throw new BaseException(ErrorCode.VOUCHER_NOT_APPLICABLE, "Voucher không còn hoạt động");
        }
        Instant now = Instant.now();
        if (now.isBefore(voucher.getStartAt()) || now.isAfter(voucher.getEndAt())) {
            throw new BaseException(ErrorCode.VOUCHER_EXPIRED);
        }
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không tồn tại"));
        if (!STATUS_ACTIVE.equals(branch.getStatus())) {
            throw new BaseException(ErrorCode.VOUCHER_NOT_APPLICABLE, "Chi nhánh đang ngừng hoạt động");
        }
        // Voucher không có bản ghi gán cho chi nhánh nào thì không áp dụng được ở bất kỳ chi nhánh nào.
        if (!voucherBranchRepository.existsByVoucherIdAndBranchIdAndStatus(
                request.voucherId(), request.branchId(), STATUS_ACTIVE)) {
            throw new BaseException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }
        if (request.orderAmount().compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new BaseException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                    "Đơn hàng chưa đạt giá trị tối thiểu để sử dụng voucher");
        }
        if (request.customerId() != null && voucher.getUsageLimitPerCustomer() != null) {
            long usedByCustomer = voucherUsageRepository.countByVoucherIdAndCustomerId(
                    request.voucherId(), request.customerId());
            if (usedByCustomer >= voucher.getUsageLimitPerCustomer()) {
                throw new BaseException(ErrorCode.USAGE_LIMIT_EXCEEDED, "Khách hàng đã đạt giới hạn sử dụng voucher");
            }
        }
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BaseException(ErrorCode.USAGE_LIMIT_EXCEEDED);
        }

        BigDecimal discount = computeDiscount(voucher, request.orderAmount());

        VoucherUsage usage = new VoucherUsage();
        usage.setStatus(STATUS_ACTIVE);
        usage.setVoucherId(voucher.getId());
        usage.setOrderId(request.orderId());
        usage.setCustomerId(request.customerId());
        usage.setDiscountAmount(discount);
        usage.setUsedAt(Instant.now());
        voucherUsageRepository.save(usage);

        voucher.setUsedCount(voucher.getUsedCount() + 1);
        voucherRepository.save(voucher);

        return new VoucherApplyResponse(
                voucher.getId().toString(),
                request.orderId().toString(),
                discount,
                request.orderAmount().subtract(discount));
    }

    private Voucher findById(UUID id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    private void apply(Voucher voucher, String code, String name, String description, String discountType,
                       BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
                       Integer usageLimit, Integer usageLimitPerCustomer, Instant startAt, Instant endAt,
                       String status) {
        validateDiscountConfig(discountType, discountValue, maxDiscountAmount);
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BaseException(ErrorCode.INVALID_DATE);
        }
        if (discountType == null
                || (!DISCOUNT_PERCENT.equals(discountType) && !DISCOUNT_FIXED.equals(discountType))) {
            throw new BaseException(ErrorCode.INVALID_PRICE, "Loại giảm giá không hợp lệ");
        }
        voucher.setCode(code);
        voucher.setName(name.trim());
        voucher.setDescription(trimToNull(description));
        voucher.setDiscountType(discountType);
        voucher.setDiscountValue(discountValue);
        voucher.setMaxDiscountAmount(maxDiscountAmount);
        voucher.setMinOrderAmount(minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO);
        voucher.setUsageLimit(usageLimit);
        voucher.setUsageLimitPerCustomer(usageLimitPerCustomer);
        voucher.setStartAt(startAt);
        voucher.setEndAt(endAt);
        voucher.setStatus(status != null && !status.isBlank() ? status.trim().toUpperCase() : DEFAULT_STATUS);
    }

    private void validateDiscountConfig(String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount) {
        if (discountValue == null) {
            throw new BaseException(ErrorCode.INVALID_PRICE);
        }
        if (DISCOUNT_PERCENT.equals(discountType)) {
            if (discountValue.compareTo(BigDecimal.ZERO) <= 0
                    || discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BaseException(ErrorCode.INVALID_PRICE,
                        "Loại PERCENT yêu cầu giá trị giảm trong khoảng 1 - 100");
            }
            if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BaseException(ErrorCode.INVALID_PRICE, "Số tiền giảm tối đa không được âm");
            }
        } else {
            if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BaseException(ErrorCode.INVALID_PRICE, "Loại FIXED yêu cầu giá trị giảm lớn hơn 0");
            }
        }
    }

    private BigDecimal computeDiscount(Voucher voucher, BigDecimal orderAmount) {
        BigDecimal discount;
        if (DISCOUNT_PERCENT.equals(voucher.getDiscountType())) {
            discount = orderAmount.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null
                    && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            discount = voucher.getDiscountValue().min(orderAmount);
        }
        return discount;
    }

    private Map<UUID, String> resolveBranchNames(List<UUID> branchIds) {
        if (branchIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(branchIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (a, b) -> a));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}