package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.TransferDecisionReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferInitiateReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferItemDTO;
import com.bank.branch.platform.workflow.service.TaskTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskTransferController 单元测试：验证 Controller 对六个转交端点直接委托
 * TaskTransferService 并将结果包装为 ResponseWrapper（不启动 Spring 上下文，纯 Mockito 瘦测试，
 * 与 {@link ProcessMonitorControllerTest} 同法）。
 */
@ExtendWith(MockitoExtension.class)
class TaskTransferControllerTest {

    @Mock
    private TaskTransferService taskTransferService;

    @InjectMocks
    private TaskTransferController controller;

    private TransferInitiateReqDTO initiateReq(String toEmpId, String reason) {
        TransferInitiateReqDTO req = new TransferInitiateReqDTO();
        req.setToEmpId(toEmpId);
        req.setReason(reason);
        return req;
    }

    /** 发起转交：委托 TaskTransferService#initiate，返回 transferId。 */
    @Test
    void initiate_delegatesToServiceAndReturnsTransferId() {
        TransferInitiateReqDTO req = initiateReq("E_TO", "出差，请代为处理");
        when(taskTransferService.initiate("TASK_1", req)).thenReturn("TRANSFER_1");

        ResponseWrapper<String> resp = controller.initiate("TASK_1", req);

        assertThat(resp.getData()).isEqualTo("TRANSFER_1");
        assertThat(resp.getCode()).isEqualTo("0");
        verify(taskTransferService).initiate("TASK_1", req);
    }

    /** 收件箱：委托 TaskTransferService#listInbox。 */
    @Test
    void inbox_delegatesToServiceListInbox() {
        TransferItemDTO item = new TransferItemDTO();
        item.setId("TRANSFER_1");
        when(taskTransferService.listInbox()).thenReturn(List.of(item));

        ResponseWrapper<List<TransferItemDTO>> resp = controller.inbox();

        assertThat(resp.getData()).containsExactly(item);
        verify(taskTransferService).listInbox();
    }

    /** 认领：委托 TaskTransferService#accept。 */
    @Test
    void accept_delegatesToService() {
        ResponseWrapper<Void> resp = controller.accept("TRANSFER_1");

        assertThat(resp.getCode()).isEqualTo("0");
        verify(taskTransferService).accept("TRANSFER_1");
    }

    /** 拒绝：委托 TaskTransferService#decline，理由从请求体透传。 */
    @Test
    void decline_delegatesToServiceWithReason() {
        TransferDecisionReqDTO req = new TransferDecisionReqDTO();
        req.setReason("时间冲突，请转他人处理");

        ResponseWrapper<Void> resp = controller.decline("TRANSFER_1", req);

        assertThat(resp.getCode()).isEqualTo("0");
        verify(taskTransferService).decline("TRANSFER_1", "时间冲突，请转他人处理");
    }

    /** 发件箱：委托 TaskTransferService#listOutbox。 */
    @Test
    void outbox_delegatesToServiceListOutbox() {
        TransferItemDTO item = new TransferItemDTO();
        item.setId("TRANSFER_2");
        when(taskTransferService.listOutbox()).thenReturn(List.of(item));

        ResponseWrapper<List<TransferItemDTO>> resp = controller.outbox();

        assertThat(resp.getData()).containsExactly(item);
        verify(taskTransferService).listOutbox();
    }

    /** 撤回：委托 TaskTransferService#cancel。 */
    @Test
    void cancel_delegatesToService() {
        ResponseWrapper<Void> resp = controller.cancel("TRANSFER_1");

        assertThat(resp.getCode()).isEqualTo("0");
        verify(taskTransferService).cancel("TRANSFER_1");
    }
}
