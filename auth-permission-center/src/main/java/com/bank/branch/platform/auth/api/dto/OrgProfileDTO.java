package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 机构本地经营画像跨模块 DTO。 */
@Data
public class OrgProfileDTO {

    private String orgCode;
    private String orgName;
    private String orgNature;
    private String operatingLevel;
    private String ownerOperatingOrgCode;
    private String cityCode;
    private String cityName;
    private BigDecimal lng;
    private BigDecimal lat;
    private String coordSys;
    /** 运行时坐标来源；旧画像坐标由画像链路标记，位置台账补充仅在服务端确认后提供。 */
    private String locationSource;
    private String status;
    private Integer version;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private String remark;
}
