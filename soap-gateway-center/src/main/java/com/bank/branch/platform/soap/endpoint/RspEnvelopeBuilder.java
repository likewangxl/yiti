package com.bank.branch.platform.soap.endpoint;

import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AxlryPrsRvrSysSvc 响应信封构造器,按实际响应报文（respXml）结构装配。
 *
 * <p>响应结构：
 * <pre>
 * soap:Envelope
 *   soap:Header / s:RspHeader / d:MsgId,d:TargetSysId
 *   soap:Body / s:RspAxlryPrsRvrSysSvc
 *     s:RspSvcHeader / TranDate,TranTime,BackendSeqNo,BackendSysId,ReturnCode,ReturnMsg
 *     s:SvcBody / ReturnCd,RspMsg
 * </pre>
 *
 * <p>字段填充规则（由对接约定）：
 * <ul>
 *   <li>TargetSysId / BackendSysId = 请求方系统号 source-sys-id</li>
 *   <li>MsgId = UUID</li>
 *   <li>TranDate = 当前日期 yyyyMMdd；TranTime = 当前时间 HHmmssSSS</li>
 *   <li>BackendSeqNo = source-sys-id 前 4 位 + yyMMdd + 16 位随机数字</li>
 *   <li>ReturnCode = 固定 12 个 0；ReturnMsg = 固定 "操作成功"</li>
 *   <li>SvcBody.ReturnCd = callpu 业务返回码；SvcBody.RspMsg = 整个 callpu 响应 JSON</li>
 * </ul>
 */
@Slf4j
@Component
public class RspEnvelopeBuilder {

    private static final DateTimeFormatter TRAN_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TRAN_TIME = DateTimeFormatter.ofPattern("HHmmssSSS");
    private static final DateTimeFormatter SEQ_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    /** ReturnCode 固定 12 个 0。 */
    private static final String RETURN_CODE_SUCCESS = "000000000000";
    /** ReturnMsg 固定文案。 */
    private static final String RETURN_MSG_SUCCESS = "操作成功";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 生产用：系统默认时钟。@Autowired 显式指定本构造器,避免多构造器下 Spring 找不到默认构造器。 */
    @Autowired
    public RspEnvelopeBuilder(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemDefaultZone());
    }

    /** 测试用：可注入固定时钟。 */
    RspEnvelopeBuilder(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 按实际响应报文结构装配 SOAP 响应信封。
     *
     * @param sourceSysId 请求方系统号（响应里的 TargetSysId / BackendSysId）
     * @param response    callpu 业务响应
     * @return 响应 SOAP XML
     */
    public String build(String sourceSysId, CallPuResponse response) {
        String sysId = sourceSysId == null ? "" : sourceSysId;
        LocalDateTime now = LocalDateTime.now(clock);

        String msgId = UUID.randomUUID().toString();
        String tranDate = now.format(TRAN_DATE);
        String tranTime = now.format(TRAN_TIME);
        String backendSeqNo = buildBackendSeqNo(sysId, now);

        String returnCd = response == null || response.getReturnCd() == null ? "" : response.getReturnCd();
        String rspMsgJson = toJson(response);

        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"")
                .append(" xmlns:s=\"http://esb.spdbbiz.com/services/S080021264\"")
                .append(" xmlns:d=\"http://esb.spdbbiz.com/metadata\"")
                .append(" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"")
                .append(" xmlns:soapenc=\"http://schemas.xmlsoap.org/soap/encoding/\">");
        sb.append("<soap:Header><s:RspHeader>");
        sb.append("<d:MsgId>").append(msgId).append("</d:MsgId>");
        sb.append("<d:TargetSysId>").append(escape(sysId)).append("</d:TargetSysId>");
        sb.append("</s:RspHeader></soap:Header>");
        sb.append("<soap:Body><s:RspAxlryPrsRvrSysSvc>");
        sb.append("<s:RspSvcHeader>");
        sb.append("<s:TranDate>").append(tranDate).append("</s:TranDate>");
        sb.append("<s:TranTime>").append(tranTime).append("</s:TranTime>");
        sb.append("<s:BackendSeqNo>").append(backendSeqNo).append("</s:BackendSeqNo>");
        sb.append("<s:BackendSysId>").append(escape(sysId)).append("</s:BackendSysId>");
        sb.append("<s:ReturnCode>").append(RETURN_CODE_SUCCESS).append("</s:ReturnCode>");
        sb.append("<s:ReturnMsg>").append(RETURN_MSG_SUCCESS).append("</s:ReturnMsg>");
        sb.append("</s:RspSvcHeader>");
        sb.append("<s:SvcBody>");
        sb.append("<s:ReturnCd>").append(escape(returnCd)).append("</s:ReturnCd>");
        sb.append("<s:RspMsg>").append(escape(rspMsgJson)).append("</s:RspMsg>");
        sb.append("</s:SvcBody>");
        sb.append("</s:RspAxlryPrsRvrSysSvc></soap:Body>");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    /** BackendSeqNo = source-sys-id 前 4 位 + yyMMdd + 16 位随机数字。 */
    private String buildBackendSeqNo(String sysId, LocalDateTime now) {
        String prefix = sysId.length() >= 4 ? sysId.substring(0, 4) : sysId;
        StringBuilder rand = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            rand.append(ThreadLocalRandom.current().nextInt(10));
        }
        return prefix + now.format(SEQ_DATE) + rand;
    }

    /** 序列化整个 callpu 响应为 JSON（作为 SvcBody.RspMsg 文本）。 */
    private String toJson(CallPuResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("[SOAP-OUT] 响应序列化失败", e);
            return "{\"ReturnCd\":\"99\",\"RspMsg\":\"响应序列化失败\"}";
        }
    }

    /** XML 文本转义（与实际报文一致,引号等编码为实体）。 */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
