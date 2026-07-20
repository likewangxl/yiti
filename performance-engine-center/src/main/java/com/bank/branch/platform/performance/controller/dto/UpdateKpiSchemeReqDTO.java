package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 KPI 方案请求 DTO (部分更新).
 *
 * <p>所有字段可空, null 表示不修改。方案编码 (schemeCode) 是 UK 不允许修改。
 */
@Data
@Schema(description = "更新 KPI 方案请求 (部分更新)")
public class UpdateKpiSchemeReqDTO {

    /** 方案名称 (可空). */
    @Schema(description = "方案名称")
    @Size(max = 100, message = "schemeName 长度不能超过 100")
    private String schemeName;

    /** 周期类型 (可空). */
    @Schema(description = "周期类型: MONTHLY/QUARTERLY/YEARLY")
    @Pattern(regexp = "^(MONTHLY|QUARTERLY|YEARLY)$", message = "cycleType 必须是 MONTHLY、QUARTERLY 或 YEARLY")
    private String cycleType;

    /** 是否向员工开放明细 (可空). */
    @Schema(description = "是否向员工开放明细")
    private Boolean openDetail;

    /** 员工标签范围 (人员标签 ID 多选, 可空; 传空数组=清空不限定). */
    @Schema(description = "员工标签范围(人员标签 PERSON_TAG.TAG_ID 数组, 可空)")
    private java.util.List<Long> empTagScopes;
}
