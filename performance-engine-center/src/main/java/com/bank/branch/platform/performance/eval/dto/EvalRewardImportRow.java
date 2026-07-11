package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 奖励分配导入 Excel 行模型（8 列，按业务样例顺序）。
 * <p>分配值列导入时忽略（提交时由分配人填入）。数值列用 BigDecimal，空/非法在校验期落行级错误。</p>
 */
@Data
public class EvalRewardImportRow {
    @ExcelProperty("被分配人工号")
    private String beAssignedUserId;
    @ExcelProperty("被分配人用户姓名")
    private String beAssignedUserName;
    @ExcelProperty("部门名称")
    private String deptName;
    @ExcelProperty("原始值")
    private BigDecimal originalValue;
    @ExcelProperty("分配值")
    private String assignValueIgnored;
    @ExcelProperty("兑现值")
    private BigDecimal cashValue;
    @ExcelProperty("分配人工号")
    private String assignUserId;
    @ExcelProperty("分配合计")
    private BigDecimal assignTotal;
}
