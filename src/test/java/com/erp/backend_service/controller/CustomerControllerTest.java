package com.erp.backend_service.controller;

import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.service.CustomerService;
import com.erp.core.dto.auth.UpdateCustomerRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.customer.CustomerDetailResponse;
import com.erp.core.enums.AuthProvider;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    private CustomerController customerController;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        customerController = new CustomerController(customerService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockCustomerAuth(UUID id) {
        CustomUserDetails userDetails = new CustomUserDetails(
                PrincipalType.CUSTOMER,
                id,
                "0987654321",
                "password",
                true,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Instant.now()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    @DisplayName("GET /me should return customer detail for authenticated customer")
    void getMe_WhenAuthenticatedCustomer_ReturnsCustomerDetail() {
        mockCustomerAuth(customerId);

        CustomerDetailResponse mockResponse = new CustomerDetailResponse(
                customerId, "CUST001", "0987654321", "Test Customer", "0987654321",
                "test@example.com", AuthProvider.LOCAL, true, true, null,
                null, "OTHER", EntityStatus.ACTIVE, null, Instant.now(), Instant.now()
        );
        when(customerService.getCustomer(customerId)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<CustomerDetailResponse>> response = customerController.getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Customer", response.getBody().data().fullName());
        verify(customerService).getCustomer(customerId);
    }

    @Test
    @DisplayName("PUT /me should update customer profile for authenticated customer")
    void updateMe_WhenAuthenticatedCustomer_UpdatesProfile() {
        mockCustomerAuth(customerId);

        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Updated Name", null, "newemail@example.com", "0987654321", null, null, "MALE", true, EntityStatus.ACTIVE
        );
        CustomerDetailResponse mockResponse = new CustomerDetailResponse(
                customerId, "CUST001", "0987654321", "Updated Name", "0987654321",
                "newemail@example.com", AuthProvider.LOCAL, true, true, null,
                null, "MALE", EntityStatus.ACTIVE, null, Instant.now(), Instant.now()
        );
        when(customerService.updateCustomer(customerId, request)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<CustomerDetailResponse>> response = customerController.updateMe(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Updated Name", response.getBody().data().fullName());
        verify(customerService).updateCustomer(customerId, request);
    }
}

