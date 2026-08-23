package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.CustomerMapper;
import com.erp.backend_service.repository.CustomerRepository;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.CustomerService;
import com.erp.backend_service.service.RefreshTokenService;
import com.erp.core.domain.Customer;
import com.erp.core.dto.auth.CreateCustomerRequest;
import com.erp.core.dto.auth.ResetCustomerPasswordRequest;
import com.erp.core.dto.auth.UpdateCustomerRequest;
import com.erp.core.dto.response.CustomerDetailResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.AuthProvider;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Triển khai {@link CustomerService}: quản lý tài khoản khách hàng (customer) do
 * admin nội bộ (ACCOUNT) thực hiện (tạo, xem, tìm kiếm, cập nhật, vô hiệu hóa,
 * đặt lại mật khẩu). Khách hàng hoàn toàn tách biệt với tài khoản nội bộ.
 */
@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final Duration accessTokenLifetime;

    public CustomerServiceImpl(CustomerRepository customerRepository,
                               CustomerMapper customerMapper,
                               PasswordEncoder passwordEncoder,
                               RefreshTokenService refreshTokenService,
                               @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenLifetime = Duration.ofSeconds(accessTokenExpiry);
    }

    /** {@inheritDoc} */
    @Override
    public CustomerDetailResponse createCustomer(CreateCustomerRequest request) {
        assertInternalAdmin();
        if (request.email() != null && customerRepository.existsByEmail(request.email())) {
            throw new BaseException(ErrorCode.EMAIL_EXISTED);
        }
        if (request.phone() != null && customerRepository.existsByPhone(request.phone())) {
            throw new BaseException(ErrorCode.PHONE_EXISTED);
        }
        if (request.username() != null && customerRepository.existsByUsername(request.username())) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }

        Customer customer = new Customer();
        customer.setCustomerCode(generateCustomerCode());
        customer.setUsername(request.username());
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setAvatarUrl(request.avatarUrl());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setGender(request.gender());
        customer.setAuthProvider(request.authProvider() != null ? request.authProvider() : AuthProvider.LOCAL);
        if (request.password() != null && !request.password().isBlank()) {
            customer.setPassword(passwordEncoder.encode(request.password()));
            customer.setHasLocalPassword(true);
        } else {
            customer.setHasLocalPassword(false);
        }
        customer.setEmailVerified(request.emailVerified() != null ? request.emailVerified() : false);
        customer.setStatus(request.status() != null ? request.status() : EntityStatus.ACTIVE);
        customer = customerRepository.save(customer);
        return customerMapper.toResponse(customer);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomer(UUID id) {
        assertInternalAdmin();
        return customerMapper.toResponse(findById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerDetailResponse> listCustomers(int page, int size, String search) {
        assertInternalAdmin();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<Customer> customerPage = customerRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, pageable);
        return new PageResponse<>(
                customerPage.getNumber(),
                customerPage.getSize(),
                customerPage.getTotalElements(),
                customerPage.getTotalPages(),
                customerPage.getContent().stream().map(customerMapper::toResponse).toList()
        );
    }

    /** {@inheritDoc} */
    @Override
    public CustomerDetailResponse updateCustomer(UUID id, UpdateCustomerRequest request) {
        assertInternalAdmin();
        Customer customer = findById(id);

        if (request.fullName() !=null) {
            customer.setFullName(request.fullName());
        }
        if (request.username() != null && !Objects.equals(customer.getUsername(), request.username())) {
            if (customerRepository.existsByUsernameAndIdNot(request.username(), id)) {
                throw new BaseException(ErrorCode.USER_EXISTED);
            }
            customer.setUsername(request.username());
        }
        if (request.email() != null && !Objects.equals(customer.getEmail(), request.email())) {
            if (customerRepository.existsByEmailAndIdNot(request.email(), id)) {
                throw new BaseException(ErrorCode.EMAIL_EXISTED);
            }
            customer.setEmail(request.email());
        }
        if (request.phone() != null && !Objects.equals(customer.getPhone(), request.phone())) {
            if (customerRepository.existsByPhoneAndIdNot(request.phone(), id)) {
                throw new BaseException(ErrorCode.PHONE_EXISTED);
            }
            customer.setPhone(request.phone());
        }
        if (request.avatarUrl() != null) {
            customer.setAvatarUrl(request.avatarUrl());
        }
        if (request.dateOfBirth() != null) {
            customer.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            customer.setGender(request.gender());
        }
        if (request.emailVerified() != null) {
            customer.setEmailVerified(request.emailVerified());
        }
        if (request.status() != null) {
            customer.setStatus(request.status());
        }
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    /** {@inheritDoc} */
    @Override
    public void deleteCustomer(UUID id) {
        assertInternalAdmin();
        Customer customer = findById(id);
        customer.setStatus(EntityStatus.INACTIVE);
        customerRepository.save(customer);
        refreshTokenService.revokeAll(PrincipalType.CUSTOMER, id);
    }

    /** {@inheritDoc} */
    @Override
    public CustomerDetailResponse resetPassword(UUID id, ResetCustomerPasswordRequest request) {
        assertInternalAdmin();
        Customer customer = findById(id);
        customer.setPassword(passwordEncoder.encode(request.password()));
        customer.setHasLocalPassword(true);
        Customer saved = customerRepository.save(customer);
        refreshTokenService.revokeAll(PrincipalType.CUSTOMER, id);
        return customerMapper.toResponse(saved);
    }

    /** Lấy khách hàng theo id, ném lỗi nếu không tồn tại. */
    private Customer findById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    /** Chỉ tài khoản nội bộ (ACCOUNT) mới được quản lý khách hàng. */
    private void assertInternalAdmin() {
        if (SecurityUtils.getCurrentPrincipalType().orElse(null) != PrincipalType.ACCOUNT) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** Sinh mã khách hàng duy nhất. */
    private String generateCustomerCode() {
        return "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
