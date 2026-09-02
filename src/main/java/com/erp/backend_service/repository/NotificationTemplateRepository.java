package com.erp.backend_service.repository;

import com.erp.core.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu mẫu thông báo (notification_template). */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /** Tìm mẫu thông báo theo mã, ưu tiên template đang hoạt động (ACTIVE). */
    Optional<NotificationTemplate> findFirstByCodeAndStatus(String code, String status);
}
