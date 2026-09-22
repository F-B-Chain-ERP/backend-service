package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.UnitConversionRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.service.UnitConversionService;
import com.erp.core.domain.Material;
import com.erp.core.domain.Unit;
import com.erp.core.domain.UnitConversion;
import com.erp.core.dto.request.inv.CreateUnitConversionRequest;
import com.erp.core.dto.request.inv.UpdateUnitConversionRequest;
import com.erp.core.dto.response.inv.UnitConversionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Quy đổi đơn vị tính.
 * Nhóm quy đổi: MASS + WEIGHT (khối lượng), VOLUME (thể tích), COUNT (đếm).
 * COUNT từng loại (cái/chai/gói/thùng...) không quy đổi lẫn nhau nếu không phải
 * pack khai báo của chính NVL đó.
 */
@Service
public class UnitConversionServiceImpl implements UnitConversionService {

    private static final String ACTIVE = "ACTIVE";

    /** Gom nhóm đơn vị: MASS và WEIGHT là một (kg/g), còn lại theo unit_type. */
    private static final Map<String, String> UNIT_FAMILY = Map.of(
        "MASS", "MASS",
        "WEIGHT", "MASS",
        "VOLUME", "VOLUME",
        "COUNT", "COUNT");

    private final UnitConversionRepository conversionRepository;
    private final UnitRepository unitRepository;

