package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 党组织新增/修改请求 DTO。
 * <p>此前 {@code ReOrgController.addOrg}/{@code updateOrg} 直接接收裸实体
 * {@link com.bank.branch.platform.redengine.entity.RePartyOrg} 且无 {@code @Valid}，与本模块
 * 其余端点"实体不跨层暴露 + DTO 校验"的惯例不一致（历史遗留写法，见 red-engine-center/CLAUDE.md
 * 「技术债」）。本次修复新建独立请求 DTO，{@link #orgName} 对齐 DDL {@code RE_PARTY_ORG.org_name}
 * 的 {@code NOT NULL} 约束标注 {@code @NotBlank}；其余字段库表本身可空，不做过度校验（前端表单已有
 * orgCode/orgType 必填的 UI 侧校验，属产品交互层面的更严格要求，不在后端重复强制，避免过度设计）。</p>
 */
@Data
@Schema(description = "党组织新增/修改请求")
public class RePartyOrgReqDTO {

    /** 党组织名称 */
    @Schema(description = "党组织名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "orgName 不能为空")
    private String orgName;

    /** 上级党组织ID，顶层为 null */
    @Schema(description = "上级党组织ID(顶层为null)")
    private Long parentId;

    /** 层级(1:分行党委 2:党支部) */
    @Schema(description = "层级(1:分行党委 2:党支部)")
    private Integer orgLevel;

    /** 党组织编码(无唯一约束) */
    @Schema(description = "党组织编码")
    private String orgCode;

    /** 组织类型(字典 RE_ORG_TYPE：经营单位/营销部室/中后台部门) */
    @Schema(description = "组织类型")
    private String orgType;

    /** 负责人姓名 */
    @Schema(description = "负责人姓名")
    private String principal;

    /** 联系电话 */
    @Schema(description = "联系电话")
    private String contactPhone;

    /** 组织地址 */
    @Schema(description = "组织地址")
    private String orgAddress;

    /** 支部书记平台用户工号 */
    @Schema(description = "支部书记平台用户工号")
    private String secretaryId;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
