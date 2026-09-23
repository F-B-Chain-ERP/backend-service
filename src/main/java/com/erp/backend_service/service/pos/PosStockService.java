package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchProductAvailabilityRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.BranchVariantDailyStockRepository;
import com.erp.backend_service.repository.BranchVariantStockLogRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductRecipeItemRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.StockTransferService;
import com.erp.backend_service.service.UnitConversionService;
import com.erp.core.domain.BranchProductAvailability;
import com.erp.core.domain.BranchVariantDailyStock;
import com.erp.core.domain.BranchVariantStockLog;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.ProductVariant;
import com.erp.core.domain.Unit;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.inv.CreateStockTransferRequest;
import com.erp.core.dto.request.inv.StockTransferItemRequest;
import com.erp.core.dto.request.pos.RestockDailyStockBatchRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockTransferResponse;
import com.erp.core.dto.response.pos.DailyStockBatchResponse;
import com.erp.core.dto.response.pos.DailyStockLineResponse;
import com.erp.core.dto.response.pos.DailyStockLogResponse;
import com.erp.core.dto.response.pos.MaterialShortageLineResponse;
import com.erp.core.dto.response.pos.MaterialShortageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/**
 * Gom mọi check/trừ/hoàn tồn bán trong ngày về một chỗ (A2, A3).
 * - ensure: lazy seed dòng tồn hôm nay (carryover số dư hôm qua, ngày đầu = 0).
 *   Không cần cron/job, lần check đầu tiên trong ngày tự tạo dòng.
 * - check/reserve/release: giữ PESSIMISTIC_WRITE + ghi stock_log để trace.
 *
 * Chốt luồng trừ kho POS (tồn ngày theo variant):
 * - Giỏ + chốt đơn (PENDING): chỉ checkAvailable, CHƯA trừ.
 * - CONFIRMED (auto lúc tạo hoặc staff bấm tay): reserve 1 LẦN duy nhất.
 * - CANCELLED/REJECTED: release CHỈ khi đơn đã từng reserve (từ CONFIRMED trở đi).
 * - PREPARING/READY/DELIVERING/COMPLETED: không trừ thêm (chống double-deduct).
 * - Topping/material (BOM kho tổng): không trừ ở POS, theo dõi ở module kho.
 */
@Service
public class PosStockService {

    private final BranchVariantDailyStockRepository stockRepository;
    private final BranchVariantStockLogRepository logRepository;
    private final PosBusinessDay businessDay;
    private final BranchRepository branchRepository;
    private final BranchProductAvailabilityRepository availabilityRepository;
    private final DataScopeHelper dataScopeHelper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final MaterialStockBalanceRepository balanceRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRecipeItemRepository recipeRepository;
    private final StockTransferService stockTransferService;
    private final UnitConversionService unitConversionService;

    public PosStockService(BranchVariantDailyStockRepository stockRepository,
                           BranchVariantStockLogRepository logRepository,
                           PosBusinessDay businessDay,
                           BranchRepository branchRepository,
                           BranchProductAvailabilityRepository availabilityRepository,
                           DataScopeHelper dataScopeHelper,
                           ProductRepository productRepository,
                           ProductVariantRepository variantRepository,
                           MaterialRepository materialRepository,
                           UnitRepository unitRepository,
                           MaterialStockBalanceRepository balanceRepository,
                           WarehouseRepository warehouseRepository,
                           ProductRecipeItemRepository recipeRepository,
                           StockTransferService stockTransferService,
                           UnitConversionService unitConversionService) {
        this.stockRepository = stockRepository;
        this.logRepository = logRepository;
        this.businessDay = businessDay;
        this.branchRepository = branchRepository;
        this.availabilityRepository = availabilityRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.balanceRepository = balanceRepository;
        this.warehouseRepository = warehouseRepository;
        this.recipeRepository = recipeRepository;
        this.stockTransferService = stockTransferService;
        this.unitConversionService = unitConversionService;
    }

