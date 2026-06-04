package com.bank.branch.platform.soap.endpoint;

import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.parse.SoapEnvelopeParser;
import com.bank.branch.platform.soap.parse.SoapMessage;
import com.bank.branch.platform.soap.service.CallPuDispatchService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AxlryPrsRvrSysSvcEndpoint：把 SOAP 报文拆包成 callpu 载荷并委托分发服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AxlryPrsRvrSysSvcEndpointTest {

    @Mock
    private CallPuDispatchService dispatchService;

    // Spring Boot 默认 ObjectMapper 即忽略未知字段（报文 Parm JSON 含 timestamp/md5 等额外字段）
    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AxlryPrsRvrSysSvcEndpoint endpoint;

    @BeforeEach
    void setUp() {
        // TargetSysId/BackendSysId 取配置值(本系统号),与请求里的 SourceSysId 无关
        com.bank.branch.platform.soap.config.SidecarUniauthProperties props =
                new com.bank.branch.platform.soap.config.SidecarUniauthProperties();
        props.setSourceSysId("37150001");
        endpoint = new AxlryPrsRvrSysSvcEndpoint(
                dispatchService, objectMapper, new RspEnvelopeBuilder(objectMapper), props);
    }

    private SoapMessage sampleMessage() throws Exception {
        String xml = StreamUtils.copyToString(
                new ClassPathResource("soap/sample-axlry-prs-rvr.xml").getInputStream(),
                StandardCharsets.UTF_8);
        return SoapEnvelopeParser.parse(xml);
    }

    @Test
    void servicePath_isServiceNumber() {
        assertThat(endpoint.getServicePath()).isEqualTo("/S080021264");
    }

    @Test
    void invoke_extractsCallPuRequestFromParm_andDispatches() throws Exception {
        when(dispatchService.dispatch(any())).thenReturn(CallPuResponse.ok(null));

        String resp = endpoint.invoke(sampleMessage());

        // 分发键取自内层 Parm JSON（RuleName=PERF_LIST），而非 SvcBody.RuleName（=post）
        ArgumentCaptor<CallPuRequest> captor = ArgumentCaptor.forClass(CallPuRequest.class);
        verify(dispatchService).dispatch(captor.capture());
        CallPuRequest dispatched = captor.getValue();
        assertThat(dispatched.getRuleName()).isEqualTo("PERF_LIST");
        assertThat(dispatched.getParm()).isNotNull();
        assertThat(dispatched.getParm().getEmployeeNo()).isEqualTo("12094108");

        // 响应按实际报文结构装配；TargetSysId/BackendSysId 取配置值(37150001),非请求里的 07440001
        assertThat(resp).contains("<s:RspAxlryPrsRvrSysSvc>");
        assertThat(resp).contains("<d:TargetSysId>37150001</d:TargetSysId>");
        assertThat(resp).contains("<s:BackendSysId>37150001</s:BackendSysId>");
        assertThat(resp).containsPattern("<s:BackendSeqNo>3715[0-9]{6}[0-9]{16}</s:BackendSeqNo>");
        assertThat(resp).contains("<s:ReturnMsg>操作成功</s:ReturnMsg>");
    }
}
