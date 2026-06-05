package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiScore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * KPI 计分明细结果 Mapper（PERF_KPI_SCORE）.
 *
 * <p>核心写入走 {@link #upsert}（INSERT ... ON DUPLICATE KEY UPDATE），
 * 依赖唯一键 {@code uk_date_scheme_metric_subject} 保证"已存在则更新数值"。
 */
@Mapper
public interface PerfKpiScoreMapper extends BaseMapper<PerfKpiScore> {

    /**
     * 按唯一键 upsert 一条计分明细.
     *
     * <p>唯一键命中（同 data_date + scheme_code + metric_code + subject_type + subject_id）
     * 时更新 actual_value / weight / target_value / base_value / score / updated_time，
     * 否则插入新行。
     *
     * @param score 计分明细（dataDate/schemeCode/metricCode/subjectType/subjectId 必填）
     * @return 受影响行数（插入=1，更新=2，无变化=0，MySQL 语义）
     */
    int upsert(PerfKpiScore score);

    /**
     * 统计某数据日期 + 方案的明细行数（供测试/校验）.
     *
     * @param dataDate   数据日期
     * @param schemeCode 方案编码
     * @return 行数
     */
    long countByDateAndScheme(@Param("dataDate") LocalDate dataDate,
                              @Param("schemeCode") String schemeCode);
}
