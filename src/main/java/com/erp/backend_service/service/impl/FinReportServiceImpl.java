package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.BranchDailyFinancialSummaryRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.FinReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchDailyFinancialSummary;
import com.erp.core.constants.ReportExportConstants;
import com.erp.core.dto.request.report.fin.ExportFinancialReportRequest;
import com.erp.core.enums.ReportModule;
import com.erp.core.report.ReportColumnDefinition;
import com.erp.core.report.ReportDataContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo tài chính chi nhánh.
 */
@Service
public class FinReportServiceImpl implements FinReportService {

    private final BranchDailyFinancialSummaryRepository summaryRepository;
    private final BranchRepository branchRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;

    public FinReportServiceImpl(BranchDailyFinancialSummaryRepository summaryRepository,
                                BranchRepository branchRepository,
                                DataScopeHelper dataScopeHelper,
                                ReportRequestHandler reportRequestHandler) {
        this.summaryRepository = summaryRepository;
        this.branchRepository = branchRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportFinancialReport(ExportFinancialReportRequest request, UUID currentUserId) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(request.branchId());

        Specification<BranchDailyFinancialSummary> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (request.fromDate() != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("businessDate"), request.fromDate()));
            }
            if (request.toDate() != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("businessDate"), request.toDate()));
            }
            return predicates;
        };

        Map<String, Object> params = new HashMap<>();
        if (effectiveBranchId != null) params.put("branchId", effectiveBranchId.toString());
        if (request.fromDate() != null) params.put("fromDate", request.fromDate().toString());
        if (request.toDate() != null) params.put("toDate", request.toDate().toString());
        if (request.reportType() != null) params.put("reportType", request.reportType());

        return reportRequestHandler.handleExport(
                ReportModule.FIN,
                ReportExportConstants.REPORT_TYPE_FIN_SUMMARY_EXPORT,
                request.format(),
                request.mode(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) summaryRepository.count(spec),
                () -> buildFinancialReportContext(spec, effectiveBranchId),
                "BaoCaoTaiChinhChiNhanh"
        );
    }

    private ReportDataContext buildFinancialReportContext(Specification<BranchDailyFinancialSummary> spec, UUID branchId) {
        List<BranchDailyFinancialSummary> summaries = summaryRepository.findAll(spec);

        Set<UUID> branchIds = summaries.stream().map(BranchDailyFinancialSummary::getBranchId).collect(Collectors.toSet());
        Map<UUID, Branch> branchMap = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.date("businessDate", "Ngày KD", 12),
                ReportColumnDefinition.text("branchName", "Chi nhánh", 22),
                ReportColumnDefinition.number("orderCount", "Số đơn", 10),
                ReportColumnDefinition.currency("grossRevenue", "Doanh thu gộp", 16),
                ReportColumnDefinition.currency("discountAmount", "Giảm giá", 14),
                ReportColumnDefinition.currency("netRevenue", "Doanh thu thuần", 16),
                ReportColumnDefinition.currency("totalCogs", "Giá vốn (COGS)", 16),
                ReportColumnDefinition.currency("grossProfit", "Lợi nhuận gộp", 16),
                ReportColumnDefinition.currency("totalExpense", "Tổng chi phí", 15),
                ReportColumnDefinition.currency("netProfit", "Lợi nhuận ròng", 16),
                ReportColumnDefinition.text("status", "Trạng thái", 12)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BranchDailyFinancialSummary s : summaries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessDate", s.getBusinessDate());
            Branch b = branchMap.get(s.getBranchId());
            row.put("branchName", b != null ? b.getName() : s.getBranchId().toString());
            row.put("orderCount", s.getOrderCount());
            row.put("grossRevenue", s.getGrossRevenue());
            row.put("discountAmount", s.getDiscountAmount());
            row.put("netRevenue", s.getNetRevenue());
            row.put("totalCogs", s.getTotalCogs());
            row.put("grossProfit", s.getGrossProfit());
            row.put("totalExpense", s.getTotalExpense());
            row.put("netProfit", s.getNetProfit());
            row.put("status", s.getStatus());
            rows.add(row);
        }

        String subtitle = "Thời gian xuất: " + java.time.LocalDate.now();
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle += " | Chi nhánh: " + branchMap.get(branchId).getName();
        }

        return ReportDataContext.simple("BÁO CÁO TỔNG HỢP TÀI CHÍNH CHI NHÁNH", subtitle, columns, rows);
    }
}
