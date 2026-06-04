package com.bank.branch.platform.soap.parse;

import com.bank.branch.platform.soap.endpoint.bind.ReqAxlryPrsRvrSysSvcType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SoapBodyBinder：把含服务号命名空间的 SOAP body 绑定成强类型对象的单元测试。
 */
class SoapBodyBinderTest {

    private String sampleXml() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("soap/sample-axlry-prs-rvr.xml").getInputStream(),
                StandardCharsets.UTF_8);
    }

    @Test
    void bind_should_map_svcBody_and_header_ignoring_service_namespace() throws Exception {
        SoapMessage msg = SoapEnvelopeParser.parse(sampleXml());

        ReqAxlryPrsRvrSysSvcType req = SoapBodyBinder.bind(msg, ReqAxlryPrsRvrSysSvcType.class);

        assertThat(req).isNotNull();
        // 服务头
        assertThat(req.getReqSvcHeader()).isNotNull();
        assertThat(req.getReqSvcHeader().getTranSeqNo()).isEqualTo("A123479");
        assertThat(req.getReqSvcHeader().getConsumerId()).isEqualTo("020060");
        assertThat(req.getReqSvcHeader().getSourceSysId()).isEqualTo("07440001");
        // 业务体
        ReqAxlryPrsRvrSysSvcType.SvcBody body = req.getSvcBody();
        assertThat(body).isNotNull();
        assertThat(body.getSrvicName1()).isEqualTo("XazcCallPuSvr");
        assertThat(body.getEmployeeNo()).isEqualTo("12094108");
        assertThat(body.getParm()).contains("\"RuleName\":\"PERF_LIST\"");
    }
}
