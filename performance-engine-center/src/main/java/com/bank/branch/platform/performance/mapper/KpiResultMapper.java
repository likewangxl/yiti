package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.KpiResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * KPI 结果表 Mapper（kpi_result）.
 *
 * <p>V1.1 Task P1.2 交付。表结构与宽表不同：
 * <ul>
 *   <li>主键 bigint AUTO_INCREMENT（insert 后 id 由 JDBC 回填）</li>
 *   <li>业务唯一键 uk_emp_cycle_asof(emp_id, cycle_type, cycle_date, as_of_date) 四列</li>
 *   <li>无 val_* 值槽；核心业务字段 kpi_total_score(decimal) + detail_json(longtext)</li>
 * </ul>
 *
 * <p>V1.1 仅暴露最小写入 + 基础读查询，后续 V1.2 计算 Job/查询 API 将按需扩展。
 * <p>insert / selectById 由 MyBatis-Plus BaseMapper 提供.
 */
@Mapper
public interface KpiResultMapper extends BaseMapper<KpiResult> {

    /**
     * 查询指定员工在某周期类型下的所有 KPI 历史记录.
     *
     * <p>按 as_of_date 降序返回，方便"查最新"场景。
     *
     * @param empId     员工工号
     * @param cycleType 周期类型（MONTHLY/QUARTERLY）
     * @return KPI 历史列表（可能为空）
     */
    List<KpiResult> selectByEmpCycle(@Param("empId") String empId,
                                     @Param("cycleType") String cycleType);

    /**
     * 查询员工在某周期类型下的最新一条 KPI 结果（by as_of_date DESC LIMIT 1）.
     *
     * <p>V1.1 Task P4.3 新增：{@code KpiApi.getCurrentKpiTotal / getCurrentKpiResult}
     * 直接调用本方法，避免调用方在全量列表上做 {@code .findFirst()} 带来的无谓 I/O。
     *
     * @param empId     员工工号
     * @param cycleType 周期类型（MONTHLY/QUARTERLY）
     * @return 最新一条 KPI 结果，不存在返回 null
     */
    KpiResult selectLatestByEmpCycle(@Param("empId") String empId,
                                     @Param("cycleType") String cycleType);

    /**
     * 查询员工在某周期类型下、{@code as_of_date} 落在 [from, to] 区间内的 KPI 结果列表.
     *
     * <p>V1.1 Task P4.3 新增：{@code KpiApi.getKpiHistory} 使用本方法实现时间范围查询。
     * 按 {@code as_of_date DESC, id DESC} 排序，便于"查最新"语义。
     *
     * @param empId     员工工号
     * @param cycleType 周期类型
     * @param from      起始日期（含）
     * @param to        截止日期（含）
     * @return KPI 结果列表（可能为空）
     */
    List<KpiResult> selectByEmpCycleRange(@Param("empId") String empId,
                                          @Param("cycleType") String cycleType,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /**
     * Q7.3 新增：基于 {@link com.bank.branch.platform.performance.service.scope.PerfScopeHelper}
     * 生成的 scopeFragment + scopeParams 注入数据范围条件.
     *
     * <p>XML 采用 {@code AND (${scopeFragment})} 注入片段，参数走 {@code #{scopeParams.*}} 预编译.
     * 空片段 → 无过滤 (ALL); "1=0" → fail-close.
     *
     * @param empId         员工工号
     * @param cycleType     周期类型
     * @param from          起始日期
     * @param to            截止日期
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()
     * @return KPI 结果列表
     */
    List<KpiResult> selectByEmpCycleRangeWithScope(@Param("empId") String empId,
                                                   @Param("cycleType") String cycleType,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to,
                                                   @Param("scopeFragment") String scopeFragment,
                                                   @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * 按 (cycleType, cycleDate, asOfDate) 查询该周期下所有员工 KPI 结果（导出场景用）.
     *
     * <p>V1.2 Task Q6.2 新增：KpiExportStrategy 按方案周期批量导出；
     * dataVersion 可选过滤（null 时不过滤）；limit 控制上限，超过 limit 则视为"行数超限"
     * 由调用方抛 {@code EXPORT_ROWS_EXCEEDS_LIMIT}。
     *
     * @param cycleType   周期类型
     * @param cycleDate   周期日期
     * @param asOfDate    基准日
     * @param dataVersion 数据版本（可空）
     * @param limit       查询上限（含）
     * @return KPI 结果列表
     */
    List<KpiResult> selectForExport(@Param("cycleType") String cycleType,
                                    @Param("cycleDate") LocalDate cycleDate,
                                    @Param("asOfDate") LocalDate asOfDate,
                                    @Param("dataVersion") String dataVersion,
                                    @Param("limit") int limit);

    /**
     * 统计某周期 (cycleType, cycleDate, asOfDate) 下符合条件的 KPI 结果行数，
     * 用于导出前的 "行数是否超限" 预检.
     *
     * @param cycleType   周期类型
     * @param cycleDate   周期日期
     * @param asOfDate    基准日
     * @param dataVersion 数据版本（可空）
     * @return 行数
     */
    long countForExport(@Param("cycleType") String cycleType,
                        @Param("cycleDate") LocalDate cycleDate,
                        @Param("asOfDate") LocalDate asOfDate,
                        @Param("dataVersion") String dataVersion);

    /**
     * V1.14：列举近期被考核员工工号集合（distinct emp_id）.
     *
     * <p>用途：报表「考核员工选择器」数据源；为避免 report-analytics-center 跨库直连
     * perf 物理表，由 {@code KpiApi.listEvalEmpIds} 暴露对外。
     *
     * <p>SQL 语义：
     * <ul>
     *   <li>{@code as_of_date >= #{sinceDate}} 限近期考核</li>
     *   <li>{@code orgCodes} 非空时 LEFT JOIN {@code EXT_USER_ORG} + {@code u.org_code IN (...)} 做机构子树裁剪</li>
     *   <li>{@code orgCodes} 为 null 时不 JOIN（管理员/全行场景）</li>
     * </ul>
     *
     * @param sinceDate 起始日期（含），必填
     * @param orgCodes  机构子树过滤；null 表示不限
     * @return 员工工号去重列表（升序），可能为空
     */
    List<String> selectEvalEmpIds(@Param("sinceDate") LocalDate sinceDate,
                                  @Param("orgCodes") Collection<String> orgCodes);
}
