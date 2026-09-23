package com.erp.backend_service.service.pos;

import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.OrderDeliveryRepository;
import com.erp.backend_service.repository.OrderStatusHistoryRepository;
import com.erp.core.domain.Account;
import com.erp.core.domain.Order;
import com.erp.core.domain.OrderDelivery;
import com.erp.core.domain.OrderStatusHistory;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Gán shipper TỰ ĐỘNG khi đơn DELIVERY vào CONFIRMED: chọn nhân viên ACTIVE
 * cùng chi nhánh (có vai trò hiệu lực) đang giữ ít đơn nhất.
 * Không có xe rảnh -> trả empty, đơn ở lại PENDING chờ gán TAY (không kẹt đơn).
 * Gán tay sau đó đè lên bình thường qua DeliveryService.assign().
 */
@Service
public class PosShipperAssignService {

    private static final Set<String> BUSY = Set.of("ASSIGNED", "PICKED_UP", "DELIVERING");

    private final OrderDeliveryRepository deliveryRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final OrderStatusHistoryRepository historyRepository;

    public PosShipperAssignService(OrderDeliveryRepository deliveryRepository,
                                   AccountRepository accountRepository,
                                   AccountRoleRepository accountRoleRepository,
                                   OrderStatusHistoryRepository historyRepository) {
        this.deliveryRepository = deliveryRepository;
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public Optional<UUID> autoAssign(Order order) {
        if (!"DELIVERY".equals(order.getOrderType())) {
            return Optional.empty();
        }
        OrderDelivery delivery = deliveryRepository.findByOrderId(order.getId()).orElse(null);
        if (delivery == null || !"PENDING".equals(delivery.getStatus())) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        // Truyền "" thay vì null cho search để Postgres khỏi đoán kiểu param
        // (cùng họ bug "? is null" từng sập màn list đơn).
        List<Account> branchAccounts = accountRepository
            .searchWithFilters("", order.getBranchId(), EntityStatus.ACTIVE, PageRequest.of(0, 100))
            .getContent();
        if (branchAccounts.isEmpty()) {
            return Optional.empty();
        }
        Set<UUID> effectiveAccountIds = accountRoleRepository.findEffectiveByAccountIdIn(
                branchAccounts.stream().map(Account::getId).toList(), EntityStatus.ACTIVE, now).stream()
            .map(com.erp.core.domain.AccountRole::getAccountId).collect(Collectors.toSet());
        List<Account> candidates = branchAccounts.stream()
            .filter(a -> effectiveAccountIds.contains(a.getId())).toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, Long> busyByShipper = new HashMap<>();
        for (Object[] row : deliveryRepository.countBusyByShipperIds(
            candidates.stream().map(Account::getId).toList(), BUSY)) {
            busyByShipper.put((UUID) row[0], (Long) row[1]);
        }
        Account chosen = candidates.stream()
            .min(Comparator.comparingLong(a -> busyByShipper.getOrDefault(a.getId(), 0L)))
            .orElse(null);
        if (chosen == null) {
            return Optional.empty();
        }
        delivery.setShipperId(chosen.getId());
        delivery.setAssignedAt(now);
        delivery.setStatus("ASSIGNED");
        delivery.setFailReason(null);
        delivery.setFailedAt(null);
        deliveryRepository.save(delivery);
        writeHistory(order, "Tự động gán shipper " + chosen.getFullName());
        return Optional.of(chosen.getId());
    }

    private void writeHistory(Order order, String reason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(order.getStatus());
        history.setNewStatus(order.getStatus());
        history.setChangedBy(null); // hệ thống tự động
        history.setChangedAt(Instant.now());
        history.setReason(reason);
        history.setStatus("ACTIVE");
        historyRepository.save(history);
    }
}
