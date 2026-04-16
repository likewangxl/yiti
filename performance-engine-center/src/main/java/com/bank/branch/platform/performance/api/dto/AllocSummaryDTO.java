package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 分配关系汇总 DTO (按员工聚合).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocSummaryDTO {
    /** 员工工号. */
    private String empId;
    /** 员工姓名 (可选). */
    private String empName;
    /** 员工所在机构. */
    private String orgCode;
    /** 业务种类. */
    private String bizKind;
    /** 负责客户总数 (去重). */
    private Long custCount;
    /** 分配金额汇总. */
    private BigDecimal totalAllocAmount;
    /** 平均分配比例. */
    private BigDecimal avgAllocRatio;
    /** 数据基准日. */
    private LocalDate asOfDate;
    /** 数据版本号. */
    private String sysControlVersion;
}
