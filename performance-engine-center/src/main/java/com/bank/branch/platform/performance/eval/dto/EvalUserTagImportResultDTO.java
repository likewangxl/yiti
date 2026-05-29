package com.bank.branch.platform.performance.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/** 人员评价角色导入结果：success=true 时 importedCount 为入库条数；否则 errors 非空且不写库。 */
@Data
public class EvalUserTagImportResultDTO {
    private boolean success;
    private int importedCount;
    private List<RowError> errors = new ArrayList<>();

    /** 单行错误：Excel 行号（从 1 开始，不含表头）+ 工号 + 原因。 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RowError {
        private int row;
        private String empId;
        private String message;
    }
}
