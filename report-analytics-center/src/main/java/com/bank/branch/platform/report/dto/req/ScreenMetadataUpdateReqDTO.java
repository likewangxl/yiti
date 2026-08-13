package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 大屏元数据与机构范围更新请求。
 *
 * <p>此 DTO 故意没有 id、blocks 或角色字段：id 只取 URL 路径，画布区块只允许经
 * {@code /canvas/save} 在独立 CAS 事务中修改，角色只允许经 PERMISSION_CHANGE 端点修改。</p>
 */
@Data
public class ScreenMetadataUpdateReqDTO {

    /** 与画布、角色变更共用的配置版本；不匹配时返回 RPT-43012。 */
    @NotNull
    private Integer expectedVersion;

    /** 元数据、条线或机构范围变更的可追溯原因。 */
    @NotBlank
    private String reason;

    private String screenCode;

    @NotBlank
    private String screenName;

    @NotBlank
    private String viewLevel;

    @NotBlank
    private String bizLine;

    @NotBlank
    private String orgScopeMode;

    private String orgGroupCode;

    private String themeJson;

    private String status;
}
