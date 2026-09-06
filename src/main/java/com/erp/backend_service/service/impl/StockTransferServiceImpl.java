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
    private final DataScopeHelper dataScopeHelper;
    private final StockBalanceMutationService balanceMutationService;

    @PersistenceContext
    private EntityManager entityManager;

    public StockTransferServiceImpl(
            StockTransferRepository transferRepository,
            StockTransferItemRepository itemRepository,
            WarehouseRepository warehouseRepository,
            MaterialRepository materialRepository,
            DataScopeHelper dataScopeHelper,
            StockBalanceMutationService balanceMutationService
    ) {
        this.transferRepository = transferRepository;
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.balanceMutationService = balanceMutationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockTransferResponse> list(
            int page,
            int size,
            String search,
            String status,
            UUID warehouseId
    ) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);

        Collection<UUID> allowed = dataScopeHelper.getAllowedWarehouseIds(warehouseId);

        if (allowed != null && allowed.isEmpty()) {
            return new PageResponse<>(page, size, 0, 0, List.of());
        }

        Page<StockTransfer> result = transferRepository.search(
                blankToNull(search),
                blankToNull(status),
                warehouseId,
                allowed,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("createdAt").descending()
                )
        );

        List<StockTransferResponse> content = result.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                content
        );
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

        String code = request.code() == null || request.code().isBlank()
                ? generateCode()
                : request.code().trim();

        if (transferRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }

        StockTransfer transfer = new StockTransfer();

        transfer.setCode(code);
        transfer.setFromWarehouseId(from.getId());
        transfer.setToWarehouseId(to.getId());
        transfer.setTransferDate(
                request.transferDate() == null ? LocalDate.now() : request.transferDate()
        );
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

        if (request == null
                || request.fromWarehouseId() == null
                || request.toWarehouseId() == null) {
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
        StockTransfer transfer = findAccessibleForUpdate(id);

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

        List<StockTransferItem> items = itemRepository.findByStockTransferId(id);

        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        /*
         * Kiểm tra toàn bộ tồn trước khi thay đổi
         * bất kỳ dữ liệu nào.
         */
        for (StockTransferItem item : items) {
            MaterialStockBalance balance = balanceMutationService.lockOrCreate(
                    transfer.getFromWarehouseId(),
                    item.getMaterialId()
            );

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

        stockOut.setCode("OUT-" + transfer.getCode());
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

            balanceMutationService.decrease(
                    transfer.getFromWarehouseId(),
                    transferItem.getMaterialId(),
                    transferItem.getQuantity()
            );
        }

        transfer.setStatus(IN_TRANSIT);

        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    @Override
    @Transactional
    public StockTransferResponse receive(
            UUID id,
            ReceiveStockTransferRequest request
    ) {
        StockTransfer transfer = findAccessibleForUpdate(id);

        if (!IN_TRANSIT.equals(transfer.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_TRANSFER_NOT_RECEIVABLE);
        }

        if (request == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        Map<UUID, StockTransferItem> itemMap = itemRepository.findByStockTransferId(id)
                .stream()
                .collect(Collectors.toMap(
                        StockTransferItem::getId,
                        item -> item
                ));

        // Receive is intentionally one-shot because the schema has no
        // partial-receipt state or request idempotency key.
        if (request.items().size() != itemMap.size()) {
            throw new BaseException(ErrorCode.INV_400_ITEMS_EMPTY);
        }

        Set<UUID> receivedItemIds = new HashSet<>();

        /*
         * Kiểm tra toàn bộ receive trước.
         */
        for (ReceiveStockTransferItemRequest req : request.items()) {
            if (req == null
                    || req.itemId() == null
                    || !receivedItemIds.add(req.itemId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }

            if (req.receivedQuantity() == null
                    || req.receivedQuantity().signum() <= 0) {
                throw new BaseException(ErrorCode.INV_400_RECEIVE_EXCEED);
            }

            StockTransferItem item = itemMap.get(req.itemId());

            if (item == null) {
                throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            if (nvl(item.getReceivedQuantity()).signum() != 0
                    || req.receivedQuantity().compareTo(item.getQuantity()) != 0) {
                throw new BaseException(ErrorCode.INV_400_RECEIVE_EXCEED);
            }
        }

        /*
         * Tạo STOCK_IN cho lần receive này.
         */
        StockIn stockIn = new StockIn();

        stockIn.setCode(
                "IN-" + transfer.getCode() + "-" + System.currentTimeMillis()
        );
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
         * Ghi từng dòng receive.
         */
        for (ReceiveStockTransferItemRequest req : request.items()) {
            StockTransferItem item = itemMap.get(req.itemId());

            item.setReceivedQuantity(req.receivedQuantity());

            itemRepository.save(item);

            StockInItem inItem = new StockInItem();

            inItem.setStockInId(stockIn.getId());
            inItem.setMaterialId(item.getMaterialId());
            inItem.setQuantity(req.receivedQuantity());
            inItem.setUnitPrice(nvl(item.getUnitPrice()));
            inItem.setStatus("ACTIVE");

            entityManager.persist(inItem);

            balanceMutationService.increase(
                    transfer.getToWarehouseId(),
                    item.getMaterialId(),
                    req.receivedQuantity()
            );
        }

        /*
         * Kiểm tra tất cả item đã nhận đủ chưa.
         */
        transfer.setStatus(RECEIVED);
        transfer.setReceivedBy(currentUserId());
        transfer.setReceivedAt(Instant.now());

        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    @Override
    @Transactional
    public StockTransferResponse cancel(UUID id) {
        StockTransfer transfer = findAccessibleForUpdate(id);

        if (!PENDING.equals(transfer.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_STATUS);
        }

        transfer.setStatus(CANCELLED);

        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    private void saveItems(
            StockTransfer transfer,
            List<StockTransferItemRequest> requests
    ) {
        Set<UUID> materials = new HashSet<>();
        List<StockTransferItem> items = new ArrayList<>();

        for (StockTransferItemRequest req : requests) {
            if (!materials.add(req.materialId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }

            if (req.quantity() == null || req.quantity().signum() <= 0) {
                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            materialRepository.findById(req.materialId())
                    .orElseThrow(() ->
                            new BaseException(ErrorCode.MATERIAL_NOT_FOUND)
                    );

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

        if (request.fromWarehouseId() == null
                || request.toWarehouseId() == null) {
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
            if (item.materialId() == null
                    || item.quantity() == null
                    || item.quantity().signum() <= 0) {
                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            if (!materialIds.add(item.materialId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }
        }
    }

    private Warehouse getActiveWarehouse(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() ->
                        new BaseException(ErrorCode.PROC_404_WAREHOUSE_NOT_FOUND)
                );

        if (!"ACTIVE".equals(warehouse.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_WAREHOUSE_INACTIVE);
        }

        return warehouse;
    }

    private StockTransfer findAccessible(UUID id) {
        StockTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() ->
                        new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND)
                );

        dataScopeHelper.enforceWarehouseAccess(transfer.getFromWarehouseId());
        dataScopeHelper.enforceWarehouseAccess(transfer.getToWarehouseId());

        return transfer;
    }

    private StockTransfer findAccessibleForUpdate(UUID id) {
        StockTransfer transfer = transferRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new BaseException(ErrorCode.INV_404_TRANSFER_NOT_FOUND)
                );

        dataScopeHelper.enforceWarehouseAccess(transfer.getFromWarehouseId());
        dataScopeHelper.enforceWarehouseAccess(transfer.getToWarehouseId());

        return transfer;
    }

    private StockTransferResponse toResponse(StockTransfer transfer) {
        Warehouse from = warehouseRepository.findById(transfer.getFromWarehouseId())
                .orElse(null);

        Warehouse to = warehouseRepository.findById(transfer.getToWarehouseId())
                .orElse(null);

        List<StockTransferItem> items =
                itemRepository.findByStockTransferId(transfer.getId());

        Map<UUID, Material> materials = materialRepository.findAllById(
                        items.stream()
                                .map(StockTransferItem::getMaterialId)
                                .distinct()
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        List<StockTransferItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Material material = materials.get(item.getMaterialId());

                    BigDecimal quantity = item.getQuantity() == null
                            ? BigDecimal.ZERO
                            : item.getQuantity();

                    BigDecimal received = item.getReceivedQuantity() == null
                            ? BigDecimal.ZERO
                            : item.getReceivedQuantity();

                    return new StockTransferItemResponse(
                            item.getId(),
                            item.getMaterialId(),
                            material == null ? null : material.getCode(),
                            material == null ? null : material.getName(),
                            quantity,
                            received,
                            quantity.subtract(received),
                            item.getUnitPrice()
                    );
                })
                .toList();

        return new StockTransferResponse(
                transfer.getId(),
                transfer.getCode(),
                transfer.getFromWarehouseId(),
                from == null ? null : from.getCode(),
                from == null ? null : from.getName(),
                transfer.getToWarehouseId(),
                to == null ? null : to.getCode(),
                to == null ? null : to.getName(),
                transfer.getTransferDate(),
                transfer.getStatus(),
                transfer.getNote(),
                transfer.getReceivedBy(),
                transfer.getReceivedAt(),
                itemResponses
        );
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentPrincipalId().orElse(null);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateCode() {
        return "TRF-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}