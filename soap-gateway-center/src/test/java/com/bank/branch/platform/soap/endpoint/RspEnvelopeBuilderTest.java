package com.bank.branch.platform.soap.endpoint;

import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.controller.dto.PerfListData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RspEnvelopeBuilder：按实际响应报文结构（respXml）装配 SOAP 响应信封的单元测试。
 *
 * <p>用固定时钟保证 TranDate/TranTime 可精确断言；UUID 与 16 位随机数字断言其格式。</p>
 */
class RspEnvelopeBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 2026-06-02T19:24:37.679Z（UTC 时钟）→ TranDate=20260602, TranTime=192437679, yyMMdd=260602
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-02T19:24:37.679Z"), ZoneOffset.UTC);
    private final RspEnvelopeBuilder builder = new RspEnvelopeBuilder(objectMapper, fixedClock);

    @Test
    void build_fillsHeaderAndBody_perSpec() {
        CallPuResponse resp = CallPuResponse.ok(new PerfListData(List.of()));

        String xml = builder.build("07440001", resp);

        // 信封/结构
        assertThat(xml).contains("<s:RspAxlryPrsRvrSysSvc>");
        assertThat(xml).contains("<s:RspSvcHeader>");
        // 头部:TargetSysId / BackendSysId = source-sys-id
        assertThat(xml).contains("<d:TargetSysId>07440001</d:TargetSysId>");
        assertThat(xml).contains("<s:BackendSysId>07440001</s:BackendSysId>");
        // 固定值:ReturnCode=12个0,ReturnMsg=操作成功
        assertThat(xml).contains("<s:ReturnCode>000000000000</s:ReturnCode>");
        assertThat(xml).contains("<s:ReturnMsg>操作成功</s:ReturnMsg>");
        // 当前日期/时间（固定时钟）
        assertThat(xml).contains("<s:TranDate>20260602</s:TranDate>");
        assertThat(xml).contains("<s:TranTime>192437679</s:TranTime>");
        // MsgId = UUID
        assertThat(xml).containsPattern("<d:MsgId>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}</d:MsgId>");
        // BackendSeqNo = source-sys-id前4位(0744) + yyMMdd(260602) + 16位随机数字
        assertThat(xml).containsPattern("<s:BackendSeqNo>0744260602[0-9]{16}</s:BackendSeqNo>");
        // 业务体:ReturnCd 取 callpu 返回码,RspMsg 为整个 callpu 响应 JSON
        assertThat(xml).contains("<s:ReturnCd>0</s:ReturnCd>");
        assertThat(xml).contains("RspMsg");
    }

    @Test
    void canBeInstantiatedAsSpringBean() {
        // 回归:RspEnvelopeBuilder 有两个构造器,Spring 需明确用哪个,否则启动报
        // "No default constructor found"(见 soapJpg/error.jpg）。
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class)
                .withUserConfiguration(RspEnvelopeBuilder.class)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(RspEnvelopeBuilder.class));
    }
}
