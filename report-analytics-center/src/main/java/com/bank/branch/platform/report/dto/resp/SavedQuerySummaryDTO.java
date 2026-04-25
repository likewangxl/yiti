package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查询方案列表行 DTO（B.1 GET /api/reports/saved-queries 响应元素）.
 *
 * <p>对外不暴露 {@code subjectIds / metricCodes} 详细 JSON，由 GET /:id 单独获取详情。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SavedQuerySummaryDTO {

    private String id;

    private String name;

    private String dim;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
