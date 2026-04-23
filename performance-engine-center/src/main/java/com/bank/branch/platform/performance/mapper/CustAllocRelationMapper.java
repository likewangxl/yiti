package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户业绩分配关系 Mapper（V1.0 只读；V1.1 Task P5.4 新增 insert 入口用于导入通道）.
 *
 * <p>V1.0 限定只读场景：支撑 {@code AllocApi} 10 个查询方法所需的时间线查询 + 数据范围过滤。
 * 写入 / 调整由 V1.2 通过工作流审批 (AllocAdjustService) 提供；V1.1 新增的 {@link #insert}
 * 入口仅供 {@code AllocRelationImportStrategy} 在 {@code /api/perf/import?importType=ALLOC}
 * 导入通道使用，不对业务模块暴露写权限（仍保持 V1.0 对外契约的"只读"语义）.
 *
 * <p>时间线语义：所有 "当前有效 / 某时点有效" 查询统一使用
 * {@code effective_date &lt;= asOfDate AND (end_date IS NULL OR end_date &gt;= asOfDate)}.
 *
 * <p><strong>安全 (SQL 注入) 注意</strong>：
 * <ul>
 *   <li>{@code dataScopeFilter} 参数在 XML 中以 {@code ${dataScopeFilter}} 方式直接拼接到 SQL（为
 *       common-dev-guide §5 允许的合法例外：数据范围 SQL 片段注入）；调用方 <strong>必须</strong>
 *       保证该参数由 common-security 的 {@code DataScopeApi} 可信生成，<strong>禁止</strong>
 *       接受任何用户输入直接拼入，否则将形成 SQL 注入漏洞。</li>
 *   <li>所有其他参数一律使用 {@code #{}} 预编译占位符。</li>
 * </ul>
 */
@Mapper
public interface CustAllocRelationMapper {

    /**
     * 新增分配关系（V1.1 Task P5.4 导入通道专用）.
     *
     * <p>仅插入必要的维度字段（id / custId / allocDim / bizKind / accountNo / empId / ratio /
     * effectiveDate / endDate / sourceBatchId），其他字段由 DDL 默认值 / AuditFieldFiller 填充。
     * 主键冲突（{@link org.springframework.dao.DuplicateKeyException}）由调用方捕获到 errorSummary。
     *
     * @param entity 分配关系实体（id 必填，由调用方生成 UUID）
     * @return 受影响行数
     */
    int insert(CustAllocRelation entity);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return 分配关系，不存在返回 null
     */
    CustAllocRelation selectById(@Param("id") String id);

    /**
     * 查某客户某业务在 asOfDate 时点生效的分配关系.
     *
     * @param custId   客户 ID
     * @param bizKind  业务种类（nullable，null 表示全部业务）
     * @param asOfDate 时间线基准日期
     * @return 分配关系列表，可能为空
     */
    List<CustAllocRelation> selectCurrentByCustAndBiz(@Param("custId") String custId,
                                                     @Param("bizKind") String bizKind,
                                                     @Param("asOfDate") LocalDate asOfDate);

    /**
     * 查某客户在 asOfDate 时点生效的所有分配关系（全业务种类）.
     *
     * @param custId   客户 ID
     * @param asOfDate 时间线基准日期（历史快照）
     * @return 分配关系列表
     */
    List<CustAllocRelation> selectHistoryByCustAsOf(@Param("custId") String custId,
                                                   @Param("asOfDate") LocalDate asOfDate);

    /**
     * 查某员工某业务在 asOfDate 时点名下所有客户的分配关系（含数据范围过滤）.
     *
     * <p>普通用户场景：{@code dataScopeFilter} 通常为 {@code " AND emp_id = 'xxx' "}（与入参 empId 一致）；
     * 管理员场景：{@code dataScopeFilter} 传 {@code null}/{@code ""} 表示无额外数据范围限制.
     *
     * @param empId           员工工号
     * @param bizKind         业务种类（nullable）
     * @param asOfDate        时间线基准日期
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @return 分配关系列表
     */
    List<CustAllocRelation> selectByEmpAndBiz(@Param("empId") String empId,
                                             @Param("bizKind") String bizKind,
                                             @Param("asOfDate") LocalDate asOfDate,
                                             @Param("dataScopeFilter") String dataScopeFilter);

    /**
     * 批量查多个客户在 asOfDate 时点的分配关系（缓存未命中合并回查使用）.
     *
     * @param custIds         客户 ID 集合（上限由调用方保证 ≤ 500）
     * @param bizKind         业务种类（nullable）
     * @param asOfDate        时间线基准日期
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @return 分配关系列表（调用方按 custId 分组）
     */
    List<CustAllocRelation> selectCurrentByCustIds(@Param("custIds") Set<String> custIds,
                                                  @Param("bizKind") String bizKind,
                                                  @Param("asOfDate") LocalDate asOfDate,
                                                  @Param("dataScopeFilter") String dataScopeFilter);

    /**
     * 按员工分组统计当前负责客户数（去重后）.
     *
     * <p>返回每项 Map 包含 {@code emp_id}（String）与 {@code cust_count}（Long）两个键.
     *
     * @param empIds   员工工号集合（上限 500）
     * @param bizKind  业务种类（nullable）
     * @param asOfDate 时间线基准日期
     * @return 分组统计结果
     */
    List<Map<String, Object>> countCustomersByEmps(@Param("empIds") Set<String> empIds,
                                                   @Param("bizKind") String bizKind,
                                                   @Param("asOfDate") LocalDate asOfDate);

    /**
     * 统计某员工某业务在 asOfDate 时点负责的去重客户数.
     *
     * @param empId    员工工号
     * @param bizKind  业务种类（nullable）
     * @param asOfDate 时间线基准日期
     * @return 客户数（去重后）
     */
    long countDistinctCustomers(@Param("empId") String empId,
                                @Param("bizKind") String bizKind,
                                @Param("asOfDate") LocalDate asOfDate);

    /**
     * 按业务种类分页查询 asOfDate 时点生效的分配关系（供 report 聚合 / 列表查询使用）.
     *
     * @param bizKind         业务种类（nullable）
     * @param asOfDate        时间线基准日期
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @param offset          偏移量
     * @param limit           每页大小
     * @return 分配关系列表
     */
    List<CustAllocRelation> selectCurrentByBizPage(@Param("bizKind") String bizKind,
                                                  @Param("asOfDate") LocalDate asOfDate,
                                                  @Param("dataScopeFilter") String dataScopeFilter,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /**
     * 按业务种类统计 asOfDate 时点生效的分配关系总数（与 {@link #selectCurrentByBizPage} 过滤一致）.
     *
     * @param bizKind         业务种类（nullable）
     * @param asOfDate        时间线基准日期
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @return 总数
     */
    long countCurrentByBiz(@Param("bizKind") String bizKind,
                           @Param("asOfDate") LocalDate asOfDate,
                           @Param("dataScopeFilter") String dataScopeFilter);

    /**
     * 客户分配汇总：查询某客户在 asOfDate 时点各员工的比例聚合.
     *
     * <p>返回每项 Map 包含 {@code emp_id}（String）、{@code biz_kind}（String, nullable）、
     * {@code ratio_sum}（BigDecimal） 三个键，调用方按需聚合.
     *
     * @param custId   客户 ID
     * @param asOfDate 时间线基准日期
     * @return 分组统计结果
     */
    List<Map<String, Object>> summaryByCust(@Param("custId") String custId,
                                            @Param("asOfDate") LocalDate asOfDate);

    /**
     * 导出：按 effectiveDate/bizKind/empId 过滤后一次性取前 limit 条.
     *
     * <p>V1.2 Task Q6.3 AllocExportStrategy 专用。支持以下可选过滤：
     * <ul>
     *   <li>{@code bizKind}：业务种类（null 不过滤）</li>
     *   <li>{@code empId}：员工工号（null 不过滤）</li>
     *   <li>{@code effectiveDate}：时间线基准日（必填，筛选在该日生效的记录）</li>
     * </ul>
     *
     * @param bizKind       业务种类（nullable）
     * @param empId         员工工号（nullable）
     * @param effectiveDate 时间线基准日
     * @param limit         上限
     * @return 分配关系列表
     */
    List<CustAllocRelation> selectForExport(@Param("bizKind") String bizKind,
                                            @Param("empId") String empId,
                                            @Param("effectiveDate") LocalDate effectiveDate,
                                            @Param("limit") int limit);

    /**
     * 导出行数预检.
     *
     * @param bizKind       业务种类（nullable）
     * @param empId         员工工号（nullable）
     * @param effectiveDate 时间线基准日
     * @return 行数
     */
    long countForExport(@Param("bizKind") String bizKind,
                        @Param("empId") String empId,
                        @Param("effectiveDate") LocalDate effectiveDate);
}
