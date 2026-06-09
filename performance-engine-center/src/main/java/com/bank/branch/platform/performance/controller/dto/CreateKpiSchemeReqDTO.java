package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建 KPI 方案请求 DTO.
 *
 * <p>方案编码 (schemeCode) 唯一, 创建后不可修改 (更新走 {@link UpdateKpiSchemeReqDTO}).
 * items 允许为空 (先建空方案后续追加), 若提供则每项走 {@link AddKpiItemReqDTO} 自身校验。
 */
@Data
@Schema(description = "新建 KPI 方案请求")
public class CreateKpiSchemeReqDTO {

    /** 方案编码 (唯一). 不再强约束格式, 调用方自行保证唯一与可识别 */
    @Schema(description = "方案编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "schemeCode 不能为空")
    @Size(max = 64, message = "schemeCode 长度不能超过 64")
    private String schemeCode;

    /** 方案名称. */
    @Schema(description = "方案名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "schemeName 不能为空")
    @Size(max = 100, message = "schemeName 长度不能超过 100")
    private String schemeName;

    /** 周期类型: MONTHLY / QUARTERLY / YEARLY. */
    @Schema(description = "周期类型: MONTHLY/QUARTERLY/YEARLY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "cycleType 不能为空")
    @Pattern(regexp = "^(MONTHLY|QUARTERLY|YEARLY)$", message = "cycleType 必须是 MONTHLY、QUARTERLY 或 YEARLY")
    private String cycleType;

    /** 是否向员工开放明细 (必填). */
    @Schema(description = "是否向员工开放明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "openDetail 不能为空")
    private Boolean openDetail;

    /** 方案项列表 (可空, 允许先建空方案). */
    @Schema(description = "方案项列表 (可空)")
    @Valid
    private List<AddKpiItemReqDTO> items;
}
