package com.erp.backend_service.mapper;

import com.erp.core.domain.Customer;
import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.CustomerResponse;
import com.erp.core.enums.PrincipalType;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi thông tin xác thực thành {@link AuthResponse}.
 */
@Component
public class AuthMapper {
    private static final String TOKEN_TYPE = "Bearer";

    /** Tạo response xác thực từ token và thông tin thực thể. */
    public AuthResponse toResponse(String accessToken,
                                    String refreshToken,
                                    PrincipalType principalType,
                                    CustomerResponse customer,
                                    boolean requiresScopeAssignment,
                                    boolean requiresEmailVerification,
                                    String verifyToken) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                principalType,
                customer,
                requiresScopeAssignment,
                requiresEmailVerification,
                verifyToken
        );
    }

    /** Ánh xạ Customer sang CustomerResponse. */
    public CustomerResponse toCustomerResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getUsername(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAuthProvider(),
                customer.isHasLocalPassword(),
                customer.isEmailVerified(),
                customer.getStatus(),
                customer.getLastLoginAt(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
