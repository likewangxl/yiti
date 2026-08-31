package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 地图经营指标条目。
 *
 * <p>只携带服务端授权后的机构身份与真实指标值；行政区落位由机构画像的
 * regionCode 或前端已授权地图点位完成，禁止以模拟数据填补缺失区域。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapRegionMetricDTO {

    private String orgCode;
    private String orgName;
    private String regionCode;
    private LocalDate dataDate;
    private Map<String, BigDecimal> metricValues;
}
