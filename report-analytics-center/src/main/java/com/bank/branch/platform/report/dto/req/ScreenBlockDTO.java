package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 大屏区块 DTO（配置保存与运行时读取共用）.
 */
@Data
public class ScreenBlockDTO {

    private Long id;

    /** LEFT / MAIN / RIGHT */
    @NotBlank
    private String region;

    @NotNull
    private Integer rowNo;

    @NotNull
    private Integer colNo;

    /** 行内宽度百分比 1~100 */
    @NotNull
    private Integer widthPct;

    /** 区域内行高百分比 1~100 */
    @NotNull
    private Integer heightPct;

    /** METRIC_CARD / LINE_TREND / PIE_SHARE / RANK_LIST / FLOW_STATUS */
    @NotBlank
    private String componentType;

    /** 数据绑定 JSON */
    @NotBlank
    private String bindJson;

    /** 样式 JSON */
    private String styleJson;

    /** 钻取/跳转 JSON */
    private String drillJson;
}
