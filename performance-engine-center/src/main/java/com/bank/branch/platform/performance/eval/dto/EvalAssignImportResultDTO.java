package com.bank.branch.platform.performance.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 待处理任务导入结果：success=true 时 importedCount 为入库条数；否则 errors 非空且不写库（all-or-none）。
 */
@Data
public class EvalAssignImportResultDTO {
    private boolean success;
    private int importedCount;
    private List<RowError> errors = new ArrayList<>();

    /** 单行错误：Excel 行号（从 1 开始，不含表头）+ 原因。 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RowError {
        private int row;
        private String message;
    }
}
