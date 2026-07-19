package com.bank.branch.platform.redengine.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 材料上报导出行（EasyExcel）。
 * <p>移植自源 redengine {@code ExportController.exportData}（{@code type=submit} 分支）的
 * hutool {@code ExcelWriter.addHeaderAlias} 表头映射，表头文案（英文）与源码逐字对齐：
 * ID / Organization / Project / Type / Date / Status。</p>
 */
@Data
public class ReSubmitExportRow {

    /** 上报记录ID（源字段 id） */
    @ExcelProperty("ID")
    private Long id;

    /** 党组织ID（源字段 orgId） */
    @ExcelProperty("Organization")
    private Long orgId;

    /** 项目名称（源字段 projectName） */
    @ExcelProperty("Project")
    private String projectName;

    /** 上报类型：1月度 2季度 3年度（源字段 submitType） */
    @ExcelProperty("Type")
    private Integer submitType;

    /** 上报日期（源字段 submitDate） */
    @ExcelProperty("Date")
    private LocalDate submitDate;

    /** 状态：0草稿 1已提交 2已通过 3已驳回（源字段 status） */
    @ExcelProperty("Status")
    private Integer status;
}
