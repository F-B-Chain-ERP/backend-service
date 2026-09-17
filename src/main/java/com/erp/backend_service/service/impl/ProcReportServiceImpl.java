package com.erp.backend_service.service.impl;

import com.erp.backend_service.export.ReportColumnDefinition;
import com.erp.backend_service.export.ReportDataContext;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ProcReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.Supplier;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.report.proc.ExportPurchaseOrderReportRequest;
import com.erp.core.enums.ReportModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo đơn mua hàng phân hệ PROC.
 */
@Service
public class ProcReportServiceImpl implements ProcReportService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;

    public ProcReportServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                 SupplierRepository supplierRepository,
                                 WarehouseRepository warehouseRepository,
                                 DataScopeHelper dataScopeHelper,
                                 ReportRequestHandler reportRequestHandler) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportPurchaseOrderReport(ExportPurchaseOrderReportRequest request, UUID currentUserId) {
        Collection<UUID> allowedWarehouseIds = dataScopeHelper.getAllowedWarehouseIds(request.warehouseId());
        String search = StringUtils.hasText(request.search()) ? request.search().trim() : null;

        Map<String, Object> params = new HashMap<>();
        if (search != null) params.put("search", search);
        if (request.status() != null) params.put("status", request.status());
        if (request.supplierId() != null) params.put("supplierId", request.supplierId().toString());
        if (request.warehouseId() != null) params.put("warehouseId", request.warehouseId().toString());
        if (request.fromDate() != null) params.put("fromDate", request.fromDate().toString());
        if (request.toDate() != null) params.put("toDate", request.toDate().toString());

        return reportRequestHandler.handleExport(
                ReportModule.PROC,
                "PROC_PO_EXPORT",
                request.format(),
                currentUserId,
                null,
                params,
                () -> {
                    if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) return 0;
                    Page<PurchaseOrder> testPage = purchaseOrderRepository.search(
                            search, request.status(), request.supplierId(), request.warehouseId(),
                            allowedWarehouseIds, request.fromDate(), request.toDate(), PageRequest.of(0, 1));
                    return (int) testPage.getTotalElements();
                },
                () -> buildPoReportContext(search, request, allowedWarehouseIds),
                "BaoCaoDonMuaHang"
        );
    }

    private ReportDataContext buildPoReportContext(String search, ExportPurchaseOrderReportRequest request, Collection<UUID> allowedWarehouseIds) {
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return new ReportDataContext("BÁO CÁO ĐƠN MUA HÀNG (PO)", "", List.of(), List.of());
        }

        Page<PurchaseOrder> allPos = purchaseOrderRepository.search(
                search, request.status(), request.supplierId(), request.warehouseId(),
                allowedWarehouseIds, request.fromDate(), request.toDate(), Pageable.unpaged());
        List<PurchaseOrder> pos = allPos.getContent();

        Set<UUID> supplierIds = pos.stream().map(PurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> warehouseIds = pos.stream().map(PurchaseOrder::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, Supplier> supplierMap = supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        Map<UUID, Warehouse> warehouseMap = warehouseRepository.findAllById(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.text("poCode", "Mã đơn PO", 14),
                ReportColumnDefinition.date("orderDate", "Ngày đặt", 12),
                ReportColumnDefinition.date("expectedDate", "Ngày nhận DK", 14),
                ReportColumnDefinition.text("supplierName", "Nhà cung cấp", 24),
                ReportColumnDefinition.text("warehouseName", "Kho nhận", 20),
                ReportColumnDefinition.text("status", "Trạng thái", 14),
                ReportColumnDefinition.currency("subtotalAmount", "Tiền hàng", 16),
                ReportColumnDefinition.currency("totalAmount", "Tổng tiền PO", 16),
                ReportColumnDefinition.text("note", "Ghi chú", 25)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("poCode", po.getPoCode());
            row.put("orderDate", po.getOrderDate());
            row.put("expectedDate", po.getExpectedDate());
            Supplier s = supplierMap.get(po.getSupplierId());
            row.put("supplierName", s != null ? s.getName() : po.getSupplierId().toString());
            Warehouse w = warehouseMap.get(po.getWarehouseId());
            row.put("warehouseName", w != null ? w.getName() : po.getWarehouseId().toString());
            row.put("status", po.getStatus());
            row.put("subtotalAmount", po.getSubtotalAmount());
            row.put("totalAmount", po.getTotalAmount());
            row.put("note", po.getNote() != null ? po.getNote() : "-");
            rows.add(row);
        }

        String subtitle = "Thời gian xuất: " + java.time.LocalDate.now();
        return new ReportDataContext("BÁO CÁO TỔNG HỢP ĐƠN MUA HÀNG (PO)", subtitle, columns, rows);
    }
}
