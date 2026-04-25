package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存查询方案入参 DTO（B.2 POST /api/reports/saved-queries）.
 */
@Data
public class SavedQuerySaveReqDTO {

    @NotBlank(message = "name 不能为空")
    @Size(max = 200, message = "name 长度不能超过 200")
    private String name;

    @NotBlank(message = "dim 不能为空")
    private String dim;

    /** JSON 数组字符串（subjectIds 列表） */
    @NotBlank(message = "subjectIds 不能为空")
    private String subjectIds;

    /** JSON 数组字符串（metricCodes 列表） */
    @NotBlank(message = "metricCodes 不能为空")
    private String metricCodes;
}
