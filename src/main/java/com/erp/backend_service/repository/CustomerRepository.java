package com.erp.backend_service.repository;

import com.erp.core.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Truy vấn dữ liệu khách hàng (Customer). */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, java.util.UUID> {

    /** Tìm khách hàng theo số điện thoại hoặc email (dùng cho đăng nhập). */
    @Query("SELECT c FROM Customer c WHERE c.phone = :identifier OR c.email = :identifier")
    Optional<Customer> findByPhoneOrEmail(@Param("identifier") String identifier);

    /** Tìm khách hàng theo mã khách hàng. */
    Optional<Customer> findByCustomerCode(String customerCode);

    /** Tìm khách hàng theo id nhà cung cấp (sub của Google, v.v.). */
    Optional<Customer> findByProviderId(String providerId);

    /** Tìm khách hàng theo email. */
    Optional<Customer> findByEmail(String email);

    /** Kiểm tra tồn tại theo số điện thoại. */
    boolean existsByPhone(String phone);

    /** Kiểm tra tồn tại theo email. */
    boolean existsByEmail(String email);
}
