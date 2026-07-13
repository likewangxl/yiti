package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import com.bank.branch.platform.workflow.support.WfMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WfTaskTransferMapperIT extends WfMapperTestBase {

    @Autowired private WfTaskTransferMapper mapper;

    private WfTaskTransfer pending(String taskId, String to) {
        WfTaskTransfer t = new WfTaskTransfer();
        t.setId(UUID.randomUUID().toString().replace("-", ""));
        t.setProcessInstanceId("PID_1");
        t.setTaskId(taskId);
        t.setFromEmpId("E_FROM");
        t.setInitiatorEmpId("E_SEC");
        t.setToEmpId(to);
        t.setStatus("PENDING_ACCEPT");
        t.setTransferReason("忙");
        t.setInitiatedTime(LocalDateTime.now());
        return t;
    }

    @Test
    void activeAndOptimisticTransition() {
        WfTaskTransfer t = pending("TASK_1", "E_TO");
        mapper.insert(t);

        assertThat(mapper.selectActiveByTaskId("TASK_1")).isNotNull();
        assertThat(mapper.selectInbox("E_TO")).hasSize(1);

        int first = mapper.updateStatusIfPending(t.getId(), "ACCEPTED", null, LocalDateTime.now());
        int second = mapper.updateStatusIfPending(t.getId(), "REJECTED", "晚了", LocalDateTime.now());
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0); // 已非 PENDING，乐观流转拦截并发
        assertThat(mapper.selectActiveByTaskId("TASK_1")).isNull();
    }
}
