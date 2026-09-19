package com.erp.backend_service.service.impl;

import com.erp.backend_service.export.ReportColumnDefinition;
import com.erp.backend_service.export.ReportDataContext;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.OrderSpecifications;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.PosReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.domain.Branch;
import com.erp.core.domain.Order;
import com.erp.core.dto.request.report.pos.ExportOrderReportRequest;
import com.erp.core.enums.ReportModule;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo bán hàng phân hệ POS.
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

        return reportRequestHandler.handleExport(
                ReportModule.POS,
                "POS_ORDER_EXPORT",
                request.format(),
                currentUserId,
                effectiveBranchId,
                params,
                () -> (int) orderRepository.count(spec),
                () -> buildOrderReportContext(spec, effectiveBranchId),
                "BaoCaoDonHangPOS"
        );
    }

    private ReportDataContext buildOrderReportContext(Specification<Order> spec, UUID branchId) {
        List<Order> orders = orderRepository.findAll(spec);

        Set<UUID> branchIds = orders.stream().map(Order::getBranchId).collect(Collectors.toSet());
        Map<UUID, Branch> branchMap = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));

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
            row.put("paymentMethod", o.getPaymentMethod() != null ? o.getPaymentMethod() : "-");
            row.put("paymentStatus", o.getPaymentStatus());
            row.put("subtotalAmount", o.getSubtotalAmount());
            row.put("discountAmount", o.getDiscountAmount());
            row.put("deliveryFee", o.getDeliveryFee());
            row.put("totalAmount", o.getTotalAmount());
            rows.add(row);
        }

        String subtitle = "Thời gian xuất: " + java.time.LocalDate.now();
        if (branchId != null && branchMap.containsKey(branchId)) {
            subtitle += " | Chi nhánh: " + branchMap.get(branchId).getName();
        }

        return new ReportDataContext("BÁO CÁO CHI TIẾT ĐƠN HÀNG POS", subtitle, columns, rows);
    }
}
