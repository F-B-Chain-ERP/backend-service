package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.StockCountService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.inv.*;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.*;
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
public class StockCountServiceImpl implements StockCountService {

    private static final String DRAFT = "DRAFT";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final String ADJUSTED = "ADJUSTED";
    private static final String POSTED = "POSTED";
    private static final String ADJUSTMENT = "ADJUSTMENT";

    private final StockCountRepository countRepository;
    private final StockCountItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final MaterialStockBalanceRepository balanceRepository;
    private final StockInRepository stockInRepository;
    private final StockOutRepository stockOutRepository;
    private final StockTransferRepository transferRepository;
    private final DataScopeHelper dataScopeHelper;
    private final StockBalanceMutationService balanceMutationService;

    @PersistenceContext
    private EntityManager entityManager;

    public StockCountServiceImpl(StockCountRepository countRepository, StockCountItemRepository itemRepository, WarehouseRepository warehouseRepository, MaterialRepository materialRepository, MaterialStockBalanceRepository balanceRepository, StockInRepository stockInRepository, StockOutRepository stockOutRepository, StockTransferRepository transferRepository, DataScopeHelper dataScopeHelper, StockBalanceMutationService balanceMutationService) {
        this.countRepository = countRepository;
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.balanceRepository = balanceRepository;
        this.stockInRepository = stockInRepository;
        this.stockOutRepository = stockOutRepository;
        this.transferRepository = transferRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.balanceMutationService = balanceMutationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockCountResponse> list(int page, int size, String search, String status, UUID warehouseId) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);

        Collection<UUID> allowed = dataScopeHelper.getAllowedWarehouseIds(warehouseId);

        if (allowed != null && allowed.isEmpty()) {
            return new PageResponse<>(page, size, 0, 0, List.of());
        }

