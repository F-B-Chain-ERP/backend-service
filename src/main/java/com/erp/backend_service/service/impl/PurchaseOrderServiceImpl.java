package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.PurchaseOrderItemMapper;
import com.erp.backend_service.mapper.PurchaseOrderMapper;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.PurchaseOrderItemRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.PurchaseOrderService;
import com.erp.core.domain.Material;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.PurchaseOrderItem;
import com.erp.core.domain.Supplier;
import com.erp.core.domain.Unit;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.proc.CreatePurchaseOrderRequest;
import com.erp.core.dto.request.proc.PurchaseOrderItemRequest;
import com.erp.core.dto.request.proc.UpdatePurchaseOrderRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.PurchaseOrderItemResponse;
import com.erp.core.dto.response.PurchaseOrderResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link PurchaseOrderService}: quản lý đơn mua hàng với các chuyển trạng thái
 * (DRAFT -> SUBMITTED -> APPROVED, hoặc CANCELLED) và giải quyết tên cho response.
 */
@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseOrderItemRepository purchaseOrderItemRepository,
                                    SupplierRepository supplierRepository,
                                    WarehouseRepository warehouseRepository,
                                    MaterialRepository materialRepository,
                                    UnitRepository unitRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    PurchaseOrderItemMapper purchaseOrderItemMapper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderItemMapper = purchaseOrderItemMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(int page, int size, String search, String status) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<PurchaseOrder> pageResult = purchaseOrderRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, status, pageable);

        List<PurchaseOrder> pos = pageResult.getContent();
        Map<UUID, String> supplierNames = resolveMap(pos.stream().map(PurchaseOrder::getSupplierId).filter(Objects::nonNull).distinct().toList(), supplierRepository::findById, Supplier::getName);
        Map<UUID, String> warehouseNames = resolveMap(pos.stream().map(PurchaseOrder::getWarehouseId).filter(Objects::nonNull).distinct().toList(), warehouseRepository::findById, Warehouse::getName);

        List<PurchaseOrderResponse> content = pos.stream().map(po -> {
            List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
            return toResponse(po, items, supplierNames, warehouseNames, Map.of(), Map.of());
        }).toList();

        return new PageResponse<>(pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages(), content);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID id) {
        PurchaseOrder po = findById(id);
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(id);
        return toResponseWithNames(po, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        if (!supplierRepository.existsById(request.supplierId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!warehouseRepository.existsById(request.warehouseId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PurchaseOrder po = new PurchaseOrder();
        String poCode = StringUtils.hasText(request.poCode()) ? request.poCode() : generatePoCode();
        if (purchaseOrderRepository.existsByPoCode(poCode)) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
        po.setPoCode(poCode);
        po.setSupplierId(request.supplierId());
        po.setWarehouseId(request.warehouseId());
        po.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
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
        if (!STATUS_DRAFT.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        if (request.supplierId() != null && !supplierRepository.existsById(request.supplierId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (request.warehouseId() != null && !warehouseRepository.existsById(request.warehouseId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (request.supplierId() != null) {
            po.setSupplierId(request.supplierId());
        }
        if (request.warehouseId() != null) {
            po.setWarehouseId(request.warehouseId());
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

        List<PurchaseOrderItem> items;
        if (request.items() != null) {
            purchaseOrderItemRepository.deleteByPurchaseOrderId(id);
            items = buildItems(id, request.items());
            items = purchaseOrderItemRepository.saveAll(items);
        } else {
            items = purchaseOrderItemRepository.findByPurchaseOrderId(id);
        }
        recalculateTotals(po, items);
        po = purchaseOrderRepository.save(po);
        return toResponseWithNames(po, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        PurchaseOrder po = findById(id);
        if (!STATUS_DRAFT.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        purchaseOrderItemRepository.deleteByPurchaseOrderId(id);
        purchaseOrderRepository.deleteById(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse submit(UUID id) {
        PurchaseOrder po = findById(id);
        if (!STATUS_DRAFT.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        po.setStatus(STATUS_SUBMITTED);
        po.setSubmittedAt(Instant.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toResponseWithNames(saved, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse approve(UUID id) {
        PurchaseOrder po = findById(id);
        if (!STATUS_SUBMITTED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        UUID approver = SecurityUtils.getCurrentPrincipalId().orElse(null);
        po.setStatus(STATUS_APPROVED);
        po.setApprovedBy(approver);
        po.setApprovedAt(Instant.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toResponseWithNames(saved, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderResponse cancel(UUID id, String reason) {
        PurchaseOrder po = findById(id);
        if (STATUS_CANCELLED.equals(po.getStatus())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        po.setStatus(STATUS_CANCELLED);
        po.setCancelledAt(Instant.now());
        po.setCancelReason(reason);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toResponseWithNames(saved, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    private PurchaseOrder findById(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<PurchaseOrderItem> buildItems(UUID purchaseOrderId, List<PurchaseOrderItemRequest> requests) {
        return requests.stream().map(r -> {
            if (!materialRepository.existsById(r.materialId())) {
                throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!unitRepository.existsById(r.unitId())) {
                throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
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
        return "PO-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private PurchaseOrderResponse toResponseWithNames(PurchaseOrder po, List<PurchaseOrderItem> items) {
        Map<UUID, String> supplierNames = resolveMap(List.of(po.getSupplierId()), supplierRepository::findById, Supplier::getName);
        Map<UUID, String> warehouseNames = resolveMap(List.of(po.getWarehouseId()), warehouseRepository::findById, Warehouse::getName);
        Map<UUID, String> materialNames = resolveMap(
                items.stream().map(PurchaseOrderItem::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialRepository::findById, Material::getName);
        Map<UUID, String> unitNames = resolveMap(
                items.stream().map(PurchaseOrderItem::getUnitId).filter(Objects::nonNull).distinct().toList(),
                unitRepository::findById, Unit::getName);
        return toResponse(po, items, supplierNames, warehouseNames, materialNames, unitNames);
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po, List<PurchaseOrderItem> items,
                                             Map<UUID, String> supplierNames, Map<UUID, String> warehouseNames,
                                             Map<UUID, String> materialNames, Map<UUID, String> unitNames) {
        List<PurchaseOrderItemResponse> itemResponses = items.stream()
                .map(i -> purchaseOrderItemMapper.toResponse(
                        i,
                        i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : null,
                        i.getUnitId() != null ? unitNames.get(i.getUnitId()) : null))
                .toList();
        String supplierName = po.getSupplierId() != null ? supplierNames.get(po.getSupplierId()) : null;
        String warehouseName = po.getWarehouseId() != null ? warehouseNames.get(po.getWarehouseId()) : null;
        return purchaseOrderMapper.toResponse(po, supplierName, warehouseName, itemResponses);
    }

    private <T> Map<UUID, String> resolveMap(List<UUID> ids, Function<UUID, java.util.Optional<T>> finder, Function<T, String> nameExtractor) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ids.stream()
                .map(finder)
                .filter(Objects::nonNull)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(t -> getId(t), nameExtractor, (a, b) -> a));
    }

    private UUID getId(Object entity) {
        if (entity instanceof Supplier s) {
            return s.getId();
        }
        if (entity instanceof Warehouse w) {
            return w.getId();
        }
        if (entity instanceof Material m) {
            return m.getId();
        }
        if (entity instanceof Unit u) {
            return u.getId();
        }
        return null;
    }
}
