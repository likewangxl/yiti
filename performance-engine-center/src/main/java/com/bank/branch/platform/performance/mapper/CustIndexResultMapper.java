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
 * 层强制校验 {@code slot ∈ [1, 200]}。
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
}
