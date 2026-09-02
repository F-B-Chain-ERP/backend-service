package com.erp.backend_service.repository;

import com.erp.core.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu thông báo (notification). */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
