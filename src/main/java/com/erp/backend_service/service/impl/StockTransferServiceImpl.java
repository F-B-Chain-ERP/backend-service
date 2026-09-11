package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.StockTransferService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.inv.*;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockTransferItemResponse;
import com.erp.core.dto.response.inv.StockTransferResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.erp.backend_service.util.CodeGenerator;
import java.util.*;

import java.util.stream.Collectors;

@Service
public class StockTransferServiceImpl implements StockTransferService {

    private static final String PENDING = "PENDING";
    private static final String IN_TRANSIT = "IN_TRANSIT";
    private static final String RECEIVED = "RECEIVED";
    private static final String CANCELLED = "CANCELLED";
    private static final String POSTED = "POSTED";
    private static final String TRANSFER_OUT = "TRANSFER_OUT";
    private static final String TRANSFER_IN = "TRANSFER_IN";

    private final StockTransferRepository transferRepository;
    private final StockTransferItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final StockInRepository stockInRepository;
    private final StockOutRepository stockOutRepository;
    private final StockCountRepository stockCountRepository;
    private final DataScopeHelper dataScopeHelper;
    private final StockBalanceMutationService balanceMutationService;

    @PersistenceContext
    private EntityManager entityManager;

    public StockTransferServiceImpl(StockTransferRepository transferRepository, StockTransferItemRepository itemRepository, WarehouseRepository warehouseRepository, MaterialRepository materialRepository, StockInRepository stockInRepository, StockOutRepository stockOutRepository, StockCountRepository stockCountRepository, DataScopeHelper dataScopeHelper, StockBalanceMutationService balanceMutationService) {
        this.transferRepository = transferRepository;
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.stockInRepository = stockInRepository;
        this.stockOutRepository = stockOutRepository;
        this.stockCountRepository = stockCountRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.balanceMutationService = balanceMutationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockTransferResponse> list(int page, int size, String search, String status, UUID warehouseId) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);

        Collection<UUID> allowed = dataScopeHelper.getAllowedWarehouseIds(warehouseId);

        if (allowed != null && allowed.isEmpty()) {
            return new PageResponse<>(page, size, 0, 0, List.of());
        }

        Page<StockTransfer> result = transferRepository.search(blankToNull(search), blankToNull(status), warehouseId, allowed, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<StockTransferResponse> content = toResponseBatch(result.getContent());

        return new PageResponse<>(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), content);
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferResponse get(UUID id) {
        return toResponse(findAccessible(id));
    }

    @Override
    @Transactional
    public StockTransferResponse create(CreateStockTransferRequest request) {
        validateCreateRequest(request);

        Warehouse from = getActiveWarehouse(request.fromWarehouseId());
        Warehouse to = getActiveWarehouse(request.toWarehouseId());

        dataScopeHelper.enforceWarehouseAccess(from.getId());
        dataScopeHelper.enforceWarehouseAccess(to.getId());

        validateItems(request.items());

        // Mã phiếu do hệ thống tự sinh, không nhận tay.
        String code = generateCode();

        if (transferRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }

        StockTransfer transfer = new StockTransfer();

        transfer.setCode(code);
        transfer.setFromWarehouseId(from.getId());
        transfer.setToWarehouseId(to.getId());
        transfer.setTransferDate(request.transferDate() == null ? LocalDate.now() : request.transferDate());
        transfer.setNote(request.note());
        transfer.setStatus(PENDING);

        transfer = transferRepository.save(transfer);

        saveItems(transfer, request.items());

        return toResponse(transfer);
    }

    @Override
    @Transactional
    public StockTransferResponse update(UUID id, UpdateStockTransferRequest request) {
        StockTransfer transfer = findAccessibleForUpdate(id);

        if (request == null || request.fromWarehouseId() == null || request.toWarehouseId() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        if (!PENDING.equals(transfer.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_TRANSFER_CANNOT_EDIT);
        }

        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_SAME);
        }

        Warehouse from = getActiveWarehouse(request.fromWarehouseId());
        Warehouse to = getActiveWarehouse(request.toWarehouseId());

        dataScopeHelper.enforceWarehouseAccess(from.getId());
        dataScopeHelper.enforceWarehouseAccess(to.getId());

        validateItems(request.items());

        transfer.setFromWarehouseId(from.getId());
        transfer.setToWarehouseId(to.getId());

        if (request.transferDate() != null) {
            transfer.setTransferDate(request.transferDate());
        }

        transfer.setNote(request.note());

        transferRepository.save(transfer);