    public UnitConversionServiceImpl(UnitConversionRepository conversionRepository,
                                     UnitRepository unitRepository) {
        this.conversionRepository = conversionRepository;
        this.unitRepository = unitRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitConversionResponse> list() {
        return conversionRepository.findByStatusOrderByCreatedAtAsc(ACTIVE).stream()
            .map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnitConversionResponse get(UUID id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public UnitConversionResponse create(CreateUnitConversionRequest request) {
        Unit from = activeUnit(request.fromUnitId());
        Unit to = activeUnit(request.toUnitId());
        if (from.getId().equals(to.getId())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Hai đầu quy đổi phải khác nhau.");
        }
        if (!sameFamily(from, to)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Chỉ tạo quy đổi cùng nhóm (khối lượng/thể tích/đếm): "
                    + from.getCode() + " (" + from.getUnitType() + ") với "
                    + to.getCode() + " (" + to.getUnitType() + ").");
        }
        if (request.factor() == null || request.factor().signum() <= 0) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Hệ số quy đổi phải lớn hơn 0.");
        }
        if (conversionRepository.existsByFromUnitIdAndToUnitId(from.getId(), to.getId())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Cặp quy đổi " + from.getCode() + " -> " + to.getCode() + " đã tồn tại.");
        }
        UnitConversion entity = new UnitConversion();
        entity.setFromUnitId(from.getId());
        entity.setToUnitId(to.getId());
        entity.setFactor(request.factor());
        entity.setStatus(ACTIVE);
        return toResponse(conversionRepository.save(entity));
    }

    @Override
    @Transactional
    public UnitConversionResponse update(UUID id, UpdateUnitConversionRequest request) {
        UnitConversion entity = findById(id);
        if (request.factor() != null) {
            if (request.factor().signum() <= 0) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Hệ số quy đổi phải lớn hơn 0.");
            }
            entity.setFactor(request.factor());
        }
        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().trim().toUpperCase();
            if (!ACTIVE.equals(status) && !"INACTIVE".equals(status)) {
                throw new BaseException(ErrorCode.INVALID_REQUEST);
            }
            entity.setStatus(status);
        }
        return toResponse(conversionRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        conversionRepository.delete(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal quantity, UUID fromUnitId, UUID toUnitId) {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        if (Objects.equals(fromUnitId, toUnitId)) {
            return quantity;
        }
        BigDecimal factor = findFactor(fromUnitId, toUnitId);
        if (factor == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Không có đường quy đổi giữa 2 đơn vị (thiếu dòng quy đổi hoặc khác nhóm).");
        }
        return quantity.multiply(factor);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal convertToBaseUnit(BigDecimal quantity, UUID fromUnitId, Material material) {
        return convertToBaseUnitInternal(quantity, fromUnitId, material);
    }

    /**
     * Bản không ném cho đường đọc (bảng tồn, đối soát): không quy được thì null
     * để UI ẩn gợi ý thay vì sập. BẮT BUỘC catch ở trong (trước khi qua proxy):
     * RuntimeException thoát khỏi method @Transactional sẽ đánh dấu cả transaction
     * rollback-only, method ngoài có catch cũng không cứu được (commit nổ
     * UnexpectedRollbackException che mất lỗi gốc).
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal convertToBaseUnitLenient(BigDecimal quantity, UUID fromUnitId, Material material) {
        try {
            return convertToBaseUnitInternal(quantity, fromUnitId, material);
        } catch (BaseException e) {
            return null;
        }
    }

    private BigDecimal convertToBaseUnitInternal(BigDecimal quantity, UUID fromUnitId, Material material) {
        if (quantity == null || material == null) {
            return BigDecimal.ZERO;
        }
        if (Objects.equals(fromUnitId, material.getBaseUnitId())) {
            return quantity;
        }
        if (Objects.equals(fromUnitId, material.getPackUnitId())
            && material.getPackToBaseFactor() != null) {
            return quantity.multiply(material.getPackToBaseFactor());
        }
        return convert(quantity, fromUnitId, material.getBaseUnitId());
    }

    @Override
    @Transactional(readOnly = true)
    public void validateBomUnit(Material material, Unit unit) {
        if (material == null || unit == null) {
            throw new BaseException(ErrorCode.MENU_400_BOM_UNIT_MISMATCH);
        }
        if (Objects.equals(unit.getId(), material.getBaseUnitId())) {
            return;
        }
        if (Objects.equals(unit.getId(), material.getPackUnitId())
            && material.getPackToBaseFactor() != null) {
            return;
        }
        Unit baseUnit = unitRepository.findById(material.getBaseUnitId()).orElse(null);
        if (baseUnit != null && sameFamily(unit, baseUnit)
            && findFactor(unit.getId(), baseUnit.getId()) != null) {
            return;
        }
        throw new BaseException(ErrorCode.MENU_400_BOM_UNIT_MISMATCH,
            "Đơn vị " + unit.getCode() + " không quy đổi được về đơn vị gốc "
                + (baseUnit != null ? baseUnit.getCode() : "?") + " của NVL " + material.getCode()
                + " (thiếu dòng quy đổi hoặc khác nhóm đơn vị).");
    }

    /** Hệ số from -&gt; to, tự đảo chiều 1/factor, null khi không có đường quy đổi ACTIVE. */
    private BigDecimal findFactor(UUID fromUnitId, UUID toUnitId) {
        return conversionRepository.findByFromUnitIdAndToUnitIdAndStatus(fromUnitId, toUnitId, ACTIVE)
            .map(UnitConversion::getFactor)
            .or(() -> conversionRepository.findByFromUnitIdAndToUnitIdAndStatus(toUnitId, fromUnitId, ACTIVE)
                .map(reverse -> BigDecimal.ONE.divide(reverse.getFactor(), 6, RoundingMode.HALF_UP)))
            .orElse(null);
    }

    private boolean sameFamily(Unit a, Unit b) {
        if (a == null || b == null || a.getUnitType() == null || b.getUnitType() == null) {
            return false;
        }
        String familyA = UNIT_FAMILY.get(a.getUnitType().trim().toUpperCase());
        String familyB = UNIT_FAMILY.get(b.getUnitType().trim().toUpperCase());
        return familyA != null && familyA.equals(familyB);
    }

    private Unit activeUnit(UUID unitId) {
        Unit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND));
        if (!ACTIVE.equals(unit.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND);
        }
        return unit;
    }

    private UnitConversion findById(UUID id) {
        return conversionRepository.findById(id)
            .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REQUEST, "Dòng quy đổi không tồn tại."));
    }

    private UnitConversionResponse toResponse(UnitConversion entity) {
        Unit from = unitRepository.findById(entity.getFromUnitId()).orElse(null);
        Unit to = unitRepository.findById(entity.getToUnitId()).orElse(null);
        return new UnitConversionResponse(
            entity.getId() != null ? entity.getId().toString() : null,
            entity.getFromUnitId() != null ? entity.getFromUnitId().toString() : null,
            from != null ? from.getCode() : null,
            from != null ? from.getName() : null,
            entity.getToUnitId() != null ? entity.getToUnitId().toString() : null,
            to != null ? to.getCode() : null,
            to != null ? to.getName() : null,
            entity.getFactor(),
            entity.getStatus(),
            entity.getCreatedAt());
    }
}
