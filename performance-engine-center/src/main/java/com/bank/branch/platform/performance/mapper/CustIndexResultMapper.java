package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.CustIndexResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 客户指标结果宽表 Mapper（cust_index_result）.
 *
 * <p>结构与 {@link EmpIndexResultMapper} 对称：以"单 slot 读写"为核心抽象，
 * 维度键为 {@code cust_id}。
 *
 * <p><strong>安全（SQL 注入）声明</strong>：同 {@link EmpIndexResultMapper}——
 * XML 使用 {@code val_${slot}} 动态拼接列名（合法例外），调用方必须在 Service
 * 层强制校验 {@code slot ∈ [1, 400]}。
 * <p>BaseMapper 标准方法由 MyBatis-Plus 提供.
 */
@Mapper
public interface CustIndexResultMapper extends BaseMapper<CustIndexResult> {

    /**
     * 插入/更新客户在指定 slot 上的指标值（UPSERT，依赖 uk_subject_date_ver）.
     */
    void insertSlotValue(@Param("custId") String custId,
                         @Param("dataDate") LocalDate dataDate,
                         @Param("version") String version,
                         @Param("slot") Integer slot,
                         @Param("value") BigDecimal value);

    /**
     * 清空指定数据日期+版本下某 slot 的全部客户指标值（落库前清理，避免上一轮残留主体的脏数据）.
     *
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200）
     */
    void clearSlot(@Param("dataDate") LocalDate dataDate,
                   @Param("version") String version,
                   @Param("slot") Integer slot);

    /**
     * 查询客户在指定 slot 上的指标值.
     */
    BigDecimal selectSlotValue(@Param("custId") String custId,
                               @Param("dataDate") LocalDate dataDate,
                               @Param("version") String version,
                               @Param("slot") Integer slot);

    /**
     * 按 custIds 批量查询同一 slot 值.
     */
    List<CustMetricValueRow> selectSlotValuesByCusts(@Param("custIds") List<String> custIds,
                                                     @Param("dataDate") LocalDate dataDate,
                                                     @Param("version") String version,
                                                     @Param("slot") Integer slot);

    /**
     * 纯行粒度插入，用于单测构造 UK 冲突场景.
     */
    int insertRow(CustIndexResult row);

    /**
     * V1.7：取多个 metricCode 对应的 val_slot 映射（一次查询，查 perf_metric_def）.
     *
     * @param metricCodes 指标编码列表
     * @return metricCode -&gt; val_slot 映射
     */
    java.util.List<java.util.Map<String, Object>> selectValSlotRows(@Param("metricCodes") java.util.List<String> metricCodes);

    /**
     * V1.7（V2 修复）：metricCode -&gt; val_slot 映射。
     *
     * <p>原返回 {@code Map} 无 {@code @MapKey} 时 MyBatis 走 {@code selectOne}，单行错映射成
     * {@code {key,value}}、多行抛 {@code TooManyResultsException}，导致引用指标恒为 0。
     * 改为 List 行查询 + Java 端聚合修复。
     */
    default java.util.Map<String, Integer> selectValSlotsByCodes(java.util.List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, Integer> slotMap = new java.util.LinkedHashMap<>();
        for (java.util.Map<String, Object> row : selectValSlotRows(metricCodes)) {
            Object code = row.get("metricCode");
            Object slot = row.get("valSlot");
            if (code != null && slot instanceof Number) {
                slotMap.put(code.toString(), ((Number) slot).intValue());
            }
        }
        return slotMap;
    }

    /**
     * V1.7：按 slot 列号查单主体单值（val_${slot} 动态列名）.
     *
     * <p><strong>安全说明</strong>：val_${slot} 属 common-dev-guide §5 合法例外，
     * 调用方必须保证 slot ∈ [1, 400]。
     *
     * @param subject  客户 ID
     * @param slot     值槽（1..200）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 指标值，行不存在返回 null
     */
    java.math.BigDecimal selectValBySlot(@Param("subject") String subject,
                                         @Param("slot") Integer slot,
                                         @Param("dataDate") java.time.LocalDate dataDate,
                                         @Param("version") String version);

    /** 试运行：取某主体某日期"最近导入"的数据版本（按 updated_time 优先），无数据返回 null. */
    String selectLatestVersionForSubject(@Param("subject") String subject,
                                         @Param("dataDate") java.time.LocalDate dataDate);

    /**
     * V1.13+：取某日某版本下宽表所有出现的客户 cust_id（替代 subject_sql 取主体集合）.
     *
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 该日该版本下宽表已有的客户 ID 列表
     */
    List<String> selectDistinctCustIds(@Param("dataDate") LocalDate dataDate,
                                       @Param("version") String version);

    /**
     * KPI 分值计算：取某数据日期下某 slot 的全部客户实际值（同客户多版本取最新 version）.
     *
     * <p>列名 {@code val_${slot}} 属 common-dev-guide §5 允许的动态列名例外，
     * 调用方必须保证 {@code slot ∈ [1, 400]}。
     *
     * @param dataDate 数据日期
     * @param slot     值槽（1..400，<strong>调用方必须校验</strong>）
     * @return 每个客户（最新版本）的 (subjectId, value)；该槽位为 null 的对象不返回
     */
    List<SubjectSlotValueRow> selectLatestSlotValuesByDate(@Param("dataDate") LocalDate dataDate,
                                                           @Param("slot") Integer slot);

    /**
     * V1.7：按 subject + 多 metricCode 在单一 dataDate+version 下取宽表 slot 值.
     *
     * @param subject     客户 ID
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
