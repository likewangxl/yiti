package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 动态查询入参 DTO（A.2 POST /api/reports/dynamic-query）.
 *
 * <p>JSR-303 基础校验：
 * <ul>
 *   <li>dim / subjectIds / metricCodes / dataDate 全部必填</li>
 *   <li>subjectIds 和 metricCodes 个数上限由 Service 层显式校验（RPT-40007 / RPT-40008），
 *       避免 400 与业务错误码混用</li>
 * </ul>
 */
@Data
public class DynamicQueryReqDTO {

    /** 维度：EMP / ORG / CUST */
    @NotBlank(message = "dim 不能为空")
    private String dim;

    /** 对象 ID 列表（上限 100，由 Service 层校验抛 RPT-40007） */
    @NotEmpty(message = "subjectIds 不能为空")
    private List<String> subjectIds;

    /** 指标编码列表（上限 20，由 Service 层校验抛 RPT-40008） */
    @NotEmpty(message = "metricCodes 不能为空")
    private List<String> metricCodes;

    /** 数据日期 */
    @NotNull(message = "dataDate 不能为空")
    private LocalDate dataDate;
}
