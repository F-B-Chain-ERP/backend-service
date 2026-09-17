package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftAssignmentMapper;
import com.erp.backend_service.mapper.ShiftReportMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.ShiftRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.ShiftOperationServiceImpl;
import com.erp.core.domain.Account;
import com.erp.core.domain.Order;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.domain.ShiftReport;
import com.erp.core.dto.request.store.CloseShiftRequest;
import com.erp.core.dto.request.store.OpenShiftRequest;
import com.erp.core.dto.response.store.ClosingSummaryResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import com.erp.core.dto.response.store.ShiftReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftOperationServiceImplTest {

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftReportRepository shiftReportRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DataScopeHelper dataScopeHelper;

    private ShiftAssignmentMapper shiftAssignmentMapper;
    private ShiftReportMapper shiftReportMapper;
    private ShiftOperationServiceImpl shiftOperationService;

    private UUID branchId;
    private UUID accountId;
    private UUID shiftId;
    private UUID assignmentId;
    private Shift shift;
    private Account account;
    private ShiftAssignment assignment;

    @BeforeEach
    void setUp() {
        shiftAssignmentMapper = new ShiftAssignmentMapper();
        shiftReportMapper = new ShiftReportMapper();

        shiftOperationService = new ShiftOperationServiceImpl(
                shiftAssignmentRepository,
                shiftRepository,
                shiftReportRepository,
                orderRepository,
                accountRepository,
                shiftAssignmentMapper,
                shiftReportMapper,
                dataScopeHelper
        );

        branchId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        shiftId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        shift = new Shift();
        shift.setBranchId(branchId);
        shift.setShiftCode("CA_SANG");
        shift.setShiftName("Ca Sáng");
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(16, 0));

        account = new Account();
        account.setUsername("cashier01");
        account.setFullName("Nguyễn Văn Thu Ngân");
        account.setEmail("cashier@erp.vn");

        assignment = new ShiftAssignment();
        assignment.setBranchId(branchId);
        assignment.setAccountId(accountId);
        assignment.setShiftId(shiftId);
        assignment.setWorkDate(LocalDate.now());
        assignment.setStatus("SCHEDULED");
    }

    @Test
    @DisplayName("Mở ca thành công: Trạng thái chuyển sang CHECKED_IN và lưu tiền đầu ca")
    void testOpenShift_Success() {
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.findFirstByAccountIdAndStatus(accountId, "CHECKED_IN")).thenReturn(Optional.empty());
        when(shiftAssignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("1500000"), "Nhận bàn giao");
        ShiftAssignmentResponse response = shiftOperationService.openShift(assignmentId, request, accountId);

        assertNotNull(response);
        assertEquals("CHECKED_IN", response.status());
        assertEquals(new BigDecimal("1500000"), response.initialCash());
        verify(shiftAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("Mở ca thất bại: Nhân viên đang có ca khác chưa đóng")
    void testOpenShift_Fail_ActiveShiftExists() {
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        ShiftAssignment activeShift = new ShiftAssignment();
        activeShift.setStatus("CHECKED_IN");
        try {
            var field = com.erp.core.domain.BaseAuditingEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(activeShift, UUID.randomUUID());
        } catch (Exception ignored) {}

        when(shiftAssignmentRepository.findFirstByAccountIdAndStatus(accountId, "CHECKED_IN")).thenReturn(Optional.of(activeShift));

        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("1500000"), "Nhận bàn giao");
        BaseException ex = assertThrows(BaseException.class, () -> shiftOperationService.openShift(assignmentId, request, accountId));
        assertEquals(ErrorCode.STORE_400_ACTIVE_SHIFT_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xem tóm tắt doanh số ca: Tính toán đúng tiền kỳ vọng ExpectedCash")
    void testGetClosingSummary_Success() {
        assignment.setStatus("CHECKED_IN");
        assignment.setCheckInAt(Instant.now().minusSeconds(3600));
        assignment.setInitialCash(new BigDecimal("1000000"));

        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Order orderCash = new Order();
        orderCash.setPaymentMethod("CASH");
        orderCash.setTotalAmount(new BigDecimal("500000"));

        Order orderTransfer = new Order();
        orderTransfer.setPaymentMethod("BANK_TRANSFER");
        orderTransfer.setTotalAmount(new BigDecimal("300000"));

        when(orderRepository.findOrdersInShiftWindow(eq(branchId), any(), any()))
                .thenReturn(List.of(orderCash, orderTransfer));

        ClosingSummaryResponse summary = shiftOperationService.getClosingSummary(assignmentId, accountId);

        assertNotNull(summary);
        assertEquals(new BigDecimal("800000"), summary.totalSales());
        assertEquals(new BigDecimal("500000"), summary.cashSales());
        assertEquals(new BigDecimal("300000"), summary.bankTransferSales());
        // Expected Cash = 1.000.000 (đầu) + 500.000 (tiền mặt) = 1.500.000
        assertEquals(new BigDecimal("1500000"), summary.expectedCash());
    }

    @Test
    @DisplayName("Đóng ca thành công: Khớp tiền tuyệt đối difference = 0")
    void testCloseShift_Success_Balanced() {
        assignment.setStatus("CHECKED_IN");
        assignment.setCheckInAt(Instant.now().minusSeconds(3600));
        assignment.setInitialCash(new BigDecimal("1000000"));

        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(orderRepository.findOrdersInShiftWindow(eq(branchId), any(), any())).thenReturn(List.of());
        when(shiftReportRepository.findByAssignmentId(assignment.getId())).thenReturn(Optional.empty());
        when(shiftReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftAssignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Expected = 1.000.000, Thực tế đếm = 1.000.000 -> difference = 0
        CloseShiftRequest request = new CloseShiftRequest(
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                null,
                "[]",
                "Ca chạy tốt"
        );

        ShiftReportResponse report = shiftOperationService.closeShift(assignmentId, request, accountId);
        assertNotNull(report);
        assertEquals(BigDecimal.ZERO, report.difference());
        assertEquals("SUBMITTED", report.status());
        assertEquals("CHECKED_OUT", assignment.getStatus());
    }

    @Test
    @DisplayName("Đóng ca thất bại theo BR-STORE-05: Lệch tiền nhưng không giải trình lý do")
    void testCloseShift_Fail_DifferenceWithoutReason() {
        assignment.setStatus("CHECKED_IN");
        assignment.setCheckInAt(Instant.now().minusSeconds(3600));
        assignment.setInitialCash(new BigDecimal("1000000"));

        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(orderRepository.findOrdersInShiftWindow(eq(branchId), any(), any())).thenReturn(List.of());

        // Expected = 1.000.000, Thực tế = 950.000 -> Lệch -50.000 nhưng lý do để trống
        CloseShiftRequest request = new CloseShiftRequest(
                new BigDecimal("950000"),
                BigDecimal.ZERO,
                null,
                "[]",
                "Ghi chú chung"
        );

        BaseException ex = assertThrows(BaseException.class, () -> shiftOperationService.closeShift(assignmentId, request, accountId));
        assertEquals(ErrorCode.STORE_400_DIFFERENCE_REASON_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xác nhận duyệt bàn giao ca: Chuyển trạng thái sang CONFIRMED")
    void testConfirmShiftReport_Success() {
        UUID reportId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        ShiftReport report = new ShiftReport();
        report.setBranchId(branchId);
        report.setStatus("SUBMITTED");
        report.setSubmittedById(accountId);

        when(shiftReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(shiftReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Account manager = new Account();
        manager.setFullName("Trần Quản Lý");
        when(accountRepository.findById(managerId)).thenReturn(Optional.of(manager));

        ShiftReportResponse response = shiftOperationService.confirmShiftReport(reportId, managerId, "Đã kiểm khớp");
        assertNotNull(response);
        assertEquals("CONFIRMED", response.status());
        assertEquals(managerId, response.approvedById());
    }
}
