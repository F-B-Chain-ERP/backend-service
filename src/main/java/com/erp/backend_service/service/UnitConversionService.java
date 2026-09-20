package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateUnitConversionRequest;
import com.erp.core.dto.request.inv.UpdateUnitConversionRequest;
import com.erp.core.dto.response.inv.UnitConversionResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.erp.core.domain.Material;
import com.erp.core.domain.Unit;

public interface UnitConversionService {

    List<UnitConversionResponse> list();

    UnitConversionResponse get(UUID id);

    UnitConversionResponse create(CreateUnitConversionRequest request);

    UnitConversionResponse update(UUID id, UpdateUnitConversionRequest request);

    void delete(UUID id);

    /** Quy đổi số lượng giữa 2 đơn vị (tự đảo chiều 1/factor). */
    BigDecimal convert(BigDecimal quantity, UUID fromUnitId, UUID toUnitId);

    /** Quy đổi về đơn vị gốc của NVL (ưu tiên pack của chính NVL đó). */
    BigDecimal convertToBaseUnit(BigDecimal quantity, UUID fromUnitId, Material material);

    /** Validate đơn vị dòng BOM: gốc, pack của NVL, hoặc cùng nhóm có dòng quy đổi. */
    void validateBomUnit(Material material, Unit unit);
}
