package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户党组织映射 DTO。
 * <p>同时承载管理端列表响应（含 username/displayName/id/createTime/updateTime）与绑定请求体
 * （仅需 userId/partyOrgId/partyRole，其余展示字段由服务端回填/忽略）。</p>
 */
@Data
@Schema(description = "用户党组织映射")
public class ReUserPartyMapDTO {

    /** 映射ID（响应字段，绑定请求可不传） */
    @Schema(description = "映射ID（响应字段，绑定请求可不传）")
    private Long id;

    /** 平台用户ID(PT_USER.USER_ID) */
    @Schema(description = "平台用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "userId 不能为空")
    private String userId;

    /** 平台用户工号(PT_USER.USERNAME，仅响应展示，绑定请求忽略) */
    @Schema(description = "平台用户工号（PT_USER.USERNAME，仅响应展示）")
    private String username;

    /** 平台用户中文姓名(PT_USER.USERCHNNAME，仅响应展示，绑定请求忽略) */
    @Schema(description = "平台用户中文姓名（PT_USER.USERCHNNAME，仅响应展示）")
    private String displayName;

    /** 党组织ID(RE_PARTY_ORG.id) */
    @Schema(description = "党组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "partyOrgId 不能为空")
    private Long partyOrgId;

    /** 党内角色: ORG_REVIEWER/BRANCH_REVIEWER/SECRETARY/REPORTER */
    @Schema(description = "党内角色: ORG_REVIEWER/BRANCH_REVIEWER/SECRETARY/REPORTER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "partyRole 不能为空")
    private String partyRole;

    /** 创建时间（响应字段） */
    @Schema(description = "创建时间（响应字段）")
    private LocalDateTime createTime;

    /** 更新时间（响应字段） */
    @Schema(description = "更新时间（响应字段）")
    private LocalDateTime updateTime;
}
