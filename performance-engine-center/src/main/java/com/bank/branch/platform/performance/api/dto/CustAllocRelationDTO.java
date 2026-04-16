package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户业绩分配关系 DTO.
 * <p>v1.2: id 为 String.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustAllocRelationDTO {
    /** 分配关系 ID (varchar 32). */
    private String id;
    /** 客户 ID. */
    private String custId;
    /** 客户姓名 (可选, 冗余展示). */
    private String custName;
    /** 调整维度: RULE/ACCOUNT. */
    private String allocDim;
    /** 业务种类. */
    private String bizKind;
    /** 账号 (账号维度必填). */
    private String accountNo;
    /** 员工工号. */
    private String empId;
    /** 员工姓名 (可选). */
    private String empName;
    /** 分配比例 (0-100). */
    private BigDecimal ratio;
    private LocalDate effectiveDate;
    private LocalDate endDate;
}
