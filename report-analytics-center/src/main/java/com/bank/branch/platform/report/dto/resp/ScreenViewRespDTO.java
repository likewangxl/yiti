package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;

import java.util.List;

/**
 * 大屏运行时整屏配置响应.
 */
@Data
public class ScreenViewRespDTO {

    /** 屏信息（blocks 置 null，区块在外层） */
    private ScreenDetailRespDTO screen;

    /** 顶层冗余返回运行时范围模式，供区块取数上下文直接使用。 */
    private String orgScopeMode;

    /** 运行时取数契约版本：1=兼容 dsId，2=服务端按 screenCode+blockId 解析。 */
    private Integer runtimeSchemaVersion;

    private List<ScreenBlockDTO> blocks;

    /** 仅 PROVINCE 屏返回（其余为空列表） */
    private List<MapPointDTO> mapPoints;

    /** schemaVersion=2 复合地图服务端包；旧屏为空。 */
    private ScreenMapRenderPackageDTO mapPackage;
}
