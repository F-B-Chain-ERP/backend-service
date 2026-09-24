package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.StoreDailyReportMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.StoreDailyReportRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.StoreDailyReportService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Role;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.domain.ShiftReport;
import com.erp.core.domain.StoreDailyReport;
import com.erp.core.dto.request.store.CreateDailyReportRequest;
import com.erp.core.dto.request.store.UpdateDailyReportRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import com.erp.core.enums.EntityStatus;
import com.erp.core.dto.request.store.CreateDailyReportRequest;
import com.erp.core.dto.request.store.UpdateDailyReportRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class StoreDailyReportServiceImpl implements StoreDailyReportService {

    private final StoreDailyReportRepository storeDailyReportRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final BranchRepository branchRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final StoreDailyReportMapper storeDailyReportMapper;
    private final DataScopeHelper dataScopeHelper;

    private static final List<String> CASH_ROLE_CODES = List.of("ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    private static final List<String> MANAGER_ROLE_CODES = List.of("ADMIN", "ROLE_MANAGER");

    private boolean isManagerOrAdmin(UUID accountId, UUID branchId) {
        if (accountId == null || branchId == null) {
            return false;
        }
        return !accountRoleRepository.findEffectiveAccountIdsByRoleCodesAndBranchId(
                List.of(accountId), MANAGER_ROLE_CODES, branchId, EntityStatus.ACTIVE, Instant.now()).isEmpty();
    }

    public StoreDailyReportServiceImpl(StoreDailyReportRepository storeDailyReportRepository,
                                        ShiftAssignmentRepository shiftAssignmentRepository,
                                        ShiftReportRepository shiftReportRepository,
                                        BranchRepository branchRepository,
                                        AccountRepository accountRepository,
                                        AccountRoleRepository accountRoleRepository,
                                        RoleRepository roleRepository,
                                        StoreDailyReportMapper storeDailyReportMapper,
                                        DataScopeHelper dataScopeHelper) {
        this.storeDailyReportRepository = storeDailyReportRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.branchRepository = branchRepository;
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.roleRepository = roleRepository;
        this.storeDailyReportMapper = storeDailyReportMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    // Chỉ ca thu ngân chưa xong mới chặn khóa sổ (pha chế điểm danh không ảnh hưởng).
    private boolean hasOpenCashShift(UUID branchId, LocalDate businessDate) {
        List<ShiftAssignment> open = shiftAssignmentRepository.findByBranchIdAndWorkDateAndStatusIn(
                branchId, businessDate, List.of("SCHEDULED", "CHECKED_IN"));
        if (open.isEmpty()) {
            return false;
        }
        Set<UUID> cashRoleIds = roleRepository.findByCodeIn(CASH_ROLE_CODES).stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
        if (cashRoleIds.isEmpty()) {
            return !open.isEmpty();
        }
        Set<UUID> accountIds = open.stream().map(ShiftAssignment::getAccountId).collect(Collectors.toSet());
        Set<UUID> cashAccounts = new java.util.HashSet<>(accountRoleRepository
                .findEffectiveByAccountIdIn(accountIds, EntityStatus.ACTIVE, Instant.now())
                .stream()
                .filter(ar -> cashRoleIds.contains(ar.getRoleId()))
                .map(com.erp.core.domain.AccountRole::getAccountId)
                .toList());
        return open.stream().anyMatch(a -> cashAccounts.contains(a.getAccountId()));
    }

    // Liệt kê ca két đang kẹt để message lỗi chỉ rõ cách xử lý (không đổi DB).
    private String describeOpenCashShifts(UUID branchId, LocalDate businessDate) {
        try {
            List<ShiftAssignment> open = shiftAssignmentRepository.findByBranchIdAndWorkDateAndStatusIn(
                    branchId, businessDate, List.of("SCHEDULED", "CHECKED_IN"));
            if (open.isEmpty()) {
                return "vẫn còn ca chưa hoàn tất";
            }
            var accountIds = open.stream().map(ShiftAssignment::getAccountId).distinct().toList();
            Map<UUID, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                    .collect(Collectors.toMap(Account::getId, java.util.function.Function.identity(), (a, b) -> a));
            return open.stream().limit(5)
                    .map(a -> {
                        Account acc = accountMap.get(a.getAccountId());
                        String who = acc != null && acc.getFullName() != null ? acc.getFullName() : a.getAccountId().toString();
                        String hint = "SCHEDULED".equalsIgnoreCase(a.getStatus()) ? "chưa mở -> Hủy nếu quá hạn" : "đang mở -> Đóng ca";
                        return who + " [" + a.getStatus() + ", " + hint + "]";
                    })
                    .collect(Collectors.joining("; "));
        } catch (Exception ignored) {
            return "vẫn còn ca chưa hoàn tất";
        }
    }

    @Override
    public StoreDailyReportResponse generateDailyReport(CreateDailyReportRequest request, UUID currentUserId) {
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Quy tắc BR-STORE-07: Không được chốt ngày khi vẫn còn ca thu ngân
        // đang chạy hoặc chưa hoàn tất (pha chế điểm danh không tính).
        if (hasOpenCashShift(request.branchId(), request.businessDate())) {
            throw new BaseException(ErrorCode.STORE_400_ACTIVE_SHIFTS_REMAINING,
                    "Còn ca két chưa xong ngày " + request.businessDate() + ": "
                            + describeOpenCashShifts(request.branchId(), request.businessDate()) + ". "
                            + "Hủy ca quá hạn chưa mở, hoặc Đóng ca đang mở rồi chốt lại");
        }

        // Lấy hoặc khởi tạo báo cáo
        StoreDailyReport report = storeDailyReportRepository
                .findByBranchIdAndBusinessDate(request.branchId(), request.businessDate())
                .orElse(new StoreDailyReport());

        // Ngày đã khóa sổ thì không được tổng hợp đè.
        if ("RECONCILED".equalsIgnoreCase(report.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        report.setBranchId(request.branchId());
        report.setBusinessDate(request.businessDate());

        // Tổng hợp từ các ca ĐÃ DUYỆT trong ngày (loại SUBMITTED/REJECTED).
        // NOTE: thẻ/ví giữ chỗ (POS chưa có thanh toán thẻ, đang 0), sau tự vào.
        List<ShiftReport> shiftReports = shiftReportRepository.findByBranchIdAndBusinessDate(request.branchId(), request.businessDate())
                .stream()
                .filter(sr -> "CONFIRMED".equalsIgnoreCase(sr.getStatus()))
                .toList();

        int totalOrders = 0;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal netRevenue = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal transferAmount = BigDecimal.ZERO;
        BigDecimal payoutAmount = BigDecimal.ZERO;

        for (ShiftReport sr : shiftReports) {
            totalOrders += sr.getOrdersCount() != null ? sr.getOrdersCount() : 0;
            grossRevenue = grossRevenue.add(sr.getTotalSales() != null ? sr.getTotalSales() : BigDecimal.ZERO);
            netRevenue = netRevenue.add(sr.getTotalSales() != null ? sr.getTotalSales() : BigDecimal.ZERO);
            cashAmount = cashAmount.add(sr.getCashSales() != null ? sr.getCashSales() : BigDecimal.ZERO);
            transferAmount = transferAmount
                    .add(sr.getBankTransferSales() != null ? sr.getBankTransferSales() : BigDecimal.ZERO)
                    .add(sr.getCardSales() != null ? sr.getCardSales() : BigDecimal.ZERO)
                    .add(sr.getEwalletSales() != null ? sr.getEwalletSales() : BigDecimal.ZERO);
            payoutAmount = payoutAmount.add(sr.getCashPayout() != null ? sr.getCashPayout() : BigDecimal.ZERO);
        }

        // Quỹ mở nối từ chốt hôm trước; nhập tay chỉ khi cần chỉnh.
        BigDecimal openingCash = request.openingCash() != null ? request.openingCash() : previousClosingCash(request.branchId(), request.businessDate());
        BigDecimal closingCash = openingCash.add(cashAmount).subtract(payoutAmount);

        report.setOpeningCash(openingCash);
        report.setClosingCash(closingCash);
        report.setTotalOrders(totalOrders);
        report.setGrossRevenue(grossRevenue);
        // NOTE(P3-later): chưa có dữ liệu chiết khấu thật ở POS nên giữ 0.
        report.setDiscountAmount(BigDecimal.ZERO);
        report.setNetRevenue(netRevenue);
        report.setCashAmount(cashAmount);
        report.setTransferAmount(transferAmount);
        report.setStatus("OPEN");
        report.setSubmittedById(currentUserId);
        report.setSubmittedAt(Instant.now());

        StoreDailyReport saved = storeDailyReportRepository.save(report);
        Account submitter = accountRepository.findById(currentUserId).orElse(null);

        return storeDailyReportMapper.toResponse(saved, submitter, payoutAmount);
    }

    private BigDecimal previousClosingCash(UUID branchId, LocalDate businessDate) {
        if (branchId == null || businessDate == null) {
            return BigDecimal.ZERO;
        }
        return storeDailyReportRepository.findByBranchIdAndBusinessDate(branchId, businessDate.minusDays(1))
                .map(prev -> prev.getClosingCash() != null ? prev.getClosingCash() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal payoutOf(UUID branchId, LocalDate businessDate) {
        if (branchId == null || businessDate == null) {
            return BigDecimal.ZERO;
        }
        return shiftReportRepository.findByBranchIdAndBusinessDate(branchId, businessDate)
                .stream()
                .filter(sr -> "CONFIRMED".equalsIgnoreCase(sr.getStatus()))
                .map(sr -> sr.getCashPayout() != null ? sr.getCashPayout() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public StoreDailyReportResponse updateDailyReport(UUID id, UpdateDailyReportRequest request) {
        StoreDailyReport report = storeDailyReportRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        // Chặn sửa tay ngày đã khóa sổ. NOTE: entity chưa có cột người duyệt,
        // nên approve chỉ đổi trạng thái; thông tin người duyệt xem ở audit log.
        if ("RECONCILED".equalsIgnoreCase(report.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        if (request.openingCash() != null) {
            report.setOpeningCash(request.openingCash());
        }
        if (request.closingCash() != null) {
            report.setClosingCash(request.closingCash());
        }
        if (request.status() != null && !request.status().isBlank()) {
            // Khóa sổ chỉ qua approve (có recompute + check quyền), update tay
            // chỉ được chuyển OPEN <-> SUBMITTED.
            String next = request.status().trim().toUpperCase();
            if ("RECONCILED".equals(next)) {
                throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION,
                        "Khóa sổ phải dùng Phê duyệt, không đổi trạng thái tay");
            }
            if (!"OPEN".equals(next) && !"SUBMITTED".equals(next)) {
                throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
            }
            report.setStatus(next);
        }

        StoreDailyReport saved = storeDailyReportRepository.save(report);
        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(saved, submitter, payoutOf(report.getBranchId(), report.getBusinessDate()));
    }

    @Override
    public StoreDailyReportResponse approveDailyReport(UUID id, UUID approverId, String note) {
        StoreDailyReport report = storeDailyReportRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        // Khóa sổ phải là quản lý/admin, và không được tự duyệt báo cáo mình lập.
        if (!isManagerOrAdmin(approverId, report.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED,
                    "Chỉ quản lý mới được phê duyệt khóa sổ ngày");
        }
        if (approverId != null && approverId.equals(report.getSubmittedById())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED,
                    "Người lập không được tự khóa sổ, cần quản lý khác phê duyệt");
        }

        // Khóa sổ ngày kinh doanh — chỉ khóa 1 lần.
        if ("RECONCILED".equalsIgnoreCase(report.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        // Chốt lại số từ các ca ĐÃ DUYỆT mới nhất trước khi khóa, tránh khóa
        // số cũ rồi két duyệt thêm gây lệch âm thầm (không thêm cột mới).
        List<ShiftReport> confirmed = shiftReportRepository
                .findByBranchIdAndBusinessDate(report.getBranchId(), report.getBusinessDate())
                .stream()
                .filter(sr -> "CONFIRMED".equalsIgnoreCase(sr.getStatus()))
                .toList();
        int totalOrders = 0;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal transferAmount = BigDecimal.ZERO;
        BigDecimal payoutAmount = BigDecimal.ZERO;
        for (ShiftReport sr : confirmed) {
            totalOrders += sr.getOrdersCount() != null ? sr.getOrdersCount() : 0;
            grossRevenue = grossRevenue.add(sr.getTotalSales() != null ? sr.getTotalSales() : BigDecimal.ZERO);
            cashAmount = cashAmount.add(sr.getCashSales() != null ? sr.getCashSales() : BigDecimal.ZERO);
            transferAmount = transferAmount
                    .add(sr.getBankTransferSales() != null ? sr.getBankTransferSales() : BigDecimal.ZERO)
                    .add(sr.getCardSales() != null ? sr.getCardSales() : BigDecimal.ZERO)
                    .add(sr.getEwalletSales() != null ? sr.getEwalletSales() : BigDecimal.ZERO);
            payoutAmount = payoutAmount.add(sr.getCashPayout() != null ? sr.getCashPayout() : BigDecimal.ZERO);
        }
        report.setTotalOrders(totalOrders);
        report.setGrossRevenue(grossRevenue);
        report.setDiscountAmount(BigDecimal.ZERO);
        report.setNetRevenue(grossRevenue);
        report.setCashAmount(cashAmount);
        report.setTransferAmount(transferAmount);
        BigDecimal opening = report.getOpeningCash() != null ? report.getOpeningCash() : BigDecimal.ZERO;
        report.setClosingCash(opening.add(cashAmount).subtract(payoutAmount));
        report.setStatus("RECONCILED");

        StoreDailyReport saved = storeDailyReportRepository.save(report);
        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(saved, submitter, payoutOf(report.getBranchId(), report.getBusinessDate()));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDailyReportResponse getDailyReportById(UUID id) {
        StoreDailyReport report = storeDailyReportRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(report, submitter, payoutOf(report.getBranchId(), report.getBusinessDate()));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDailyReportResponse getDailyReportByDate(UUID branchId, LocalDate businessDate) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        StoreDailyReport report = storeDailyReportRepository
                .findByBranchIdAndBusinessDate(effectiveBranchId, businessDate)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(report, submitter, payoutOf(report.getBranchId(), report.getBusinessDate()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreDailyReportResponse> searchDailyReports(UUID branchId,
                                                                     LocalDate startDate,
                                                                     LocalDate endDate,
                                                                     String status,
                                                                     Pageable pageable) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        Specification<StoreDailyReport> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (startDate != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("businessDate"), startDate));
            }
            if (endDate != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("businessDate"), endDate));
            }
            if (status != null && !status.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            return predicates;
        };

        Page<StoreDailyReport> page = storeDailyReportRepository.findAll(spec, pageable);
        var reports = page.getContent();

        var submitterIds = reports.stream().map(StoreDailyReport::getSubmittedById).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, Account> submitterMap = accountRepository.findAllById(submitterIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<StoreDailyReportResponse> items = reports.stream()
                .map(r -> storeDailyReportMapper.toResponse(r, submitterMap.get(r.getSubmittedById()),
                        payoutOf(r.getBranchId(), r.getBusinessDate())))
                .toList();

        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }
}
