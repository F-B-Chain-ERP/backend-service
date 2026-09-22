package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.StoreDailyReportMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.StoreDailyReportRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.StoreDailyReportService;
import com.erp.core.domain.Account;
import com.erp.core.domain.ShiftReport;
import com.erp.core.domain.StoreDailyReport;
import com.erp.core.dto.request.store.CreateDailyReportRequest;
import com.erp.core.dto.request.store.UpdateDailyReportRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final StoreDailyReportMapper storeDailyReportMapper;
    private final DataScopeHelper dataScopeHelper;

    private static final Logger log = LoggerFactory.getLogger(StoreDailyReportServiceImpl.class);

    public StoreDailyReportServiceImpl(StoreDailyReportRepository storeDailyReportRepository,
                                       ShiftAssignmentRepository shiftAssignmentRepository,
                                       ShiftReportRepository shiftReportRepository,
                                       BranchRepository branchRepository,
                                       AccountRepository accountRepository,
                                       StoreDailyReportMapper storeDailyReportMapper,
                                       DataScopeHelper dataScopeHelper) {
        this.storeDailyReportRepository = storeDailyReportRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.branchRepository = branchRepository;
        this.accountRepository = accountRepository;
        this.storeDailyReportMapper = storeDailyReportMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    public StoreDailyReportResponse generateDailyReport(CreateDailyReportRequest request, UUID currentUserId) {
        log.info("Generate daily report: branchId={}, businessDate={}", request.branchId(), request.businessDate());
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Quy tắc BR-STORE-07: Không được chốt ngày khi vẫn còn ca đang chạy hoặc chưa hoàn tất
        long activeShifts = shiftAssignmentRepository.countByBranchIdAndWorkDateAndStatusIn(
                request.branchId(),
                request.businessDate(),
                List.of("SCHEDULED", "CHECKED_IN")
        );
        if (activeShifts > 0) {
            throw new BaseException(ErrorCode.STORE_400_ACTIVE_SHIFTS_REMAINING);
        }

        // Lấy hoặc khởi tạo báo cáo
        StoreDailyReport report = storeDailyReportRepository
                .findByBranchIdAndBusinessDate(request.branchId(), request.businessDate())
                .orElse(new StoreDailyReport());

        report.setBranchId(request.branchId());
        report.setBusinessDate(request.businessDate());

        // Tổng hợp từ tất cả các ca trong ngày
        List<ShiftReport> shiftReports = shiftReportRepository.findByBranchIdAndBusinessDate(request.branchId(), request.businessDate());

        int totalOrders = 0;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal netRevenue = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal transferAmount = BigDecimal.ZERO;

        for (ShiftReport sr : shiftReports) {
            totalOrders += sr.getOrdersCount() != null ? sr.getOrdersCount() : 0;
            grossRevenue = grossRevenue.add(sr.getTotalSales() != null ? sr.getTotalSales() : BigDecimal.ZERO);
            netRevenue = netRevenue.add(sr.getTotalSales() != null ? sr.getTotalSales() : BigDecimal.ZERO);
            cashAmount = cashAmount.add(sr.getCashSales() != null ? sr.getCashSales() : BigDecimal.ZERO);
            transferAmount = transferAmount.add(sr.getBankTransferSales() != null ? sr.getBankTransferSales() : BigDecimal.ZERO);
        }

        BigDecimal openingCash = request.openingCash() != null ? request.openingCash() : BigDecimal.ZERO;
        BigDecimal closingCash = openingCash.add(cashAmount);

        report.setOpeningCash(openingCash);
        report.setClosingCash(closingCash);
        report.setTotalOrders(totalOrders);
        report.setGrossRevenue(grossRevenue);
        report.setDiscountAmount(BigDecimal.ZERO);
        report.setNetRevenue(netRevenue);
        report.setCashAmount(cashAmount);
        report.setTransferAmount(transferAmount);
        report.setStatus("OPEN");
        report.setSubmittedById(currentUserId);
        report.setSubmittedAt(Instant.now());

        StoreDailyReport saved = storeDailyReportRepository.save(report);
        Account submitter = accountRepository.findById(currentUserId).orElse(null);

        return storeDailyReportMapper.toResponse(saved, submitter);
    }

    @Override
    public StoreDailyReportResponse updateDailyReport(UUID id, UpdateDailyReportRequest request) {
        log.info("Update daily report id={}", id);
        StoreDailyReport report = storeDailyReportRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        if (request.openingCash() != null) {
            report.setOpeningCash(request.openingCash());
        }
        if (request.closingCash() != null) {
            report.setClosingCash(request.closingCash());
        }
        if (request.status() != null && !request.status().isBlank()) {
            report.setStatus(request.status());
        }

        StoreDailyReport saved = storeDailyReportRepository.save(report);
        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(saved, submitter);
    }

    @Override
    public StoreDailyReportResponse approveDailyReport(UUID id, UUID approverId, String note) {
        log.info("Approve daily report id={}, approverId={}", id, approverId);
        StoreDailyReport report = storeDailyReportRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        // Khóa sổ ngày kinh doanh
        report.setStatus("RECONCILED");

        StoreDailyReport saved = storeDailyReportRepository.save(report);
        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(saved, submitter);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDailyReportResponse getDailyReportById(UUID id) {
        log.info("Get daily report id={}", id);
        StoreDailyReport report = storeDailyReportRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(report, submitter);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDailyReportResponse getDailyReportByDate(UUID branchId, LocalDate businessDate) {
        log.info("Get daily report by date: branchId={}, businessDate={}", branchId, businessDate);
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        StoreDailyReport report = storeDailyReportRepository
                .findByBranchIdAndBusinessDate(effectiveBranchId, businessDate)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_DAILY_REPORT_NOT_FOUND));

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        return storeDailyReportMapper.toResponse(report, submitter);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreDailyReportResponse> searchDailyReports(UUID branchId,
                                                                     LocalDate startDate,
                                                                     LocalDate endDate,
                                                                     Pageable pageable) {
        log.info("Search daily reports: branchId={}, startDate={}, endDate={}, page={}, size={}",
                branchId, startDate, endDate, pageable.getPageNumber(), pageable.getPageSize());
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
            return predicates;
        };

        Page<StoreDailyReport> page = storeDailyReportRepository.findAll(spec, pageable);
        var reports = page.getContent();

        var submitterIds = reports.stream().map(StoreDailyReport::getSubmittedById).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, Account> submitterMap = accountRepository.findAllById(submitterIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<StoreDailyReportResponse> items = reports.stream()
                .map(r -> storeDailyReportMapper.toResponse(r, submitterMap.get(r.getSubmittedById())))
                .toList();

        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }
}
