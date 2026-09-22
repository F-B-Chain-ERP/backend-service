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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.erp.backend_service.repository.OrderItemRepository;
import com.erp.core.domain.OrderItem;
import java.math.RoundingMode;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo bán hàng phân hệ POS.
 *
 * <p>Hỗ trợ mẫu {@code POS_ORDER_EXPORT} (danh sách đơn) và {@code POS_SALES_SUMMARY}
 * (tổng hợp doanh thu theo ngày) theo hợp đồng {@link ReportExportConstants}.</p>
 */
@Service
public class PosReportServiceImpl implements PosReportService {

    private static final Logger log = LoggerFactory.getLogger(PosReportServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BranchRepository branchRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;

    public PosReportServiceImpl(OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                BranchRepository branchRepository,
                                DataScopeHelper dataScopeHelper,
                                ReportRequestHandler reportRequestHandler) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.branchRepository = branchRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportOrderReport(ExportOrderReportRequest request, UUID currentUserId) {
        log.info("Export POS report: type={}, format={}, mode={}, branchId={}", request.reportType(), request.format(), request.mode(), request.branchId());
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
        List<OrderItem> orderItems = loadActiveItems(orders);

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.text("productCode", "Mã SP", 14),
                ReportColumnDefinition.text("productName", "Tên sản phẩm", 24),
                ReportColumnDefinition.text("variantName", "Biến thể", 16),
                ReportColumnDefinition.number("quantity", "Số lượng", 10),
                ReportColumnDefinition.currency("unitPrice", "Đơn giá TB", 14),
                ReportColumnDefinition.currency("revenue", "Doanh thu", 16),
                ReportColumnDefinition.currency("cogs", "Giá vốn (COGS)", 16),
                ReportColumnDefinition.currency("grossProfit", "Lãi gộp", 16),
                ReportColumnDefinition.text("profitMargin", "Biên LN", 10)
        );

        List<Map<String, Object>> rows = buildProductSummaryRows(orderItems);

        return new ReportDataContext(
                "BÁO CÁO TỔNG HỢP DOANH THU POS",
                buildSubtitle(params, branchId, branchMap),
                null,
                Map.of("rowCount", rows.size(), "orderCount", orders.size()),
                columns,
                rows,
                buildProductSummaryTotal(orders, orderItems),
                paymentMethodBreakdown(orders)
        );
    }

    private List<OrderItem> loadActiveItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        return orderItemRepository.findByOrderIdIn(orderIds).stream()
                .filter(item -> item.getStatus() == null || "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .toList();
    }

    private List<Map<String, Object>> buildProductSummaryRows(List<OrderItem> orderItems) {
        Map<String, List<OrderItem>> grouped = orderItems.stream()
                .collect(Collectors.groupingBy(PosReportServiceImpl::productKey,
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        grouped.forEach((key, items) -> {
            int quantity = items.stream().mapToInt(PosReportServiceImpl::quantityOf).sum();
            BigDecimal revenue = sumAmount(items, OrderItem::getTotalPrice);
            BigDecimal cogs = sumAmount(items, PosReportServiceImpl::itemCogs);
            BigDecimal grossProfit = revenue.subtract(cogs);
            BigDecimal unitPrice = revenue.divide(BigDecimal.valueOf(Math.max(quantity, 1)),
                    2, RoundingMode.HALF_UP);
            BigDecimal profitMargin = revenue.signum() != 0
                    ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            OrderItem first = items.get(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productCode", first.getProductCode());
            row.put("productName", first.getProductName());
            row.put("variantName", first.getVariantName() != null ? first.getVariantName() : "");
            row.put("quantity", quantity);
            row.put("unitPrice", unitPrice);
            row.put("revenue", revenue);
            row.put("cogs", cogs);
            row.put("grossProfit", grossProfit);
            row.put("profitMargin", profitMargin.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%");
            rows.add(row);
        });
        return rows;
    }

    private Map<String, Object> buildProductSummaryTotal(List<Order> orders, List<OrderItem> orderItems) {
        BigDecimal revenue = sumAmount(orderItems, OrderItem::getTotalPrice);
        BigDecimal cogs = sumAmount(orderItems, PosReportServiceImpl::itemCogs);
        BigDecimal grossProfit = revenue.subtract(cogs);
        BigDecimal profitMargin = revenue.signum() != 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("label", "TỔNG CỘNG");
        summary.put("orderCount", orders.size());
        summary.put("quantity", orderItems.stream().mapToInt(PosReportServiceImpl::quantityOf).sum());
        summary.put("revenue", revenue);
        summary.put("cogs", cogs);
        summary.put("grossProfit", grossProfit);
        summary.put("profitMargin", profitMargin);
        return summary;
    }

    private static BigDecimal itemCogs(OrderItem item) {
        if (item.getUnitCogsAmount() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.getUnitCogsAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private static int quantityOf(OrderItem item) {
        return item.getQuantity() != null ? item.getQuantity() : 0;
    }

    private static String productKey(OrderItem item) {
        return (item.getProductCode() == null ? "" : item.getProductCode())
                + "|"
                + (item.getVariantName() == null ? "" : item.getVariantName());
    }

    private BigDecimal sumAmount(List<OrderItem> items, Function<OrderItem, BigDecimal> extractor) {
        return items.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}