package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.MapPointDTO;
import lombok.Data;

import java.util.List;

/**
 * 大屏运行时渲染响应(发布态或草稿态渲染包直投).
 *
 * <p>renderPackageJson 即 CANVAS_PUBLISHED_JSON(或草稿合成包)的 JSON 字符串,
 * 前端 JSON.parse 后按绝对定位渲染。mapPoints 供 PROVINCE 屏地图(沿用旧 getViewByCode
 * 的实时查询语义:不烘焙进渲染包,published/draft 两态均按当前 ACTIVE 点位现查现填)。
 */
@Data
public class ScreenRenderRespDTO {
    private Long screenId;
    private String screenCode;
    private String screenName;
    private String viewLevel;
    /** 渲染包 JSON(canvasStyle + components + bindSnapshots) */
    private String renderPackageJson;
    /** 状态:published / draft */
    private String state;
    /** PROVINCE 屏地图点位(非 PROVINCE 屏为空列表) */
    private List<MapPointDTO> mapPoints;
}
