package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.OrderSpecifications;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.PosReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.constants.ReportExportConstants;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Order;
import com.erp.core.dto.request.report.pos.ExportOrderReportRequest;
import com.erp.core.enums.ReportModule;
import com.erp.core.report.ReportColumnDefinition;
import com.erp.core.report.ReportDataContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo bán hàng phân hệ POS.
 *
 * <p>Hỗ trợ mẫu {@code POS_ORDER_EXPORT} (danh sách đơn) và {@code POS_SALES_SUMMARY}
 * (tổng hợp doanh thu theo ngày) theo hợp đồng {@link ReportExportConstants}.</p>
 */
@Service
public class PosReportServiceImpl implements PosReportService {

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;

    public PosReportServiceImpl(OrderRepository orderRepository,
                                BranchRepository branchRepository,
                                DataScopeHelper dataScopeHelper,
                                ReportRequestHandler reportRequestHandler) {
        this.orderRepository = orderRepository;
        this.branchRepository = branchRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportOrderReport(ExportOrderReportRequest request, UUID currentUserId) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(request.branchId());

        Instant fromInstant = request.fromDate() != null
                ? request.fromDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;
        Instant toInstant = request.toDate() != null
                ? request.toDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;

        Specification<Order> spec = OrderSpecifications.filter(
                effectiveBranchId,
                null,
                request.orderType(),
                request.status(),
                fromInstant,
                toInstant
        );

        Map<String, Object> params = new HashMap<>();
        if (effectiveBranchId != null) params.put("branchId", effectiveBranchId.toString());
        if (request.orderType() != null) params.put("orderType", request.orderType());
        if (request.status() != null) params.put("status", request.status());
        if (request.fromDate() != null) params.put("fromDate", request.fromDate().toString());
        if (request.toDate() != null) params.put("toDate", request.toDate().toString());

        String reportType = request.reportType() != null
                ? request.reportType()
                : ReportExportConstants.REPORT_TYPE_POS_ORDER_EXPORT;
        boolean salesSummary = ReportExportConstants.REPORT_TYPE_POS_SALES_SUMMARY.equalsIgnoreCase(reportType);

        return reportRequestHandler.handleExport(
                ReportModule.POS,
                salesSummary ? ReportExportConstants.REPORT_TYPE_POS_SALES_SUMMARY
                        : ReportExportConstants.REPORT_TYPE_POS_ORDER_EXPORT,
                request.format(),
                request.mode(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) orderRepository.count(spec),
                salesSummary
                        ? () -> buildSalesSummaryContext(spec, params, effectiveBranchId)
                        : () -> buildOrderReportContext(spec, params, effectiveBranchId),
                salesSummary ? "BaoCaoTongHopDoanhThuPOS" : "BaoCaoChiTietDonHangPOS"
        );
    }

    private static LocalDate businessDateOf(Order o) {
        return o.getCreatedAt() != null
                ? LocalDate.ofInstant(o.getCreatedAt(), ZoneId.systemDefault())
                : LocalDate.MIN;
    }

    private static String paymentChannelOf(Order o) {
        return o.getPaymentMethod() != null && !o.getPaymentMethod().isBlank() ? o.getPaymentMethod() : "-";
    }

