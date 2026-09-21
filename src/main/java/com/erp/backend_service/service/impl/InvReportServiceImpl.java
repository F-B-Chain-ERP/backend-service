package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.InvReportService;
import com.erp.backend_service.service.report.ReportRequestHandler;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.Warehouse;
import com.erp.core.constants.ReportExportConstants;
import com.erp.core.dto.request.report.inv.ExportStockReportRequest;
import com.erp.core.enums.ReportModule;
import com.erp.core.report.ReportColumnDefinition;
import com.erp.core.report.ReportDataContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực dịch vụ trích xuất và kết xuất báo cáo kho hàng (Tồn kho nguyên vật liệu).
 */
@Service
public class InvReportServiceImpl implements InvReportService {

    private final MaterialStockBalanceRepository balanceRepository;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ReportRequestHandler reportRequestHandler;

    public InvReportServiceImpl(MaterialStockBalanceRepository balanceRepository,
                                MaterialRepository materialRepository,
                                WarehouseRepository warehouseRepository,
                                DataScopeHelper dataScopeHelper,
                                ReportRequestHandler reportRequestHandler) {
        this.balanceRepository = balanceRepository;
        this.materialRepository = materialRepository;
        this.warehouseRepository = warehouseRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.reportRequestHandler = reportRequestHandler;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportStockReport(ExportStockReportRequest request, UUID currentUserId) {
        Collection<UUID> allowedWarehouseIds = dataScopeHelper.getAllowedWarehouseIds(request.warehouseId());

        Map<String, Object> params = new HashMap<>();
        if (request.warehouseId() != null) params.put("warehouseId", request.warehouseId().toString());
        if (request.materialId() != null) params.put("materialId", request.materialId().toString());

        return reportRequestHandler.handleExport(
                ReportModule.INV,
                ReportExportConstants.REPORT_TYPE_INV_STOCK_BALANCE,
                request.format(),
                request.mode(),
                currentUserId,
                null,
                params,
                () -> {
                    if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) return 0;
                    Page<MaterialStockBalance> p = balanceRepository.searchPaged(
                            request.warehouseId(), request.materialId(), allowedWarehouseIds, PageRequest.of(0, 1));
                    return (int) p.getTotalElements();
                },
                () -> buildStockReportContext(request, allowedWarehouseIds),
                "BaoCaoTonKho"
        );
    }

    private ReportDataContext buildStockReportContext(ExportStockReportRequest request, Collection<UUID> allowedWarehouseIds) {
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return ReportDataContext.simple("BÁO CÁO TỒN KHO NGUYÊN VẬT LIỆU", "", List.of(), List.of());
        }

        Page<MaterialStockBalance> pageResult = balanceRepository.searchPaged(
                request.warehouseId(), request.materialId(), allowedWarehouseIds, Pageable.unpaged());
        List<MaterialStockBalance> balances = pageResult.getContent();

        Set<UUID> warehouseIds = balances.stream().map(MaterialStockBalance::getWarehouseId).collect(Collectors.toSet());
        Set<UUID> materialIds = balances.stream().map(MaterialStockBalance::getMaterialId).collect(Collectors.toSet());

        Map<UUID, Warehouse> warehouseMap = warehouseRepository.findAllById(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<UUID, Material> materialMap = materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));

        List<ReportColumnDefinition> columns = List.of(
                ReportColumnDefinition.text("warehouseName", "Kho hàng", 22),
                ReportColumnDefinition.text("materialCode", "Mã NVL", 14),
                ReportColumnDefinition.text("materialName", "Tên nguyên vật liệu", 26),
                ReportColumnDefinition.number("physicalQuantity", "Tồn thực tế", 14),
                ReportColumnDefinition.number("reservedQuantity", "Giữ chỗ", 12),
                ReportColumnDefinition.number("availableQuantity", "Khả dụng", 14)
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialStockBalance b : balances) {
            Map<String, Object> row = new LinkedHashMap<>();
            Warehouse w = warehouseMap.get(b.getWarehouseId());
            row.put("warehouseName", w != null ? w.getName() : b.getWarehouseId().toString());
            Material m = materialMap.get(b.getMaterialId());
            row.put("materialCode", m != null ? m.getCode() : "-");
            row.put("materialName", m != null ? m.getName() : b.getMaterialId().toString());
            row.put("physicalQuantity", b.getQuantityOnHand());
            row.put("reservedQuantity", b.getQuantityReserved());
            BigDecimal avail = (b.getQuantityOnHand() != null ? b.getQuantityOnHand() : java.math.BigDecimal.ZERO)
                    .subtract(b.getQuantityReserved() != null ? b.getQuantityReserved() : java.math.BigDecimal.ZERO);
            row.put("availableQuantity", avail);
            rows.add(row);
        }

        String subtitle = "Thời gian xuất: " + java.time.LocalDate.now();
        return ReportDataContext.simple("BÁO CÁO SỐ DƯ TỒN KHO NGUYÊN VẬT LIỆU", subtitle, columns, rows);
    }
}
