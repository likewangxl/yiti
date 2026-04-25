package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查询方案详情响应 DTO（B.1.2 GET /api/reports/saved-queries/:id）.
 *
 * <p>包含 subjectIds / metricCodes JSON 字段，便于前端反序列化为下次查询入参。
 * <p>{@code version} 字段供 PUT 接口乐观锁使用。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SavedQueryDetailRespDTO {

    private String id;

    private String name;

    private String dim;

    /** JSON 数组字符串：subjectIds */
    private String subjectIds;

    /** JSON 数组字符串：metricCodes */
    private String metricCodes;

    /** 乐观锁版本（PUT 时回带） */
    private Integer version;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
