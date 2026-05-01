package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.OrgIndexResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 机构指标结果宽表 Mapper（org_index_result）.
 *
 * <p>结构与 {@link EmpIndexResultMapper} 对称：以"单 slot 读写"为核心抽象，
 * 维度键为 {@code org_code}。
 *
 * <p><strong>安全（SQL 注入）声明</strong>：同 {@link EmpIndexResultMapper}——
 * XML 使用 {@code val_${slot}} 动态拼接列名（合法例外），调用方必须在 Service
 * 层强制校验 {@code slot ∈ [1, 200]}。
 * <p>BaseMapper 标准方法由 MyBatis-Plus 提供.
 */
@Mapper
public interface OrgIndexResultMapper extends BaseMapper<OrgIndexResult> {

    /**
     * 插入/更新机构在指定 slot 上的指标值（UPSERT，依赖 uk_subject_date_ver）.
     *
     * @param orgCode  机构编码
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200）
     * @param value    指标值
     */
    void insertSlotValue(@Param("orgCode") String orgCode,
                         @Param("dataDate") LocalDate dataDate,
                         @Param("version") String version,
                         @Param("slot") Integer slot,
                         @Param("value") BigDecimal value);

    /**
     * 查询机构在指定 slot 上的指标值.
     */
    BigDecimal selectSlotValue(@Param("orgCode") String orgCode,
                               @Param("dataDate") LocalDate dataDate,
                               @Param("version") String version,
                               @Param("slot") Integer slot);

    /**
     * 按 orgCodes 批量查询同一 slot 值.
     */
    List<OrgMetricValueRow> selectSlotValuesByOrgs(@Param("orgCodes") List<String> orgCodes,
                                                   @Param("dataDate") LocalDate dataDate,
                                                   @Param("version") String version,
                                                   @Param("slot") Integer slot);

    /**
     * 纯行粒度插入，用于单测构造 UK 冲突场景.
     */
    int insertRow(OrgIndexResult row);

    /**
     * V1.7：取多个 metricCode 对应的 val_slot 映射（一次查询，查 perf_metric_def）.
     *
     * @param metricCodes 指标编码列表
     * @return metricCode -&gt; val_slot 映射
     */
    java.util.Map<String, Integer> selectValSlotsByCodes(@Param("metricCodes") java.util.List<String> metricCodes);

    /**
     * V1.7：按 slot 列号查单主体单值（val_${slot} 动态列名）.
     *
     * <p><strong>安全说明</strong>：val_${slot} 属 common-dev-guide §5 合法例外，
     * 调用方必须保证 slot ∈ [1, 200]。
     *
     * @param subject  机构编码
     * @param slot     值槽（1..200）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 指标值，行不存在返回 null
     */
    java.math.BigDecimal selectValBySlot(@Param("subject") String subject,
                                         @Param("slot") Integer slot,
                                         @Param("dataDate") java.time.LocalDate dataDate,
                                         @Param("version") String version);

    /**
     * V1.7：按 subject + 多 metricCode 在单一 dataDate+version 下取宽表 slot 值.
     *
     * @param subject     机构编码
     * @param metricCodes 指标编码列表（空列表直接返回空 Map）
     * @param dataDate    数据日期
     * @param version     数据版本
     * @return metricCode -&gt; 指标值 映射
     */
    default java.util.Map<String, java.math.BigDecimal> selectSlotValuesByCodes(
            String subject, java.util.List<String> metricCodes,
            java.time.LocalDate dataDate, String version) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, Integer> slotMap = selectValSlotsByCodes(metricCodes);
        java.util.Map<String, java.math.BigDecimal> result = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Integer> e : slotMap.entrySet()) {
            java.math.BigDecimal value = selectValBySlot(subject, e.getValue(), dataDate, version);
            if (value != null) {
                result.put(e.getKey(), value);
            }
        }
        return result;
    }
}
