package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * schemaVersion=2 复合地图渲染包。
 *
 * <p>本 DTO 特意把真实经纬度本地节点与示意锚点节点分开，避免前端将异地导航节点误当作地理坐标。</p>
 */
@Data
public class ScreenMapRenderPackageDTO {

    private int schemaVersion = 2;

    private String mode;

    private String baseRegion;

    private String disclaimer;

    private List<ScreenMapLocalPointDTO> localPoints;

    private List<ScreenMapSatelliteNodeDTO> satelliteNodes;
}