        itemRepository.deleteByStockTransferId(id);

        saveItems(transfer, request.items());

        return toResponse(transfer);
    }

    @Override
    @Transactional
    public StockTransferResponse dispatch(UUID id) {
        StockTransfer transfer = findFromForUpdate(id);

        /*
         * Idempotency level 1:
         * Nếu request dispatch được retry sau khi
         * đã thành công thì không được trừ tồn lần 2.
         */
        if (IN_TRANSIT.equals(transfer.getStatus())) {
            return toResponse(transfer);
        }

        if (!PENDING.equals(transfer.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_STATUS);
        }

        // Kho nguồn/đích có thể đã bị khóa sau lúc tạo phiếu — kiểm tra lại trước khi xuất.
        getActiveWarehouse(transfer.getFromWarehouseId());
        getActiveWarehouse(transfer.getToWarehouseId());

        // Kho nguồn đang kiểm kê thì không cho xuất đi (số chốt sẽ sai).
        if (stockCountRepository.existsCounting(transfer.getFromWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_COUNTING);
        }

        List<StockTransferItem> items = itemRepository.findByStockTransferId(id);

        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        /*
         * Kiểm tra toàn bộ tồn trước khi thay đổi
         * bất kỳ dữ liệu nào.
         */
        for (StockTransferItem item : items) {
            MaterialStockBalance balance = balanceMutationService.lockOrCreate(transfer.getFromWarehouseId(), item.getMaterialId());

            BigDecimal onHand = nvl(balance.getQuantityOnHand());
            BigDecimal reserved = nvl(balance.getQuantityReserved());
            BigDecimal available = onHand.subtract(reserved);

            if (available.compareTo(item.getQuantity()) < 0) {
                throw new BaseException(ErrorCode.INV_400_INSUFFICIENT_STOCK);
            }
        }

        /*
         * Tạo chứng từ STOCK_OUT.
         */
        StockOut stockOut = new StockOut();

        stockOut.setCode(generateStockOutCode());
        stockOut.setWarehouseId(transfer.getFromWarehouseId());
        stockOut.setDestinationType(TRANSFER_OUT);
        stockOut.setDestinationReferenceId(transfer.getId());
        stockOut.setOutDate(transfer.getTransferDate());
        stockOut.setNote("Stock out for transfer " + transfer.getCode());
        stockOut.setStatus(POSTED);
        stockOut.setIssuedBy(currentUserId());
        stockOut.setPostedAt(Instant.now());

        entityManager.persist(stockOut);

        /*
         * Tạo stock_out_item + giảm balance.
         */
        for (StockTransferItem transferItem : items) {
            StockOutItem outItem = new StockOutItem();

            outItem.setStockOutId(stockOut.getId());
            outItem.setMaterialId(transferItem.getMaterialId());
            outItem.setQuantity(transferItem.getQuantity());
            outItem.setUnitPrice(nvl(transferItem.getUnitPrice()));
            outItem.setStatus("ACTIVE");

            entityManager.persist(outItem);

            balanceMutationService.decrease(transfer.getFromWarehouseId(), transferItem.getMaterialId(), transferItem.getQuantity());
        }

        transfer.setStatus(IN_TRANSIT);

        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    @Override
    @Transactional
    public StockTransferResponse receive(UUID id, ReceiveStockTransferRequest request) {
        StockTransfer transfer = findToForUpdate(id);

        if (!IN_TRANSIT.equals(transfer.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_TRANSFER_NOT_RECEIVABLE);
        }

        // Kho đích có thể đã bị khóa giữa đường — không nhập vào kho ngừng hoạt động.
        getActiveWarehouse(transfer.getToWarehouseId());

        // Kho đích đang kiểm kê thì không cho nhập vào (số chốt sẽ sai).
        if (stockCountRepository.existsCounting(transfer.getToWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_COUNTING);
        }

        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        Map<UUID, StockTransferItem> itemMap = itemRepository.findByStockTransferId(id).stream().collect(Collectors.toMap(StockTransferItem::getId, item -> item));

        if (itemMap.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        Set<UUID> receivedItemIds = new HashSet<>();
        Map<StockTransferItem, BigDecimal> toApply = new LinkedHashMap<>();

        /*
         * Kiểm tra toàn bộ đợt nhận trước: cộng dồn vào số đã nhận,
         * không cho vượt số lượng chuyển của từng dòng (cho phép nhận thiếu).
         */
        for (ReceiveStockTransferItemRequest req : request.items()) {
            if (req == null || req.itemId() == null || !receivedItemIds.add(req.itemId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }

            if (req.receivedQuantity() == null || req.receivedQuantity().signum() <= 0) {
                throw new BaseException(ErrorCode.INV_400_RECEIVE_EXCEED);
            }

            StockTransferItem item = itemMap.get(req.itemId());

            if (item == null) {
                throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            if (nvl(item.getReceivedQuantity()).add(req.receivedQuantity()).compareTo(item.getQuantity()) > 0) {
                throw new BaseException(ErrorCode.INV_400_RECEIVE_EXCEED);
            }

            toApply.put(item, req.receivedQuantity());
        }

        /*
         * Tạo STOCK_IN cho đợt nhận này (mỗi đợt một phiếu SI- riêng).
         */
        StockIn stockIn = new StockIn();

        stockIn.setCode(generateStockInCode());
        stockIn.setWarehouseId(transfer.getToWarehouseId());
        stockIn.setSourceType(TRANSFER_IN);
        stockIn.setSourceReferenceId(transfer.getId());
        stockIn.setInDate(LocalDate.now());
        stockIn.setNote("Stock in for transfer " + transfer.getCode());
        stockIn.setStatus(POSTED);
        stockIn.setReceivedBy(currentUserId());
        stockIn.setPostedAt(Instant.now());

        entityManager.persist(stockIn);

        /*
         * Ghi từng dòng nhận: cộng dồn số đã nhận + tăng tồn kho đích.
         */
        for (Map.Entry<StockTransferItem, BigDecimal> entry : toApply.entrySet()) {
            StockTransferItem item = entry.getKey();
            BigDecimal receivedQty = entry.getValue();

            item.setReceivedQuantity(nvl(item.getReceivedQuantity()).add(receivedQty));

            itemRepository.save(item);

            StockInItem inItem = new StockInItem();

            inItem.setStockInId(stockIn.getId());
            inItem.setMaterialId(item.getMaterialId());
            inItem.setQuantity(receivedQty);
            inItem.setUnitPrice(nvl(item.getUnitPrice()));
            inItem.setStatus("ACTIVE");

            entityManager.persist(inItem);

            balanceMutationService.increase(transfer.getToWarehouseId(), item.getMaterialId(), receivedQty);
        }

        /*
         * Đủ hết các dòng mới đóng phiếu; còn thiếu thì giữ IN_TRANSIT
         * để nhận tiếp đợt sau (phần thiếu xử lý dứt điểm khi void).
         */
        boolean fullyReceived = itemMap.values().stream().allMatch(item -> nvl(item.getReceivedQuantity()).compareTo(item.getQuantity()) >= 0);

        if (fullyReceived) {
            transfer.setStatus(RECEIVED);
            transfer.setReceivedBy(currentUserId());
            transfer.setReceivedAt(Instant.now());

            transferRepository.save(transfer);
        }

        return toResponse(transfer);
    }

    /**
     * Hủy phiếu chuyển. PENDING hủy tự do; IN_TRANSIT bắt buộc kèm lý do và
     * hoàn phần chưa nhận về kho nguồn (tránh mất hàng sổ sách).
     */
    @Override
    @Transactional
    public StockTransferResponse cancel(UUID id, String reason) {
        StockTransfer transfer = transferRepository.findByIdForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND));

        if (PENDING.equals(transfer.getStatus())) {
            if (!hasWarehouseAccess(transfer.getFromWarehouseId()) && !hasWarehouseAccess(transfer.getToWarehouseId())) {
                throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
            }
            transfer.setStatus(CANCELLED);
            transferRepository.save(transfer);
            return toResponse(transfer);
        }

        if (!IN_TRANSIT.equals(transfer.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_STATUS);
        }
        // Hủy khi đang đi đường hoàn tồn về nguồn -> yêu cầu quyền kho nguồn
        dataScopeHelper.enforceWarehouseAccess(transfer.getFromWarehouseId());

        if (reason == null || reason.isBlank()) {
            throw new BaseException(ErrorCode.INV_400_TRANSFER_CANCEL_REASON_REQUIRED);
        }

        List<StockTransferItem> items = itemRepository.findByStockTransferId(id);
        Map<UUID, BigDecimal> missingByMaterial = new LinkedHashMap<>();
        for (StockTransferItem it : items) {
            BigDecimal missing = nvl(it.getQuantity()).subtract(nvl(it.getReceivedQuantity()));
            if (missing.signum() > 0) {
                missingByMaterial.merge(it.getMaterialId(), missing, BigDecimal::add);
            }
        }
        if (!missingByMaterial.isEmpty()) {
            StockIn returnIn = new StockIn();
            returnIn.setCode(generateStockInCode());
            returnIn.setWarehouseId(transfer.getFromWarehouseId());
            returnIn.setSourceType(TRANSFER_IN);
            returnIn.setSourceReferenceId(transfer.getId());
            returnIn.setInDate(LocalDate.now());
            returnIn.setNote("Return unreceived qty on cancel transfer " + transfer.getCode() + ": " + reason.trim());
            returnIn.setStatus(POSTED);
            returnIn.setReceivedBy(currentUserId());
            returnIn.setPostedAt(Instant.now());
            entityManager.persist(returnIn);
            for (Map.Entry<UUID, BigDecimal> e : missingByMaterial.entrySet()) {
                StockInItem ri = new StockInItem();
                ri.setStockInId(returnIn.getId());
                ri.setMaterialId(e.getKey());
                ri.setQuantity(e.getValue());
                ri.setUnitPrice(BigDecimal.ZERO);
                ri.setStatus("ACTIVE");
                entityManager.persist(ri);
                balanceMutationService.increase(transfer.getFromWarehouseId(), e.getKey(), e.getValue());
            }
        }

        transfer.setStatus(CANCELLED);
        transfer.setNote(buildVoidNote(reason, transfer.getNote()));

        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    /** Ghi lý do void vào note (varchar 500), cắt ngắn để không vỡ DB. */
    private String buildVoidNote(String reason, String existingNote) {
        String suffix = (existingNote == null || existingNote.isBlank()) ? "" : " | " + existingNote;
        int maxReason = 500 - "Void in-transit []".length() - suffix.length();
        String trimmed = reason.trim();
        if (trimmed.length() > maxReason) {
            trimmed = trimmed.substring(0, Math.max(maxReason, 0));
        }
        return "Void in-transit [" + trimmed + "]" + suffix;
    }

    private void saveItems(StockTransfer transfer, List<StockTransferItemRequest> requests) {
        Set<UUID> materials = new HashSet<>();
        List<StockTransferItem> items = new ArrayList<>();

        for (StockTransferItemRequest req : requests) {
            if (!materials.add(req.materialId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }

            if (req.quantity() == null || req.quantity().signum() <= 0) {
                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            com.erp.core.domain.Material mat = materialRepository.findById(req.materialId()).orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));
            if (!"ACTIVE".equals(mat.getStatus())) {
                throw new BaseException(ErrorCode.MATERIAL_NOT_FOUND);
            }

            StockTransferItem item = new StockTransferItem();

            item.setStockTransferId(transfer.getId());
            item.setMaterialId(req.materialId());
            item.setQuantity(req.quantity());
            item.setReceivedQuantity(BigDecimal.ZERO);
            item.setUnitPrice(nvl(req.unitPrice()));
            item.setStatus("ACTIVE");

            items.add(item);
        }

        itemRepository.saveAll(items);
    }

    private void validateCreateRequest(CreateStockTransferRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        if (request.fromWarehouseId() == null || request.toWarehouseId() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_SAME);
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }
    }

    private void validateItems(List<StockTransferItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        Set<UUID> materialIds = new HashSet<>();

        for (StockTransferItemRequest item : items) {
            if (item.materialId() == null || item.quantity() == null || item.quantity().signum() <= 0) {
                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            if (!materialIds.add(item.materialId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }
        }
    }

    private Warehouse getActiveWarehouse(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_WAREHOUSE_NOT_FOUND));

        if (!"ACTIVE".equals(warehouse.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_WAREHOUSE_INACTIVE);
        }

        return warehouse;
    }

    private StockTransfer findAccessible(UUID id) {
        StockTransfer transfer = transferRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND));

        // Xem phiếu: chỉ cần quyền 1 trong 2 kho (bên gửi hoặc bên nhận)
        if (!hasWarehouseAccess(transfer.getFromWarehouseId()) && !hasWarehouseAccess(transfer.getToWarehouseId())) {
            throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
        }

        return transfer;
    }

    private StockTransfer findAccessibleForUpdate(UUID id) {
        StockTransfer transfer = transferRepository.findByIdForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND));

        dataScopeHelper.enforceWarehouseAccess(transfer.getFromWarehouseId());
        dataScopeHelper.enforceWarehouseAccess(transfer.getToWarehouseId());

        return transfer;
    }

    /** Xuất kho: chỉ cần quyền kho nguồn. */
    private StockTransfer findFromForUpdate(UUID id) {
        StockTransfer transfer = transferRepository.findByIdForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND));
        dataScopeHelper.enforceWarehouseAccess(transfer.getFromWarehouseId());
        return transfer;
    }

    /** Nhận kho: chỉ cần quyền kho đích. */
    private StockTransfer findToForUpdate(UUID id) {
        StockTransfer transfer = transferRepository.findByIdForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND));
        dataScopeHelper.enforceWarehouseAccess(transfer.getToWarehouseId());
        return transfer;
    }

    private boolean hasWarehouseAccess(UUID warehouseId) {
        try {
            dataScopeHelper.enforceWarehouseAccess(warehouseId);
            return true;
        } catch (BaseException e) {
            return false;
        }
    }

    private StockTransferResponse toResponse(StockTransfer transfer) {
        return toResponseBatch(List.of(transfer)).get(0);
    }

    private List<StockTransferResponse> toResponseBatch(List<StockTransfer> transfers) {
        if (transfers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = transfers.stream().map(StockTransfer::getId).toList();
        Map<UUID, List<StockTransferItem>> itemsByTransfer = new HashMap<>();
        for (StockTransferItem it : itemRepository.findByStockTransferIdIn(ids)) {
            itemsByTransfer.computeIfAbsent(it.getStockTransferId(), k -> new ArrayList<>()).add(it);
        }
        Set<UUID> warehouseIds = new HashSet<>();
        Set<UUID> materialIds = new HashSet<>();
        for (StockTransfer t : transfers) {
            warehouseIds.add(t.getFromWarehouseId());
            warehouseIds.add(t.getToWarehouseId());
        }
        for (List<StockTransferItem> items : itemsByTransfer.values()) {
            for (StockTransferItem it : items) {
                materialIds.add(it.getMaterialId());
            }
        }
        Map<UUID, Warehouse> warehouseMap = warehouseRepository.findAllById(warehouseIds.stream().filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, w -> w, (a, b) -> a));
        Map<UUID, Material> materialMap = materialRepository.findAllById(new ArrayList<>(materialIds))
                .stream().collect(Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
        List<StockTransferResponse> out = new ArrayList<>();
        for (StockTransfer transfer : transfers) {
            out.add(buildTransferResponse(transfer,
                    warehouseMap.get(transfer.getFromWarehouseId()),
                    warehouseMap.get(transfer.getToWarehouseId()),
                    itemsByTransfer.getOrDefault(transfer.getId(), List.of()),
                    materialMap));
        }
        return out;
    }

    private StockTransferResponse buildTransferResponse(StockTransfer transfer, Warehouse from, Warehouse to,
                                                        List<StockTransferItem> items, Map<UUID, Material> materials) {

        List<StockTransferItemResponse> itemResponses = items.stream().map(item -> {
            Material material = materials.get(item.getMaterialId());

            BigDecimal quantity = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();

            BigDecimal received = item.getReceivedQuantity() == null ? BigDecimal.ZERO : item.getReceivedQuantity();

            return new StockTransferItemResponse(item.getId(), item.getMaterialId(), material == null ? null : material.getCode(), material == null ? null : material.getName(), quantity, received, quantity.subtract(received), item.getUnitPrice());
        }).toList();

        return new StockTransferResponse(transfer.getId(), transfer.getCode(), transfer.getFromWarehouseId(), from == null ? null : from.getCode(), from == null ? null : from.getName(), transfer.getToWarehouseId(), to == null ? null : to.getCode(), to == null ? null : to.getName(), transfer.getTransferDate(), transfer.getStatus(), transfer.getNote(), transfer.getReceivedBy(), transfer.getReceivedAt(), itemResponses);
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentPrincipalId().orElse(null);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateCode() {
        return CodeGenerator.random("TRF-", transferRepository::existsByCode);
    }

    private String generateStockInCode() {
        return CodeGenerator.nextMonthlySequence(
                "SI-",
                prefix -> stockInRepository.findFirstByCodeStartingWithOrderByCodeDesc(prefix, PageRequest.of(0, 1))
                        .getContent().stream().findFirst().map(StockIn::getCode),
                stockInRepository::existsByCode);
    }

    private String generateStockOutCode() {
        return CodeGenerator.nextMonthlySequence(
                "SO-",
                prefix -> stockOutRepository.findFirstByCodeStartingWithOrderByCodeDesc(prefix, PageRequest.of(0, 1))
                        .getContent().stream().findFirst().map(StockOut::getCode),
                stockOutRepository::existsByCode);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}