package com.bank.branch.platform.redengine.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分导出行（EasyExcel）。
 * <p>移植自源 redengine {@code ExportController.exportData}（{@code type=score} 分支）的
 * hutool {@code ExcelWriter.addHeaderAlias} 表头映射，表头文案（英文）与源码逐字对齐：
 * ID / Organization / Period / Base Score / Deduction / Final Score。</p>
 */
@Data
public class ReScoreExportRow {

    /** 评分记录ID（源字段 id） */
    @ExcelProperty("ID")
    private Long id;

    /** 党组织ID（源字段 orgId） */
    @ExcelProperty("Organization")
    private Long orgId;

    /** 考核期间 YYYY-MM（源字段 scorePeriod） */
    @ExcelProperty("Period")
    private String scorePeriod;

    /** 基础分（源字段 baseScore） */
    @ExcelProperty("Base Score")
    private BigDecimal baseScore;

    /** 扣分（源字段 deductionScore） */
    @ExcelProperty("Deduction")
    private BigDecimal deductionScore;

    /** 最终得分（源字段 finalScore） */
    @ExcelProperty("Final Score")
    private BigDecimal finalScore;
}
