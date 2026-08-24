package com.erp.backend_service.repository;

import com.erp.core.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu khách hàng (Customer). */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** Tìm khách hàng theo username, số điện thoại hoặc email (dùng cho đăng nhập). */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.username = :identifier
               OR c.phone = :identifier
               OR c.email = :identifier
            """)
    Optional<Customer> findByUsernameOrPhoneOrEmail(@Param("identifier") String identifier);

    /** Tìm khách hàng theo username. */
    Optional<Customer> findByUsername(String username);

    /** Kiểm tra tồn tại theo username. */
    boolean existsByUsername(String username);

    /** Kiểm tra username đã tồn tại ở một khách hàng khác (dùng khi cập nhật). */
    boolean existsByUsernameAndIdNot(String username, UUID id);

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

    /** Kiểm tra mã khách hàng đã tồn tại ở một khách hàng khác (dùng khi cập nhật). */
    boolean existsByCustomerCodeAndIdNot(String customerCode, UUID id);

    /** Kiểm tra email đã tồn tại ở một khách hàng khác (dùng khi cập nhật). */
    boolean existsByEmailAndIdNot(String email, UUID id);

    /** Kiểm tra số điện thoại đã tồn tại ở một khách hàng khác (dùng khi cập nhật). */
    boolean existsByPhoneAndIdNot(String phone, UUID id);

    /**
     * Tìm kiếm phân trang theo mã khách hàng, họ tên, số điện thoại hoặc email.
     * Khi {@code search} rỗng/null thì trả về toàn bộ.
     */
    @Query("""
            select c from Customer c
            where (:search is null or :search = ''
                   or lower(c.customerCode) like lower(concat('%', :search, '%'))
                   or lower(c.fullName) like lower(concat('%', :search, '%'))
                   or lower(c.phone) like lower(concat('%', :search, '%'))
                   or lower(c.email) like lower(concat('%', :search, '%')))
            """)
    Page<Customer> search(@Param("search") String search, Pageable pageable);
}
