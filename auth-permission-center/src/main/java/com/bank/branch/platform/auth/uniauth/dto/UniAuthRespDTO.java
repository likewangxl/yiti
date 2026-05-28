package com.bank.branch.platform.auth.uniauth.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * SOAP Body 根元素：{@code <s:RspNewAuthrQuery>...</s:RspNewAuthrQuery>}。
 * <p>子结构按 S120030044 文档骨架；UserAdv/UserCpc/Role/Task/FrontFunc 当前请求 Flag=false 不取，
 * 后续需要时按文档补强类型字段即可。
 * <p>所有嵌套类实现 Serializable：Spring Session JDBC 用 Java 序列化持久化 session attribute，
 * loginByUniAuth 把 UserBscInfo / InstInfoList 存 session 供后续业务消费。
 */
@Data
@XmlRootElement(name = "RspNewAuthrQuery")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniAuthRespDTO implements Serializable {

    @XmlElement(name = "RspSvcHeader") private RspSvcHeader rspSvcHeader;
    @XmlElement(name = "SvcBody")      private SvcBody svcBody;

    /** 业务成功判定：ReturnCode == "000000000000" */
    public boolean isSuccess() {
        return rspSvcHeader != null && "000000000000".equals(rspSvcHeader.getReturnCode());
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RspSvcHeader implements Serializable {
        @XmlElement(name = "ReturnCode")  private String returnCode;
        @XmlElement(name = "ReturnMsg")   private String returnMsg;
        @XmlElement(name = "GlobalSeqNo") private String globalSeqNo;
        @XmlElement(name = "TranSeqNo")   private String tranSeqNo;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SvcBody implements Serializable {
        @XmlElement(name = "UserInfoQryRslt") private UserInfoQryRslt userInfoQryRslt;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UserInfoQryRslt implements Serializable {
        @XmlElement(name = "UserBscInfo")  private UserBscInfo userBscInfo;
        @XmlElement(name = "InstInfoList") private InstInfoList instInfoList;
        // UserAdvInfo / UserCpcInfo / GrpInfoList / RoleInfoList / TaskInfoList / FrontFuncInfoList
        // 当前请求 Flag=false 不取；后续需要时按 S120030044 文档补字段
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UserBscInfo implements Serializable {
        @XmlElement(name = "UserDomainName") private String userDomainName;
        @XmlElement(name = "UserChnName")    private String userChnName;
        @XmlElement(name = "UserEnName")     private String userEnName;
        // TODO 按 S120030044 文档补：UserStatus / Email / Phone / DeptId 等
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class InstInfoList implements Serializable {
        @XmlElement(name = "InstInfo") private List<InstInfo> instInfo;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class InstInfo implements Serializable {
        @XmlElement(name = "InstId")   private String instId;
        @XmlElement(name = "InstName") private String instName;
        // TODO 按文档补
    }
}
