package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.PurchaseOrderItemMapper;
import com.erp.backend_service.mapper.PurchaseOrderMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.PurchaseOrderItemRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.NotificationService;
import com.erp.backend_service.service.PurchaseOrderService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Material;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.PurchaseOrderItem;
import com.erp.core.domain.Supplier;
import com.erp.core.domain.Unit;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.proc.CreatePurchaseOrderRequest;
import com.erp.core.dto.request.proc.PurchaseOrderItemRequest;
import com.erp.core.dto.request.proc.ReceivePurchaseOrderRequest;
import com.erp.core.dto.request.proc.ReceivePurchaseOrderItemRequest;
import com.erp.core.dto.request.proc.UpdatePurchaseOrderRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.proc.ApprovedByResponse;
import com.erp.core.dto.response.proc.PurchaseOrderItemResponse;
import com.erp.core.dto.response.proc.PurchaseOrderResponse;
import com.erp.core.dto.response.proc.PurchaseOrderSupplierResponse;
import com.erp.core.dto.response.proc.PurchaseOrderWarehouseResponse;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link PurchaseOrderService}: quản lý đơn mua hàng với các chuyển trạng thái
 * (DRAFT -> SUBMITTED -> APPROVED, PARTIALLY_RECEIVED -> RECEIVED, hoặc CANCELLED),
 * sinh mã PO, validate nghiệp vụ và giải quyết tên/code cho response.
 */
