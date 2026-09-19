package com.erp.backend_service.service.impl;

import com.erp.backend_service.export.ReportColumnDefinition;
import com.erp.backend_service.export.ReportDataContext;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.StoreDailyReportRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.StoreReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.domain.Branch;
import com.erp.core.domain.ShiftReport;
import com.erp.core.domain.StoreDailyReport;
import com.erp.core.dto.request.report.store.ExportDailyReportRequest;
import com.erp.core.dto.request.report.store.ExportShiftReportRequest;
import com.erp.core.enums.ReportModule;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo nghiệp vụ cửa hàng (Store & Shift).
 */
@Service
public class StoreReportServiceImpl implements StoreReportService {

    private final StoreDailyReportRepository storeDailyReportRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final BranchRepository branchRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;

    public StoreReportServiceImpl(StoreDailyReportRepository storeDailyReportRepository,
                                  ShiftReportRepository shiftReportRepository,
                                  BranchRepository branchRepository,
                                  DataScopeHelper dataScopeHelper,
                                  ReportRequestHandler reportRequestHandler) {
        this.storeDailyReportRepository = storeDailyReportRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.branchRepository = branchRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportDailyReport(ExportDailyReportRequest request, UUID currentUserId) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(request.branchId());

        Specification<StoreDailyReport> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (request.startDate() != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("businessDate"), request.startDate()));
            }
            if (request.endDate() != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("businessDate"), request.endDate()));
            }
            if (request.status() != null && !request.status().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), request.status()));
            }
            return predicates;
        };

        Map<String, Object> params = new HashMap<>();
        if (effectiveBranchId != null) params.put("branchId", effectiveBranchId.toString());
        if (request.startDate() != null) params.put("startDate", request.startDate().toString());
        if (request.endDate() != null) params.put("endDate", request.endDate().toString());
        if (request.status() != null) params.put("status", request.status());

        return reportRequestHandler.handleExport(
                ReportModule.STORE,
                "STORE_DAILY_REPORT",
                request.format(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) storeDailyReportRepository.count(spec),
                () -> buildDailyReportContext(spec, effectiveBranchId),
                "BaoCaoNgayCuaHang"
        );
    }

    private ReportDataContext buildDailyReportContext(Specification<StoreDailyReport> spec, UUID branchId) {
        List<StoreDailyReport> reports = storeDailyReportRepository.findAll(spec);

        Set<UUID> branchIds = reports.stream().map(StoreDailyReport::getBranchId).collect(Collectors.toSet());
        Map<UUID, Branch> branchMap = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.date("businessDate", "Ngày KD", 12),
                ReportColumnDefinition.text("branchName", "Chi nhánh", 22),
                ReportColumnDefinition.number("totalOrders", "Số đơn", 10),
                ReportColumnDefinition.currency("grossRevenue", "Doanh thu gộp", 16),
                ReportColumnDefinition.currency("discountAmount", "Giảm giá", 14),
                ReportColumnDefinition.currency("netRevenue", "Doanh thu thuần", 16),
                ReportColumnDefinition.currency("cashAmount", "Tiền mặt", 14),
                ReportColumnDefinition.currency("transferAmount", "Chuyển khoản", 14),
                ReportColumnDefinition.currency("openingCash", "Tiền mở két", 14),
                ReportColumnDefinition.currency("closingCash", "Tiền chốt két", 14),
                ReportColumnDefinition.text("status", "Trạng thái", 14)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (StoreDailyReport r : reports) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessDate", r.getBusinessDate());
            Branch b = branchMap.get(r.getBranchId());
            row.put("branchName", b != null ? b.getName() : r.getBranchId().toString());
            row.put("totalOrders", r.getTotalOrders());
            row.put("grossRevenue", r.getGrossRevenue());
            row.put("discountAmount", r.getDiscountAmount());
            row.put("netRevenue", r.getNetRevenue());
            row.put("cashAmount", r.getCashAmount());
            row.put("transferAmount", r.getTransferAmount());
            row.put("openingCash", r.getOpeningCash());
            row.put("closingCash", r.getClosingCash());
            row.put("status", r.getStatus());
            rows.add(row);
        }

        String subtitle = "Thời gian xuất: " + java.time.LocalDate.now();
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle += " | Chi nhánh: " + branchMap.get(branchId).getName();
        }

        return new ReportDataContext("BÁO CÁO DOANH THU NGÀY CHI NHÁNH", subtitle, columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportShiftReport(ExportShiftReportRequest request, UUID currentUserId) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(request.branchId());

        Specification<ShiftReport> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (request.businessDate() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("businessDate"), request.businessDate()));
            }
            if (request.startDate() != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("businessDate"), request.startDate()));
            }
            if (request.endDate() != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("businessDate"), request.endDate()));
            }
            if (request.status() != null && !request.status().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), request.status()));
            }
            return predicates;
        };

        Map<String, Object> params = new HashMap<>();
        if (effectiveBranchId != null) params.put("branchId", effectiveBranchId.toString());
        if (request.businessDate() != null) params.put("businessDate", request.businessDate().toString());
        if (request.startDate() != null) params.put("startDate", request.startDate().toString());
        if (request.endDate() != null) params.put("endDate", request.endDate().toString());
        if (request.status() != null) params.put("status", request.status());

        return reportRequestHandler.handleExport(
                ReportModule.STORE,
                "STORE_SHIFT_REPORT",
                request.format(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) shiftReportRepository.count(spec),
                () -> buildShiftReportContext(spec, effectiveBranchId),
                "BaoCaoChotCa"
        );
    }

    private ReportDataContext buildShiftReportContext(Specification<ShiftReport> spec, UUID branchId) {
        List<ShiftReport> reports = shiftReportRepository.findAll(spec);

        Set<UUID> branchIds = reports.stream().map(ShiftReport::getBranchId).collect(Collectors.toSet());
        Map<UUID, Branch> branchMap = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.date("businessDate", "Ngày KD", 12),
                ReportColumnDefinition.text("branchName", "Chi nhánh", 22),
                ReportColumnDefinition.number("ordersCount", "Số đơn", 10),
                ReportColumnDefinition.currency("initialCash", "Tiền mở ca", 14),
                ReportColumnDefinition.currency("totalSales", "Tổng doanh thu", 16),
                ReportColumnDefinition.currency("cashSales", "Tiền mặt bán", 14),
                ReportColumnDefinition.currency("expectedCash", "Tiền lý thuyết", 15),
                ReportColumnDefinition.currency("actualCash", "Tiền thực đếm", 15),
                ReportColumnDefinition.currency("difference", "Chênh lệch", 14),
                ReportColumnDefinition.text("differenceReason", "Lý do lệch", 20),
                ReportColumnDefinition.text("status", "Trạng thái", 14)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ShiftReport r : reports) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessDate", r.getBusinessDate());
            Branch b = branchMap.get(r.getBranchId());
            row.put("branchName", b != null ? b.getName() : r.getBranchId().toString());
            row.put("ordersCount", r.getOrdersCount());
            row.put("initialCash", r.getInitialCash());
            row.put("totalSales", r.getTotalSales());
            row.put("cashSales", r.getCashSales());
            row.put("expectedCash", r.getExpectedCash());
            row.put("actualCash", r.getActualCash());
            row.put("difference", r.getDifference());
            row.put("differenceReason", r.getDifferenceReason() != null ? r.getDifferenceReason() : "-");
            row.put("status", r.getStatus());
            rows.add(row);
        }

        String subtitle = "Thời gian xuất: " + java.time.LocalDate.now();
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle += " | Chi nhánh: " + branchMap.get(branchId).getName();
        }

        return new ReportDataContext("BÁO CÁO BIÊN BẢN CHỐT CA BÁN HÀNG", subtitle, columns, rows);
    }
}
