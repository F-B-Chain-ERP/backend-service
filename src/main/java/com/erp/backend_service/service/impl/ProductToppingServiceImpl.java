package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ProductToppingMapper;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductToppingRepository;
import com.erp.backend_service.repository.ToppingRepository;
import com.erp.backend_service.service.ProductToppingService;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductTopping;
import com.erp.core.domain.Topping;
import com.erp.core.dto.request.menu.AddProductToppingRequest;
import com.erp.core.dto.request.menu.UpdateProductToppingRequest;
import com.erp.core.dto.response.menu.ProductToppingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai {@link ProductToppingService}: quản lý liên kết topping–sản phẩm
 * với kiểm tra sản phẩm ACTIVE (chỉ cho POST/PUT), trùng lặp và hard delete.
 */
@Service
public class ProductToppingServiceImpl implements ProductToppingService {

    private static final Logger log = LoggerFactory.getLogger(ProductToppingServiceImpl.class);

    private final ProductToppingRepository productToppingRepository;
    private final ProductRepository productRepository;
    private final ToppingRepository toppingRepository;
    private final ProductToppingMapper mapper;

    public ProductToppingServiceImpl(
            ProductToppingRepository productToppingRepository,
            ProductRepository productRepository,
            ToppingRepository toppingRepository,
            ProductToppingMapper mapper
    ) {
        this.productToppingRepository = productToppingRepository;
        this.productRepository = productRepository;
        this.toppingRepository = toppingRepository;
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ProductToppingResponse> listByProduct(UUID productId) {
        log.info("Lấy danh sách topping của sản phẩm: {}", productId);
        productRepository.findById(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));

        List<ProductTopping> pts = productToppingRepository.findByProductIdOrderByCreatedAtAsc(productId);
        Map<UUID, Topping> toppingMap = loadToppings(pts);
        return pts.stream()
                .map(pt -> mapper.toResponse(pt, toppingMap.get(pt.getToppingId())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductToppingResponse add(UUID productId, AddProductToppingRequest request) {
        log.info("Thêm topping {} vào sản phẩm {}", request.toppingId(), productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_409_PRODUCT_INACTIVE);
        }

        Topping topping = toppingRepository.findById(request.toppingId())
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_TOPPING_NOT_FOUND));

        if (productToppingRepository.existsByProductIdAndToppingId(productId, request.toppingId())) {
            throw new BaseException(ErrorCode.MENU_409_PRODUCT_TOPPING_EXISTED);
        }

        int maxQty = request.maxQuantity() != null ? request.maxQuantity() : 1;
        if (maxQty < 1) {
            throw new BaseException(ErrorCode.MENU_400_INVALID_MAX_QUANTITY);
        }

        ProductTopping pt = new ProductTopping();
        pt.setProductId(productId);
        pt.setToppingId(request.toppingId());
        pt.setDefault(Boolean.TRUE.equals(request.isDefault()));
        pt.setMaxQuantity(maxQty);
        pt.setStatus("ACTIVE");

        return mapper.toResponse(productToppingRepository.save(pt), topping);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductToppingResponse update(UUID id, UpdateProductToppingRequest request) {
        log.info("Cập nhật product-topping: {}", id);

        ProductTopping pt = productToppingRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_TOPPING_NOT_FOUND));

        Product product = productRepository.findById(pt.getProductId())
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_409_PRODUCT_INACTIVE);
        }

        int maxQty = request.maxQuantity() != null ? request.maxQuantity() : pt.getMaxQuantity();
        if (maxQty < 1) {
            throw new BaseException(ErrorCode.MENU_400_INVALID_MAX_QUANTITY);
        }

        pt.setDefault(Boolean.TRUE.equals(request.isDefault()));
        pt.setMaxQuantity(maxQty);

        Topping topping = toppingRepository.findById(pt.getToppingId()).orElse(null);
        return mapper.toResponse(productToppingRepository.save(pt), topping);
    }

    /** {@inheritDoc} — hard delete, hoạt động cả khi sản phẩm INACTIVE. */
    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Xóa product-topping (hard delete): {}", id);
        ProductTopping pt = productToppingRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_TOPPING_NOT_FOUND));
        productToppingRepository.delete(pt);
    }

    private Map<UUID, Topping> loadToppings(List<ProductTopping> pts) {
        Set<UUID> ids = pts.stream().map(ProductTopping::getToppingId).collect(Collectors.toSet());
        return toppingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Topping::getId, t -> t, (a, b) -> a));
    }
}
