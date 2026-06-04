package com.bank.branch.platform.soap.endpoint.bind;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Data;

/**
 * 业务请求根对象,对应报文 soap:Body 下的 &lt;s:ReqAxlryPrsRvrSysSvc&gt;。
 *
 * <p>由 {@code ReqSvcHeader}(服务头)+ {@code SvcBody}(业务体)组成。
 * {@code SvcBody.Parm} 是字符串(内部为一段 callpu JSON),与外部渠道 JAXB 类保持一致。</p>
 */
@Data
@XmlRootElement(name = "ReqAxlryPrsRvrSysSvc")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReqAxlryPrsRvrSysSvcType", propOrder = {
        "reqSvcHeader",
        "svcBody"
})
public class ReqAxlryPrsRvrSysSvcType {

    @XmlElement(name = "ReqSvcHeader", required = true)
    private ReqSvcHeaderType reqSvcHeader;

    @XmlElement(name = "SvcBody")
    private SvcBody svcBody;

    /**
     * 业务体 SvcBody,对应报文 &lt;s:SvcBody&gt;。
     * {@code Parm} 内部是一段 callpu JSON({@code {RuleName, IntType, Parm}})。
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
            "srvicName1", "token1", "employeeNo", "ruleName", "intType", "parm"
    })
    public static class SvcBody {

        @XmlElement(name = "SrvicName1", required = true)
        private String srvicName1;
        @XmlElement(name = "Token1")
        private String token1;
        @XmlElement(name = "EmployeeNo", required = true)
        private String employeeNo;
        @XmlElement(name = "RuleName", required = true)
        private String ruleName;
        @XmlElement(name = "IntType", required = true)
        private String intType;
        @XmlElement(name = "Parm", required = true)
        private String parm;
    }
}