@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final int FIXED_PAGE_SIZE = 10;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final DateTimeFormatter PO_CODE_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final AccountRepository accountRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final DataScopeHelper dataScopeHelper;
    private final NotificationService notificationService;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseOrderItemRepository purchaseOrderItemRepository,
                                    SupplierRepository supplierRepository,
                                    WarehouseRepository warehouseRepository,
                                    MaterialRepository materialRepository,
                                    UnitRepository unitRepository,
                                    AccountRepository accountRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    PurchaseOrderItemMapper purchaseOrderItemMapper,
                                    DataScopeHelper dataScopeHelper,
                                    NotificationService notificationService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.accountRepository = accountRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderItemMapper = purchaseOrderItemMapper;
        this.dataScopeHelper = dataScopeHelper;
        this.notificationService = notificationService;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(int page, int size, String search, String status,
                                                    UUID supplierId, UUID warehouseId, LocalDate fromDate, LocalDate toDate) {
        int safeSize = FIXED_PAGE_SIZE;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_FILTER);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());

        java.util.Collection<UUID> allowedWarehouseIds = dataScopeHelper.getAllowedWarehouseIds(warehouseId);
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return new PageResponse<>(page, safeSize, 0L, 0, List.of());
        }

        Page<PurchaseOrder> pageResult = purchaseOrderRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, status,
                supplierId, warehouseId, allowedWarehouseIds, fromDate, toDate, pageable);

        List<PurchaseOrder> pos = pageResult.getContent();

        Map<UUID, List<PurchaseOrderItem>> itemsByPo = new HashMap<>();
        List<PurchaseOrderItem> allItems = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
            itemsByPo.put(po.getId(), items);
            allItems.addAll(items);
        }

        Map<UUID, Supplier> supplierMap = toMap(supplierRepository.findAllById(
                distinctNonNull(pos, PurchaseOrder::getSupplierId)), Supplier::getId);
        Map<UUID, Warehouse> warehouseMap = toMap(warehouseRepository.findAllById(
                distinctNonNull(pos, PurchaseOrder::getWarehouseId)), Warehouse::getId);
        Map<UUID, Account> accountMap = toMap(accountRepository.findAllById(
                distinctNonNull(pos, PurchaseOrder::getApprovedBy)), Account::getId);
        Map<UUID, Material> materialMap = toMap(materialRepository.findAllById(
                distinctNonNull(allItems, PurchaseOrderItem::getMaterialId)), Material::getId);
        Map<UUID, Unit> unitMap = toMap(unitRepository.findAllById(
                distinctNonNull(allItems, PurchaseOrderItem::getUnitId)), Unit::getId);

        List<PurchaseOrderResponse> content = pos.stream()
                .map(po -> toResponse(po, itemsByPo.get(po.getId()), supplierMap, warehouseMap, accountMap, materialMap, unitMap))
                .toList();

        return new PageResponse<>(pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages(), content);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID id) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(id);
        return toResponseWithNames(po, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new BaseException(ErrorCode.PROC_404_SUPPLIER_NOT_FOUND));
        if (!EntityStatus.ACTIVE.name().equals(supplier.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_SUPPLIER_INACTIVE);
        }
        Warehouse warehouse = dataScopeHelper.enforceWarehouseAccess(request.warehouseId());
        if (!EntityStatus.ACTIVE.name().equals(warehouse.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_WAREHOUSE_INACTIVE);
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.PROC_400_PO_ITEMS_EMPTY);
        }

        LocalDate orderDate = request.orderDate() != null ? request.orderDate() : LocalDate.now();
        if (orderDate.isAfter(LocalDate.now())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ORDER_DATE);
        }
        if (request.expectedDate() != null && request.expectedDate().isBefore(orderDate)) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_EXPECTED_DATE);
        }

        String poCode = StringUtils.hasText(request.poCode()) ? request.poCode() : generatePoCode();
        if (purchaseOrderRepository.existsByPoCode(poCode)) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setPoCode(poCode);
        po.setSupplierId(supplier.getId());
        po.setWarehouseId(warehouse.getId());
        po.setOrderDate(orderDate);
        po.setExpectedDate(request.expectedDate());
        po.setNote(request.note());
        po.setStatus(STATUS_DRAFT);

        po = purchaseOrderRepository.save(po);
        List<PurchaseOrderItem> items = buildItems(po.getId(), request.items());
        items = purchaseOrderItemRepository.saveAll(items);
        recalculateTotals(po, items);
        po = purchaseOrderRepository.save(po);
        return toResponseWithNames(po, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse update(UUID id, UpdatePurchaseOrderRequest request) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        if (!STATUS_DRAFT.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_EDIT);
        }
        if (request.supplierId() != null) {
            Supplier supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new BaseException(ErrorCode.PROC_404_SUPPLIER_NOT_FOUND));
            if (!EntityStatus.ACTIVE.name().equals(supplier.getStatus())) {
                throw new BaseException(ErrorCode.PROC_400_SUPPLIER_INACTIVE);
            }
            po.setSupplierId(supplier.getId());
        }
        if (request.warehouseId() != null) {
            Warehouse warehouse = dataScopeHelper.enforceWarehouseAccess(request.warehouseId());
            if (!EntityStatus.ACTIVE.name().equals(warehouse.getStatus())) {
                throw new BaseException(ErrorCode.PROC_400_WAREHOUSE_INACTIVE);
            }
            po.setWarehouseId(warehouse.getId());
        }
        if (request.orderDate() != null) {
            po.setOrderDate(request.orderDate());
        }
        if (request.expectedDate() != null) {
            po.setExpectedDate(request.expectedDate());
        }
        if (request.note() != null) {
            po.setNote(request.note());
        }

        LocalDate effOrder = po.getOrderDate();
        LocalDate effExpected = po.getExpectedDate();
        if (effOrder != null && effOrder.isAfter(LocalDate.now())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ORDER_DATE);
        }
        if (effExpected != null && effOrder != null && effExpected.isBefore(effOrder)) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_EXPECTED_DATE);
        }

        if (request.items() != null) {
            if (request.items().isEmpty()) {
                throw new BaseException(ErrorCode.PROC_400_PO_ITEMS_EMPTY);
            }
            purchaseOrderItemRepository.deleteByPurchaseOrderId(id);
            List<PurchaseOrderItem> items = buildItems(id, request.items());
            items = purchaseOrderItemRepository.saveAll(items);
            recalculateTotals(po, items);
        } else {
            recalculateTotals(po, purchaseOrderItemRepository.findByPurchaseOrderId(id));
        }
        po = purchaseOrderRepository.save(po);
        return toResponseWithNames(po, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        if (!STATUS_DRAFT.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_EDIT);
        }
        purchaseOrderItemRepository.deleteByPurchaseOrderId(id);
        purchaseOrderRepository.deleteById(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse submit(UUID id) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        if (!STATUS_DRAFT.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_SUBMIT);
        }
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(id);
        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.PROC_400_PO_ITEMS_EMPTY);
        }
        po.setStatus(STATUS_SUBMITTED);
        po.setSubmittedAt(java.time.Instant.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toResponseWithNames(saved, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse approve(UUID id) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        if (!STATUS_SUBMITTED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_APPROVE);
        }
        UUID approver = SecurityUtils.getCurrentPrincipalId().orElse(null);
        po.setStatus(STATUS_APPROVED);
        po.setApprovedBy(approver);
        po.setApprovedAt(java.time.Instant.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        notifyPoApproved(saved);
        return toResponseWithNames(saved, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse cancel(UUID id, String reason) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        boolean cancellable = STATUS_DRAFT.equals(po.getStatus())
                || STATUS_SUBMITTED.equals(po.getStatus())
                || STATUS_APPROVED.equals(po.getStatus());
        if (!cancellable) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_CANCEL);
        }
        if (reason == null || reason.isBlank()) {
            throw new BaseException(ErrorCode.PROC_400_PO_CANCEL_REASON_REQUIRED);
        }
        po.setStatus(STATUS_CANCELLED);
        po.setCancelledAt(java.time.Instant.now());
        po.setCancelReason(reason);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        notifyPoCancelled(saved, reason);
        return toResponseWithNames(saved, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    /** Gửi thông báo in-app cho người tạo đơn khi đơn được duyệt (SCRUM-49). */
    private void notifyPoApproved(PurchaseOrder po) {
        UUID recipientId = resolvePoCreatorAccountId(po);
        if (recipientId == null) {
            return;
        }
        notificationService.notifyAccount(recipientId,
                "Đơn mua hàng đã được duyệt",
                "Đơn mua hàng " + po.getPoCode() + " đã được duyệt.");
    }

    /** Gửi thông báo in-app cho người tạo đơn khi đơn bị huỷ (SCRUM-52). */
    private void notifyPoCancelled(PurchaseOrder po, String reason) {
        UUID recipientId = resolvePoCreatorAccountId(po);
        if (recipientId == null) {
            return;
        }
        String reasonText = (reason == null || reason.isBlank()) ? "không rõ lý do" : reason;
        notificationService.notifyAccount(recipientId,
                "Đơn mua hàng đã bị huỷ",
                "Đơn mua hàng " + po.getPoCode() + " đã bị huỷ. Lý do: " + reasonText);
    }

    /** Xác định account ID của người tạo đơn (tra theo username trong created_by). */
    private UUID resolvePoCreatorAccountId(PurchaseOrder po) {
        String createdBy = po.getCreatedBy();
        if (!StringUtils.hasText(createdBy)) {
            return null;
        }
        return accountRepository.findByUsername(createdBy)
                .map(Account::getId)
                .orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse reject(UUID id, String reason) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        if (!STATUS_SUBMITTED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_REJECT);
        }
        if (reason == null || reason.isBlank()) {
            throw new BaseException(ErrorCode.PROC_400_PO_REJECT_REASON_REQUIRED);
        }
        po.setStatus(STATUS_DRAFT);
        po.setCancelReason(reason);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toResponseWithNames(saved, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse receive(UUID id, ReceivePurchaseOrderRequest request) {
        PurchaseOrder po = findById(id);
        dataScopeHelper.enforceWarehouseAccess(po.getWarehouseId());
        if (!STATUS_APPROVED.equals(po.getStatus()) && !STATUS_PARTIALLY_RECEIVED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_PO_INVALID_STATUS_FOR_RECEIVE);
        }
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(id);
        Map<UUID, PurchaseOrderItem> itemMap = items.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity(), (a, b) -> a));

        for (ReceivePurchaseOrderItemRequest r : request.items()) {
            PurchaseOrderItem item = itemMap.get(r.purchaseOrderItemId());
            if (item == null) {
                throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ITEM);
            }
            BigDecimal remaining = item.getQuantity().subtract(item.getReceivedQuantity());
            if (r.receivedQuantity().compareTo(remaining) > 0) {
                throw new BaseException(ErrorCode.PROC_400_PO_RECEIVED_EXCEED);
            }
            item.setReceivedQuantity(item.getReceivedQuantity().add(r.receivedQuantity()));
        }
        items = purchaseOrderItemRepository.saveAll(items);

        boolean allReceived = items.stream().allMatch(i -> i.getReceivedQuantity().compareTo(i.getQuantity()) >= 0);
        boolean anyReceived = items.stream().anyMatch(i -> i.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (allReceived) {
            po.setStatus(STATUS_RECEIVED);
        } else if (anyReceived) {
            po.setStatus(STATUS_PARTIALLY_RECEIVED);
        }
        po = purchaseOrderRepository.save(po);
        return toResponseWithNames(po, items);
    }

    private PurchaseOrder findById(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.PROC_404_PO_NOT_FOUND));
    }

    private List<PurchaseOrderItem> buildItems(UUID purchaseOrderId, List<PurchaseOrderItemRequest> requests) {
        return requests.stream().map(r -> {
            if (!materialRepository.existsById(r.materialId())) {
                throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ITEM);
            }
            if (!unitRepository.existsById(r.unitId())) {
                throw new BaseException(ErrorCode.PROC_400_PO_INVALID_ITEM);
            }
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrderId(purchaseOrderId);
            item.setMaterialId(r.materialId());
            item.setUnitId(r.unitId());
            item.setQuantity(r.quantity());
            item.setUnitPrice(r.unitPrice());
            item.setTotalPrice(r.quantity().multiply(r.unitPrice()));
            item.setReceivedQuantity(BigDecimal.ZERO);
            item.setStatus("ACTIVE");
            return item;
        }).toList();
    }

    private void recalculateTotals(PurchaseOrder po, List<PurchaseOrderItem> items) {
        BigDecimal subtotal = items.stream()
                .map(i -> i.getTotalPrice() != null ? i.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setSubtotalAmount(subtotal);
        po.setTotalAmount(subtotal);
    }

    private String generatePoCode() {
        String prefix = "PO-" + LocalDate.now().format(PO_CODE_MONTH_FMT) + "-";
        Page<PurchaseOrder> last = purchaseOrderRepository.findFirstByPoCodeStartingWithOrderByPoCodeDesc(
                prefix, PageRequest.of(0, 1));
        int next = 1;
        if (!last.isEmpty()) {
            String code = last.getContent().get(0).getPoCode();
            try {
                next = Integer.parseInt(code.substring(prefix.length())) + 1;
            } catch (RuntimeException e) {
                next = 1;
            }
        }
        return prefix + String.format("%04d", next);
    }

    private PurchaseOrderResponse toResponseWithNames(PurchaseOrder po, List<PurchaseOrderItem> items) {
        Map<UUID, Supplier> supplierMap = po.getSupplierId() == null ? Map.of()
                : toMap(supplierRepository.findAllById(List.of(po.getSupplierId())), Supplier::getId);
        Map<UUID, Warehouse> warehouseMap = po.getWarehouseId() == null ? Map.of()
                : toMap(warehouseRepository.findAllById(List.of(po.getWarehouseId())), Warehouse::getId);
        Map<UUID, Account> accountMap = po.getApprovedBy() == null ? Map.of()
                : toMap(accountRepository.findAllById(List.of(po.getApprovedBy())), Account::getId);
        Map<UUID, Material> materialMap = toMap(materialRepository.findAllById(
                distinctNonNull(items, PurchaseOrderItem::getMaterialId)), Material::getId);
        Map<UUID, Unit> unitMap = toMap(unitRepository.findAllById(
                distinctNonNull(items, PurchaseOrderItem::getUnitId)), Unit::getId);
        return toResponse(po, items, supplierMap, warehouseMap, accountMap, materialMap, unitMap);
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po, List<PurchaseOrderItem> items,
                                             Map<UUID, Supplier> supplierMap, Map<UUID, Warehouse> warehouseMap,
                                             Map<UUID, Account> accountMap, Map<UUID, Material> materialMap,
                                             Map<UUID, Unit> unitMap) {
        List<PurchaseOrderItemResponse> itemResponses = (items == null ? List.<PurchaseOrderItem>of() : items).stream()
                .map(i -> {
                    Material m = i.getMaterialId() != null ? materialMap.get(i.getMaterialId()) : null;
                    Unit u = i.getUnitId() != null ? unitMap.get(i.getUnitId()) : null;
                    return purchaseOrderItemMapper.toResponse(
                            i,
                            m != null ? m.getCode() : null,
                            m != null ? m.getName() : null,
                            u != null ? u.getName() : null);
                }).toList();

        PurchaseOrderSupplierResponse supplier = toSupplierResponse(supplierMap, po.getSupplierId());
        PurchaseOrderWarehouseResponse warehouse = toWarehouseResponse(warehouseMap, po.getWarehouseId());
        ApprovedByResponse approvedBy = toApprovedByResponse(accountMap, po.getApprovedBy());
        return purchaseOrderMapper.toResponse(po, supplier, warehouse, approvedBy, itemResponses);
    }

    private PurchaseOrderSupplierResponse toSupplierResponse(Map<UUID, Supplier> map, UUID id) {
        if (id == null) {
            return null;
        }
        Supplier s = map.get(id);
        return s == null ? null : new PurchaseOrderSupplierResponse(s.getId().toString(), s.getCode(), s.getName());
    }

    private PurchaseOrderWarehouseResponse toWarehouseResponse(Map<UUID, Warehouse> map, UUID id) {
        if (id == null) {
            return null;
        }
        Warehouse w = map.get(id);
        return w == null ? null : new PurchaseOrderWarehouseResponse(w.getId().toString(), w.getCode(), w.getName());
    }

    private ApprovedByResponse toApprovedByResponse(Map<UUID, Account> map, UUID id) {
        if (id == null) {
            return null;
        }
        Account a = map.get(id);
        return a == null ? null : new ApprovedByResponse(a.getId().toString(), a.getFullName());
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
