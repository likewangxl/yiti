package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 党组织新增/修改请求 DTO。
 * <p>使用独立请求 DTO 避免 Controller 直接接收并暴露持久化实体。
 * {@link #orgName} 对齐 {@code RE_PARTY_ORG.org_name} 的 {@code NOT NULL} 约束标注
 * {@code @NotBlank}；其余字段按当前表结构允许为空，不在此处追加超出服务端契约的强制校验。</p>
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
