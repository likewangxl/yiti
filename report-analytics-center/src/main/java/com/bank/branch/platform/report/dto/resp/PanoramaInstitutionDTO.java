package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/** CODE 全景屏运行时机构目录的最小字段集合。 */
@Data
public class PanoramaInstitutionDTO {

    private String orgCode;
    private String orgName;
    private String cityCode;
    private String cityName;
    private String ownerOperatingOrgCode;
    private String operatingLevel;
    private String orgNature;
    private BigDecimal lng;
    private BigDecimal lat;
    /** 已核验坐标系；运行地图只接受 GCJ02。 */
    private String coordSys;
    private boolean located;
    /**
     * 位置来源；只有 located=true 时才向运行时目录暴露。允许 PROFILE、MANUAL、
     * GEOCODE_VERIFIED 和授权旧点位回退 LEGACY_MAP_POINT。
     */
    private String locationSource;
}
