package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 地址解析候选。低精度候选可用于人工说明，但没有可确认令牌。 */
@Data
public class OrgLocationGeocodeCandidateDTO {

    private String candidateToken;
    private String address;
    private String formattedAddress;
    private String cityCode;
    private BigDecimal lng;
    private BigDecimal lat;
    private String coordSys;
    private String provider;
    private String matchLevel;
    private boolean verificationAllowed;
    private String verificationReason;
}
