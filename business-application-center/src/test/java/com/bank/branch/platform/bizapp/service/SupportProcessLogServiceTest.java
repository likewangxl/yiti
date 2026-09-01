package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.dto.req.CreateSupportProcessLogReq;
import com.bank.branch.platform.bizapp.entity.SupportProcessLog;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.mapper.SupportProcessLogMapper;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中台支持过程记录约束、幂等和结果留痕测试。 */
@ExtendWith(MockitoExtension.class)
class SupportProcessLogServiceTest {

    @Mock
    private SupportProcessLogMapper logMapper;
    @Mock
    private SupportRequestMapper supportMapper;
    @Mock
    private FileApi fileApi;

    @AfterEach
    void clearScope() {
        com.bank.branch.platform.common.security.context.DataScopeContext.clear();
    }

    @Test
    void addLog_process_requiresContentCheckinLocationAndPhoto() {
        CreateSupportProcessLogReq req = new CreateSupportProcessLogReq();
        req.setClientUuid("client-1");

        SupportProcessLogService service = newService();

        assertThatThrownBy(() -> service.addLog("SR001", req, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42201");
        verify(supportMapper, never()).selectForUpdate(any());
    }

    @Test
    void addLog_process_validBindsPhotoAndWritesCheckin() {
        SupportRequest request = request(SupportStatus.IN_PROGRESS);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(request);
        when(logMapper.selectByRequestAndClientUuid("SR001", "client-1")).thenReturn(null);
        when(fileApi.listBizFiles("SUPPORT_LOG", "log-1")).thenReturn(Collections.emptyList());

        CreateSupportProcessLogReq req = new CreateSupportProcessLogReq();
        req.setClientUuid("client-1");
        req.setContent("已完成现场沟通");
        req.setCheckinTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        req.setLocationAddress("上海市浦东新区");
        req.setFileIds(List.of("file-1"));

        SupportProcessLogService service = newService();
        // 让 ID 可预测仅用于断言关联参数，不改变服务端 ID 生成策略。
        when(logMapper.insert(any(SupportProcessLog.class))).thenAnswer(invocation -> {
            SupportProcessLog log = invocation.getArgument(0);
            log.setId("log-1");
            return 1;
        });

        service.addLog("SR001", req, "E001");

        ArgumentCaptor<SupportProcessLog> captor = ArgumentCaptor.forClass(SupportProcessLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getLogType()).isEqualTo("PROCESS");
        assertThat(captor.getValue().getCheckinTime()).isEqualTo(req.getCheckinTime());
        verify(fileApi).bindFile("SUPPORT_LOG", "log-1", "file-1", "PHOTO");
    }

    @Test
    void addLog_scenarioAInApproval_allowsCurrentProductOwnerToWriteProcess() {
        SupportRequest request = request(SupportStatus.IN_APPROVAL);
        request.setProductId("P001");
        request.setSupportDeptId(null);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(request);
        when(logMapper.selectByRequestAndClientUuid("SR001", "client-a")).thenReturn(null);
        when(fileApi.listBizFiles("SUPPORT_LOG", "log-a")).thenReturn(Collections.emptyList());
        when(logMapper.insert(any(SupportProcessLog.class))).thenAnswer(invocation -> {
            SupportProcessLog log = invocation.getArgument(0);
            log.setId("log-a");
            return 1;
        });

        CreateSupportProcessLogReq req = validProcessReq();
        req.setClientUuid("client-a");

        newService().addLog("SR001", req, "E001");

        ArgumentCaptor<SupportProcessLog> captor = ArgumentCaptor.forClass(SupportProcessLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getLogType()).isEqualTo("PROCESS");
        assertThat(captor.getValue().getSupportRequestId()).isEqualTo("SR001");
    }

    @Test
    void addLog_inApprovalNonScenarioA_isNotWritable() {
        SupportRequest request = request(SupportStatus.IN_APPROVAL);
        request.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(request);

        assertThatThrownBy(() -> newService().addLog("SR001", validProcessReq(), "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40304");
        verify(logMapper, never()).insert(any(SupportProcessLog.class));
    }

    @Test
    void appendResultLog_internalResultMayOmitLocationAndPhoto() {
        SupportRequest request = request(SupportStatus.IN_PROGRESS);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(request);
        when(logMapper.selectByRequestAndClientUuid(eq("SR001"), eq("RESULT:SR001:E001")))
                .thenReturn(null);
        when(logMapper.insert(any(SupportProcessLog.class))).thenAnswer(invocation -> {
            SupportProcessLog log = invocation.getArgument(0);
            log.setId("result-1");
            return 1;
        });
        when(fileApi.listBizFiles("SUPPORT_LOG", "result-1")).thenReturn(Collections.emptyList());

        SupportProcessLogService service = newService();
        service.appendResultLog("SR001", "办理结果已确认", "E001", Collections.emptyList());

        ArgumentCaptor<SupportProcessLog> captor = ArgumentCaptor.forClass(SupportProcessLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getLogType()).isEqualTo("RESULT");
        verify(fileApi, never()).bindFile(any(), any(), any(), any());
    }

    @Test
    void addLog_onlyCurrentAssigneeInProgressCanWrite() {
        SupportRequest request = request(SupportStatus.IN_PROGRESS);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(request);
        CreateSupportProcessLogReq req = validProcessReq();

        SupportProcessLogService service = newService();

        assertThatThrownBy(() -> service.addLog("SR001", req, "E999"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40304");
        verify(logMapper, never()).insert(any(SupportProcessLog.class));
    }

    private SupportProcessLogService newService() {
        return new SupportProcessLogService(logMapper, supportMapper, fileApi, null);
    }

    private SupportRequest request(SupportStatus status) {
        SupportRequest request = new SupportRequest();
        request.setId("SR001");
        request.setStatus(status.getCode());
        request.setAssignedEmpId("E001");
        request.setSupportDeptId("DEPT001");
        request.setOwnerOrgId("ORG001");
        request.setCreatedBy("CREATOR");
        return request;
    }

    private CreateSupportProcessLogReq validProcessReq() {
        CreateSupportProcessLogReq req = new CreateSupportProcessLogReq();
        req.setClientUuid("client-valid");
        req.setContent("过程记录");
        req.setCheckinTime(LocalDateTime.now());
        req.setLocationAddress("现场");
        req.setFileIds(List.of("file-1"));
        return req;
    }
}
