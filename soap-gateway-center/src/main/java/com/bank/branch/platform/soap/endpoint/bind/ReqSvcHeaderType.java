package com.bank.branch.platform.soap.endpoint.bind;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Data;

/**
 * SOAP 服务头 {@code ReqSvcHeader} 的强类型绑定（对应报文 &lt;s:ReqSvcHeader&gt;）。
 *
 * <p>字段与外部渠道下发的 JAXB 类保持一致（来源见 soapJpg/ReqSvcHeaderType1~4）。
 * 反序列化时按元素本地名匹配（命名空间由 {@code SoapBodyBinder} 统一剥离）。</p>
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReqSvcHeaderType", propOrder = {
        "tranDate", "tranTime", "tranTellerNo", "tranSeqNo", "consumerId",
        "globalSeqNo", "sourceSysId", "branchId", "terminalCode", "cityCode",
        "authrTellerNo", "authrPwd", "authrCardFlag", "authrCardNo", "langCode",
        "tranCode", "pin", "keyVersionNo", "sysOffset1", "sysOffset2",
        "targetAdr", "sourceAdr", "msgEndFlag", "msgSeqNo", "subTranCode",
        "tranMode", "tranSerialNo"
})
public class ReqSvcHeaderType {

    @XmlElement(name = "TranDate", required = true)
    private String tranDate;
    @XmlElement(name = "TranTime", required = true)
    private String tranTime;
    @XmlElement(name = "TranTellerNo", required = true)
    private String tranTellerNo;
    @XmlElement(name = "TranSeqNo", required = true)
    private String tranSeqNo;
    @XmlElement(name = "ConsumerId", required = true)
    private String consumerId;
    @XmlElement(name = "GlobalSeqNo")
    private String globalSeqNo;
    @XmlElement(name = "SourceSysId")
    private String sourceSysId;
    @XmlElement(name = "BranchId")
    private String branchId;
    @XmlElement(name = "TerminalCode")
    private String terminalCode;
    @XmlElement(name = "CityCode")
    private String cityCode;
    @XmlElement(name = "AuthrTellerNo")
    private String authrTellerNo;
    @XmlElement(name = "AuthrPwd")
    private String authrPwd;
    @XmlElement(name = "AuthrCardFlag")
    private String authrCardFlag;
    @XmlElement(name = "AuthrCardNo")
    private String authrCardNo;
    @XmlElement(name = "LangCode")
    private String langCode;
    @XmlElement(name = "TranCode")
    private String tranCode;
    @XmlElement(name = "PIN")
    private String pin;
    @XmlElement(name = "KeyVersionNo")
    private String keyVersionNo;
    @XmlElement(name = "SysOffset1")
    private String sysOffset1;
    @XmlElement(name = "SysOffset2")
    private String sysOffset2;
    @XmlElement(name = "TargetAdr")
    private String targetAdr;
    @XmlElement(name = "SourceAdr")
    private String sourceAdr;
    @XmlElement(name = "MsgEndFlag")
    private String msgEndFlag;
    @XmlElement(name = "MsgSeqNo")
    private String msgSeqNo;
    @XmlElement(name = "SubTranCode")
    private String subTranCode;
    @XmlElement(name = "TranMode")
    private String tranMode;
    @XmlElement(name = "TranSerialNo")
    private String tranSerialNo;
}
