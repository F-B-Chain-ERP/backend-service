package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.StockInItemMapper;
import com.erp.backend_service.mapper.StockInMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.PurchaseOrderItemRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.StockInItemRepository;
import com.erp.backend_service.repository.StockInRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.StockInService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.PurchaseOrderItem;
import com.erp.core.domain.StockIn;
import com.erp.core.domain.StockInItem;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateStockInRequest;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.request.inv.StockInItemRequest;
import com.erp.core.dto.request.inv.UpdateStockInRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockInItemResponse;
import com.erp.core.dto.response.inv.StockInResponse;
import com.erp.core.dto.response.inv.StockInUserResponse;
import com.erp.core.dto.response.inv.StockInWarehouseResponse;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link StockInService} theo SRS INV: phiếu nhập được lưu DRAFT, khi xác nhận
 * (POSTED) sẽ tăng tồn kho và ghi người/thời điểm xác nhận; nếu nguồn nhập là PURCHASE thì
 * đồng thời cập nhật số lượng nhận và trạng thái đơn mua hàng tương ứng.
 */
@Service
public class StockInServiceImpl implements StockInService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String SOURCE_PURCHASE = "PURCHASE";
    private static final List<String> VALID_STATUSES = List.of("DRAFT", "POSTED", "CANCELLED");
    private static final List<String> VALID_SOURCE_TYPES = List.of(
            "PURCHASE", "TRANSFER_IN", "ADJUSTMENT", "RETURN");
    private static final DateTimeFormatter CODE_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final StockInRepository stockInRepository;
    private final StockInItemRepository stockInItemRepository;
    private final MaterialStockBalanceRepository materialStockBalanceRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final AccountRepository accountRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final StockInMapper stockInMapper;
    private final StockInItemMapper stockInItemMapper;
    private final DataScopeHelper dataScopeHelper;

    public StockInServiceImpl(StockInRepository stockInRepository,
                              StockInItemRepository stockInItemRepository,
                              MaterialStockBalanceRepository materialStockBalanceRepository,
                              WarehouseRepository warehouseRepository,
                              MaterialRepository materialRepository,
                              AccountRepository accountRepository,
                              PurchaseOrderRepository purchaseOrderRepository,
                              PurchaseOrderItemRepository purchaseOrderItemRepository,
                              StockInMapper stockInMapper,
                              StockInItemMapper stockInItemMapper,
                              DataScopeHelper dataScopeHelper) {
        this.stockInRepository = stockInRepository;
        this.stockInItemRepository = stockInItemRepository;
        this.materialStockBalanceRepository = materialStockBalanceRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.accountRepository = accountRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.stockInMapper = stockInMapper;
        this.stockInItemMapper = stockInItemMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockInResponse> list(int page, int size, String search, String status,
                                              UUID warehouseId, String sourceType, LocalDate fromDate, LocalDate toDate) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_INVALID_FILTER);
        }
        validateFilterValues(status, sourceType);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());

        Collection<UUID> allowedWarehouseIds = dataScopeHelper.getAllowedWarehouseIds(warehouseId);
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return new PageResponse<>(page, safeSize, 0L, 0, List.of());
        }

        Page<StockIn> pageResult = stockInRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, status,
                warehouseId, allowedWarehouseIds, sourceType, fromDate, toDate, pageable);

        List<StockIn> stockIns = pageResult.getContent();

        Map<UUID, List<StockInItem>> itemsByStockIn = new HashMap<>();
        List<StockInItem> allItems = new ArrayList<>();
        for (StockIn si : stockIns) {
            List<StockInItem> items = stockInItemRepository.findByStockInId(si.getId());
            itemsByStockIn.put(si.getId(), items);
            allItems.addAll(items);
        }

        Map<UUID, Warehouse> warehouseMap = toMap(
                warehouseRepository.findAllById(distinctNonNull(stockIns, StockIn::getWarehouseId)), Warehouse::getId);
        Map<UUID, Account> accountMap = toMap(
                accountRepository.findAllById(distinctNonNull(stockIns, StockIn::getReceivedBy)), Account::getId);
        Map<UUID, Material> materialMap = toMap(
                materialRepository.findAllById(distinctNonNull(allItems, StockInItem::getMaterialId)), Material::getId);
        Map<UUID, PurchaseOrder> poMap = toMap(
                purchaseOrderRepository.findAllById(distinctNonNull(stockIns, StockIn::getSourceReferenceId)), PurchaseOrder::getId);

        List<StockInResponse> content = stockIns.stream()
                .map(si -> toResponse(si, itemsByStockIn.get(si.getId()), warehouseMap, accountMap, materialMap, poMap))
                .toList();

        return new PageResponse<>(pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages(), content);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public StockInResponse get(UUID id) {
        StockIn stockIn = findById(id);
        dataScopeHelper.enforceWarehouseAccess(stockIn.getWarehouseId());
        return toResponseWithNames(stockIn, stockInItemRepository.findByStockInId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StockInResponse create(CreateStockInRequest request) {
        validateWarehouse(request.warehouseId());
        validateSourceType(request.sourceType());
        if (request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_ITEMS_EMPTY);
        }
        validateItems(request.items());
        validatePurchaseReference(request.sourceType(), request.sourceReferenceId(), request.items());

        StockIn stockIn = new StockIn();
        stockIn.setCode(generateStockInCode());
        stockIn.setWarehouseId(request.warehouseId());
        stockIn.setSourceType(request.sourceType());
        stockIn.setSourceReferenceId(request.sourceReferenceId());
        stockIn.setInDate(request.inDate() != null ? request.inDate() : LocalDate.now());
        stockIn.setNote(request.note());
        stockIn.setStatus(STATUS_DRAFT);
        stockIn = stockInRepository.save(stockIn);

        List<StockInItem> items = stockInItemRepository.saveAll(buildItems(stockIn.getId(), request.items()));
        return toResponseWithNames(stockIn, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StockInResponse update(UUID id, UpdateStockInRequest request) {
        StockIn stockIn = findById(id);
        dataScopeHelper.enforceWarehouseAccess(stockIn.getWarehouseId());
        if (!STATUS_DRAFT.equals(stockIn.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_INVALID_STATUS_FOR_EDIT);
        }
        validateWarehouse(request.warehouseId());
        validateSourceType(request.sourceType());
        if (request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_ITEMS_EMPTY);
        }
        validateItems(request.items());
        validatePurchaseReference(request.sourceType(), request.sourceReferenceId(), request.items());

        stockIn.setWarehouseId(request.warehouseId());
        stockIn.setSourceType(request.sourceType());
        stockIn.setSourceReferenceId(request.sourceReferenceId());
        stockIn.setInDate(request.inDate() != null ? request.inDate() : LocalDate.now());
        stockIn.setNote(request.note());

        stockInItemRepository.deleteByStockInId(id);
        List<StockInItem> items = stockInItemRepository.saveAll(buildItems(stockIn.getId(), request.items()));
        stockIn = stockInRepository.save(stockIn);
        return toResponseWithNames(stockIn, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StockInResponse changeStatus(UUID id, StatusUpdateRequest request) {
        StockIn stockIn = findById(id);
        dataScopeHelper.enforceWarehouseAccess(stockIn.getWarehouseId());
        if (!STATUS_DRAFT.equals(stockIn.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_INVALID_STATUS);
        }
        String target = request.status() == null ? null : request.status().trim().toUpperCase();
        if (!STATUS_POSTED.equals(target) && !STATUS_CANCELLED.equals(target)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_INVALID_STATUS);
        }

        List<StockInItem> items = stockInItemRepository.findByStockInId(id);

        if (STATUS_POSTED.equals(target)) {
            if (items.isEmpty()) {
                throw new BaseException(ErrorCode.INV_400_STOCK_IN_ITEMS_EMPTY);
            }
            applyPurchaseReceipt(stockIn, items);
            applyStockInQuantity(stockIn, items);
            stockIn.setReceivedBy(SecurityUtils.getCurrentPrincipalId().orElse(null));
            stockIn.setPostedAt(Instant.now());
        }
        stockIn.setStatus(target);
        stockIn = stockInRepository.save(stockIn);
        return toResponseWithNames(stockIn, items);
    }

    private void validateFilterValues(String status, String sourceType) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_INVALID_FILTER);
        }
        if (sourceType != null && !VALID_SOURCE_TYPES.contains(sourceType)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_IN_INVALID_FILTER);
        }
    }

    private Warehouse validateWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_WAREHOUSE_NOT_FOUND));
        dataScopeHelper.enforceWarehouseAccess(warehouseId);
        if (!EntityStatus.ACTIVE.name().equals(warehouse.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_WAREHOUSE_INACTIVE);
        }
        return warehouse;
    }

    private void validateSourceType(String sourceType) {
        if (sourceType == null || !VALID_SOURCE_TYPES.contains(sourceType)) {
            throw new BaseException(ErrorCode.INV_400_INVALID_SOURCE_TYPE);
        }
    }

    private void validateItems(List<StockInItemRequest> itemRequests) {
        for (StockInItemRequest r : itemRequests) {
            Material material = materialRepository.findById(r.materialId())
                    .orElseThrow(() -> new BaseException(ErrorCode.INV_404_MATERIAL_NOT_FOUND));
            if (!EntityStatus.ACTIVE.name().equals(material.getStatus())) {
                throw new BaseException(ErrorCode.INV_404_MATERIAL_NOT_FOUND);
            }
        }
    }

    /**
     * Khi nguồn nhập là PURCHASE, kiểm tra PO nguồn tồn tại, ở trạng thái cho phép nhận và
     * từng dòng được liên kết với dòng PO hợp lệ (UC-INV-IO-02, API POST/PUT).
     */
    private void validatePurchaseReference(String sourceType, UUID sourceReferenceId, List<StockInItemRequest> itemRequests) {
        if (!SOURCE_PURCHASE.equals(sourceType)) {
            return;
        }
        if (sourceReferenceId == null) {
            throw new BaseException(ErrorCode.INV_400_PO_INVALID_STATUS_FOR_RECEIVE);
        }
        PurchaseOrder po = purchaseOrderRepository.findById(sourceReferenceId)
                .orElseThrow(() -> new BaseException(ErrorCode.PROC_404_PO_NOT_FOUND));
        if (!STATUS_APPROVED.equals(po.getStatus()) && !STATUS_PARTIALLY_RECEIVED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_PO_INVALID_STATUS_FOR_RECEIVE);
        }
        Set<UUID> poItemIds = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId()).stream()
                .map(PurchaseOrderItem::getId)
                .collect(Collectors.toSet());
        for (StockInItemRequest r : itemRequests) {
            if (r.purchaseOrderItemId() == null || !poItemIds.contains(r.purchaseOrderItemId())) {
                throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ITEM);
            }
        }
    }

    /**
     * Khi nguồn nhập là PURCHASE: cập nhật received_quantity trên các dòng đơn mua hàng
     * và trạng thái nhận hàng của đơn (RECEIVED/PARTIALLY_RECEIVED). Chặn nhập vượt số còn lại.
     */
    private void applyPurchaseReceipt(StockIn stockIn, List<StockInItem> items) {
        if (!SOURCE_PURCHASE.equals(stockIn.getSourceType())) {
            return;
        }
        if (stockIn.getSourceReferenceId() == null) {
            throw new BaseException(ErrorCode.INV_400_PO_INVALID_STATUS_FOR_RECEIVE);
        }
        PurchaseOrder po = purchaseOrderRepository.findById(stockIn.getSourceReferenceId())
                .orElseThrow(() -> new BaseException(ErrorCode.PROC_404_PO_NOT_FOUND));
        if (!STATUS_APPROVED.equals(po.getStatus()) && !STATUS_PARTIALLY_RECEIVED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_PO_INVALID_STATUS_FOR_RECEIVE);
        }

        Map<UUID, PurchaseOrderItem> poItemMap = new HashMap<>();
        for (PurchaseOrderItem it : purchaseOrderItemRepository.findByPurchaseOrderId(po.getId())) {
            poItemMap.put(it.getId(), it);
        }

        for (StockInItem item : items) {
            if (item.getPurchaseOrderItemId() == null) {
                throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ITEM);
            }
            PurchaseOrderItem poItem = poItemMap.get(item.getPurchaseOrderItemId());
            if (poItem == null) {
                throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ITEM);
            }
            BigDecimal remaining = poItem.getQuantity().subtract(poItem.getReceivedQuantity());
            if (item.getQuantity().compareTo(remaining) > 0) {
                throw new BaseException(ErrorCode.INV_400_OVER_RECEIPT);
            }
            poItem.setReceivedQuantity(poItem.getReceivedQuantity().add(item.getQuantity()));
        }
        List<PurchaseOrderItem> poItems = purchaseOrderItemRepository.saveAll(new ArrayList<>(poItemMap.values()));

        boolean allReceived = poItems.stream().allMatch(i -> i.getReceivedQuantity().compareTo(i.getQuantity()) >= 0);
        boolean anyReceived = poItems.stream().anyMatch(i -> i.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (allReceived) {
            po.setStatus(STATUS_RECEIVED);
        } else if (anyReceived) {
            po.setStatus(STATUS_PARTIALLY_RECEIVED);
        }
        purchaseOrderRepository.save(po);
    }

    private void applyStockInQuantity(StockIn stockIn, List<StockInItem> items) {
        Map<UUID, BigDecimal> qtyByMaterial = new HashMap<>();
        for (StockInItem item : items) {
            qtyByMaterial.merge(item.getMaterialId(), item.getQuantity(), BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> entry : qtyByMaterial.entrySet()) {
            MaterialStockBalance balance = materialStockBalanceRepository
                    .findByWarehouseIdAndMaterialId(stockIn.getWarehouseId(), entry.getKey())
                    .orElse(null);
            MaterialStockBalance target = balance != null ? balance
                    : createBalance(stockIn.getWarehouseId(), entry.getKey());
            BigDecimal current = target.getQuantityOnHand() == null ? BigDecimal.ZERO : target.getQuantityOnHand();
            target.setQuantityOnHand(current.add(entry.getValue()));
            materialStockBalanceRepository.save(target);
        }
    }

    private MaterialStockBalance createBalance(UUID warehouseId, UUID materialId) {
        MaterialStockBalance balance = new MaterialStockBalance();
        balance.setWarehouseId(warehouseId);
        balance.setMaterialId(materialId);
        balance.setQuantityOnHand(BigDecimal.ZERO);
        balance.setQuantityReserved(BigDecimal.ZERO);
        return balance;
    }

    private List<StockInItem> buildItems(UUID stockInId, List<StockInItemRequest> requests) {
        return requests.stream().map(r -> {
            StockInItem item = new StockInItem();
            item.setStockInId(stockInId);
            item.setPurchaseOrderItemId(r.purchaseOrderItemId());
            item.setMaterialId(r.materialId());
            item.setQuantity(r.quantity());
            item.setUnitPrice(r.unitPrice() != null ? r.unitPrice() : BigDecimal.ZERO);
            item.setBatchNo(r.batchNo());
            item.setExpiryDate(r.expiryDate());
            item.setStatus("ACTIVE");
            return item;
        }).toList();
    }

    private String generateStockInCode() {
        String prefix = "SI-" + LocalDate.now().format(CODE_MONTH_FMT) + "-";
        Page<StockIn> last = stockInRepository.findFirstByCodeStartingWithOrderByCodeDesc(prefix, PageRequest.of(0, 1));
        int next = 1;
        if (!last.isEmpty()) {
            String code = last.getContent().get(0).getCode();
            try {
                next = Integer.parseInt(code.substring(prefix.length())) + 1;
            } catch (RuntimeException e) {
                next = 1;
            }
        }
        return prefix + String.format("%04d", next);
    }

    private StockIn findById(UUID id) {
        return stockInRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_STOCK_IN_NOT_FOUND));
    }

    private StockInResponse toResponseWithNames(StockIn stockIn, List<StockInItem> items) {
        Map<UUID, Warehouse> warehouseMap = toMap(
                warehouseRepository.findAllById(List.of(stockIn.getWarehouseId())), Warehouse::getId);
        Map<UUID, Account> accountMap = stockIn.getReceivedBy() == null ? Map.of()
                : toMap(accountRepository.findAllById(List.of(stockIn.getReceivedBy())), Account::getId);
        Map<UUID, Material> materialMap = toMap(
                materialRepository.findAllById(distinctNonNull(items, StockInItem::getMaterialId)), Material::getId);
        Map<UUID, PurchaseOrder> poMap = resolvePurchaseOrderMap(stockIn);
        return toResponse(stockIn, items, warehouseMap, accountMap, materialMap, poMap);
    }

    private Map<UUID, PurchaseOrder> resolvePurchaseOrderMap(StockIn stockIn) {
        UUID poId = stockIn.getSourceReferenceId();
        if (!SOURCE_PURCHASE.equals(stockIn.getSourceType()) || poId == null) {
            return Map.of();
        }
        return toMap(purchaseOrderRepository.findAllById(List.of(poId)), PurchaseOrder::getId);
    }

    private StockInResponse toResponse(StockIn stockIn, List<StockInItem> items,
                                       Map<UUID, Warehouse> warehouseMap, Map<UUID, Account> accountMap,
                                       Map<UUID, Material> materialMap, Map<UUID, PurchaseOrder> poMap) {
        Warehouse warehouse = stockIn.getWarehouseId() != null ? warehouseMap.get(stockIn.getWarehouseId()) : null;
        Account receivedBy = stockIn.getReceivedBy() != null ? accountMap.get(stockIn.getReceivedBy()) : null;

        List<StockInItemResponse> itemResponses = (items == null ? List.<StockInItem>of() : items).stream()
                .map(i -> {
                    Material m = i.getMaterialId() != null ? materialMap.get(i.getMaterialId()) : null;
                    return stockInItemMapper.toResponse(
                            i, m != null ? m.getCode() : null, m != null ? m.getName() : null);
                }).toList();

        PurchaseOrder po = stockIn.getSourceReferenceId() != null ? poMap.get(stockIn.getSourceReferenceId()) : null;
        String purchaseOrderStatus = SOURCE_PURCHASE.equals(stockIn.getSourceType()) && po != null ? po.getStatus() : null;

        return stockInMapper.toResponse(stockIn,
                warehouse != null ? new StockInWarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName()) : null,
                receivedBy != null ? new StockInUserResponse(receivedBy.getId(), receivedBy.getFullName()) : null,
                purchaseOrderStatus,
                itemResponses);
    }

    private <T> Map<UUID, T> toMap(Iterable<T> iterable, Function<T, UUID> idFn) {
        Map<UUID, T> map = new HashMap<>();
        for (T t : iterable) {
            if (t != null) {
                map.put(idFn.apply(t), t);
            }
        }
        return map;
    }

    private <T> List<UUID> distinctNonNull(List<T> list, Function<T, UUID> idFn) {
        return list.stream().map(idFn).filter(Objects::nonNull).distinct().toList();
    }
}