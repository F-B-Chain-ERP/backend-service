package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftAssignmentMapper;
import com.erp.backend_service.mapper.ShiftReportMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.ShiftRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ShiftOperationService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Order;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.domain.ShiftReport;
import com.erp.core.dto.request.store.CloseShiftRequest;
import com.erp.core.dto.request.store.OpenShiftRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ClosingSummaryResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import com.erp.core.dto.response.store.ShiftReportResponse;
import com.erp.core.enums.EntityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShiftOperationServiceImpl implements ShiftOperationService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final BranchRepository branchRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final ShiftReportMapper shiftReportMapper;
    private final DataScopeHelper dataScopeHelper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Ngưỡng lệch két cho phép: max(10.000đ, 0,5% doanh thu ca). Trong mức thì
    // lý do giải trình optional, vượt mức thì bắt buộc lý do >= 10 ký tự.
    private static final BigDecimal TOLERANCE_ABSOLUTE = BigDecimal.valueOf(10000);
    private static final BigDecimal TOLERANCE_RATE = new BigDecimal("0.005");

    public ShiftOperationServiceImpl(ShiftAssignmentRepository shiftAssignmentRepository,
                                     ShiftRepository shiftRepository,
                                     ShiftReportRepository shiftReportRepository,
                                     OrderRepository orderRepository,
                                     AccountRepository accountRepository,
                                     AccountRoleRepository accountRoleRepository,
                                     BranchRepository branchRepository,
                                     ShiftAssignmentMapper shiftAssignmentMapper,
                                     ShiftReportMapper shiftReportMapper,
                                     DataScopeHelper dataScopeHelper) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftRepository = shiftRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.branchRepository = branchRepository;
        this.shiftAssignmentMapper = shiftAssignmentMapper;
        this.shiftReportMapper = shiftReportMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    private static final List<String> CASH_ROLE_CODES = List.of("ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    private static final List<String> MANAGER_ROLE_CODES = List.of("ADMIN", "ROLE_MANAGER");

    // Chỉ thu ngân/quản lý/admin được cầm két (mở két). Pha chế chỉ điểm danh sau này.
    private boolean hasEffectiveRole(UUID accountId, UUID branchId, List<String> roleCodes) {
        if (accountId == null || branchId == null || roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return !accountRoleRepository.findEffectiveAccountIdsByRoleCodesAndBranchId(
                List.of(accountId), roleCodes, branchId, EntityStatus.ACTIVE, Instant.now()).isEmpty();
    }

    private boolean isCashHandler(UUID accountId, UUID branchId) {
        return hasEffectiveRole(accountId, branchId, CASH_ROLE_CODES);
    }

    private boolean isManagerOrAdmin(UUID accountId, UUID branchId) {
        return hasEffectiveRole(accountId, branchId, MANAGER_ROLE_CODES);
    }

    private void enforceOperationAccess(ShiftAssignment assignment, UUID currentUserId) {
        boolean ownsAssignment = currentUserId != null && currentUserId.equals(assignment.getAccountId());
        if (!ownsAssignment && !isManagerOrAdmin(currentUserId, assignment.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED);
        }
    }

    // Ca CHECKED_IN mà ngày làm đã qua hôm nay là ca kẹt qua đêm.
    private static boolean isStaleShift(ShiftAssignment a) {
        return a.getWorkDate() != null && a.getWorkDate().isBefore(LocalDate.now());
    }

    private String describeShift(ShiftAssignment a) {
        Shift s = shiftRepository.findById(a.getShiftId()).orElse(null);
        Account acc = accountRepository.findById(a.getAccountId()).orElse(null);
        String code = s != null && s.getShiftCode() != null ? s.getShiftCode() : "?";
        String who = acc != null && acc.getFullName() != null ? acc.getFullName() : String.valueOf(a.getAccountId());
        return "ca " + code + " ngày " + a.getWorkDate() + " của " + who;
    }

    // Tự đóng ca kẹt qua đêm để ca sau mở được (không cần cron, chạy lazy lúc mở ca).
    // - Ca két: lập biên bản SUBMITTED với actual = expected (chênh lệch 0), người nộp
    //   là chủ ca — vẫn phải chờ quản lý khác duyệt/từ chối, tiền không tự vào sổ.
    // - Ca pha chế (không két): checkout thẳng, chỉ là timestamp chấm công.
    private void autoCloseStaleShift(ShiftAssignment stale) {
        Instant now = Instant.now();
        boolean cashShift = isCashHandler(stale.getAccountId(), stale.getBranchId())
                && stale.getInitialCash() != null;
        if (cashShift) {
            Instant checkInAt = stale.getCheckInAt() != null ? stale.getCheckInAt() : now;
            List<Order> orders = orderRepository.findOrdersInShiftWindow(
                    stale.getBranchId(), resolveWindowStart(stale, checkInAt), now);
            SalesTotals totals = aggregateSales(orders);
            BigDecimal initialCash = stale.getInitialCash() != null ? stale.getInitialCash() : BigDecimal.ZERO;
            BigDecimal expectedCash = initialCash.add(totals.cashSales());

            ShiftReport report = shiftReportRepository.findByAssignmentId(stale.getId())
                    .orElse(new ShiftReport());
            report.setAssignmentId(stale.getId());
            report.setBranchId(stale.getBranchId());
            report.setBusinessDate(stale.getWorkDate());
            report.setInitialCash(initialCash);
            report.setCashSales(totals.cashSales());
            report.setCardSales(totals.cardSales());
            report.setBankTransferSales(totals.bankTransferSales());
            report.setEwalletSales(totals.ewalletSales());
            report.setTotalSales(totals.totalSales());
            report.setOrdersCount(orders.size());
            report.setCashPayout(BigDecimal.ZERO);
            report.setExpectedCash(expectedCash);
            report.setActualCash(expectedCash);
            report.setDifference(BigDecimal.ZERO);
            report.setDifferenceReason("Hệ thống tự đóng ca quá hạn — quản lý kiểm tra số liệu, duyệt hoặc từ chối");
            report.setStatus("SUBMITTED");
            // Người nộp để trống (số do hệ thống tính, chênh lệch 0) để mọi quản lý
            // đều duyệt được, tránh deadlock chi nhánh 1 quản lý tự giữ két.
            report.setSubmittedById(null);
            report.setSubmittedAt(now);
            String tag = " | Hệ thống tự đóng ca quá hạn, chờ quản lý duyệt";
            report.setNote(report.getNote() != null ? report.getNote() + tag : tag.replace("| ", ""));
            shiftReportRepository.save(report);

            stale.setStatus("CHECKED_OUT");
            stale.setCheckOutAt(now);
            stale.setFinalCash(expectedCash);
            stale.setCashDifference(BigDecimal.ZERO);
        } else {
            stale.setStatus("CHECKED_OUT");
            stale.setCheckOutAt(now);
        }
        String tag = " | Hệ thống tự đóng ca quá hạn";
        stale.setNote(stale.getNote() != null ? stale.getNote() + tag : tag.replace("| ", ""));
        shiftAssignmentRepository.save(stale);
    }

    private record SalesTotals(BigDecimal cashSales, BigDecimal cardSales, BigDecimal bankTransferSales,
                               BigDecimal ewalletSales, BigDecimal totalSales, int ordersCount) {
    }

    // Đơn chưa thanh toán không được tính vào két: chặn chốt ca tới khi thu/hủy hết.
    // Quy tắc đơn giản phase hiện tại (sau này mới tách luồng thu sau/ca sau).
    private static List<Order> unpaidOrders(List<Order> orders) {
        return orders.stream()
                .filter(o -> !"PAID".equalsIgnoreCase(o.getPaymentStatus()))
                .toList();
    }

    private static String describeUnpaid(List<Order> unpaid) {
        String codes = unpaid.stream().limit(5)
                .map(o -> o.getOrderCode() != null ? o.getOrderCode() : String.valueOf(o.getId()))
                .collect(java.util.stream.Collectors.joining(", "));
        return "Còn " + unpaid.size() + " đơn chưa thanh toán (" + codes
                + (unpaid.size() > 5 ? ", ..." : "")
                + ") — thu tiền hoặc hủy đơn rồi chốt ca";
    }

    // NOTE(P3-later): hiện cộng thẳng mọi đơn trong khung giờ (kể cả UNPAID) vì
    // chưa thanh toán thật. Sau này có cổng thanh toán thì chỉ quét
    // paymentStatus = PAID và loại CANCELLED/REJECTED/REFUNDED ở OrderRepository.
    private SalesTotals aggregateSales(List<Order> orders) {
        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal bankTransferSales = BigDecimal.ZERO;
        BigDecimal ewalletSales = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;

        for (Order order : orders) {
            BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            totalSales = totalSales.add(amount);

            String method = order.getPaymentMethod() != null ? order.getPaymentMethod().toUpperCase() : "CASH";
            if (method.contains("CASH") || method.contains("COD")) {
                // Tiền mặt tại quầy + shipper COD nộp lại: vào két.
                cashSales = cashSales.add(amount);
            } else if (method.contains("TRANSFER") || method.contains("BANK")
                    || method.contains("VNPAY") || method.contains("QR")) {
                // Chuyển khoản/VNPAY: về tài khoản công ty, KHÔNG vào két.
                bankTransferSales = bankTransferSales.add(amount);
            } else if (method.contains("WALLET") || method.contains("MOMO")
                    || method.contains("ZALO") || method.contains("EWALLET")) {
                // Ví điện tử: về tài khoản công ty, KHÔNG vào két.
                ewalletSales = ewalletSales.add(amount);
            } else if (method.contains("CARD") || method.contains("VISA") || method.contains("MASTER")) {
                cardSales = cardSales.add(amount);
            } else {
                throw new BaseException(ErrorCode.BAD_REQUEST,
                        "Phương thức thanh toán chưa hỗ trợ đối soát: " + order.getPaymentMethod());
            }
        }
        return new SalesTotals(cashSales, cardSales, bankTransferSales, ewalletSales, totalSales, orders.size());
    }

    private static BigDecimal toleranceFor(BigDecimal totalSales) {
        BigDecimal relative = (totalSales != null ? totalSales : BigDecimal.ZERO).multiply(TOLERANCE_RATE);
        return TOLERANCE_ABSOLUTE.max(relative);
    }

    // Bảng kê mệnh giá: [{denomination, quantity, amount?}] — tổng phải bằng tiền đếm.
    private static void validateCashDenominations(String raw, BigDecimal actualCash) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (!root.isArray()) {
                throw new BaseException(ErrorCode.STORE_400_DENOMINATION_MISMATCH);
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (JsonNode item : root) {
                BigDecimal amount;
                if (item.has("amount") && !item.get("amount").isNull()) {
                    amount = new BigDecimal(item.get("amount").asText("0"));
                } else {
                    BigDecimal denomination = new BigDecimal(item.path("denomination").asText("0"));
                    BigDecimal quantity = new BigDecimal(item.path("quantity").asText("0"));
                    amount = denomination.multiply(quantity);
                }
                sum = sum.add(amount);
            }
            BigDecimal actual = actualCash != null ? actualCash : BigDecimal.ZERO;
            if (sum.compareTo(actual) != 0) {
                throw new BaseException(ErrorCode.STORE_400_DENOMINATION_MISMATCH);
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(ErrorCode.STORE_400_DENOMINATION_MISMATCH);
        }
    }

    // Mốc từ quét đơn: ca nối ca thì lấy checkOut ca trước (không trùng/sót),
    // ca đầu ngày thì lấy 0h ngày làm việc (đơn bán trước khi mở két dồn vào đây).
    // NOTE: múi giờ cố định Asia/Ho_Chi_Minh theo branch.timezone mặc định.
    private Instant resolveWindowStart(ShiftAssignment assignment, Instant checkInAt) {
        List<ShiftAssignment> closed = shiftAssignmentRepository.findByBranchIdAndWorkDateAndStatus(
                assignment.getBranchId(), assignment.getWorkDate(), "CHECKED_OUT");
        Instant prevOut = closed.stream()
                .filter(a -> isCashHandler(a.getAccountId(), a.getBranchId()))
                .map(ShiftAssignment::getCheckOutAt)
                .filter(Objects::nonNull)
                .filter(t -> !t.isAfter(checkInAt))
                .max(Instant::compareTo)
                .orElse(null);
        if (prevOut == null) {
            LocalDate workDate = assignment.getWorkDate() != null ? assignment.getWorkDate() : LocalDate.now();
            return workDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }
        return prevOut;
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftAssignmentResponse getMyActiveShift(UUID accountId) {
        UUID currentBranchId = dataScopeHelper.getCurrentBranchId().orElse(null);
        List<ShiftAssignment> todayAssignments = currentBranchId != null
                ? shiftAssignmentRepository.findByBranchIdAndWorkDate(currentBranchId, LocalDate.now())
                : List.of();

        ShiftAssignment activeAssignment;
        if (currentBranchId != null) {
            activeAssignment = shiftAssignmentRepository.findByBranchIdAndStatus(currentBranchId, "CHECKED_IN")
                    .stream()
                    .filter(a -> accountId.equals(a.getAccountId()))
                    .findFirst()
                    .orElse(null);
        } else {
            activeAssignment = shiftAssignmentRepository
                    .findFirstByAccountIdAndStatus(accountId, "CHECKED_IN")
                    .orElse(null);
        }

        if (activeAssignment == null) {
            if (currentBranchId != null) {
                activeAssignment = todayAssignments.stream()
                        .filter(a -> accountId.equals(a.getAccountId()))
                        .filter(a -> "SCHEDULED".equalsIgnoreCase(a.getStatus()))
                        .findFirst()
                        .orElse(null);
            } else {
                activeAssignment = shiftAssignmentRepository
                        .findFirstByAccountIdAndWorkDateAndStatus(accountId, LocalDate.now(), "SCHEDULED")
                        .orElse(null);
            }
        }

        if (activeAssignment == null) {
            if (currentBranchId != null) {
                activeAssignment = todayAssignments.stream()
                        .filter(a -> accountId.equals(a.getAccountId()))
                        .filter(a -> "CHECKED_OUT".equalsIgnoreCase(a.getStatus()))
                        .max((left, right) -> {
                            Instant leftTime = left.getCheckOutAt() != null ? left.getCheckOutAt() : Instant.EPOCH;
                            Instant rightTime = right.getCheckOutAt() != null ? right.getCheckOutAt() : Instant.EPOCH;
                            return leftTime.compareTo(rightTime);
                        })
                        .orElse(null);
            } else {
                activeAssignment = shiftAssignmentRepository
                        .findFirstByAccountIdAndWorkDateAndStatus(accountId, LocalDate.now(), "CHECKED_OUT")
                        .orElse(null);
            }
        }

        if (activeAssignment == null) {
            return null;
        }

        Shift shift = shiftRepository.findById(activeAssignment.getShiftId()).orElse(null);
        Account account = accountRepository.findById(accountId).orElse(null);
        return shiftAssignmentMapper.toResponse(activeAssignment, shift, account,
                isCashHandler(accountId, activeAssignment.getBranchId()));
    }

    @Override
    public ShiftAssignmentResponse openShift(UUID assignmentId, OpenShiftRequest request, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());
        enforceOperationAccess(assignment, currentUserId);

        if (!"SCHEDULED".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        // Ca quá hạn (quên mở nhiều ngày) không cho mở nữa — phải Hủy ca quá hạn
        // rồi phân lại, tránh két dồn đơn và chặn khóa sổ ngày mới. Cho phép
        // lệch 1 ngày để bao ca qua đêm.
        if (assignment.getWorkDate() != null) {
            long daysLate = Math.abs(
                    java.time.temporal.ChronoUnit.DAYS.between(assignment.getWorkDate(), LocalDate.now()));
            if (daysLate > 1) {
                throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION,
                        "Ca ngày " + assignment.getWorkDate() + " đã quá hạn mở. "
                                + "Vui lòng Hủy ca quá hạn này rồi phân ca mới");
            }
        }

        // Mở két chỉ dành cho thu ngân/quản lý (người được phân ca).
        if (!isCashHandler(assignment.getAccountId(), assignment.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED, "Chỉ thu ngân hoặc quản lý được mở két");
        }

        branchRepository.findByIdForUpdate(assignment.getBranchId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        // Nhân viên này đã có ca nào khác đang hoạt động không. Ca kẹt qua đêm
        // thì hệ thống tự đóng rồi mở tiếp; ca kẹt cùng ngày thì chặn để người xử lý.
        var existingOpt = shiftAssignmentRepository.findFirstByAccountIdAndStatus(assignment.getAccountId(), "CHECKED_IN");
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(assignment.getId())) {
            ShiftAssignment existing = existingOpt.get();
            if (isStaleShift(existing)) {
                autoCloseStaleShift(existing);
            } else {
                throw new BaseException(ErrorCode.STORE_400_ACTIVE_SHIFT_EXISTS,
                        "Nhân viên đang kẹt " + describeShift(existing) + " chưa kết thúc. "
                                + "Đóng ca đó (hoặc dùng nút Đóng hộ) rồi mở ca mới");
            }
        }

        // 1 chi nhánh tại 1 thời điểm chỉ 1 két mở (chỉ tính ca thu ngân,
        // pha chế điểm danh không ảnh hưởng). Ca két kẹt qua đêm tự đóng.
        List<ShiftAssignment> cashOpen = shiftAssignmentRepository.findByBranchIdAndStatus(assignment.getBranchId(), "CHECKED_IN")
                .stream()
                .filter(a -> !a.getId().equals(assignment.getId()))
                .filter(a -> isCashHandler(a.getAccountId(), a.getBranchId()))
                .toList();
        for (ShiftAssignment o : cashOpen) {
            if (isStaleShift(o)) {
                autoCloseStaleShift(o);
            }
        }
        var stillOpen = cashOpen.stream().filter(o -> !isStaleShift(o)).toList();
        if (!stillOpen.isEmpty()) {
            throw new BaseException(ErrorCode.STORE_400_ACTIVE_SHIFT_EXISTS,
                    "Két chi nhánh đang kẹt " + describeShift(stillOpen.get(0)) + " chưa đóng. "
                            + "Đóng ca đó (hoặc dùng nút Đóng hộ) rồi mở ca mới");
        }

        assignment.setStatus("CHECKED_IN");
        assignment.setCheckInAt(Instant.now());
        assignment.setInitialCash(request.initialCash() != null ? request.initialCash() : BigDecimal.ZERO);
        if (request.note() != null && !request.note().isBlank()) {
            assignment.setNote(request.note());
        }

        ShiftAssignment saved = shiftAssignmentRepository.save(assignment);
        Shift shift = shiftRepository.findById(saved.getShiftId()).orElse(null);
        Account account = accountRepository.findById(saved.getAccountId()).orElse(null);

        return shiftAssignmentMapper.toResponse(saved, shift, account,
                isCashHandler(saved.getAccountId(), saved.getBranchId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ClosingSummaryResponse getClosingSummary(UUID assignmentId, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());
        enforceOperationAccess(assignment, currentUserId);

        if (!isCashHandler(assignment.getAccountId(), assignment.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED, "Ca này không vận hành két tiền");
        }

        if (!"CHECKED_IN".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        Shift shift = shiftRepository.findById(assignment.getShiftId()).orElse(null);
        Account cashier = accountRepository.findById(assignment.getAccountId()).orElse(null);

        Instant checkInAt = assignment.getCheckInAt() != null ? assignment.getCheckInAt() : Instant.now();
        Instant now = Instant.now();

        List<Order> orders = orderRepository.findOrdersInShiftWindow(assignment.getBranchId(), resolveWindowStart(assignment, checkInAt), now);

        SalesTotals totals = aggregateSales(orders);
        BigDecimal cashSales = totals.cashSales();
        BigDecimal cardSales = totals.cardSales();
        BigDecimal bankTransferSales = totals.bankTransferSales();
        BigDecimal ewalletSales = totals.ewalletSales();
        BigDecimal totalSales = totals.totalSales();

        BigDecimal initialCash = assignment.getInitialCash() != null ? assignment.getInitialCash() : BigDecimal.ZERO;
        BigDecimal cashPayout = BigDecimal.ZERO;
        BigDecimal expectedCash = initialCash.add(cashSales).subtract(cashPayout);

        return new ClosingSummaryResponse(
                assignment.getId(),
                assignment.getBranchId(),
                shift != null ? shift.getShiftCode() : null,
                shift != null ? shift.getShiftName() : null,
                cashier != null ? cashier.getId() : null,
                cashier != null ? cashier.getFullName() : null,
                checkInAt,
                initialCash,
                cashSales,
                cardSales,
                bankTransferSales,
                ewalletSales,
                totalSales,
                orders.size(),
                cashPayout,
                expectedCash,
                unpaidOrders(orders).size()
        );
    }

    @Override
    public ShiftReportResponse closeShift(UUID assignmentId, CloseShiftRequest request, UUID currentUserId) {
        // Lock bi quan ca đang chốt để chống double-close concurrent (2 tab cùng bấm).
        ShiftAssignment assignment = shiftAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());
        enforceOperationAccess(assignment, currentUserId);

        if (!isCashHandler(assignment.getAccountId(), assignment.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED, "Ca này không vận hành két tiền");
        }

        if (!"CHECKED_IN".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        Instant checkInAt = assignment.getCheckInAt() != null ? assignment.getCheckInAt() : Instant.now();
        Instant checkOutAt = Instant.now();

        List<Order> orders = orderRepository.findOrdersInShiftWindow(assignment.getBranchId(), resolveWindowStart(assignment, checkInAt), checkOutAt);

        // Phải thu hết mới được kết ca: còn đơn chưa thanh toán thì chặn chốt.
        List<Order> unpaid = unpaidOrders(orders);
        if (!unpaid.isEmpty()) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION, describeUnpaid(unpaid));
        }

        SalesTotals totals = aggregateSales(orders);
        BigDecimal cashSales = totals.cashSales();
        BigDecimal cardSales = totals.cardSales();
        BigDecimal bankTransferSales = totals.bankTransferSales();
        BigDecimal ewalletSales = totals.ewalletSales();
        BigDecimal totalSales = totals.totalSales();

        BigDecimal initialCash = assignment.getInitialCash() != null ? assignment.getInitialCash() : BigDecimal.ZERO;
        BigDecimal cashPayout = request.cashPayout() != null ? request.cashPayout() : BigDecimal.ZERO;
        BigDecimal expectedCash = initialCash.add(cashSales).subtract(cashPayout);
        BigDecimal actualCash = request.actualCash() != null ? request.actualCash() : BigDecimal.ZERO;
        BigDecimal difference = actualCash.subtract(expectedCash);

        // Quy tắc nghiệp vụ BR-STORE-05: vượt ngưỡng max(10k, 0,5% doanh thu ca)
        // mới bắt buộc giải trình lý do >= 10 ký tự.
        BigDecimal allowedGap = difference.abs().subtract(toleranceFor(totalSales));
        if (allowedGap.compareTo(BigDecimal.ZERO) > 0) {
            if (request.differenceReason() == null || request.differenceReason().trim().length() < 10) {
                throw new BaseException(ErrorCode.STORE_400_DIFFERENCE_REASON_REQUIRED);
            }
        }

        validateCashDenominations(request.cashDenominations(), actualCash);

        // Tạo ShiftReport
        ShiftReport report = shiftReportRepository.findByAssignmentId(assignment.getId())
                .orElse(new ShiftReport());

        report.setAssignmentId(assignment.getId());
        report.setBranchId(assignment.getBranchId());
        report.setBusinessDate(assignment.getWorkDate());
        report.setInitialCash(initialCash);
        report.setCashSales(cashSales);
        report.setCardSales(cardSales);
        report.setBankTransferSales(bankTransferSales);
        report.setEwalletSales(ewalletSales);
        report.setTotalSales(totalSales);
        report.setOrdersCount(orders.size());
        report.setCashPayout(cashPayout);
        report.setExpectedCash(expectedCash);
        report.setActualCash(actualCash);
        report.setDifference(difference);
        report.setDifferenceReason(request.differenceReason());
        report.setCashDenominations(request.cashDenominations());
        report.setStatus("SUBMITTED");
        report.setSubmittedById(currentUserId);
        report.setSubmittedAt(checkOutAt);
        report.setNote(request.note());

        ShiftReport savedReport = shiftReportRepository.save(report);

        // Cập nhật ShiftAssignment
        assignment.setStatus("CHECKED_OUT");
        assignment.setCheckOutAt(checkOutAt);
        assignment.setFinalCash(actualCash);
        assignment.setCashDifference(difference);
        shiftAssignmentRepository.save(assignment);

        Account submitter = accountRepository.findById(currentUserId).orElse(null);
        return shiftReportMapper.toResponse(savedReport, submitter, null);
    }

    @Override
    public ShiftReportResponse confirmShiftReport(UUID reportId, UUID managerId, String note) {
        ShiftReport report = shiftReportRepository.findById(reportId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());
        if (!isManagerOrAdmin(managerId, report.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED);
        }

        // Chỉ duyệt biên bản đang chờ, duyệt rồi thì khóa cứng.
        if (!"SUBMITTED".equalsIgnoreCase(report.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        // Người nộp không được tự duyệt cho chính mình (tách nộp — duyệt).
        if (managerId != null && managerId.equals(report.getSubmittedById())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED,
                    "Người nộp két không được tự duyệt, cần quản lý khác xác nhận");
        }

        report.setStatus("CONFIRMED");
        report.setApprovedById(managerId);
        report.setApprovedAt(Instant.now());
        if (note != null && !note.isBlank()
                && (report.getNote() == null || !report.getNote().contains(note.trim()))) {
            report.setNote(report.getNote() != null ? report.getNote() + " | Duyệt: " + note : "Duyệt: " + note);
        }

        ShiftReport saved = shiftReportRepository.save(report);
        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        Account approver = accountRepository.findById(managerId).orElse(null);

        return shiftReportMapper.toResponse(saved, submitter, approver);
    }

    @Override
    public ShiftReportResponse rejectShiftReport(UUID reportId, UUID managerId, String reason) {
        ShiftReport report = shiftReportRepository.findById(reportId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());
        if (!isManagerOrAdmin(managerId, report.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED);
        }

        // Chỉ trả về biên bản đang chờ để thu ngân sửa và nộp lại.
        if (!"SUBMITTED".equalsIgnoreCase(report.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new BaseException(ErrorCode.STORE_400_DIFFERENCE_REASON_REQUIRED);
        }

        // Người nộp cũng không được tự từ chối rồi mở lại ca của chính mình.
        if (managerId != null && managerId.equals(report.getSubmittedById())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED,
                    "Người nộp két không được tự từ chối, cần quản lý khác xử lý");
        }

        report.setStatus("REJECTED");
        report.setApprovedById(managerId);
        report.setApprovedAt(Instant.now());
        report.setNote(report.getNote() != null
                ? report.getNote() + " | Từ chối: " + reason.trim()
                : "Từ chối: " + reason.trim());

        ShiftReport saved = shiftReportRepository.save(report);

        // Mở lại ca để thu ngân sửa và chốt lại.
        shiftAssignmentRepository.findById(report.getAssignmentId()).ifPresent(assignment -> {
            if ("CHECKED_OUT".equalsIgnoreCase(assignment.getStatus())) {
                assignment.setStatus("CHECKED_IN");
                assignment.setCheckOutAt(null);
                shiftAssignmentRepository.save(assignment);
            }
        });

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        Account approver = accountRepository.findById(managerId).orElse(null);

        return shiftReportMapper.toResponse(saved, submitter, approver);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportResponse getShiftReportByAssignmentId(UUID assignmentId, UUID currentUserId) {
        ShiftReport report = shiftReportRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_REPORT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(report.getBranchId());
        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));
        enforceOperationAccess(assignment, currentUserId);

        Account submitter = report.getSubmittedById() != null ? accountRepository.findById(report.getSubmittedById()).orElse(null) : null;
        Account approver = report.getApprovedById() != null ? accountRepository.findById(report.getApprovedById()).orElse(null) : null;

        return shiftReportMapper.toResponse(report, submitter, approver);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShiftReportResponse> searchShiftReports(UUID branchId, LocalDate businessDate, Pageable pageable) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        Page<ShiftReport> page;
        if (businessDate != null) {
            page = shiftReportRepository.findByBranchIdAndBusinessDate(effectiveBranchId, businessDate, pageable);
        } else {
            page = shiftReportRepository.findByBranchId(effectiveBranchId, pageable);
        }

        var reports = page.getContent();
        var submitterIds = reports.stream().map(ShiftReport::getSubmittedById).filter(java.util.Objects::nonNull).distinct().toList();
        var approverIds = reports.stream().map(ShiftReport::getApprovedById).filter(java.util.Objects::nonNull).distinct().toList();

        Map<UUID, Account> submitterMap = accountRepository.findAllById(submitterIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, Account> approverMap = accountRepository.findAllById(approverIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<ShiftReportResponse> items = reports.stream()
                .map(r -> shiftReportMapper.toResponse(r, submitterMap.get(r.getSubmittedById()), approverMap.get(r.getApprovedById())))
                .toList();

        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }
}
