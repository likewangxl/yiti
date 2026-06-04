package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustDetailDTO;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 单据详情接口 {@code getAllocAdjustDetail} 单元测试（TDD Red→Green）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerfApprovalQueryFacadeDetailTest {

    @Mock private AllocAdjustTodoService allocAdjustTodoService;
    @Mock private AllocAdjustDoneService allocAdjustDoneService;
    @Mock private AddressBookApi addressBookApi;
    @Mock private CustomerQueryApi customerQueryApi;
    @Mock private PerfAllocAdjustApplyMapper allocAdjustApplyMapper;
    @Mock private AllocAdjustService allocAdjustService;

    @InjectMocks private PerfApprovalQueryFacade facade;

    /** 构造测试用 apply（id=PA_1, createdBy=U001, status=IN_APPROVAL）。 */
    private PerfAllocAdjustApply buildApply() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("PA_1");
        apply.setApplyNo("AA20260604001");
        apply.setCustId("C001");
        apply.setCustName("测试客户");
        apply.setCustType("CORP");
        apply.setAllocDim("ACCOUNT");
        apply.setBizKind("CORP_LOAN");
        apply.setAccountNo("ACC001");
        apply.setStatus("IN_APPROVAL");
        apply.setRemark("测试调整理由");
        apply.setCreatedBy("U001");
        apply.setCreatedTime(LocalDateTime.of(2026, 6, 4, 9, 0, 0));
        return apply;
    }

    /** 构造两条明细：一条 ORIGIN（张三/70%），一条 NEW（李四/30%）。 */
    private List<PerfAllocAdjustItem> buildItems() {
        PerfAllocAdjustItem origin = new PerfAllocAdjustItem();
        origin.setId("ITEM_1");
        origin.setApplyId("PA_1");
        origin.setItemKind("ORIGIN");
        origin.setEmpId("E1");
        origin.setUsername("zhangsan");
        origin.setEmpChnName("张三");
        origin.setRatio(new BigDecimal("70.00"));

        PerfAllocAdjustItem newItem = new PerfAllocAdjustItem();
        newItem.setId("ITEM_2");
        newItem.setApplyId("PA_1");
        newItem.setItemKind("NEW");
        newItem.setEmpId("E2");
        newItem.setUsername("lisi");
        newItem.setEmpChnName("李四");
        newItem.setRatio(new BigDecimal("30.00"));

        return List.of(origin, newItem);
    }

    @BeforeEach
    void setUp() {
        // allocAdjustService.getById 返回 apply + 2 items
        AllocAdjustService.ApplyWithItems bundle =
                new AllocAdjustService.ApplyWithItems(buildApply(), buildItems());
        when(allocAdjustService.getById("PA_1")).thenReturn(bundle);

        // 待办列表包含 PA_1，empId=U001 → canApprove=true
        AdjustTodoRespDTO todoItem = new AdjustTodoRespDTO();
        todoItem.setId("PA_1");
        todoItem.setStatus("IN_APPROVAL");
        when(allocAdjustTodoService.listMyTodosByEmp(
                eq("U001"), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 1L, List.of(todoItem)));

        // 申请人 U001 → 王五
        EmployeeDTO emp = EmployeeDTO.builder().empId("U001").empName("王五").build();
        when(addressBookApi.getEmployee("U001")).thenReturn(Optional.of(emp));
    }

    @Test
    void getAllocAdjustDetail_returnsCorrectAllocItems() {
        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");
        assertThat(d.getAllocaters()).hasSize(2);
        assertThat(d.getAllocaters()).extracting("isOriginal")
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void getAllocAdjustDetail_canApprove_whenMyTodoAndInApproval() {
        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");
        assertThat(d.isCanApprove()).isTrue();
    }

    @Test
    void getAllocAdjustDetail_canDelete_whenCreatorAndInApproval() {
        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");
        assertThat(d.isCanDelete()).isTrue();
    }

    @Test
    void getAllocAdjustDetail_custType_correctlyMapped() {
        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");
        assertThat(d.getCustType()).isEqualTo("CORP");
    }

    @Test
    void getAllocAdjustDetail_applyFullname_resolvedFromAddressBook() {
        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");
        assertThat(d.getApplyFullname()).isEqualTo("王五");
    }

    @Test
    void getAllocAdjustDetail_canApprove_false_whenNotMyTodo() {
        // 非 U001 的操作员查看，不在待办列表中
        when(allocAdjustTodoService.listMyTodosByEmp(
                eq("U999"), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 0L, List.of()));

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U999");
        assertThat(d.isCanApprove()).isFalse();
    }

    @Test
    void getAllocAdjustDetail_canDelete_false_whenNotCreator() {
        // 非申请人查看
        when(allocAdjustTodoService.listMyTodosByEmp(
                eq("U999"), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 0L, List.of()));

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U999");
        assertThat(d.isCanDelete()).isFalse();
    }

    @Test
    void getAllocAdjustDetail_ratioMappedToString() {
        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");
        assertThat(d.getAllocaters())
                .extracting("ratio")
                .containsExactlyInAnyOrder("70.00", "30.00");
    }
}
