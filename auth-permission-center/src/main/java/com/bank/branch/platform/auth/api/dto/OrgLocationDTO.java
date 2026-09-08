package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 机构详细地址与定位管理 DTO。该 DTO 不跨模块进入机构画像 DTO。 */
@Data
public class OrgLocationDTO {

    private String orgCode;
    private String address;
    private String addressSource;
    private String cityCode;
    private BigDecimal lng;
    private BigDecimal lat;
    private String coordSys;
    private String provider;
    private String matchLevel;
    private String status;
    private Integer version;
    private LocalDateTime updatedTime;
    private String locationSource;
}
