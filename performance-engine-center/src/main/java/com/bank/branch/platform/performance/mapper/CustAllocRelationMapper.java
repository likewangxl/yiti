package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
 * <p>insert / selectById 由 MyBatis-Plus BaseMapper 提供.
 */
@Mapper
public interface CustAllocRelationMapper extends BaseMapper<CustAllocRelation> {

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
     * 插入新分配前置：把该客户（{@code cust_id}）名下、与审批账号匹配的<strong>全部</strong>
     * {@code is_original='2'} 存量分配标记为「原分配」{@code is_original='1'}，确保插入完成后只有
     * 本次新增的 {@code is_original='2'} 行是当前分配。
     *
     * <p>范围为「客户 + 账号」：{@code accountNo} 由审批通过的申请传入；为 null（RULE 维度无账号）时
     * 按 {@code account_no IS NULL} 精确匹配，非 null 时按 {@code account_no = accountNo} 过滤。
     *
     * <p>同时把命中行的失效日期 {@code end_date} 置为 {@code endDate}（当天），与新分配
     * {@code effective_date=今天} 形成连续时间线。
     *
     * @param custId    客户编号
     * @param accountNo 账号（nullable，来源审批申请）
     * @param endDate   失效日期（当天）
     * @return 受影响行数（命中并置 1 的存量当前分配条数，0 表示无存量）
     */
    int markAllOriginalByCustId(@Param("custId") String custId,
                                @Param("accountNo") String accountNo,
                                @Param("endDate") java.time.LocalDate endDate);

    /**
     * 原业绩分配反显：取该客户当前生效分配（{@code is_original='2'}）的最新来源批次全部关系。
     *
     * <p>来源批次按非空 {@code source_batch_id} 分组；无批次历史行按自身 {@code id} 独立成批次。
     * 批次排序优先 {@code source_process_date}、其次 {@code created_time}，最后以稳定主键消歧。
     * {@code allocDim='ACCOUNT'} 只在 ACCOUNT 候选中取最新批次；RULE/空在 RULE+ACCOUNT 候选中取整体最新批次。
     * 姓名/部门直接读快照列 fullname/dept_no/dept_name。
     *
     * @param custId   客户编号
     * @param allocDim 分配维度 RULE/ACCOUNT/null
     * @return 当前生效分配列表（可能为空）
     */
    List<CustAllocRelation> selectCurrentOriginalByCust(@Param("custId") String custId,
                                                        @Param("allocDim") String allocDim);

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
     * Q7.2 新增：基于 {@link com.bank.branch.platform.performance.service.scope.PerfScopeHelper}
     * 生成的 scopeFragment + scopeParams 注入数据范围条件。
     *
     * <p>与老的 {@link #selectByEmpAndBiz} 区别：
     * <ul>
     *   <li>老方法只支持单字符串 dataScopeFilter（形如 " AND emp_id = 'xxx' "，手工拼 empId 字面值）；</li>
     *   <li>新方法通过 PerfScopeHelper 统一生成 7 种 DataScopeType 对应片段，
     *       参数走 {@code #{scopeParams.*}} 预编译，彻底杜绝 SQL 注入。</li>
     * </ul>
     *
     * <p>XML 采用 {@code AND (${scopeFragment})} 注入片段，scopeFragment 来自可信 Helper 生成。
     *
     * @param empId         员工工号（主查询主体）
     * @param bizKind       业务种类（nullable）
     * @param asOfDate      时间线基准日期
     * @param scopeFragment PerfScopeHelper.Fragment#getSql()（空串 → 无过滤 / "1=0" → fail-close / 其他 → 注入）
     * @param scopeParams   PerfScopeHelper.Fragment#getParams()（预编译参数, 对应 #{scopeParams.*}）
     * @return 分配关系列表
     */
    List<CustAllocRelation> selectByEmpAndBizWithScope(@Param("empId") String empId,
                                                      @Param("bizKind") String bizKind,
                                                      @Param("asOfDate") LocalDate asOfDate,
                                                      @Param("scopeFragment") String scopeFragment,
                                                      @Param("scopeParams") Map<String, Object> scopeParams);

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
