package com.erp.backend_service.repository;

import com.erp.core.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu tài khoản (Account). */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Tìm tài khoản theo tên đăng nhập. */
    Optional<Account> findByUsername(String username);

    /** Tìm tài khoản theo email. */
    Optional<Account> findByEmail(String email);

    /** Tìm tài khoản theo username hoặc email (dùng cho đăng nhập). */
    Optional<Account> findByUsernameOrEmail(String username, String email);

    /** Kiểm tra tồn tại theo số điện thoại. */
    boolean existsByPhone(String phone);

    /** Kiểm tra username đã tồn tại ở một tài khoản khác (dùng khi cập nhật). */
    boolean existsByUsernameAndIdNot(String username, UUID id);

    /** Kiểm tra email đã tồn tại ở một tài khoản khác (dùng khi cập nhật). */
    boolean existsByEmailAndIdNot(String email, UUID id);

    /** Kiểm tra số điện thoại đã tồn tại ở một tài khoản khác (dùng khi cập nhật). */
    boolean existsByPhoneAndIdNot(String phone, UUID id);

    /**
     * Tìm kiếm phân trang theo username, họ tên hoặc email, hỗ trợ lọc theo chi nhánh.
     * Khi {@code search} hoặc {@code branchId} rỗng/null thì không áp dụng điều kiện tương ứng.
     */
    @Query("""
            select a from Account a
            where (:search is null or :search = ''
                   or lower(a.username) like lower(concat('%', :search, '%'))
                   or lower(a.fullName) like lower(concat('%', :search, '%'))
                   or lower(a.email) like lower(concat('%', :search, '%')))
              and (:branchId is null or a.primaryBranchId = :branchId)
            """)
    Page<Account> search(@Param("search") String search,
                         @Param("branchId") UUID branchId,
                         Pageable pageable);
}


