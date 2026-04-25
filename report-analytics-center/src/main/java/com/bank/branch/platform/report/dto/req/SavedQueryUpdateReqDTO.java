package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新查询方案入参 DTO（B.3 PUT /api/reports/saved-queries/:id）.
 *
 * <p>字段全部可选（部分更新），但必传 {@code expectedVersion} 用于乐观锁判定.
 */
@Data
public class SavedQueryUpdateReqDTO {

    @Size(max = 200, message = "name 长度不能超过 200")
    private String name;

    private String dim;

    /** JSON 数组字符串（subjectIds 列表） */
    private String subjectIds;

    /** JSON 数组字符串（metricCodes 列表） */
    private String metricCodes;

    /** 乐观锁版本号（必传） */
    @NotNull(message = "expectedVersion 不能为空")
    private Integer expectedVersion;
}
