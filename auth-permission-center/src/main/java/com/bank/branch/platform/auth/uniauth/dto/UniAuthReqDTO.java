package com.bank.branch.platform.auth.uniauth.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Data;

/**
 * SOAP Body 根元素：{@code <s:ReqNewAuthrQuery>...</s:ReqNewAuthrQuery>}。
 * <p>字段顺序、命名严格对齐 wangyq/统一认证接口/ 下的真实 SOAP 样本。
 */
@Data
@XmlRootElement(name = "ReqNewAuthrQuery")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"reqSvcHeader", "svcBody"})
public class UniAuthReqDTO {

    @XmlElement(name = "ReqSvcHeader") private ReqSvcHeader reqSvcHeader;
    @XmlElement(name = "SvcBody")      private SvcBody svcBody;

    /** ReqSvcHeader（s: 命名空间），顺序与样本一致 */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"tranDate", "tranTime", "tranTellerNo", "tranSeqNo",
            "consumerId", "globalSeqNo", "branchId"})
    public static class ReqSvcHeader {
        @XmlElement(name = "TranDate")     private String tranDate;      // yyyyMMdd
        @XmlElement(name = "TranTime")     private String tranTime;      // HHmmssSSS
        @XmlElement(name = "TranTellerNo") private String tranTellerNo = "";
        @XmlElement(name = "TranSeqNo")    private String tranSeqNo    = "";
        @XmlElement(name = "ConsumerId")   private String consumerId;
        @XmlElement(name = "GlobalSeqNo")  private String globalSeqNo  = "";
        @XmlElement(name = "BranchId")     private String branchId;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"userInfoQryInArgs", "userInfoQryInOpts"})
    public static class SvcBody {
        @XmlElement(name = "UserInfoQryInArgs") private UserInfoQryInArgs userInfoQryInArgs;
        @XmlElement(name = "UserInfoQryInOpts") private UserInfoQryInOpts userInfoQryInOpts;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"userDomainName", "appId"})
    public static class UserInfoQryInArgs {
        @XmlElement(name = "UserDomainName") private String userDomainName;
        @XmlElement(name = "AppId")          private String appId;
    }

    /** 8 个 RetXxxFlag，默认值跟真实样本一致（Bsc/Adv/Inst = true，其余 = false） */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {
            "retUserBscInfoFlag", "retUserAdvInfoFlag", "retUserCpcInfoFlag",
            "retInstInfoFlag",    "retUserGrpInfoFlag", "retRoleInfoFlag",
            "retTaskInfoFlag",    "retFrontFuncInfoFlag"
    })
    public static class UserInfoQryInOpts {
        @XmlElement(name = "RetUserBscInfoFlag")   private String retUserBscInfoFlag   = "true";
        @XmlElement(name = "RetUserAdvInfoFlag")   private String retUserAdvInfoFlag   = "true";
        @XmlElement(name = "RetUserCpcInfoFlag")   private String retUserCpcInfoFlag   = "false";
        @XmlElement(name = "RetInstInfoFlag")      private String retInstInfoFlag      = "true";
        @XmlElement(name = "RetUserGrpInfoFlag")   private String retUserGrpInfoFlag   = "false";
        @XmlElement(name = "RetRoleInfoFlag")      private String retRoleInfoFlag      = "false";
        @XmlElement(name = "RetTaskInfoFlag")      private String retTaskInfoFlag      = "false";
        @XmlElement(name = "RetFrontFuncInfoFlag") private String retFrontFuncInfoFlag = "false";
    }
}
