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
 *   <li>dim / metricCodes / dataDate 必填</li>
 *   <li>subjectIds 可空：不选对象时按数据范围查"能看到的全部对象"，由 Service 层枚举 + 分页</li>
 * </ul>
 */
@Data
public class DynamicQueryReqDTO {

    /** 维度：EMP / ORG / CUST */
    @NotBlank(message = "dim 不能为空")
    private String dim;

    /**
     * 对象 ID 列表，可空。
     * <p>空/不传 = 不指定对象，按数据范围查"能看到的全部对象"（取自当天宽表实际有数据的对象），
     * 由 Service 层枚举后分页返回；非空 = 仅查所选对象（含越权校验）。</p>
     */
    private List<String> subjectIds;

    /** 指标编码列表（必填） */
    @NotEmpty(message = "metricCodes 不能为空")
    private List<String> metricCodes;

    /** 数据日期 */
    @NotNull(message = "dataDate 不能为空")
    private LocalDate dataDate;

    /** 页码（从 1 开始），不传默认 1 */
    private Integer pageNo;

    /** 每页对象数，不传默认 20，最大 100 */
    private Integer pageSize;
}
