package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.service.ProcessMonitorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProcessMonitorController 单元测试：验证 Controller 直接委托 ProcessMonitorService
 * 并将分页结果包装为 ResponseWrapper（不启动 Spring 上下文，纯 Mockito 瘦测试）。
 */
@ExtendWith(MockitoExtension.class)
class ProcessMonitorControllerTest {

    @Mock
    private ProcessMonitorService service;

    @InjectMocks
    private ProcessMonitorController controller;

    @Test
    void listDelegatesToService() {
        when(service.query("RUNNING", null, null, null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 0, java.util.List.of()));

        ResponseWrapper<?> resp = controller.list("RUNNING", null, null, null, 1, 20);

        assertThat(resp).isNotNull();
        verify(service).query("RUNNING", null, null, null, 1, 20);
    }
}
