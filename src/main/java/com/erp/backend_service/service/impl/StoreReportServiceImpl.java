package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.StoreDailyReportRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.StoreReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.domain.Branch;
import com.erp.core.domain.ShiftReport;
import com.erp.core.domain.StoreDailyReport;
import com.erp.core.constants.ReportExportConstants;
import com.erp.core.dto.request.report.store.ExportDailyReportRequest;
import com.erp.core.dto.request.report.store.ExportShiftReportRequest;
import com.erp.core.enums.ReportModule;
import com.erp.core.report.ReportColumnDefinition;
import com.erp.core.report.ReportDataContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo nghiệp vụ cửa hàng (Store & Shift).
 */
@Service
public class StoreReportServiceImpl implements StoreReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StoreDailyReportRepository storeDailyReportRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final BranchRepository branchRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;
    private final ObjectMapper objectMapper;

    public StoreReportServiceImpl(StoreDailyReportRepository storeDailyReportRepository,
                                  ShiftReportRepository shiftReportRepository,
                                  BranchRepository branchRepository,
                                  DataScopeHelper dataScopeHelper,
                                  ReportRequestHandler reportRequestHandler,
                                  ObjectMapper objectMapper) {
        this.storeDailyReportRepository = storeDailyReportRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.branchRepository = branchRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
        this.objectMapper = objectMapper;
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
                ReportExportConstants.REPORT_TYPE_STORE_DAILY_REPORT,
                request.format(),
                request.mode(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) storeDailyReportRepository.count(spec),
                () -> buildDailyReportContext(spec, effectiveBranchId, request.startDate(), request.endDate()),
                "BaoCaoNgayCuaHang"
        );
    }

    private ReportDataContext buildDailyReportContext(Specification<StoreDailyReport> spec, UUID branchId,
                                                      LocalDate startDate, LocalDate endDate) {
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

        String subtitle = "Thời gian kết xuất: " + LocalDate.now();
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle += " | Chi nhánh: " + branchMap.get(branchId).getName();
        }
        if (startDate != null && endDate != null) {
            subtitle += " | " + startDate + " - " + endDate;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("label", "TỔNG CỘNG");
        summary.put("dayCount", reports.size());
        summary.put("totalOrders", reports.stream().mapToInt(r -> r.getTotalOrders() != null ? r.getTotalOrders() : 0).sum());
        summary.put("grossRevenue", sumDaily(reports, StoreDailyReport::getGrossRevenue));
        summary.put("discountAmount", sumDaily(reports, StoreDailyReport::getDiscountAmount));
        summary.put("netRevenue", sumDaily(reports, StoreDailyReport::getNetRevenue));
        summary.put("cashAmount", sumDaily(reports, StoreDailyReport::getCashAmount));
        summary.put("transferAmount", sumDaily(reports, StoreDailyReport::getTransferAmount));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("branchName", branchId != null && branchMap.containsKey(branchId)
                ? branchMap.get(branchId).getName() : null);
        metadata.put("startDate", startDate);
        metadata.put("endDate", endDate);

        return new ReportDataContext(
                "BÁO CÁO DOANH THU NGÀY CHI NHÁNH",
                subtitle,
                null,
                metadata,
                columns,
                rows,
                summary,
                List.of()
        );
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
                ReportExportConstants.REPORT_TYPE_STORE_SHIFT_REPORT,
                request.format(),
                request.mode(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) shiftReportRepository.count(spec),
                () -> buildShiftReportContext(spec, effectiveBranchId, request.businessDate()),
                "BaoCaoChotCa"
        );
    }

    private ReportDataContext buildShiftReportContext(Specification<ShiftReport> spec, UUID branchId,
                                                      LocalDate businessDate) {
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
                ReportColumnDefinition.currency("cardSales", "Quẹt thẻ", 14),
                ReportColumnDefinition.currency("bankTransferSales", "Chuyển khoản", 14),
                ReportColumnDefinition.currency("ewalletSales", "Ví điện tử", 14),
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
            row.put("cardSales", r.getCardSales());
            row.put("bankTransferSales", r.getBankTransferSales());
            row.put("ewalletSales", r.getEwalletSales());
            row.put("expectedCash", r.getExpectedCash());
            row.put("actualCash", r.getActualCash());
            row.put("difference", r.getDifference());
            row.put("differenceReason", r.getDifferenceReason() != null ? r.getDifferenceReason() : "-");
            row.put("status", r.getStatus());
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("label", "TỔNG CỘNG CA");
        summary.put("shiftCount", reports.size());
        summary.put("ordersCount", reports.stream().mapToInt(r -> r.getOrdersCount() != null ? r.getOrdersCount() : 0).sum());
        summary.put("initialCash", sum(reports, ShiftReport::getInitialCash));
        summary.put("totalSales", sum(reports, ShiftReport::getTotalSales));
        summary.put("cashSales", sum(reports, ShiftReport::getCashSales));
        summary.put("cardSales", sum(reports, ShiftReport::getCardSales));
        summary.put("bankTransferSales", sum(reports, ShiftReport::getBankTransferSales));
        summary.put("ewalletSales", sum(reports, ShiftReport::getEwalletSales));
        summary.put("cashPayout", sum(reports, ShiftReport::getCashPayout));
        summary.put("expectedCash", sum(reports, ShiftReport::getExpectedCash));
        summary.put("actualCash", sum(reports, ShiftReport::getActualCash));
        summary.put("difference", sum(reports, ShiftReport::getDifference));

        List<Map<String, Object>> secondaryData = new ArrayList<>(shiftPaymentChannels(reports));
        secondaryData.addAll(collectDenominations(reports));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("branchName", branchId != null && branchMap.containsKey(branchId)
                ? branchMap.get(branchId).getName() : null);
        metadata.put("businessDate", businessDate);
        metadata.put("submittedBy", reports.isEmpty()
                ? null : Objects.toString(reports.get(0).getSubmittedById(), null));
        metadata.put("approvedBy", reports.isEmpty()
                ? null : Objects.toString(reports.get(0).getApprovedById(), null));
        metadata.put("submittedAt", reports.isEmpty()
                ? null : Objects.toString(reports.get(0).getSubmittedAt(), null));
        metadata.put("approvedAt", reports.isEmpty()
                ? null : Objects.toString(reports.get(0).getApprovedAt(), null));
        metadata.put("note", reports.isEmpty() ? null : reports.get(0).getNote());

        String subtitle = "Thời gian kết xuất: " + LocalDate.now();
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle += " | Chi nhánh: " + branchMap.get(branchId).getName();
        }
        if (businessDate != null) {
            subtitle += " | Ngày: " + businessDate;
        }

        return new ReportDataContext(
                "BÁO CÁO BIÊN BẢN CHỐT CA BÁN HÀNG",
                subtitle,
                null,
                metadata,
                columns,
                rows,
                summary,
                secondaryData
        );
    }

    private BigDecimal sum(List<ShiftReport> reports, Function<ShiftReport, BigDecimal> extractor) {
        return reports.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal sumDaily(List<StoreDailyReport> reports, Function<StoreDailyReport, BigDecimal> extractor) {
        return reports.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private List<Map<String, Object>> shiftPaymentChannels(List<ShiftReport> reports) {
        List<Map<String, Object>> channelRows = new ArrayList<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("Tiền mặt", sum(reports, ShiftReport::getCashSales));
        totals.put("Quẹt thẻ", sum(reports, ShiftReport::getCardSales));
        totals.put("Chuyển khoản", sum(reports, ShiftReport::getBankTransferSales));
        totals.put("Ví điện tử", sum(reports, ShiftReport::getEwalletSales));
        totals.forEach((channel, amount) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channel", channel);
            row.put("orderCount", null);
            row.put("amount", amount);
            channelRows.add(row);
        });
        return channelRows;
    }

    private List<Map<String, Object>> collectDenominations(List<ShiftReport> reports) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ShiftReport r : reports) {
            if (r.getCashDenominations() == null || r.getCashDenominations().isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(r.getCashDenominations());
                if (node.isObject()) {
                    node.fields().forEachRemaining(entry -> rows.add(denominationRow(entry.getKey(), entry.getValue().asInt(0))));
                } else if (node.isArray()) {
                    for (JsonNode item : node) {
                        String denom = item.path("denomination").asText(
                                item.path("value").asText(item.path("menhGia").asText("")));
                        int count = item.path("count").asInt(item.path("quantity").asInt(0));
                        rows.add(denominationRow(denom, count));
                    }
                }
            } catch (Exception e) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("denomination", "-");
                row.put("count", null);
                row.put("amount", new BigDecimal(r.getCashDenominations().trim().replaceAll("[^0-9.]", "")));
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> denominationRow(String value, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        String normalized = value != null ? value.trim() : "";
        BigDecimal denom = normalized.matches("\\d+") ? new BigDecimal(normalized) : ZERO;
        row.put("denomination", normalized);
        row.put("count", count);
        row.put("amount", denom.multiply(BigDecimal.valueOf(count)));
        return row;
    }
}
