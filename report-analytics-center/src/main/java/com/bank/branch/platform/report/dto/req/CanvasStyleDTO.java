package com.bank.branch.platform.report.dto.req;

import lombok.Data;
import java.util.Map;

/** 画布全局样式(对应 CANVAS_STYLE_JSON 的强类型入参). */
@Data
public class CanvasStyleDTO {
    /** schema 版本,自 v1 起 */
    private Integer schemaVersion = 1;
    private Integer designWidth = 1920;
    private Integer designHeight = 1080;
    private String background;
    /** 背景类型:solid/gradient/image(与前端 normalizeCanvasStyle 对齐). */
    private String backgroundType;
    /** 线性渐变背景配置;backgroundType=gradient 时使用. */
    private CanvasGradientDTO bgGradient;
    /** 背景图片 URL;backgroundType=image 时使用. */
    private String bgImage;
    /** 适配策略:keep/keepProportion/widthFirst/heightFirst */
    private String adaptor = "keepProportion";
    private Map<String, Object> themeOverride;
}
