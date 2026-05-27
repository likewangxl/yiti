package com.bank.branch.platform.auth.uniauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一认证（S120030044）边车调用参数。
 * <p>默认值取自 wangyq/统一认证接口/ 下的真实 SOAP 样本，标记 TODO 的字段需要行内最终拍板。
 */
@ConfigurationProperties(prefix = "platform.sidecar.uniauth")
public class UniAuthProperties {

    // === 边车路由 ===
    /** 边车统一认证服务 path（11003 端口 + /services/S120030044）。
     *  走"网关模式"——不传 serviceName/functionId header，边车自动转 7603 行内 ESB 网关。
     *  原因：点对点调用要求"前 4 位系统 ID 相同 + 安全中心 functionId 申请"，yiti 跨系统场景用不上。 */
    private String path = "/services/S120030044";

    // === SOAP Header 内 ReqHeader（d: 命名空间） ===
    private String sourceSysId   = "37150001";
    private String consumerId    = "37150001";
    private String serviceAdr    = "http://esb.spdbbiz.com:7701/services/S120030044";
    private String serviceAction = "urn:/NewAuthrQuery";

    // === SOAP Body 内 ReqSvcHeader（s: 命名空间） ===
    /** ReqSvcHeader.ConsumerId（通常与 ReqHeader.ConsumerId 一致） */
    private String svcConsumerId = "37150001";
    private String branchId      = "9901";
    /** 样本里是空字符串；保留可配置 */
    private String tranTellerNo  = "";

    // === 业务字段 UserInfoQryInArgs ===
    /** 应用程序 ID（UIAS 系统简称，行内分配。不是 ESB 数字编号 SYS_CODE） */
    private String appId = "XAFA";

    // === UIAS 单点重定向链路（正式生产用） ===
    /** UIAS 单点登录页 URL（302 跳转目标）。当前为 UAT 环境。 */
    private String redirectUrl = "http://uias.uat.spdb.com/NIDP/";

    /** yiti 回调地址（UIAS 完认证后跳回这里）。
     *  开发期 vite proxy 模式：浏览器同域走 8090；生产 nginx 反代：同前端域。 */
    private String callbackUrl = "http://localhost:8090/api/auth/uniauth/callback";

    /** UIAS 重定向时传"回调地址"用的 query 参数名（xanpd 用 ssotarget） */
    private String ssoTargetParamName = "ssotarget";

    /** UIAS 回调进来时，用户身份所在的 query 参数名。
     *  行内 UIAS 通常用 userDomainName / ssoUser / uid 之一；实测后改这里。
     *  如果是 HTTP Header / Cookie 注入，去 AuthController.uniAuthCallback 改取值方式。 */
    private String userParamName = "userDomainName";

    /** 登录成功后 302 跳的前端首页。默认 hash 路由首页。 */
    private String frontHomeUrl = "/#/workspace";

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getSsoTargetParamName() { return ssoTargetParamName; }
    public void setSsoTargetParamName(String ssoTargetParamName) { this.ssoTargetParamName = ssoTargetParamName; }

    public String getUserParamName() { return userParamName; }
    public void setUserParamName(String userParamName) { this.userParamName = userParamName; }

    public String getFrontHomeUrl() { return frontHomeUrl; }
    public void setFrontHomeUrl(String frontHomeUrl) { this.frontHomeUrl = frontHomeUrl; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getSourceSysId() { return sourceSysId; }
    public void setSourceSysId(String sourceSysId) { this.sourceSysId = sourceSysId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getServiceAdr() { return serviceAdr; }
    public void setServiceAdr(String serviceAdr) { this.serviceAdr = serviceAdr; }

    public String getServiceAction() { return serviceAction; }
    public void setServiceAction(String serviceAction) { this.serviceAction = serviceAction; }

    public String getSvcConsumerId() { return svcConsumerId; }
    public void setSvcConsumerId(String svcConsumerId) { this.svcConsumerId = svcConsumerId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getTranTellerNo() { return tranTellerNo; }
    public void setTranTellerNo(String tranTellerNo) { this.tranTellerNo = tranTellerNo; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
}