    private String buildSubtitle(Map<String, Object> params, UUID branchId, Map<UUID, Branch> branchMap) {
        StringBuilder subtitle = new StringBuilder("Thời gian xuất: ").append(LocalDate.now());
        if (params.get("fromDate") != null || params.get("toDate") != null) {
            subtitle.append(" | Từ ngày: ").append(params.get("fromDate"))
                    .append(" Đến ngày: ").append(params.get("toDate"));
        }
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle.append(" | Chi nhánh: ").append(branchMap.get(branchId).getName());
        }
        return subtitle.toString();
    }

    private Map<UUID, Branch> loadBranchMap(List<Order> orders) {
        Set<UUID> branchIds = orders.stream().map(Order::getBranchId).collect(Collectors.toSet());
        if (branchIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
    }

    private BigDecimal sum(List<Order> orders, Function<Order, BigDecimal> extractor) {
        return orders.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> buildSummary(List<Order> orders) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("label", "TỔNG CỘNG");
        summary.put("orderCount", orders.size());
        summary.put("subtotalAmount", sum(orders, Order::getSubtotalAmount));
        summary.put("discountAmount", sum(orders, Order::getDiscountAmount));
        summary.put("deliveryFee", sum(orders, Order::getDeliveryFee));
        summary.put("totalAmount", sum(orders, Order::getTotalAmount));
        return summary;
    }

    /** Bảng phụ phân bổ doanh thu theo phương thức thanh toán (Sheet 2). */
    private List<Map<String, Object>> paymentMethodBreakdown(List<Order> orders) {
        Map<String, Long> counts = orders.stream()
                .collect(Collectors.groupingBy(PosReportServiceImpl::paymentChannelOf, Collectors.counting()));
        Map<String, BigDecimal> amounts = orders.stream()
                .collect(Collectors.groupingBy(PosReportServiceImpl::paymentChannelOf,
                        Collectors.reducing(BigDecimal.ZERO,
                                o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)));

        List<Map<String, Object>> rows = new ArrayList<>();
        counts.keySet().stream().sorted().forEach(channel -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channel", channel);
            row.put("orderCount", counts.get(channel));
            row.put("amount", amounts.getOrDefault(channel, BigDecimal.ZERO));
            rows.add(row);
        });
        return rows;
    }

    private ReportDataContext buildOrderReportContext(Specification<Order> spec,
                                                      Map<String, Object> params, UUID branchId) {
        List<Order> orders = orderRepository.findAll(spec);
        Map<UUID, Branch> branchMap = loadBranchMap(orders);

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.text("orderCode", "Mã đơn", 14),
                ReportColumnDefinition.dateTime("createdAt", "Thời gian đặt", 16),
                ReportColumnDefinition.text("branchName", "Chi nhánh", 22),
                ReportColumnDefinition.text("customerName", "Khách hàng", 18),
                ReportColumnDefinition.text("customerPhone", "SĐT", 12),
                ReportColumnDefinition.text("orderType", "Loại đơn", 10),
                ReportColumnDefinition.text("status", "Trạng thái", 12),
                ReportColumnDefinition.text("paymentMethod", "PT thanh toán", 14),
                ReportColumnDefinition.text("paymentStatus", "Trạng thái TT", 14),
                ReportColumnDefinition.currency("subtotalAmount", "Tạm tính", 14),
                ReportColumnDefinition.currency("discountAmount", "Giảm giá", 12),
                ReportColumnDefinition.currency("deliveryFee", "Phí giao", 12),
                ReportColumnDefinition.currency("totalAmount", "Tổng tiền", 16)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderCode", o.getOrderCode());
            LocalDateTime ldt = o.getCreatedAt() != null
                    ? LocalDateTime.ofInstant(o.getCreatedAt(), ZoneId.systemDefault())
                    : null;
            row.put("createdAt", ldt);
            Branch b = branchMap.get(o.getBranchId());
            row.put("branchName", b != null ? b.getName() : o.getBranchId().toString());
            row.put("customerName", o.getCustomerName());
            row.put("customerPhone", o.getCustomerPhone());
            row.put("orderType", o.getOrderType());
            row.put("status", o.getStatus());
            row.put("paymentMethod", paymentChannelOf(o));
            row.put("paymentStatus", o.getPaymentStatus());
            row.put("subtotalAmount", o.getSubtotalAmount());
            row.put("discountAmount", o.getDiscountAmount());
            row.put("deliveryFee", o.getDeliveryFee());
            row.put("totalAmount", o.getTotalAmount());
            rows.add(row);
        }

        return new ReportDataContext(
                "BÁO CÁO CHI TIẾT ĐƠN HÀNG POS",
                buildSubtitle(params, branchId, branchMap),
                null,
                Map.of("rowCount", orders.size()),
                columns,
                rows,
                buildSummary(orders),
                paymentMethodBreakdown(orders)
        );
    }

    private ReportDataContext buildSalesSummaryContext(Specification<Order> spec,
                                                       Map<String, Object> params, UUID branchId) {
        List<Order> orders = orderRepository.findAll(spec);
        Map<UUID, Branch> branchMap = loadBranchMap(orders);

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.date("businessDate", "Ngày KD", 12),
                ReportColumnDefinition.number("orderCount", "Số đơn", 10),
                ReportColumnDefinition.currency("subtotalAmount", "Tạm tính", 14),
                ReportColumnDefinition.currency("discountAmount", "Giảm giá", 12),
                ReportColumnDefinition.currency("deliveryFee", "Phí giao", 12),
                ReportColumnDefinition.currency("totalAmount", "Tổng tiền", 16)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        orders.stream()
                .collect(Collectors.groupingBy(PosReportServiceImpl::businessDateOf,
                        LinkedHashMap::new, Collectors.toList()))
                .forEach((businessDate, dayOrders) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("businessDate", businessDate);
                    row.put("orderCount", dayOrders.size());
                    row.put("subtotalAmount", sum(dayOrders, Order::getSubtotalAmount));
                    row.put("discountAmount", sum(dayOrders, Order::getDiscountAmount));
                    row.put("deliveryFee", sum(dayOrders, Order::getDeliveryFee));
                    row.put("totalAmount", sum(dayOrders, Order::getTotalAmount));
                    rows.add(row);
                });

        rows.sort(Comparator.comparing(r -> (LocalDate) r.get("businessDate")));

        return new ReportDataContext(
                "BÁO CÁO TỔNG HỢP DOANH THU POS",
                buildSubtitle(params, branchId, branchMap),
                null,
                Map.of("rowCount", orders.size()),
                columns,
                rows,
                buildSummary(orders),
                paymentMethodBreakdown(orders)
        );
    }
}