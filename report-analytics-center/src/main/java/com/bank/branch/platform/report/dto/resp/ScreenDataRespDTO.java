package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大屏统一取数响应（所有可视化组件同构消费的二维结构）.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenDataRespDTO {

    /** 列名（宽表指标列为指标名别名） */
    private List<String> columns;

    /** 数据行（值为 Number/String，日期已转字符串） */
    private List<List<Object>> rows;
}