    /**
     * Đảm bảo có dòng tồn hôm nay. Chưa có -> tạo mới, opening = số dư mới nhất
     * (carryover hôm qua), ngày đầu tiên = 0 và quản lý phải restock.
     */
    @Transactional
    public BranchVariantDailyStock ensureToday(UUID branchId, UUID variantId) {
        LocalDate today = businessDay.today(branchId);
        BranchVariantDailyStock existing = stockRepository
            .findByBranchIdAndVariantIdAndBusinessDateAndStatus(branchId, variantId, today, "ACTIVE")
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        long lockId = UUID.nameUUIDFromBytes(("DAILY_STOCK|" + branchId + "|" + variantId + "|" + today)
            .getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        stockRepository.acquireTransactionLock(lockId);
        existing = stockRepository
            .findByBranchIdAndVariantIdAndBusinessDateAndStatus(branchId, variantId, today, "ACTIVE")
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        int opening = stockRepository
            .findFirstByBranchIdAndVariantIdAndStatusOrderByBusinessDateDesc(branchId, variantId, "ACTIVE")
            .map(BranchVariantDailyStock::getRemainingQuantity).orElse(0);
        BranchVariantDailyStock created = new BranchVariantDailyStock();
        created.setBranchId(branchId);
        created.setVariantId(variantId);
        created.setBusinessDate(today);
        created.setOpeningQuantity(Math.max(0, opening));
        created.setRemainingQuantity(Math.max(0, opening));
        created.setSoldQuantity(0);
        created.setStatus("ACTIVE");
        BranchVariantDailyStock saved = stockRepository.save(created);
        writeLog(branchId, variantId, 0, null, "RESTOCK", "Auto carryover tồn sang ngày " + today);
        return saved;
    }

    /**
     * Quản lý chốt tồn mở bán: opening tuyệt đối, remaining = opening - đã bán (kẹp >= 0).
     * Gọi mỗi sáng hoặc khi nhập thêm hàng trong ngày.
     */
    @Transactional
    public BranchVariantDailyStock restock(UUID branchId, UUID variantId, int openingQuantity, String note) {
        if (openingQuantity < 0) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Tồn mở bán không được âm.");
        }
        BranchVariantDailyStock stock = ensureToday(branchId, variantId);
        BranchVariantDailyStock locked = stockRepository
            .lockByBranchVariantDate(branchId, variantId, stock.getBusinessDate(), "ACTIVE")
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY));
        int delta = openingQuantity - locked.getOpeningQuantity();
        locked.setOpeningQuantity(openingQuantity);
        locked.setRemainingQuantity(Math.max(0, openingQuantity - locked.getSoldQuantity()));
        stockRepository.save(locked);
        writeLog(branchId, variantId, delta, null, "RESTOCK", note != null ? note : "Quản lý chốt tồn mở bán");
        return locked;
    }

    @Transactional
    public void checkAvailable(UUID branchId, UUID variantId, int quantity) {
        if (variantId == null) {
            return;
        }
        BranchVariantDailyStock stock = ensureToday(branchId, variantId);
        if (quantity > stock.getRemainingQuantity()) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY,
                "Sản phẩm đã hết hàng (còn " + stock.getRemainingQuantity() + ", cần " + quantity + ").");
        }
    }

    @Transactional
    public void reserve(UUID branchId, UUID variantId, int quantity, UUID orderId) {
        if (variantId == null || quantity <= 0) {
            return;
        }
        ensureToday(branchId, variantId);
        LocalDate today = businessDay.today(branchId);
        BranchVariantDailyStock stock = stockRepository
            .lockByBranchVariantDate(branchId, variantId, today, "ACTIVE")
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY,
                "Sản phẩm chưa có tồn trong ngày, vui lòng restock."));
        if (stock.getRemainingQuantity() < quantity) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY,
                "Tồn không đủ để xác nhận đơn (còn " + stock.getRemainingQuantity() + ", cần " + quantity + ").");
        }
        stock.setRemainingQuantity(stock.getRemainingQuantity() - quantity);
        stock.setSoldQuantity(stock.getSoldQuantity() + quantity);
        stockRepository.save(stock);
        writeLog(branchId, variantId, -quantity, orderId, "SALE", "Reserve khi CONFIRMED đơn " + orderId);
    }

    @Transactional
    public void release(UUID branchId, UUID variantId, int quantity, UUID orderId) {
        if (variantId == null || quantity <= 0) {
            return;
        }
        // Hoàn vào dòng hôm nay (nơi đơn mới trừ vào), kể cả đơn hôm qua bị hủy muộn.
        ensureToday(branchId, variantId);
        LocalDate today = businessDay.today(branchId);
        BranchVariantDailyStock stock = stockRepository
            .lockByBranchVariantDate(branchId, variantId, today, "ACTIVE")
            .orElse(null);
        if (stock == null) {
            return;
        }
        stock.setRemainingQuantity(stock.getRemainingQuantity() + quantity);
        stock.setSoldQuantity(Math.max(0, stock.getSoldQuantity() - quantity));
        stockRepository.save(stock);
        writeLog(branchId, variantId, quantity, orderId, "ADJUSTMENT", "Hoàn tồn khi hủy/từ chối đơn " + orderId);
    }

    private void writeLog(UUID branchId, UUID variantId, int qtyChange, UUID referenceId, String changeType,
                          String note) {
        BranchVariantStockLog log = new BranchVariantStockLog();
        log.setBranchId(branchId);
        log.setVariantId(variantId);
        log.setChangeType(changeType);
        log.setQuantityChange(qtyChange);
        log.setReferenceId(referenceId);
        log.setNote(note);
        log.setStatus("ACTIVE");
        logRepository.save(log);
    }

    /**
     * Màn Tồn sản phẩm: TOÀN BỘ biến thể đang mở bán tại chi nhánh (kể cả chưa có
     * dòng tồn hôm nay — các số mở bán/đã bán/còn lại null hiển thị "—"),
     * kèm tên SP/biến thể + gợi ý năng lực NVL, lọc search, phân trang trong bộ nhớ.
     */
    @Transactional(readOnly = true)
    public PageResponse<DailyStockLineResponse> list(UUID branchId, LocalDate date, String search,
                                                     int page, int size) {
        if (branchId == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không được để trống.");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "page/size không hợp lệ.");
        }
        UUID effectiveBranch = dataScopeHelper.resolveEffectiveBranchId(branchId);
        LocalDate businessDate = date != null ? date : businessDay.today(effectiveBranch);
        // Nền: toàn bộ biến thể đang mở bán tại chi nhánh (kể cả chưa có dòng tồn hôm nay).
        List<BranchProductAvailability> availabilities =
            availabilityRepository.findByBranchIdAndStatus(effectiveBranch, "ACTIVE").stream()
                .filter(BranchProductAvailability::isAvailable).toList();
        Map<UUID, Product> products = productRepository.findAllById(
            availabilities.stream().map(BranchProductAvailability::getProductId)
                .filter(Objects::nonNull).distinct().toList()).stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        Map<UUID, ProductVariant> variants = products.isEmpty() ? Map.of()
            : variantRepository.findByProductIdInAndStatus(products.keySet(), "ACTIVE").stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity(), (a, b) -> a));
        Map<UUID, BranchVariantDailyStock> stockByVariant =
            stockRepository.findByBranchIdAndBusinessDateAndStatus(effectiveBranch, businessDate, "ACTIVE")
                .stream().collect(Collectors.toMap(BranchVariantDailyStock::getVariantId,
                    Function.identity(), (a, b) -> a));
        Map<UUID, List<ProductRecipeItem>> recipesByVariant = variants.keySet().isEmpty() ? Map.of()
            : recipeRepository.findByVariantIdInAndStatus(variants.keySet(), "ACTIVE").stream()
                .collect(Collectors.groupingBy(ProductRecipeItem::getVariantId));
        Set<UUID> materialIds = recipesByVariant.values().stream().flatMap(List::stream)
            .map(ProductRecipeItem::getMaterialId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, Material> materialsById = materialIds.isEmpty() ? Map.of()
            : materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
        Map<UUID, BigDecimal> availableByMaterial = loadAvailableMaterials(effectiveBranch);
        String keyword = search == null ? null : search.trim().toLowerCase();
        List<DailyStockLineResponse> filtered = new ArrayList<>();
        for (ProductVariant v : variants.values()) {
            Product p = products.get(v.getProductId());
            if (keyword != null && !keyword.isEmpty()) {
                String haystack = ((nvl(v.getVariantCode()) + " " + nvl(v.getVariantName())) + " "
                    + (p == null ? "" : nvl(p.getCode()) + " " + nvl(p.getName()))).toLowerCase();
                if (!haystack.contains(keyword)) {
                    continue;
                }
            }
            BranchVariantDailyStock s = stockByVariant.get(v.getId());
            filtered.add(new DailyStockLineResponse(
                v.getId(),
                v.getVariantCode(),
                v.getVariantName(),
                p == null ? null : p.getId(),
                p == null ? null : p.getCode(),
                p == null ? null : p.getName(),
                businessDate,
                s == null ? null : s.getOpeningQuantity(),
                s == null ? null : s.getSoldQuantity(),
                s == null ? null : s.getRemainingQuantity(),
                capabilityOf(recipesByVariant.get(v.getId()), availableByMaterial, materialsById),
                recipesByVariant.containsKey(v.getId())));
        }
        filtered.sort((a, b) -> {
            int byProduct = compareNullLast(a.productName(), b.productName());
            return byProduct != 0 ? byProduct : compareNullLast(a.variantName(), b.variantName());
        });
        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(page, size, total, totalPages, filtered.subList(fromIndex, toIndex));
    }

    /**
     * Lịch sử biến động tồn (mới nhất trước). variantId null = mọi variant của chi nhánh.
     */
    @Transactional(readOnly = true)
    public PageResponse<DailyStockLogResponse> history(UUID branchId, UUID variantId,
                                                       Instant from, Instant to, int page, int size) {
        if (branchId == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không được để trống.");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "page/size không hợp lệ.");
        }
        UUID effectiveBranch = dataScopeHelper.resolveEffectiveBranchId(branchId);
        Instant fromInstant = from != null ? from : Instant.EPOCH;
        Instant toInstant = to != null ? to : Instant.now();
        Page<BranchVariantStockLog> result = variantId == null
            ? logRepository.findByBranchIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                effectiveBranch, "ACTIVE", fromInstant, toInstant, PageRequest.of(page, size))
            : logRepository.findByBranchIdAndVariantIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                effectiveBranch, variantId, "ACTIVE", fromInstant, toInstant, PageRequest.of(page, size));
        Map<UUID, ProductVariant> variants = variantRepository.findAllById(
            result.getContent().stream().map(BranchVariantStockLog::getVariantId).filter(Objects::nonNull)
                .distinct().toList()).stream()
            .collect(Collectors.toMap(ProductVariant::getId, Function.identity(), (a, b) -> a));
        List<DailyStockLogResponse> content = result.getContent().stream().map(l -> {
            ProductVariant v = variants.get(l.getVariantId());
            return new DailyStockLogResponse(l.getId(), l.getVariantId(),
                v == null ? null : v.getVariantCode(), v == null ? null : v.getVariantName(),
                l.getChangeType(), l.getQuantityChange(), l.getReferenceId(), l.getNote(), l.getCreatedAt());
        }).toList();
        return new PageResponse<>(result.getNumber(), result.getSize(), result.getTotalElements(),
            result.getTotalPages(), content);
    }

    /**
     * Bảng đối soát NVL ngày của chi nhánh cho màn Tồn sản phẩm (tab NVL &amp; Cấp hàng).
     * Kế hoạch = mở bán × BOM, đã dùng = đã bán × BOM (quy về đơn vị gốc),
     * tồn = khả dụng tại kho bán hàng, thiếu = max(0, kế hoạch - tồn).
     */
    @Transactional(readOnly = true)
    public MaterialShortageResponse materialShortage(UUID branchId, LocalDate date) {
        ShortageComputed computed = computeShortage(branchId, date);
        return new MaterialShortageResponse(branchId, computed.businessDate(),
            computed.warehouse().getId(), computed.warehouse().getCode(),
            computed.central().getId(), computed.central().getCode(), computed.lines());
    }

    /**
     * Tạo yêu cầu cấp hàng từ kho tổng về kho quán theo số thiếu (trạng thái REQUESTED,
     * đi tiếp luồng duyệt 2 phe). Không thiếu gì thì từ chối để khỏi phiếu rỗng.
     */
    @Transactional
    public StockTransferResponse requestReplenishment(UUID branchId, LocalDate date) {
        ShortageComputed computed = computeShortage(branchId, date);
        List<StockTransferItemRequest> items = computed.lines().stream()
            .filter(l -> l.shortageQuantity() != null && l.shortageQuantity().signum() > 0)
            .map(l -> new StockTransferItemRequest(l.materialId(), l.shortageQuantity(), null))
            .toList();
        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Kho quán đủ NVL cho kế hoạch ngày " + computed.businessDate() + ", không cần xin cấp.");
        }
        String note = "Xin cấp NVL ngày " + computed.businessDate() + " cho "
            + computed.warehouse().getCode() + " (" + items.size() + " NVL thiếu)";
        // Người quán thiếu quyền kho tổng -> create() tự rẽ REQUESTED (phe quán xin, phe kho duyệt).
        return stockTransferService.create(new CreateStockTransferRequest(
            null, computed.central().getId(), computed.warehouse().getId(),
            computed.businessDate(), note, items));
    }

    private ShortageComputed computeShortage(UUID branchId, LocalDate date) {
        if (branchId == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không được để trống.");
        }
        UUID effectiveBranch = dataScopeHelper.resolveEffectiveBranchId(branchId);
        var branch = branchRepository.findById(effectiveBranch)
            .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_BRANCH_INACTIVE);
        }
        LocalDate businessDate = date != null ? date : businessDay.today(effectiveBranch);
        Warehouse warehouse = resolveSellingWarehouse(effectiveBranch);
        Warehouse central = resolveCentralWarehouse();
        List<BranchVariantDailyStock> lines =
            stockRepository.findByBranchIdAndBusinessDateAndStatus(effectiveBranch, businessDate, "ACTIVE");
        Map<UUID, List<ProductRecipeItem>> recipesByVariant = Map.of();
        Set<UUID> materialIds = Set.of();
        if (!lines.isEmpty()) {
            Set<UUID> variantIds = lines.stream().map(BranchVariantDailyStock::getVariantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!variantIds.isEmpty()) {
                recipesByVariant = recipeRepository.findByVariantIdInAndStatus(variantIds, "ACTIVE")
                    .stream().collect(Collectors.groupingBy(ProductRecipeItem::getVariantId));
                materialIds = recipesByVariant.values().stream().flatMap(List::stream)
                    .map(ProductRecipeItem::getMaterialId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            }
        }
        Map<UUID, Material> materials = materialIds.isEmpty() ? Map.of()
            : materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
        Map<UUID, String> unitCodes = unitRepository.findAll().stream()
            .collect(Collectors.toMap(Unit::getId, Unit::getCode, (a, b) -> a));
        Map<UUID, BigDecimal> availableByMaterial = balanceRepository.findByWarehouseId(warehouse.getId())
            .stream().collect(Collectors.toMap(MaterialStockBalance::getMaterialId,
                b -> nvlDecimal(b.getQuantityOnHand()).subtract(nvlDecimal(b.getQuantityReserved())),
                BigDecimal::add));
        Map<UUID, BigDecimal> planned = new HashMap<>();
        Map<UUID, BigDecimal> consumed = new HashMap<>();
        Map<UUID, Boolean> unitMismatch = new HashMap<>();
        for (BranchVariantDailyStock s : lines) {
            List<ProductRecipeItem> recipeLines = recipesByVariant.get(s.getVariantId());
            if (recipeLines == null) {
                continue;
            }
            for (ProductRecipeItem recipe : recipeLines) {
                Material material = materials.get(recipe.getMaterialId());
                if (material == null) {
                    continue;
                }
                BigDecimal bomQuantity = recipe.getQuantity() != null ? recipe.getQuantity() : BigDecimal.ZERO;
                BigDecimal wastage = recipe.getWastagePercent() != null ? recipe.getWastagePercent() : BigDecimal.ZERO;
                BigDecimal factor = BigDecimal.ONE.add(
                    wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                BigDecimal perCup = bomQuantity.multiply(factor);
                BigDecimal plannedAdd = toBaseUnit(perCup, recipe.getUnitId(), material,
                    recipe.getMaterialId(), unitMismatch);
                BigDecimal consumedAdd = toBaseUnit(perCup, recipe.getUnitId(), material,
                    recipe.getMaterialId(), unitMismatch);
                planned.merge(recipe.getMaterialId(),
                    plannedAdd.multiply(BigDecimal.valueOf(nvlInt(s.getOpeningQuantity()))), BigDecimal::add);
                consumed.merge(recipe.getMaterialId(),
                    consumedAdd.multiply(BigDecimal.valueOf(nvlInt(s.getSoldQuantity()))), BigDecimal::add);
            }
        }
        Set<UUID> allMaterialIds = new HashSet<>(planned.keySet());
        allMaterialIds.addAll(consumed.keySet());
        List<MaterialShortageLineResponse> result = new ArrayList<>();
        for (UUID materialId : allMaterialIds) {
            Material material = materials.get(materialId);
            if (material == null) {
                continue;
            }
            BigDecimal plannedQty = planned.getOrDefault(materialId, BigDecimal.ZERO)
                .setScale(3, RoundingMode.HALF_UP);
            BigDecimal consumedQty = consumed.getOrDefault(materialId, BigDecimal.ZERO)
                .setScale(3, RoundingMode.HALF_UP);
            BigDecimal onHand = availableByMaterial.getOrDefault(materialId, BigDecimal.ZERO)
                .setScale(3, RoundingMode.HALF_UP);
            BigDecimal shortage = plannedQty.subtract(onHand);
            if (shortage.signum() < 0) {
                shortage = BigDecimal.ZERO;
            }
            result.add(new MaterialShortageLineResponse(material.getId(), material.getCode(),
                material.getName(), unitCodes.get(material.getBaseUnitId()), plannedQty, consumedQty,
                onHand, shortage.setScale(3, RoundingMode.HALF_UP),
                unitMismatch.getOrDefault(materialId, false)));
        }
        result.sort((a, b) -> compareNullLast(a.materialCode(), b.materialCode()));
        return new ShortageComputed(businessDate, warehouse, central, result);
    }

    /** Số lượng quy về đơn vị gốc; không quy được thì cộng thô + gắn cờ để đối chiếu tay. */
    private BigDecimal toBaseUnit(BigDecimal quantity, UUID unitId, Material material,
                                  UUID materialId, Map<UUID, Boolean> unitMismatch) {
        BigDecimal converted = unitConversionService.convertToBaseUnitLenient(quantity, unitId, material);
        if (converted == null) {
            unitMismatch.put(materialId, true);
            return quantity;
        }
        return converted;
    }

    /** Kho bán hàng của chi nhánh: ACTIVE, không phải CENTRAL, bắt buộc đúng 1 kho. */
    private Warehouse resolveSellingWarehouse(UUID branchId) {
        List<Warehouse> candidates = warehouseRepository.findByBranchId(branchId).stream()
            .filter(w -> "ACTIVE".equals(w.getStatus()) && !"CENTRAL".equals(w.getWarehouseType()))
            .toList();
        if (candidates.size() != 1) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Chi nhánh có " + candidates.size()
                    + " kho bán hàng khả dụng, cần đúng 1 kho (ACTIVE, không phải CENTRAL) để đối soát NVL.");
        }
        return candidates.get(0);
    }

    /** Kho tổng cấp hàng: ACTIVE loại CENTRAL, bắt buộc đúng 1 kho. */
    private Warehouse resolveCentralWarehouse() {
        List<Warehouse> candidates = warehouseRepository.findByStatus("ACTIVE").stream()
            .filter(w -> "CENTRAL".equals(w.getWarehouseType()))
            .toList();
        if (candidates.size() != 1) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Cần đúng 1 kho tổng (CENTRAL, ACTIVE) đang hoạt động để xin cấp hàng, hiện có "
                    + candidates.size() + ".");
        }
        return candidates.get(0);
    }

    private record ShortageComputed(LocalDate businessDate, Warehouse warehouse, Warehouse central,
                                    List<MaterialShortageLineResponse> lines) {
    }

    /**
     * Chốt tồn mở bán hàng loạt (nút Chốt theo gợi ý): từng dòng độc lập,
     * lỗi dòng nào báo dòng đó, không rollback cả lô.
     */
    @Transactional
    public DailyStockBatchResponse restockBatch(RestockDailyStockBatchRequest request) {
        if (request == null || request.branchId() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không được để trống.");
        }
        UUID effectiveBranch = dataScopeHelper.resolveEffectiveBranchId(request.branchId());
        List<DailyStockBatchResponse.DailyStockBatchItemResponse> results = new ArrayList<>();
        int succeeded = 0;
        for (RestockDailyStockBatchRequest.RestockDailyStockBatchItemRequest item : request.items()) {
            try {
                BranchVariantDailyStock saved = restock(effectiveBranch, item.variantId(),
                    item.openingQuantity(), request.note());
                succeeded++;
                results.add(new DailyStockBatchResponse.DailyStockBatchItemResponse(
                    item.variantId(), true, "OK",
                    saved.getOpeningQuantity(), saved.getRemainingQuantity()));
            } catch (BaseException e) {
                results.add(new DailyStockBatchResponse.DailyStockBatchItemResponse(
                    item.variantId(), false, e.getMessage(), null, null));
            }
        }
        return new DailyStockBatchResponse(succeeded, results.size() - succeeded, results);
    }

    /**
     * Tồn NVL khả dụng (onHand - reserved) tại kho bán hàng.
     * Chi nhánh nhiều/không có kho thì trả rỗng để màn hình ẩn gợi ý thay vì sập.
     * KHÔNG catch Exception ở đây: RuntimeException bị nuốt trong transaction sẽ đánh
     * dấu rollback-only, commit nổ UnexpectedRollbackException che mất lỗi gốc.
     */
    private Map<UUID, BigDecimal> loadAvailableMaterials(UUID branchId) {
        List<Warehouse> candidates = warehouseRepository.findByBranchId(branchId).stream()
            .filter(w -> "ACTIVE".equals(w.getStatus()) && !"CENTRAL".equals(w.getWarehouseType()))
            .toList();
        if (candidates.size() != 1) {
            return Map.of();
        }
        return balanceRepository.findByWarehouseId(candidates.get(0).getId()).stream()
            .collect(Collectors.toMap(MaterialStockBalance::getMaterialId,
                b -> nvlDecimal(b.getQuantityOnHand()).subtract(nvlDecimal(b.getQuantityReserved())),
                BigDecimal::add));
    }

    /**
     * Số ly tối đa pha được từ tồn NVL (min theo BOM, quy đơn vị gốc).
     * null = chưa có công thức hoặc thiếu dữ liệu kho/đơn vị.
     */
    private Integer capabilityOf(List<ProductRecipeItem> recipeLines,
                                   Map<UUID, BigDecimal> availableByMaterial,
                                   Map<UUID, Material> materialsById) {
        if (recipeLines == null || recipeLines.isEmpty() || availableByMaterial.isEmpty()) {
            return null;
        }
        BigDecimal best = null;
        for (ProductRecipeItem recipe : recipeLines) {
            Material material = materialsById.get(recipe.getMaterialId());
            BigDecimal available = availableByMaterial.get(recipe.getMaterialId());
            if (material == null || available == null) {
                return null;
            }
            BigDecimal bomQuantity = recipe.getQuantity() != null ? recipe.getQuantity() : BigDecimal.ZERO;
            BigDecimal wastage = recipe.getWastagePercent() != null ? recipe.getWastagePercent() : BigDecimal.ZERO;
            BigDecimal perCup = bomQuantity.multiply(BigDecimal.ONE.add(
                wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
            if (perCup.signum() <= 0) {
                continue;
            }
            BigDecimal perCupBase = unitConversionService.convertToBaseUnitLenient(
                perCup, recipe.getUnitId(), material);
            if (perCupBase == null || perCupBase.signum() <= 0) {
                return null;
            }
            BigDecimal cups = available.divide(perCupBase, 0, RoundingMode.FLOOR);
            if (best == null || cups.compareTo(best) < 0) {
                best = cups;
            }
        }
        if (best == null) {
            return null;
        }
        try {
            return best.intValueExact();
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal nvlDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nvlInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static int compareNullLast(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareToIgnoreCase(b);
    }
}