        Page<StockCount> result = countRepository.search(blankToNull(search), blankToNull(status), warehouseId, allowed, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<StockCountResponse> content = result.getContent().stream().map(this::toResponse).toList();

        return new PageResponse<>(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), content);
    }

    @Override
    @Transactional(readOnly = true)
    public StockCountResponse get(UUID id) {
        return toResponse(findAccessible(id));
    }

    @Override
    @Transactional
    public StockCountResponse create(CreateStockCountRequest request) {
        if (request == null || request.warehouseId() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId()).orElseThrow(() -> new BaseException(ErrorCode.PROC_404_WAREHOUSE_NOT_FOUND));

        if (!"ACTIVE".equals(warehouse.getStatus())) {
            throw new BaseException(ErrorCode.PROC_400_WAREHOUSE_INACTIVE);
        }

        dataScopeHelper.enforceWarehouseAccess(warehouse.getId());

        // Mỗi kho một thời điểm chỉ kiểm một phiếu: 2 phiếu song song sẽ
        // điều chỉnh chênh lệch 2 lần trên cùng một tồn.
        if (countRepository.existsCounting(warehouse.getId())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_COUNTING);
        }

        // Mã phiếu do hệ thống tự sinh, không nhận tay.
        String code = generateCode();

        if (countRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }

        StockCount count = new StockCount();

        count.setCode(code);

        count.setWarehouseId(warehouse.getId());

        count.setCountDate(request.countDate() == null ? LocalDate.now() : request.countDate());

        count.setNote(request.note());

        count.setStatus(DRAFT);

        count = countRepository.save(count);

        if (request.items() == null || request.items().isEmpty()) {
            // Không tự snapshot toàn bộ NVL: ép kiểm theo danh sách chọn
            // (nhẹ, kiểm được theo nhóm, và tương thích cột NOT NULL trên DB).
            throw new BaseException(ErrorCode.INV_400_COUNT_ITEMS_EMPTY);
        }

        saveItems(count, request.items());

        return toResponse(count);
    }

    @Override
    @Transactional
    public StockCountResponse update(UUID id, UpdateStockCountRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        StockCount count = findAccessibleForUpdate(id);

        if (!DRAFT.equals(count.getStatus()) && !IN_PROGRESS.equals(count.getStatus())) {

            throw new BaseException(ErrorCode.INV_400_COUNT_CANNOT_EDIT);
        }

        if (request.note() != null) {
            count.setNote(request.note());
        }

        if (DRAFT.equals(count.getStatus())) {
            if (request.items() != null) {
                if (request.items().isEmpty()) {
                    throw new BaseException(ErrorCode.INV_400_COUNT_ITEMS_EMPTY);
                }

                itemRepository.deleteByStockCountId(id);
                saveItems(count, request.items());
            }
        } else if (request.items() != null) {
            updateCountedItems(id, request.items());
        }

        countRepository.save(count);

        return toResponse(count);
    }

    @Override
    @Transactional
    public StockCountResponse start(UUID id) {
        StockCount count = findAccessibleForUpdate(id);

        if (!DRAFT.equals(count.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_STATUS);
        }

        // Hàng đang đi đường mà chốt kiểm kê thì số hệ thống thiếu đúng phần đó.
        if (transferRepository.existsOpenTransfer(count.getWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_COUNT_HAS_OPEN_TRANSFER);
        }

        // Phiếu khác đã bắt đầu kiểm kho này trước (tạo 2 DRAFT rồi start cả 2).
        if (countRepository.existsCounting(count.getWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_WAREHOUSE_COUNTING);
        }

        List<StockCountItem> startItems = itemRepository.findByStockCountId(id);
        if (startItems.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_COUNT_ITEMS_EMPTY);
        }

        // Re-snapshot tồn tại thời điểm START (sau đó In/Out/Transfer bị chặn bởi existsCounting)
        for (StockCountItem it : startItems) {
            it.setSystemQuantity(currentQuantity(count.getWarehouseId(), it.getMaterialId()));
            if (it.getCountedQuantity() != null) {
                it.setVarianceQuantity(it.getCountedQuantity().subtract(nvl(it.getSystemQuantity())));
            }
        }
        itemRepository.saveAll(startItems);

        count.setStatus(IN_PROGRESS);
        countRepository.save(count);
        return toResponse(count);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        StockCount count = findAccessibleForUpdate(id);

        if (!DRAFT.equals(count.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_COUNT_CANNOT_EDIT);
        }

        itemRepository.deleteByStockCountId(id);

        countRepository.delete(count);
    }

    @Override
    @Transactional
    public StockCountResponse complete(UUID id) {
        StockCount count = findAccessibleForUpdate(id);

        if (!IN_PROGRESS.equals(count.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_STATUS);
        }

        // Chặn chốt khi có phiếu chuyển đi/đến kho đang đi đường (tồn sẽ đổi sau khi nhận).
        if (transferRepository.existsOpenTransfer(count.getWarehouseId())) {
            throw new BaseException(ErrorCode.INV_400_COUNT_HAS_OPEN_TRANSFER);
        }

        List<StockCountItem> items = itemRepository.findByStockCountId(id);

        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_COUNT_ITEMS_EMPTY);
        }

        /*
         * countedQuantity phải có đủ.
         */
        for (StockCountItem item : items) {

            if (item.getCountedQuantity() == null || item.getCountedQuantity().signum() < 0) {

                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            BigDecimal system = nvl(item.getSystemQuantity());

            BigDecimal counted = item.getCountedQuantity();

            item.setVarianceQuantity(counted.subtract(system));
        }

        itemRepository.saveAll(items);

        count.setStatus(COMPLETED);

        countRepository.save(count);

        return toResponse(count);
    }

    @Override
    @Transactional
    public StockCountResponse adjust(UUID id) {
        StockCount count = findAccessibleForUpdate(id);

        if (ADJUSTED.equals(count.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_COUNT_ALREADY_ADJUSTED);
        }

        if (!COMPLETED.equals(count.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_COUNT_NOT_COMPLETED);
        }

        List<StockCountItem> items = itemRepository.findByStockCountId(id);

        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_COUNT_ITEMS_EMPTY);
        }

        /*
         * Toàn bộ Adjustment nằm trong cùng transaction.
         * Dùng tồn HIỆN TẠI (lock) thay vì snapshot cũ để tránh cộng chồng
         * khi có nhập/xuất giữa COMPLETED -> ADJUSTED.
         */
        for (StockCountItem item : items) {

            BigDecimal system = nvl(item.getSystemQuantity());

            BigDecimal counted = nvl(item.getCountedQuantity());

            MaterialStockBalance balance = balanceMutationService.lockOrCreate(count.getWarehouseId(), item.getMaterialId());
            BigDecimal current = nvl(balance.getQuantityOnHand());
            BigDecimal reserved = nvl(balance.getQuantityReserved());

            BigDecimal adjustQty = counted.subtract(current);
            BigDecimal variance = counted.subtract(system);

            item.setVarianceQuantity(variance);

            /*
             * Không có chênh lệch so với hiện tại -> bỏ qua
             * (vẫn lưu variance vs snapshot để audit).
             */
            if (adjustQty.signum() == 0) {
                continue;
            }

            if (adjustQty.signum() > 0) {

                /*
                 * Tồn thực tế > tồn hệ thống hiện tại
                 *
                 * => STOCK_IN
                 * source_type = ADJUSTMENT
                 */
                createAdjustmentStockIn(count, item, adjustQty);

            } else {

                /*
                 * Tồn thực tế < tồn hệ thống hiện tại
                 *
                 * => STOCK_OUT
                 * destination_type = ADJUSTMENT
                 */

                BigDecimal decrease = adjustQty.abs();

                BigDecimal newOnHand = current.subtract(decrease);

                /*
                 * Không được đưa tồn xuống dưới
                 * quantity_reserved.
                 */
                if (newOnHand.compareTo(reserved) < 0) {

                    throw new BaseException(ErrorCode.INV_400_COUNT_BELOW_RESERVED);
                }

                createAdjustmentStockOut(count, item, decrease);
            }
        }

        itemRepository.saveAll(items);

        count.setStatus(ADJUSTED);

        countRepository.save(count);

        return toResponse(count);
    }

    private void createAdjustmentStockIn(StockCount count, StockCountItem item, BigDecimal quantity) {
        StockIn stockIn = new StockIn();

        stockIn.setCode(generateStockInCode());

        stockIn.setWarehouseId(count.getWarehouseId());

        stockIn.setSourceType(ADJUSTMENT);

        stockIn.setSourceReferenceId(count.getId());

        stockIn.setInDate(count.getCountDate());

        stockIn.setNote("Inventory adjustment increase - " + count.getCode());

        stockIn.setStatus(POSTED);

        stockIn.setReceivedBy(currentUserId());

        stockIn.setPostedAt(Instant.now());

        entityManager.persist(stockIn);

        StockInItem stockInItem = new StockInItem();

        stockInItem.setStockInId(stockIn.getId());

        stockInItem.setMaterialId(item.getMaterialId());

        stockInItem.setQuantity(quantity);

        stockInItem.setUnitPrice(BigDecimal.ZERO);

        stockInItem.setStatus("ACTIVE");

        entityManager.persist(stockInItem);

        balanceMutationService.increase(count.getWarehouseId(), item.getMaterialId(), quantity);
    }

    private void createAdjustmentStockOut(StockCount count, StockCountItem item, BigDecimal quantity) {
        StockOut stockOut = new StockOut();

        stockOut.setCode(generateStockOutCode());

        stockOut.setWarehouseId(count.getWarehouseId());

        stockOut.setDestinationType(ADJUSTMENT);

        stockOut.setDestinationReferenceId(count.getId());

        stockOut.setOutDate(count.getCountDate());

        stockOut.setNote("Inventory adjustment decrease - " + count.getCode());

        stockOut.setStatus(POSTED);

        stockOut.setIssuedBy(currentUserId());

        stockOut.setPostedAt(Instant.now());

        entityManager.persist(stockOut);

        StockOutItem stockOutItem = new StockOutItem();

        stockOutItem.setStockOutId(stockOut.getId());

        stockOutItem.setMaterialId(item.getMaterialId());

        stockOutItem.setQuantity(quantity);

        stockOutItem.setUnitPrice(BigDecimal.ZERO);

        stockOutItem.setStatus("ACTIVE");

        entityManager.persist(stockOutItem);

        balanceMutationService.decrease(count.getWarehouseId(), item.getMaterialId(), quantity);
    }

    /**
     * Ghi dòng kiểm kê: snapshot tồn hệ thống tại lúc ghi + số đếm thực tế
     * (bắt buộc, tương thích cột NOT NULL) + chênh lệch.
     */
    private void saveItems(StockCount count, List<StockCountItemRequest> requests) {
        Set<UUID> materialIds = new HashSet<>();

        List<StockCountItem> items = new ArrayList<>();

        for (StockCountItemRequest request : requests) {

            if (request.materialId() == null) {
                throw new BaseException(ErrorCode.MATERIAL_NOT_FOUND);
            }

            if (!materialIds.add(request.materialId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }

            materialRepository.findById(request.materialId()).orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));

            BigDecimal system = currentQuantity(count.getWarehouseId(), request.materialId());

            BigDecimal counted = request.countedQuantity();

            if (counted == null || counted.signum() < 0) {
                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            StockCountItem item = new StockCountItem();

            item.setStockCountId(count.getId());

            item.setMaterialId(request.materialId());

            item.setSystemQuantity(system);

            item.setCountedQuantity(counted);

            item.setVarianceQuantity(counted == null ? null : counted.subtract(system));

            item.setNote(request.note());

            items.add(item);
        }

        itemRepository.saveAll(items);
    }

    /**
     * Cập nhật số đếm khi đang kiểm. Cho phép thêm mã mới ngoài danh sách
     * (hàng thừa ngoài sổ phát hiện lúc kiểm), tồn hệ thống lấy hiện tại.
     */
    private void updateCountedItems(UUID countId, List<StockCountItemRequest> requests) {
        if (requests.isEmpty()) {
            throw new BaseException(ErrorCode.INV_400_COUNT_ITEMS_EMPTY);
        }

        StockCount count = countRepository.findById(countId).orElseThrow(() -> new BaseException(ErrorCode.INV_404_COUNT_NOT_FOUND));

        Map<UUID, StockCountItem> existing = itemRepository.findByStockCountId(countId).stream().collect(Collectors.toMap(StockCountItem::getMaterialId, item -> item));

        Set<UUID> seen = new HashSet<>();
        List<StockCountItem> created = new ArrayList<>();
        for (StockCountItemRequest request : requests) {
            if (request == null || request.materialId() == null || !seen.add(request.materialId())) {
                throw new BaseException(ErrorCode.INV_400_DUPLICATE_MATERIAL);
            }

            BigDecimal counted = request.countedQuantity();
            if (counted == null || counted.signum() < 0) {
                throw new BaseException(ErrorCode.INVALID_QUANTITY);
            }

            StockCountItem item = existing.get(request.materialId());
            if (item == null) {
                materialRepository.findById(request.materialId()).orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));
                StockCountItem created1 = new StockCountItem();
                created1.setStockCountId(countId);
                created1.setMaterialId(request.materialId());
                created1.setSystemQuantity(currentQuantity(count.getWarehouseId(), request.materialId()));
                created1.setCountedQuantity(counted);
                created1.setVarianceQuantity(counted.subtract(nvl(created1.getSystemQuantity())));
                created1.setNote(request.note());
                created.add(created1);
                continue;
            }

            item.setCountedQuantity(counted);
            item.setVarianceQuantity(counted.subtract(nvl(item.getSystemQuantity())));
            item.setNote(request.note());
        }

        itemRepository.saveAll(existing.values());
        if (!created.isEmpty()) {
            itemRepository.saveAll(created);
        }
    }

    private BigDecimal currentQuantity(UUID warehouseId, UUID materialId) {
        return balanceRepository.findByWarehouseIdAndMaterialId(warehouseId, materialId).map(balance -> nvl(balance.getQuantityOnHand())).orElse(BigDecimal.ZERO);
    }

    private StockCount findAccessible(UUID id) {
        StockCount count = countRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_COUNT_NOT_FOUND));

        dataScopeHelper.enforceWarehouseAccess(count.getWarehouseId());

        return count;
    }

    private StockCount findAccessibleForUpdate(UUID id) {
        StockCount count = countRepository.findByIdForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.INV_404_COUNT_NOT_FOUND));

        dataScopeHelper.enforceWarehouseAccess(count.getWarehouseId());
        return count;
    }

    private StockCountResponse toResponse(StockCount count) {
        Warehouse warehouse = warehouseRepository.findById(count.getWarehouseId()).orElse(null);

        List<StockCountItem> items = itemRepository.findByStockCountId(count.getId());

        Map<UUID, Material> materials = materialRepository.findAllById(items.stream().map(StockCountItem::getMaterialId).distinct().toList()).stream().collect(Collectors.toMap(Material::getId, material -> material));

        List<StockCountItemResponse> itemResponses = items.stream().map(item -> {

            Material material = materials.get(item.getMaterialId());

            return new StockCountItemResponse(item.getId(), item.getMaterialId(), material == null ? null : material.getCode(), material == null ? null : material.getName(), item.getSystemQuantity(), item.getCountedQuantity(), item.getVarianceQuantity(), item.getNote());
        }).toList();

        return new StockCountResponse(count.getId(), count.getCode(), count.getWarehouseId(), warehouse == null ? null : warehouse.getCode(), warehouse == null ? null : warehouse.getName(), count.getCountDate(), count.getStatus(), count.getNote(), itemResponses);
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentPrincipalId().orElse(null);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateCode() {
        return CodeGenerator.random("CNT-", countRepository::existsByCode);
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
