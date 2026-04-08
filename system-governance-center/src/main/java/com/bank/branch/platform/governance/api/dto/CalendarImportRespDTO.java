package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 日历批量导入响应DTO（B.4导入节假日结果）
 */
@Data
public class CalendarImportRespDTO {
    /** 导入总行数 */
    private Integer totalRows;
    /** 成功行数 */
    private Integer successRows;
    /** 跳过行数（已过去的日期） */
    private Integer skippedRows;
}
