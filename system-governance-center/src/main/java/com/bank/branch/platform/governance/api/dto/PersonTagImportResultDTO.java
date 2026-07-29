package com.bank.branch.platform.governance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务标签导入结果（同步原子：任一行错误则整体不入库，返回行级错误明细）。
 */
@Data
public class PersonTagImportResultDTO {

    /** 是否导入成功（false 时一条都未写入）. */
    private boolean success;

    /** 新写入的关联条数. */
    private int importedCount;

    /** 自动新建的标签数（仅全局导入用，成员导入恒为 0）. */
    private int createdTagCount;

    /** 因已存在而跳过的关联条数（仅全局导入用，全量覆盖导入恒为 0）. */
    private int skippedCount;

    /** 行级错误明细（success=false 时非空）. */
    private List<RowError> errors = new ArrayList<>();

    /** 单行错误明细。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        /** Excel 数据行号（从 1 起，不含表头）. */
        private int row;
        /** 该行成员标识（EMP=工号，ORG=机构名称，可空）. */
        private String username;
        /** 错误原因. */
        private String message;
    }
}
