package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KPI 方案 DTO.
 * <p>v1.2: id 为 String (对齐生产 DDL varchar(32)).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSchemeDTO {
    /** 方案 ID (varchar 32). */
    private String id;
    private String schemeCode;
    private String schemeName;
    /** MONTHLY/QUARTERLY/YEARLY. */
    private String cycleType;
    /** 是否向员工开放明细. */
    private Boolean openDetail;
    /** 状态: ACTIVE/DISABLED. */
    private String status;
    /** 方案内指标项. */
    private List<KpiItemDTO> items;
}
