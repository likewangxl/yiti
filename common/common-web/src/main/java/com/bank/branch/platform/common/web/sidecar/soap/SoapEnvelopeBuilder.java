package com.bank.branch.platform.common.web.sidecar.soap;

/**
 * SOAP 1.1 信封字符串构造器。按 ESB 规范手包 Envelope + Header（含 {@code d:} 命名空间的 ReqHeader），
 * Body 内容由调用方传入（通常是 JAXB marshal 出的业务报文 XML 片段）。
 * <p>对照 wangyq/统一认证接口/ 下的真实样本：xmlns:soap / soapenc / xsd / d / s 五个 namespace 一字不差。
 */
public class SoapEnvelopeBuilder {

    public static final String NS_SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String NS_SOAP_ENC = "http://schemas.xmlsoap.org/soap/encoding/";
    public static final String NS_XSD      = "http://www.w3.org/2001/XMLSchema";
    public static final String NS_METADATA = "http://esb.spdbbiz.com/metadata";

    /**
     * 包成完整 SOAP Envelope。
     * @param serviceNamespace 业务 namespace（如 http://esb.spdbbiz.com/services/S120030044）
     * @param header           SOAP Header 内 {@code s:ReqHeader} 五字段
     * @param bodyInnerXml     SOAP Body 内的根元素 XML（如 {@code <s:ReqNewAuthrQuery>...</s:ReqNewAuthrQuery>}）
     */
    public static String build(String serviceNamespace, ReqHeader header, String bodyInnerXml) {
        return new StringBuilder(2048)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<soap:Envelope xmlns:soap=\"").append(NS_SOAP_ENV).append("\"")
                .append(" xmlns:soapenc=\"").append(NS_SOAP_ENC).append("\"")
                .append(" xmlns:xsd=\"").append(NS_XSD).append("\"")
                .append(" xmlns:d=\"").append(NS_METADATA).append("\"")
                .append(" xmlns:s=\"").append(serviceNamespace).append("\">\n")
                .append("  <soap:Header>\n")
                .append("    <s:ReqHeader>\n")
                .append("      <d:MsgId>").append(esc(header.msgId)).append("</d:MsgId>\n")
                .append("      <d:SourceSysId>").append(esc(header.sourceSysId)).append("</d:SourceSysId>\n")
                .append("      <d:ConsumerId>").append(esc(header.consumerId)).append("</d:ConsumerId>\n")
                .append("      <d:ServiceAdr>").append(esc(header.serviceAdr)).append("</d:ServiceAdr>\n")
                .append("      <d:ServiceAction>").append(esc(header.serviceAction)).append("</d:ServiceAction>\n")
                .append("    </s:ReqHeader>\n")
                .append("  </soap:Header>\n")
                .append("  <soap:Body>\n")
                .append(bodyInnerXml)
                .append("\n  </soap:Body>\n")
                .append("</soap:Envelope>")
                .toString();
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** SOAP Header 内 ReqHeader（d: 命名空间） */
    public static class ReqHeader {
        public String msgId;
        public String sourceSysId;
        public String consumerId;
        public String serviceAdr;
        public String serviceAction;
    }
}
