package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.StockOutItemMapper;
import com.erp.backend_service.mapper.StockOutMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.StockOutItemRepository;
import com.erp.backend_service.repository.StockOutRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.StockOutService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.StockOut;
import com.erp.core.domain.StockOutItem;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateStockOutRequest;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.request.inv.StockOutItemRequest;
import com.erp.core.dto.request.inv.UpdateStockOutRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockOutItemResponse;
import com.erp.core.dto.response.inv.StockOutResponse;
import com.erp.core.dto.response.inv.StockOutUserResponse;
import com.erp.core.dto.response.inv.StockOutWarehouseResponse;
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
import java.util.UUID;
import java.util.function.Function;

/**
 * Triển khai {@link StockOutService} theo SRS INV: phiếu xuất được lưu DRAFT, khi xác nhận
 * (POSTED) sẽ giảm tồn kho và ghi người/thời điểm xác nhận; tồn kho không được phép âm;
 * hủy (CANCELLED) không làm thay đổi tồn kho.
 */
@Service
public class StockOutServiceImpl implements StockOutService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final List<String> VALID_STATUSES = List.of("DRAFT", "POSTED", "CANCELLED");
    private static final List<String> VALID_DESTINATION_TYPES = List.of(
            "PRODUCTION_ISSUE", "TRANSFER_OUT", "ADJUSTMENT", "WASTAGE", "BRANCH_ISSUE");
    private static final DateTimeFormatter CODE_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final StockOutRepository stockOutRepository;
    private final StockOutItemRepository stockOutItemRepository;
    private final MaterialStockBalanceRepository materialStockBalanceRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final AccountRepository accountRepository;
    private final StockOutMapper stockOutMapper;
    private final StockOutItemMapper stockOutItemMapper;
    private final DataScopeHelper dataScopeHelper;

    public StockOutServiceImpl(StockOutRepository stockOutRepository,
                               StockOutItemRepository stockOutItemRepository,
                               MaterialStockBalanceRepository materialStockBalanceRepository,
                               WarehouseRepository warehouseRepository,
                               MaterialRepository materialRepository,
                               AccountRepository accountRepository,
                               StockOutMapper stockOutMapper,
                               StockOutItemMapper stockOutItemMapper,
                               DataScopeHelper dataScopeHelper) {
        this.stockOutRepository = stockOutRepository;
        this.stockOutItemRepository = stockOutItemRepository;
        this.materialStockBalanceRepository = materialStockBalanceRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.accountRepository = accountRepository;
        this.stockOutMapper = stockOutMapper;
        this.stockOutItemMapper = stockOutItemMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockOutResponse> list(int page, int size, String search, String status,
                                               UUID warehouseId, String destinationType, LocalDate fromDate, LocalDate toDate) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_INVALID_FILTER);
        }
        validateFilterValues(status, destinationType);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());

        Collection<UUID> allowedWarehouseIds = dataScopeHelper.getAllowedWarehouseIds(warehouseId);
        if (allowedWarehouseIds != null && allowedWarehouseIds.isEmpty()) {
            return new PageResponse<>(page, safeSize, 0L, 0, List.of());
        }

        Page<StockOut> pageResult = stockOutRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, status,
                warehouseId, allowedWarehouseIds, destinationType, fromDate, toDate, pageable);

        List<StockOut> stockOuts = pageResult.getContent();

        Map<UUID, List<StockOutItem>> itemsByStockOut = new HashMap<>();
        List<StockOutItem> allItems = new ArrayList<>();
        for (StockOut so : stockOuts) {
            List<StockOutItem> items = stockOutItemRepository.findByStockOutId(so.getId());
            itemsByStockOut.put(so.getId(), items);
            allItems.addAll(items);
        }

        Map<UUID, Warehouse> warehouseMap = toMap(
                warehouseRepository.findAllById(distinctNonNull(stockOuts, StockOut::getWarehouseId)), Warehouse::getId);
        Map<UUID, Account> accountMap = toMap(
                accountRepository.findAllById(distinctNonNull(stockOuts, StockOut::getIssuedBy)), Account::getId);
        Map<UUID, Material> materialMap = toMap(
                materialRepository.findAllById(distinctNonNull(allItems, StockOutItem::getMaterialId)), Material::getId);

        List<StockOutResponse> content = stockOuts.stream()
                .map(so -> toResponse(so, itemsByStockOut.get(so.getId()), warehouseMap, accountMap, materialMap))
                .toList();

        return new PageResponse<>(pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages(), content);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public StockOutResponse get(UUID id) {
        StockOut stockOut = findById(id);
        dataScopeHelper.enforceWarehouseAccess(stockOut.getWarehouseId());
        return toResponseWithNames(stockOut, stockOutItemRepository.findByStockOutId(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StockOutResponse create(CreateStockOutRequest request) {
        validateWarehouse(request.warehouseId());
        validateDestinationType(request.destinationType());
        if (request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_ITEMS_EMPTY);
        }
        validateItems(request.items());

        StockOut stockOut = new StockOut();
        stockOut.setCode(generateStockOutCode());
        stockOut.setWarehouseId(request.warehouseId());
        stockOut.setDestinationType(request.destinationType());
        stockOut.setDestinationReferenceId(request.destinationReferenceId());
        stockOut.setOutDate(request.outDate() != null ? request.outDate() : LocalDate.now());
        stockOut.setNote(request.note());
        stockOut.setStatus(STATUS_DRAFT);
        stockOut = stockOutRepository.save(stockOut);

        List<StockOutItem> items = stockOutItemRepository.saveAll(buildItems(stockOut.getId(), request.items()));
        return toResponseWithNames(stockOut, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StockOutResponse update(UUID id, UpdateStockOutRequest request) {
        StockOut stockOut = findById(id);
        dataScopeHelper.enforceWarehouseAccess(stockOut.getWarehouseId());
        if (!STATUS_DRAFT.equals(stockOut.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_INVALID_STATUS_FOR_EDIT);
        }
        validateWarehouse(request.warehouseId());
        validateDestinationType(request.destinationType());
        if (request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_ITEMS_EMPTY);
        }
        validateItems(request.items());

        stockOut.setWarehouseId(request.warehouseId());
        stockOut.setDestinationType(request.destinationType());
        stockOut.setDestinationReferenceId(request.destinationReferenceId());
        stockOut.setOutDate(request.outDate() != null ? request.outDate() : LocalDate.now());
        stockOut.setNote(request.note());

        stockOutItemRepository.deleteByStockOutId(id);
        List<StockOutItem> items = stockOutItemRepository.saveAll(buildItems(stockOut.getId(), request.items()));
        stockOut = stockOutRepository.save(stockOut);
        return toResponseWithNames(stockOut, items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StockOutResponse changeStatus(UUID id, StatusUpdateRequest request) {
        StockOut stockOut = findById(id);
        dataScopeHelper.enforceWarehouseAccess(stockOut.getWarehouseId());
        if (!STATUS_DRAFT.equals(stockOut.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_INVALID_STATUS);
        }
        String target = request.status() == null ? null : request.status().trim().toUpperCase();
        if (!STATUS_POSTED.equals(target) && !STATUS_CANCELLED.equals(target)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_INVALID_STATUS);
        }

        List<StockOutItem> items = stockOutItemRepository.findByStockOutId(id);

        if (STATUS_POSTED.equals(target)) {
            if (items.isEmpty()) {
                throw new BaseException(ErrorCode.INV_400_STOCK_OUT_ITEMS_EMPTY);
            }
            applyStockOutQuantity(stockOut, items);
            stockOut.setIssuedBy(SecurityUtils.getCurrentPrincipalId().orElse(null));
            stockOut.setPostedAt(Instant.now());
        }
        stockOut.setStatus(target);
        stockOut = stockOutRepository.save(stockOut);
        return toResponseWithNames(stockOut, items);
    }

    private void validateFilterValues(String status, String destinationType) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_INVALID_FILTER);
        }
        if (destinationType != null && !VALID_DESTINATION_TYPES.contains(destinationType)) {
            throw new BaseException(ErrorCode.INV_400_STOCK_OUT_INVALID_FILTER);
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

    private void validateDestinationType(String destinationType) {
        if (destinationType == null || !VALID_DESTINATION_TYPES.contains(destinationType)) {
            throw new BaseException(ErrorCode.INV_400_INVALID_DESTINATION_TYPE);
        }
    }

    private void validateItems(List<StockOutItemRequest> itemRequests) {
        for (StockOutItemRequest r : itemRequests) {
            Material material = materialRepository.findById(r.materialId())
                    .orElseThrow(() -> new BaseException(ErrorCode.INV_404_MATERIAL_NOT_FOUND));
            if (!EntityStatus.ACTIVE.name().equals(material.getStatus())) {
                throw new BaseException(ErrorCode.INV_404_MATERIAL_NOT_FOUND);
            }
        }
    }

    private void applyStockOutQuantity(StockOut stockOut, List<StockOutItem> items) {
        Map<UUID, BigDecimal> requiredByMaterial = new HashMap<>();
        for (StockOutItem item : items) {
            requiredByMaterial.merge(item.getMaterialId(), item.getQuantity(), BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> entry : requiredByMaterial.entrySet()) {
            MaterialStockBalance balance = materialStockBalanceRepository
                    .findByWarehouseIdAndMaterialId(stockOut.getWarehouseId(), entry.getKey())
                    .orElse(null);
            BigDecimal current = balance == null ? BigDecimal.ZERO : balance.getQuantityOnHand();
            if (current.compareTo(entry.getValue()) < 0) {
                throw new BaseException(ErrorCode.INV_400_INSUFFICIENT_STOCK);
            }
            MaterialStockBalance target = balance != null ? balance
                    : createBalance(stockOut.getWarehouseId(), entry.getKey());
            target.setQuantityOnHand(current.subtract(entry.getValue()));
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

    private List<StockOutItem> buildItems(UUID stockOutId, List<StockOutItemRequest> requests) {
        return requests.stream().map(r -> {
            StockOutItem item = new StockOutItem();
            item.setStockOutId(stockOutId);
            item.setMaterialId(r.materialId());
            item.setQuantity(r.quantity());
            item.setUnitPrice(r.unitPrice() != null ? r.unitPrice() : BigDecimal.ZERO);
            item.setBatchNo(r.batchNo());
            item.setStatus("ACTIVE");
            return item;
        }).toList();
    }

    private String generateStockOutCode() {
        String prefix = "SO-" + LocalDate.now().format(CODE_MONTH_FMT) + "-";
        Page<StockOut> last = stockOutRepository.findFirstByCodeStartingWithOrderByCodeDesc(prefix, PageRequest.of(0, 1));
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

    private StockOut findById(UUID id) {
        return stockOutRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_STOCK_OUT_NOT_FOUND));
    }

    private StockOutResponse toResponseWithNames(StockOut stockOut, List<StockOutItem> items) {
        Map<UUID, Warehouse> warehouseMap = toMap(
                warehouseRepository.findAllById(List.of(stockOut.getWarehouseId())), Warehouse::getId);
        Map<UUID, Account> accountMap = stockOut.getIssuedBy() == null ? Map.of()
                : toMap(accountRepository.findAllById(List.of(stockOut.getIssuedBy())), Account::getId);
        Map<UUID, Material> materialMap = toMap(
                materialRepository.findAllById(distinctNonNull(items, StockOutItem::getMaterialId)), Material::getId);
        return toResponse(stockOut, items, warehouseMap, accountMap, materialMap);
    }

    private StockOutResponse toResponse(StockOut stockOut, List<StockOutItem> items,
                                        Map<UUID, Warehouse> warehouseMap, Map<UUID, Account> accountMap,
                                        Map<UUID, Material> materialMap) {
        Warehouse warehouse = stockOut.getWarehouseId() != null ? warehouseMap.get(stockOut.getWarehouseId()) : null;
        Account issuedBy = stockOut.getIssuedBy() != null ? accountMap.get(stockOut.getIssuedBy()) : null;

        List<StockOutItemResponse> itemResponses = (items == null ? List.<StockOutItem>of() : items).stream()
                .map(i -> {
                    Material m = i.getMaterialId() != null ? materialMap.get(i.getMaterialId()) : null;
                    return stockOutItemMapper.toResponse(
                            i, m != null ? m.getCode() : null, m != null ? m.getName() : null);
                }).toList();

        return stockOutMapper.toResponse(stockOut,
                warehouse != null ? new StockOutWarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName()) : null,
                issuedBy != null ? new StockOutUserResponse(issuedBy.getId(), issuedBy.getFullName()) : null,
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