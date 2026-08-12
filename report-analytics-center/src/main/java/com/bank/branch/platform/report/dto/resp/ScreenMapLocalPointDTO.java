package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/** 西安主图真实 GCJ-02 点位。 */
@Data
public class ScreenMapLocalPointDTO {

    private String orgCode;
    private String orgName;
    private BigDecimal lng;
    private BigDecimal lat;
    private String coordSys = "GCJ02";
    private String targetScreenCode;
}
