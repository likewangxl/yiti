package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 驾驶舱-逾期条目 DTO。
 * <p>对应源 redengine {@code BizCockpitServiceImpl.getOverdueList} 返回的
 * {@code List<Map<String,Object>>}（id/orgId/projectName/submitDate/status 五键），拍平为强类型 DTO。
 * 逾期口径以代码为准（非源文档"迟1天-1分"口径，见 {@code ReCockpitService} 类注释）：
 * RE_SUBMIT.status∈{0,1} 且 submitDate+7天 严格早于今天。</p>
 */
@Data
@Schema(description = "驾驶舱-逾期条目")
public class ReOverdueItemDTO {

    /** 上报ID */
    @Schema(description = "上报ID")
    private Long id;

    /** 党组织ID */
    @Schema(description = "党组织ID")
    private Long orgId;

    /** 项目名称 */
    @Schema(description = "项目名称")
    private String projectName;

    /** 上报日期 */
    @Schema(description = "上报日期")
    private LocalDate submitDate;

    /** 状态(0草稿 1已提交) */
    @Schema(description = "状态")
    private Integer status;
}
