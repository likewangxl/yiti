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
    /** 适配策略:keep/keepProportion/widthFirst/heightFirst */
    private String adaptor = "keepProportion";
    private Map<String, Object> themeOverride;
}
