package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 地图支行点位 DTO.
 */
@Data
public class MapPointDTO {

    private Long id;

    @NotBlank
    private String orgCode;

    @NotBlank
    private String orgName;

    @NotNull
    private BigDecimal lng;

    @NotNull
    private BigDecimal lat;

    /** 点击跳转目标屏编码（空=SCR_BRANCH） */
    private String targetScreenCode;

    /** ACTIVE / DISABLED */
    private String status;
}
