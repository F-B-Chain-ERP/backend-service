package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftAssignmentMapper;
import com.erp.backend_service.mapper.ShiftReportMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.ShiftRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ShiftOperationService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Order;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.domain.ShiftReport;
import com.erp.core.dto.request.store.CloseShiftRequest;
import com.erp.core.dto.request.store.OpenShiftRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ClosingSummaryResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import com.erp.core.dto.response.store.ShiftReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShiftOperationServiceImpl implements ShiftOperationService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final ShiftReportMapper shiftReportMapper;
    private final DataScopeHelper dataScopeHelper;

    public ShiftOperationServiceImpl(ShiftAssignmentRepository shiftAssignmentRepository,
                                     ShiftRepository shiftRepository,
                                     ShiftReportRepository shiftReportRepository,
                                     OrderRepository orderRepository,
                                     AccountRepository accountRepository,
                                     ShiftAssignmentMapper shiftAssignmentMapper,
                                     ShiftReportMapper shiftReportMapper,
                                     DataScopeHelper dataScopeHelper) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftRepository = shiftRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.shiftAssignmentMapper = shiftAssignmentMapper;
        this.shiftReportMapper = shiftReportMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftAssignmentResponse getMyActiveShift(UUID accountId) {
        // Tìm ca đang CHECKED_IN của nhân viên
        ShiftAssignment activeAssignment = shiftAssignmentRepository
                .findFirstByAccountIdAndStatus(accountId, "CHECKED_IN")
                .orElse(null);

        if (activeAssignment == null) {
            // Nếu chưa check-in, tìm ca SCHEDULED của hôm nay
            activeAssignment = shiftAssignmentRepository
                    .findFirstByAccountIdAndWorkDateAndStatus(accountId, LocalDate.now(), "SCHEDULED")
                    .orElse(null);
        }

        if (activeAssignment == null) {
            return null;
        }

        Shift shift = shiftRepository.findById(activeAssignment.getShiftId()).orElse(null);
        Account account = accountRepository.findById(accountId).orElse(null);
        return shiftAssignmentMapper.toResponse(activeAssignment, shift, account);
    }

    @Override
    public ShiftAssignmentResponse openShift(UUID assignmentId, OpenShiftRequest request, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        if (!"SCHEDULED".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        // Kiểm tra xem nhân viên này đã có ca nào khác đang hoạt động không
        shiftAssignmentRepository.findFirstByAccountIdAndStatus(assignment.getAccountId(), "CHECKED_IN")
                .ifPresent(existing -> {
                    if (!existing.getId().equals(assignment.getId())) {
                        throw new BaseException(ErrorCode.STORE_400_ACTIVE_SHIFT_EXISTS);
                    }
                });

        assignment.setStatus("CHECKED_IN");
        assignment.setCheckInAt(Instant.now());
        assignment.setInitialCash(request.initialCash() != null ? request.initialCash() : BigDecimal.ZERO);
        if (request.note() != null && !request.note().isBlank()) {
            assignment.setNote(request.note());
        }

        ShiftAssignment saved = shiftAssignmentRepository.save(assignment);
        Shift shift = shiftRepository.findById(saved.getShiftId()).orElse(null);
        Account account = accountRepository.findById(saved.getAccountId()).orElse(null);

        return shiftAssignmentMapper.toResponse(saved, shift, account);
    }

    @Override
    @Transactional(readOnly = true)
    public ClosingSummaryResponse getClosingSummary(UUID assignmentId, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        if (!"CHECKED_IN".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        Shift shift = shiftRepository.findById(assignment.getShiftId()).orElse(null);
        Account cashier = accountRepository.findById(assignment.getAccountId()).orElse(null);

        Instant checkInAt = assignment.getCheckInAt() != null ? assignment.getCheckInAt() : Instant.now();
        Instant now = Instant.now();

        List<Order> orders = orderRepository.findOrdersInShiftWindow(assignment.getBranchId(), checkInAt, now);

        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal bankTransferSales = BigDecimal.ZERO;
        BigDecimal ewalletSales = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;

        for (Order order : orders) {
            BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            totalSales = totalSales.add(amount);

            String method = order.getPaymentMethod() != null ? order.getPaymentMethod().toUpperCase() : "CASH";
            if (method.contains("CASH")) {
                cashSales = cashSales.add(amount);
            } else if (method.contains("CARD") || method.contains("VISA") || method.contains("MASTER")) {
                cardSales = cardSales.add(amount);
            } else if (method.contains("TRANSFER") || method.contains("BANK") || method.contains("QR")) {
                bankTransferSales = bankTransferSales.add(amount);
            } else if (method.contains("WALLET") || method.contains("MOMO") || method.contains("ZALO")) {
                ewalletSales = ewalletSales.add(amount);
            } else {
                cashSales = cashSales.add(amount);
            }
        }

        BigDecimal initialCash = assignment.getInitialCash() != null ? assignment.getInitialCash() : BigDecimal.ZERO;
        BigDecimal cashPayout = BigDecimal.ZERO;
        BigDecimal expectedCash = initialCash.add(cashSales).subtract(cashPayout);

        return new ClosingSummaryResponse(
                assignment.getId(),
                assignment.getBranchId(),
                shift != null ? shift.getShiftCode() : null,
                shift != null ? shift.getShiftName() : null,
                cashier != null ? cashier.getId() : null,
                cashier != null ? cashier.getFullName() : null,
                checkInAt,
                initialCash,
                cashSales,
                cardSales,
                bankTransferSales,
                ewalletSales,
                totalSales,
                orders.size(),
                cashPayout,
                expectedCash
        );
    }

    @Override
    public ShiftReportResponse closeShift(UUID assignmentId, CloseShiftRequest request, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        if (!"CHECKED_IN".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        Instant checkInAt = assignment.getCheckInAt() != null ? assignment.getCheckInAt() : Instant.now();
        Instant checkOutAt = Instant.now();

        List<Order> orders = orderRepository.findOrdersInShiftWindow(assignment.getBranchId(), checkInAt, checkOutAt);

        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal bankTransferSales = BigDecimal.ZERO;
        BigDecimal ewalletSales = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;

        for (Order order : orders) {
            BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            totalSales = totalSales.add(amount);

            String method = order.getPaymentMethod() != null ? order.getPaymentMethod().toUpperCase() : "CASH";
            if (method.contains("CASH")) {
                cashSales = cashSales.add(amount);
            } else if (method.contains("CARD") || method.contains("VISA") || method.contains("MASTER")) {
                cardSales = cardSales.add(amount);
            } else if (method.contains("TRANSFER") || method.contains("BANK") || method.contains("QR")) {
                bankTransferSales = bankTransferSales.add(amount);
            } else if (method.contains("WALLET") || method.contains("MOMO") || method.contains("ZALO")) {
                ewalletSales = ewalletSales.add(amount);
            } else {
                cashSales = cashSales.add(amount);
            }
        }

        BigDecimal initialCash = assignment.getInitialCash() != null ? assignment.getInitialCash() : BigDecimal.ZERO;
        BigDecimal cashPayout = request.cashPayout() != null ? request.cashPayout() : BigDecimal.ZERO;
        BigDecimal expectedCash = initialCash.add(cashSales).subtract(cashPayout);
        BigDecimal actualCash = request.actualCash() != null ? request.actualCash() : BigDecimal.ZERO;
        BigDecimal difference = actualCash.subtract(expectedCash);

        // Quy tắc nghiệp vụ BR-STORE-05: Lệch tiền bắt buộc giải trình lý do
        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            if (request.differenceReason() == null || request.differenceReason().trim().length() < 10) {
                throw new BaseException(ErrorCode.STORE_400_DIFFERENCE_REASON_REQUIRED);
            }
        }

        // Tạo ShiftReport
        ShiftReport report = shiftReportRepository.findByAssignmentId(assignment.getId())
                .orElse(new ShiftReport());

        report.setAssignmentId(assignment.getId());
        report.setBranchId(assignment.getBranchId());
        report.setBusinessDate(assignment.getWorkDate());
        report.setInitialCash(initialCash);
        report.setCashSales(cashSales);
        report.setCardSales(cardSales);
        report.setBankTransferSales(bankTransferSales);
        report.setEwalletSales(ewalletSales);
        report.setTotalSales(totalSales);
        report.setOrdersCount(orders.size());
        report.setCashPayout(cashPayout);
        report.setExpectedCash(expectedCash);
        report.setActualCash(actualCash);
        report.setDifference(difference);
        report.setDifferenceReason(request.differenceReason());
        report.setCashDenominations(request.cashDenominations());
        report.setStatus("SUBMITTED");
        report.setSubmittedById(currentUserId);
        report.setSubmittedAt(checkOutAt);
        report.setNote(request.note());

        ShiftReport savedReport = shiftReportRepository.save(report);

        // Cập nhật ShiftAssignment
        assignment.setStatus("CHECKED_OUT");
        assignment.setCheckOutAt(checkOutAt);
        assignment.setFinalCash(actualCash);
        assignment.setCashDifference(difference);
        shiftAssignmentRepository.save(assignment);

        Account submitter = accountRepository.findById(currentUserId).orElse(null);
        return shiftReportMapper.toResponse(savedReport, submitter, null);
    }

    @Override
    public ShiftReportResponse confirmShiftReport(UUID reportId, UUID managerId, String note) {
        ShiftReport report = shiftReportRepository.findById(reportId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        report.setStatus("CONFIRMED");
        report.setApprovedById(managerId);
        report.setApprovedAt(Instant.now());
        if (note != null && !note.isBlank()) {
            report.setNote(report.getNote() != null ? report.getNote() + " | Duyệt: " + note : "Duyệt: " + note);
        }

        ShiftReport saved = shiftReportRepository.save(report);
        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        Account approver = accountRepository.findById(managerId).orElse(null);

        return shiftReportMapper.toResponse(saved, submitter, approver);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportResponse getShiftReportByAssignmentId(UUID assignmentId) {
        ShiftReport report = shiftReportRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        Account approver = report.getApprovedById() != null ? accountRepository.findById(report.getApprovedById()).orElse(null) : null;

        return shiftReportMapper.toResponse(report, submitter, approver);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShiftReportResponse> searchShiftReports(UUID branchId, LocalDate businessDate, Pageable pageable) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        Page<ShiftReport> page;
        if (businessDate != null) {
            page = shiftReportRepository.findByBranchIdAndBusinessDate(effectiveBranchId, businessDate, pageable);
        } else {
            page = shiftReportRepository.findByBranchId(effectiveBranchId, pageable);
        }

        var reports = page.getContent();
        var submitterIds = reports.stream().map(ShiftReport::getSubmittedById).filter(java.util.Objects::nonNull).distinct().toList();
        var approverIds = reports.stream().map(ShiftReport::getApprovedById).filter(java.util.Objects::nonNull).distinct().toList();

        Map<UUID, Account> submitterMap = accountRepository.findAllById(submitterIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, Account> approverMap = accountRepository.findAllById(approverIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<ShiftReportResponse> items = reports.stream()
                .map(r -> shiftReportMapper.toResponse(r, submitterMap.get(r.getSubmittedById()), approverMap.get(r.getApprovedById())))
                .toList();

        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }
}
