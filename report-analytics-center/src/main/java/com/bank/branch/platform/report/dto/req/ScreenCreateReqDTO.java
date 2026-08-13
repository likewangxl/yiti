package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建大屏元数据请求。
 *
 * <p>画布组件、区块数据绑定及角色白名单均有独立高风险入口，禁止在新建元数据请求中夹带。</p>
 */
@Data
public class ScreenCreateReqDTO {

    /** 空时由服务端生成稳定的 SCR_ 前缀编码。 */
    private String screenCode;

    @NotBlank
    private String screenName;

    @NotBlank
    private String viewLevel;

    /** 新建必须显式提交：CORP / RETAIL / COMMON。 */
    @NotBlank
    private String bizLine;

    /** 新建必须显式提交：LEGACY_CONTEXT / NAMED_GROUP。 */
    @NotBlank
    private String orgScopeMode;

    /** NAMED_GROUP 时必须是已存在的 auth 机构组编码。 */
    private String orgGroupCode;

    private String themeJson;

    /** ACTIVE / DISABLED，空时服务端设为 ACTIVE。 */
    private String status;
}
