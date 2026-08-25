package com.bank.branch.platform.customer.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户标签关联 Excel 导入结果。任一行校验失败时整批不入库。
 */
@Data
public class TagCustomerImportResultDTO {

    private boolean success;
    private int totalRows;
    private int importedCount;
    private int skippedCount;
    private String mode;
    private List<RowError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        /** Excel 中的实际行号（含表头）。 */
        private int row;
        private String unifiedCreditCode;
        private String message;
    }
}
