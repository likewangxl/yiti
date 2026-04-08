package com.bank.branch.platform.governance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL探查执行响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlProbeRespDTO {

    /**
     * 列名列表
     */
    private List<String> columns;

    /**
     * 结果数据
     */
    private List<Map<String, Object>> rows;

    /**
     * 返回行数
     */
    private Integer rowCount;

    /**
     * 执行耗时（ms）
     */
    private Integer executionTime;
}