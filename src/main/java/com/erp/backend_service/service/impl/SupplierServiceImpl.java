package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.SupplierMapper;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.service.SupplierService;
import com.erp.core.domain.Supplier;
import com.erp.core.dto.request.proc.CreateSupplierRequest;
import com.erp.core.dto.request.proc.UpdateSupplierRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.proc.SupplierResponse;
import com.erp.core.enums.EntityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Triển khai {@link SupplierService}: quản lý nhà cung cấp với kiểm tra trùng mã
 * và ánh xạ thực thể sang response.
 */
@Service
public class SupplierServiceImpl implements SupplierService {

    private static final int FIXED_PAGE_SIZE = 10;
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final Logger log = LoggerFactory.getLogger(SupplierServiceImpl.class);

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    public SupplierServiceImpl(SupplierRepository supplierRepository, SupplierMapper supplierMapper) {
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(int page, int size, String search, EntityStatus status) {
        log.info("Supplier list: keyword={}, page={}, size={}, status={}", search, page, size, status);
        Pageable pageable = PageRequest.of(Math.max(page, 0), FIXED_PAGE_SIZE, Sort.by("createdAt").descending());
        Page<Supplier> supplierPage = supplierRepository.search(
                StringUtils.hasText(search) ? search.trim() : "",
                status != null ? status.name() : null,
                pageable);
        return new PageResponse<>(
                supplierPage.getNumber(),
                supplierPage.getSize(),
                supplierPage.getTotalElements(),
                supplierPage.getTotalPages(),
                supplierPage.getContent().stream().map(this::toResponse).toList()
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        log.info("Supplier get: id={}", id);
        return toResponse(findById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        log.info("Supplier create: code={}, name={}", request.code(), request.name());
        if (supplierRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.SUPPLIER_CODE_EXISTED);
        }
        if (request.taxCode() != null
        && !request.taxCode().isBlank()
        && supplierRepository.existsByTaxCode(request.taxCode())){
            throw new BaseException(ErrorCode.SUPPLIER_TAX_CODE_EXISTED);
        }
        Supplier supplier = new Supplier();
        apply(supplier, request.code(), request.name(), request.taxCode(), request.contactName(),
                request.phone(), request.email(), request.address(), request.paymentTermDays(), request.status());
        Supplier saved = supplierRepository.save(supplier);
        log.info("Supplier created: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SupplierResponse update(UUID id, UpdateSupplierRequest request) {
        log.info("Supplier update: id={}", id);
        Supplier supplier = findById(id);
        if (!supplier.getCode().equals(request.code()) && supplierRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
        if(StringUtils.hasText(request.taxCode())
        && supplierRepository.existsByTaxCodeAndIdNot(request.taxCode(), id)){
            throw new BaseException(ErrorCode.SUPPLIER_TAX_CODE_EXISTED);
        }
        apply(supplier, request.code(), request.name(), request.taxCode(), request.contactName(),
                request.phone(), request.email(), request.address(), request.paymentTermDays(), request.status());
        Supplier saved = supplierRepository.save(supplier);
        log.info("Supplier updated: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    /** PATCH */
    @Override
    @Transactional
    public SupplierResponse updateStatus(UUID id, String status) {
        log.info("Supplier update status: id={}, newStatus={}", id, status);
        Supplier supplier = findById(id);

        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedStatus = status.trim().toUpperCase();

        if (!"ACTIVE".equals(normalizedStatus)
                && !"INACTIVE".equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        supplier.setStatus(normalizedStatus);

        Supplier saved = supplierRepository.save(supplier);
        log.info("Supplier status updated: id={}, status={}", saved.getId(), normalizedStatus);
        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Supplier delete: id={}", id);
        if (!supplierRepository.existsById(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        supplierRepository.deleteById(id);
        log.info("Supplier deleted: id={}", id);
    }

    private Supplier findById(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void apply(Supplier supplier, String code, String name, String taxCode, String contactName,
                       String phone, String email, String address, Integer paymentTermDays, String status) {
        supplier.setCode(code);
        supplier.setName(name);
        supplier.setTaxCode(taxCode);
        supplier.setContactName(contactName);
        supplier.setPhone(phone);
        supplier.setEmail(email);
        supplier.setAddress(address);
        supplier.setPaymentTermDays(paymentTermDays != null ? paymentTermDays : 0);
        supplier.setStatus(status != null && !status.isBlank() ? status : DEFAULT_STATUS);
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return supplierMapper.toResponse(supplier);
    }
}
