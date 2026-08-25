package com.bank.branch.platform.report.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/** 画布线性渐变背景配置，与前端 bgGradient(from/to/angle) 对应。 */
@Data
public class CanvasGradientDTO {

    /** 渐变起始颜色，例如 #050e2b。 */
    private String from;

    /** 渐变结束颜色，例如 #0a1f4e。 */
    private String to;

    /** 渐变角度（度），允许前端输入小数。 */
    private BigDecimal angle;
}
