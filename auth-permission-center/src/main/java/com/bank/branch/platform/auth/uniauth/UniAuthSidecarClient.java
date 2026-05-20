package com.bank.branch.platform.auth.uniauth;

import com.bank.branch.platform.auth.uniauth.dto.UniAuthReqDTO;
import com.bank.branch.platform.auth.uniauth.dto.UniAuthRespDTO;
import com.bank.branch.platform.common.web.sidecar.SidecarHttpClient;
import com.bank.branch.platform.common.web.sidecar.soap.SoapEnvelopeBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 统一认证（S120030044）边车薄封装。
 * <p>对应 xanpd_backend 的 NewAuthrQueryClient_ESF（SDK + SOAP 版）；
 * yiti 改用 HTTP+SOAP 走边车 11003，请求/响应报文 schema 与 xanpd 完全一致（仅传输层不同）。
 */
@Slf4j
@Component
@EnableConfigurationProperties(UniAuthProperties.class)
@RequiredArgsConstructor
public class UniAuthSidecarClient {

    private static final String SVC_NS = "http://esb.spdbbiz.com/services/S120030044";
    private static final DateTimeFormatter F_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter F_TIME = DateTimeFormatter.ofPattern("HHmmssSSS");

    private final SidecarHttpClient sidecar;
    private final UniAuthProperties props;

    private JAXBContext reqCtx;
    private JAXBContext rspCtx;

    /** JAXBContext 重量级，启动期 init 一次复用 */
    @PostConstruct
    public void init() throws Exception {
        this.reqCtx = JAXBContext.newInstance(UniAuthReqDTO.class);
        this.rspCtx = JAXBContext.newInstance(UniAuthRespDTO.class);
    }

    /**
     * 查询用户授权信息。
     * @param userDomainName AD 域账号（即工号，例 {@code "12050965"}）
     */
    public UniAuthRespDTO queryUserInfo(String userDomainName) {
        // 1) 业务 DTO
        UniAuthReqDTO req = new UniAuthReqDTO();
        req.setReqSvcHeader(buildSvcHeader());
        req.setSvcBody(buildSvcBody(userDomainName));

        // 2) JAXB marshal SOAP Body 内部根元素（不输出 <?xml ?>，外层 Envelope 由 SoapEnvelopeBuilder 输出）
        String bodyXml;
        try {
            Marshaller m = reqCtx.createMarshaller();
            m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
            StringWriter sw = new StringWriter();
            m.marshal(req, sw);
            bodyXml = sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("UniAuth 请求 marshal 失败: " + e.getMessage(), e);
        }

        // 3) 包成完整 SOAP Envelope
        SoapEnvelopeBuilder.ReqHeader rh = new SoapEnvelopeBuilder.ReqHeader();
        rh.msgId         = UUID.randomUUID().toString();
        rh.sourceSysId   = props.getSourceSysId();
        rh.consumerId    = props.getConsumerId();
        rh.serviceAdr    = props.getServiceAdr();
        rh.serviceAction = props.getServiceAction();
        String envelope = SoapEnvelopeBuilder.build(SVC_NS, rh, bodyXml);

        // 4) 通过 SidecarHttpClient.postSoap 发出
        //    自动注入 global_flow_no/cons_flow_no（URL） + serviceName/functionId（Header）
        log.info("[UniAuth] query userDomainName={} appId={}", userDomainName, props.getAppId());
        String respXml = sidecar.postSoap(props.getPath(), envelope);

        // 5) 从响应 Envelope 抽 <s:RspNewAuthrQuery>，JAXB unmarshal
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(respXml)));
            NodeList nl = doc.getElementsByTagNameNS(SVC_NS, "RspNewAuthrQuery");
            if (nl.getLength() == 0) {
                throw new RuntimeException("UniAuth 响应里找不到 <RspNewAuthrQuery>，原文: " + respXml);
            }
            Unmarshaller um = rspCtx.createUnmarshaller();
            UniAuthRespDTO resp = (UniAuthRespDTO) um.unmarshal(nl.item(0));
            if (!resp.isSuccess()) {
                log.warn("[UniAuth] returnCode={} returnMsg={}",
                        resp.getRspSvcHeader() == null ? null : resp.getRspSvcHeader().getReturnCode(),
                        resp.getRspSvcHeader() == null ? null : resp.getRspSvcHeader().getReturnMsg());
            }
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("UniAuth 响应解析失败: " + e.getMessage() + " body=" + respXml, e);
        }
    }

    private UniAuthReqDTO.ReqSvcHeader buildSvcHeader() {
        UniAuthReqDTO.ReqSvcHeader h = new UniAuthReqDTO.ReqSvcHeader();
        LocalDateTime now = LocalDateTime.now();
        h.setTranDate(F_DATE.format(now));
        h.setTranTime(F_TIME.format(now));
        h.setTranTellerNo(props.getTranTellerNo());   // 样本是空字符串，可在配置留空
        h.setTranSeqNo("");                            // 样本是空字符串
        h.setConsumerId(props.getSvcConsumerId());
        h.setGlobalSeqNo("");                          // 样本是空字符串
        h.setBranchId(props.getBranchId());
        return h;
    }

    private UniAuthReqDTO.SvcBody buildSvcBody(String userDomainName) {
        UniAuthReqDTO.UserInfoQryInArgs args = new UniAuthReqDTO.UserInfoQryInArgs();
        args.setUserDomainName(userDomainName);
        args.setAppId(props.getAppId());

        UniAuthReqDTO.UserInfoQryInOpts opts = new UniAuthReqDTO.UserInfoQryInOpts();
        UniAuthReqDTO.SvcBody body = new UniAuthReqDTO.SvcBody();
        body.setUserInfoQryInArgs(args);
        body.setUserInfoQryInOpts(opts);
        return body;
    }
}
