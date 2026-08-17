# 绩效计算中心 — 对外 API 契约

> 模块编码: `performance-engine-center`
> 关联文档: `01-功能规格.md` / `02-后端架构.md` / `03-接口设计与报文.md`
> 契约层: 所有跨模块调用必须通过本文件定义的 `*Api` 接口, 禁止直接访问本模块的 mapper/entity/serviceImpl
> 契约位置: `com.bank.branch.platform.performance.api.*`
> 最后更新: **2026-07-19**（此前一次实质更新 2026-04-25）

> **2026-07-19 回填说明**：`performance-engine-center` 模块实际共 11 个 `*Api`/`*QueryApi`（`api/` 目录，不含 `dto/` 子包），原文档仅收录 7 个（§1～§7），本次依据源码补齐缺失的 4 个：
> - **§8 `CustStatQueryApi`**（客户财务统计展示表只读查询，面向 soap-gateway-center callpu `CASH_GETCUST_INFO`）
> - **§9 `EvalQueryApi`**（eval 考核评价子域对外只读查询，供 report-analytics-center 未来引用）
> - **§10 `PerfApprovalCmdApi`**（分配关系调整审批"写"接口，面向手机端 callpu 网关）
> - **§11 `PerfApprovalQueryApi`**（分配关系调整审批列表"读"接口，**已确认被 `soap-gateway-center` 的 `CallPuDispatchService` 实际消费**，`PERF_LIST`/`PERF_SAVE`/`PERF_RECALL`/`PERF_APPR` 四个 callpu 操作码分别调用本接口与 `PerfApprovalCmdApi`）
>
> 原 §8～§12（DTO 定义 / 调用约束 / 领域事件 / 接口注册与暴露 / 不对外暴露的内部能力）依次顺延为 **§12～§16**。§12 DTO 定义新增 §10 补充 DTO（`AllocAdjustPreviewItemDTO`）。此外 §1 `MetricApi`、§3 `KpiApi`、§5 `PerfCalcApi`、§7 `AllocApi` 四个既有接口经源码核对存在方法级漏收/失真，已在对应章节原地订正（详见各章节内 "2026-07-19 代码核实" 标注）。一切以源码（`api/*.java`）为准。

---

## 0. 全局约定

### 0.5 主键类型统一为 String（2026-04-22 修订）

生产 DDL（`docs/schema/ddl-performance.sql`）中 `perf_kpi_scheme.id` / `perf_target_plan.id` / `sys_control.id` / `perf_metric_def.id` 均为 `varchar(32)`（业务编码主键），与原 04 契约的 `Long` 冲突。V1.0 已全局对齐为 `String`，本条为 04/03/05 三份文档的正式统一修正。跨模块消费方应使用 `String` 类型。

---

## 1. MetricApi (指标查询)

**接口路径:** `com.bank.branch.platform.performance.api.MetricApi`

**调用方:** `portal-content-center`, `report-analytics-center`, `customer-marketing-center`

**职责:** 供外部模块查询指标定义、员工/机构/客户指标实际值

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标查询对外 API.
 * 所有跨模块指标相关查询的唯一入口.
 */
public interface MetricApi {

    /**
     * 获取员工工作台的指标卡片聚合数据.
     * 返回该员工当前周期可见的全部指标卡片, 包含标题、当前值、达成率.
     * portal-content-center 的工作台首屏聚合入口, 建议缓存 5 分钟.
     *
     * <p><b>V1.3 交付范围（2026-04-24 Task R2.5 实际实现）：</b>
     * <ul>
     *   <li>ACTIVE KPI 方案并集：从 {@code kpi_item.metric_code} 采集 distinct 列表（V1.3 未引入员工-方案绑定，用"所有 ACTIVE"近似）</li>
     *   <li>过滤 baseDim != EMP 与未分配 val_slot 的指标</li>
     *   <li>actual 值：读 sys_control(EMP) 当前版本 + latest_data_date → emp_index_result slot 值</li>
     *   <li>target 值：{@code perf_target_value.selectByUniqueKey(planId=null, subjectType=EMP, subjectId=empId, cycleKey, metricCode)} 近似查询</li>
     *   <li>{@code cycleKey} 按方案 {@code cycleType} 精确匹配（V1.4 S3 交付，2026-04-24）：
     *     <ul>
     *       <li>YEARLY    → {@code yyyy}（如 {@code 2026}）</li>
     *       <li>QUARTERLY → {@code yyyyQn}（如 {@code 2026Q2}）</li>
     *       <li>MONTHLY   → {@code yyyyMM}（如 {@code 202604}）</li>
     *       <li>WEEKLY    → {@code yyyyWnn}（如 {@code 2026W15}）</li>
     *     </ul>
     *   </li>
     *   <li>无 EMP 基线版本 / 无 ACTIVE 方案 / 无匹配 metric 时均返回空列表（fail-safe）</li>
     * </ul>
     *
     * <p><b>V1.4 新增字段（2026-04-24，Task S3）：</b>
     * <ul>
     *   <li>{@code previousValue}：同周期上一期实际值（读历史版本宽表）</li>
     *   <li>{@code mom}：环比（MONTHLY→上月，QUARTERLY→上季度，YEARLY→上年同周期）</li>
     *   <li>{@code yoy}：同比（统一 -1 年；WEEKLY 可能跨年漂移，接受约束）</li>
     *   <li>当 previousValue 缺失时 mom / yoy 自动降级为 null</li>
     * </ul>
     *
     * <p><b>V1.5 已消化项（2026-04-24）：</b>
     * <ul>
     *   <li>P3.1 多 scheme 共享 metric 时 {@code codeToCycleType} 取首命中的歧义（M01）：
     *       改 {@code LinkedHashSet<MetricKey>} 组合键，同 metricCode 跨方案 cycleType 不同 → 多卡片；相同 → 去重 1 卡</li>
     *   <li>P4.1 每 metric 3 次宽表查询 batch 化（M02）：新增 {@code EmpIndexResultMapper.selectSlotValuesByDates}，
     *       20 metric 从 60 次降至 20 次（-66%）</li>
     *   <li>P5.1 yoy 按 cycleType 分支（M03）：{@code calculateYearAgoDate(cycleType, date)}，
     *       WEEKLY → {@code minus(52, ChronoUnit.WEEKS)}；其他 → {@code minusYears(1)}</li>
     * </ul>
     *
     * <p><b>业务规划（非技术债）：</b>
     * <ul>
     *   <li>员工-KPI 方案个人绑定（当前仍是 ACTIVE 方案并集）。
     *       详见模块 AGENTS.md 的已知边界说明，V1.5 后无技术债遗留。</li>
     * </ul>
     *
     * @param empId 员工工号
     * @return 指标卡片列表, 按 sortNo 排序
     */
    List<MetricCardDTO> getUserMetricCards(String empId);

    /**
     * 查询单个指标定义.
     *
     * @param metricCode 指标编码
     * @return 指标定义, 不存在时返回 Optional.empty()
     */
    Optional<MetricDefDTO> getMetricDef(String metricCode);

    /**
     * 批量查询指标定义.
     * 对于 N+1 场景, 外部模块应使用本接口替代循环调用 getMetricDef.
     *
     * @param metricCodes 指标编码列表, 最多 100 个
     * @return 指标定义列表, 未找到的 code 不会出现在返回中
     */
    List<MetricDefDTO> getMetricDefs(List<String> metricCodes);

    /**
     * 按维度和层级查询指标定义.
     * 仅返回 status=PUBLISHED 的指标.
     *
     * @param baseDim     基础维度 EMP/ORG/CUST, 不可为 null
     * @param metricLevel 指标层级 1/2/3, null 表示不过滤
     * @return 指标定义列表
     */
    List<MetricDefDTO> listMetrics(String baseDim, Integer metricLevel);

    /**
     * 查询员工指标实际值, 按当前有效版本.
     * 内部实现会读取 sys_control 当前 is_valid=1 的版本, 从 emp_index_result 取对应 val_slot.
     *
     * @param empId        员工工号
     * @param dataDate     数据日期, null 表示读取 sys_control 的 latest_data_date
     * @param metricCodes  指标编码列表, 最多 100 个
     * @return Map of 指标编码 → 指标值, 未命中的 code 不出现
     */
    Map<String, BigDecimal> getEmpMetricValues(String empId, LocalDate dataDate, List<String> metricCodes);

    /**
     * 查询机构指标实际值.
     *
     * @param orgCode     机构编码
     * @param dataDate    数据日期, null 表示当前版本
     * @param metricCodes 指标编码列表
     */
    Map<String, BigDecimal> getOrgMetricValues(String orgCode, LocalDate dataDate, List<String> metricCodes);

    /**
     * 查询客户指标实际值.
     *
     * @param custId      客户 ID
     * @param dataDate    数据日期, null 表示当前版本
     * @param metricCodes 指标编码列表
     */
    Map<String, BigDecimal> getCustMetricValues(String custId, LocalDate dataDate, List<String> metricCodes);

    /**
     * 取某维度 SYS_CONTROL 的最新有效数据日期（current_version 对应的 latest_data_date）.
     * 供报表/仪表盘"读取最新版本汇总数据"使用，未配置时返回 null.
     *
     * @param scopeDim 维度 EMP / ORG / CUST
     * @return 最新数据日期，无配置返回 null
     */
    LocalDate getLatestDataDate(String scopeDim);

    /**
     * 列出某日宽表中有数据的全部员工工号（不限 version）.
     * 动态指标查询"不选对象=查范围内全部对象"用：对象集合取自当天宽表实际有数据的对象，
     * 天然有界、避免返回全是空值的行。调用方需再按数据范围裁剪。
     *
     * @param dataDate 数据日期
     * @return 员工工号列表（可能为空）
     */
    List<String> listEmpIdsWithData(LocalDate dataDate);

    /**
     * 列出某日宽表中有数据的全部机构编码（不限 version）.同 {@link #listEmpIdsWithData}。
     */
    List<String> listOrgCodesWithData(LocalDate dataDate);

    /**
     * 列出某日宽表中有数据的全部客户 ID（不限 version）.同 {@link #listEmpIdsWithData}。
     */
    List<String> listCustIdsWithData(LocalDate dataDate);
}
```

> **2026-07-19 代码核实**：源码 `MetricApi` 比本节原文档多 4 个方法（上方已补入代码块）：`getLatestDataDate`（报表/仪表盘取最新数据日期）、`listEmpIdsWithData`/`listOrgCodesWithData`/`listCustIdsWithData`（动态指标查询"不选对象=查全部"场景的对象集合来源，均为 `report-analytics-center` 消费）。

### MetricApi 调用约束

| 规则 | 说明 |
|---|---|
| 频率 | `getUserMetricCards` 高频, 建议消费端缓存 5 分钟 |
| 批量 | `getEmpMetricValues` 单次 metricCodes 不超过 100 条 |
| 数据范围 | 不强制应用 `DATA_SCOPE`, 由调用方自己保证传入合法 empId (通常 portal 已基于登录上下文过滤) |
| 版本 | 所有查询以 `sys_control is_valid=1` 的版本为准 |
| 空值 | 返回 `Map` 时, 指标值为 `null` 的条目不出现在 map 中, 调用方自行处理缺失 |

#### 返回卡片分组语义（V1.5 P3.1 更新）

- 同一 metricCode 被多个 KPI 方案共享且 cycleType 不同时，返回多张卡片（每个 cycleType 一张）
- 前端须按 (metricCode, cycleType) 作为唯一显示键；MetricCardDTO 未新增 cycleType 字段，保持既有契约
- 未来若需显式区分 cycleType，以新字段 + 向后兼容方式引入

---

## 2. MetricQueryApi (批量高性能查询)

**接口路径:** `com.bank.branch.platform.performance.api.MetricQueryApi`

**调用方:** `report-analytics-center` 专用

**职责:** 报表模块的大批量 / 跨日期范围的指标查询, 与 `MetricApi` 区别在于该接口允许指定任意历史版本、支持一次查多日多人的矩阵结构。

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import java.time.LocalDate;
import java.util.List;

/**
 * 报表模块专用的指标批量查询 API.
 */
public interface MetricQueryApi {

    /**
     * 批量查询员工指标快照.
     *
     * <p><b>V1.3 交付（2026-04-24 Task R2.2 实际实现）：</b>
     * <ul>
     *   <li>支持单日点查询（dateFrom == dateTo），跨日期区间查询留 V1.4 迭代</li>
     *   <li>按 metricCode 循环调用 {@code EmpIndexResultMapper.selectSlotValuesByEmps(empIds, dataDate, version, slot)}</li>
     *   <li>version 从 {@code sys_control(EMP)} 当前基线版本读取（调用方无法指定）</li>
     *   <li>返回结构：每个 empId 对应一个 {@code EmpMetricSnapshotDTO}，内含 metricCode → BigDecimal 映射</li>
     *   <li>过滤 baseDim != EMP 与未分配 val_slot 的指标定义（同时过滤 null metricValue）</li>
     * </ul>
     *
     * @param empIds      员工 ID 列表, 最多 500 个（超限抛 PERF-40002 BATCH_QUERY_EXCEEDS_LIMIT）
     * @param dateFrom    起始日期（V1.3 必须等于 dateTo）
     * @param dateTo      结束日期（V1.3 必须等于 dateFrom）
     * @param metricCodes 指标编码, 最多 50 个（超限抛 PERF-40002 BATCH_QUERY_EXCEEDS_LIMIT）
     * @return 快照列表, 输入 empIds 顺序保留
     */
    List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(
        List<String> empIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);

    /**
     * 批量查询机构指标快照.
     *
     * <p><b>V1.3 交付（2026-04-24 Task R2.3 实际实现）：</b>
     * <ul>
     *   <li>与 {@link #batchQueryEmpSnapshots} 同构，baseDim 过滤 ORG，version 取 sys_control(ORG)</li>
     *   <li>Mapper 使用 {@code OrgIndexResultMapper.selectSlotValuesByOrgs}</li>
     * </ul>
     */
    List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(
        List<String> orgCodes, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);

    /**
     * 批量查询客户指标快照.
     *
     * <p><b>V1.3 交付（2026-04-24 Task R2.4 实际实现）：</b>
     * <ul>
     *   <li>与 {@link #batchQueryEmpSnapshots} 同构，baseDim 过滤 CUST，version 取 sys_control(CUST)</li>
     *   <li>Mapper 使用 {@code CustIndexResultMapper.selectSlotValuesByCusts}</li>
     * </ul>
     */
    List<CustMetricSnapshotDTO> batchQueryCustSnapshots(
        List<String> custIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);
}
```

**约束:**

- 该接口绕过缓存, 直连数据库 (从库)
- V1.3 批量上限细化：subject（empIds/orgCodes/custIds）≤ 500，metricCodes ≤ 50，任一超限均抛 `PERF-40002 BATCH_QUERY_EXCEEDS_LIMIT`
- V1.3 时间窗口简化：dateFrom 必须等于 dateTo（单日点查询），跨日期矩阵查询留 V1.4
- 只读接口, 不需要事务

---

## 3. KpiApi (KPI 查询)

**接口路径:** `com.bank.branch.platform.performance.api.KpiApi`

**调用方:** `portal-content-center`, `report-analytics-center`

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * KPI 查询对外 API.
 */
public interface KpiApi {

    /**
     * 获取员工当前周期的 KPI 总分.
     * 返回 sys_control is_valid=1 版本下的最新 KPI 结果.
     *
     * @param empId     员工工号
     * @param cycleType MONTHLY / QUARTERLY / YEARLY
     * @return KPI 总分, 不存在时返回 null
     */
    BigDecimal getCurrentKpiTotal(String empId, String cycleType);

    /**
     * 获取员工当前周期的 KPI 结果 (含明细).
     */
    Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType);

    /**
     * 获取员工 KPI 历史结果.
     *
     * @param empId     员工工号
     * @param cycleType 周期类型
     * @param from      起始 cycle_date
     * @param to        结束 cycle_date
     */
    List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to);

    /**
     * 获取 KPI 方案定义.
     *
     * @param schemeCode 方案编码
     */
    Optional<KpiSchemeDTO> getKpiScheme(String schemeCode);

    /**
     * 按 ID 获取 KPI 方案.
     */
    Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId);

    /**
     * V1.14：列举近期被考核过的员工工号（来自 kpi_result distinct emp_id）.
     *
     * <p>用途：报表「动态指标查询」页面 → 考核员工选择器数据源。本方法是为避免
     * report-analytics-center 跨库直连 perf 物理表而暴露的对外只读 Api，调用方必须先通过
     * auth.OrgApi.getOrgSubtreeCodes(currentOrg) 完成数据范围裁剪后再传 orgCodes。
     *
     * <p>语义：sinceDate 必填，为 null 时返回空列表（fail-close）；orgCodes 为 null 表示
     * 不限机构（管理员场景）；orgCodes 为 empty set 表示数据范围裁剪后无可见机构 → 返回空列表（fail-close）。
     *
     * @param sinceDate 仅返回 as_of_date >= sinceDate 的员工（建议 today.minusDays(90)）
     * @param orgCodes  机构子树过滤；null 表示不限
     * @return 去重后的 empId 列表（按 empId 升序），永不返回 null
     */
    List<String> listEvalEmpIds(LocalDate sinceDate, java.util.Set<String> orgCodes);
}
```

> **2026-07-19 代码核实（破坏性差异）**：源码 `KpiApi` **不存在**本节原文档描述的 `batchGetKpiTotals(List<String> empIds, String cycleType, LocalDate cycleDate)` 方法——全仓检索该方法名零命中，属历史设计从未落地或已被移除。取而代之的是上方 `listEvalEmpIds(LocalDate sinceDate, Set<String> orgCodes)`（V1.14 新增，语义完全不同：列举近期被考核员工工号供报表选择器用，而非批量取总分）。消费方如需"批量获取某组员工 KPI 总分"，当前只能循环调用 `getCurrentKpiTotal`，本契约暂无批量方法，如有性能需求需在源码新增后回填本文档。

**约束:** `listEvalEmpIds` 的 `sinceDate` 必填（fail-close 设计）；`orgCodes` 由调用方基于 `OrgApi.getOrgSubtreeCodes` 完成数据范围裁剪后传入。

---

## 4. TargetApi (目标查询)

**接口路径:** `com.bank.branch.platform.performance.api.TargetApi`

**调用方:** `report-analytics-center`, `portal-content-center`

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 目标查询对外 API.
 */
public interface TargetApi {

    /**
     * 获取目标方案.
     */
    Optional<TargetPlanDTO> getTargetPlan(String planCode);

    /**
     * 按 ID 获取目标方案.
     */
    Optional<TargetPlanDTO> getTargetPlanById(String planId);

    /**
     * 获取特定主体在特定周期的目标值.
     *
     * @param planId      目标方案 ID
     * @param subjectType EMP / ORG
     * @param subjectId   主体 ID
     * @param cycleKey    周期, 如 2026 / 2026Q2 / 202604
     * @param metricCode  指标编码
     * @return 目标值, 不存在时 empty
     */
    Optional<BigDecimal> getTargetValue(
        String planId, String subjectType, String subjectId, String cycleKey, String metricCode);

    /**
     * 批量查询某主体在某周期的所有目标值.
     *
     * @param planId      目标方案 ID
     * @param subjectType EMP / ORG
     * @param subjectId   主体 ID
     * @param cycleKey    周期
     */
    List<TargetValueDTO> listTargetValues(String planId, String subjectType, String subjectId, String cycleKey);
}
```

---

## 5. PerfCalcApi (计算触发)

**接口路径:** `com.bank.branch.platform.performance.api.PerfCalcApi`

**调用方:** `system-governance-center` (定时任务回调), 自身内部 (历史回算)

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 绩效计算触发对外 API.
 * 仅允许运维/定时任务模块调用.
 */
public interface PerfCalcApi {

    /**
     * 触发某方案的 KPI 批量计算（V1.3 R2.1 实际实现）.
     *
     * <p><b>2026-04-24 V1.3 签名变更（破坏性升级）：</b>
     * <ul>
     *   <li>V1.0 占位签名：{@code String triggerKpiCalc(LocalDate dataDate)} 抛 UOE</li>
     *   <li>V1.3 真实签名：{@code int triggerKpiCalc(String schemeCode, String cycleType, LocalDate cycleDate, LocalDate asOfDate, String version)}</li>
     * </ul>
     *
     * <p>V1.3 交付方式：直接委托 {@code KpiCalcService.calcScheme}，对方案内所有员工计算 KPI 并写入 {@code kpi_result}。
     * 返回本次批量计算成功的员工数（int），不再返回 run_task ID。
     *
     * <p><b>双入口共存：</b>与 {@code KpiApi.triggerKpiCalc} 语义完全相同，两者均可使用，消费方自行选择。
     * 保留双入口是为了避免破坏 V1.0/V1.1/V1.2 既有 04 契约文档（PerfCalcApi 一直声明了该方法，只是实现抛 UOE）。
     *
     * @param schemeCode KPI 方案编码（必填）
     * @param cycleType  周期类型（MONTHLY / QUARTERLY / YEARLY）
     * @param cycleDate  周期对应日期
     * @param asOfDate   计算基准日（对齐宽表 data_date）
     * @param version    数据版本（对齐宽表 version）
     * @return 本次批量计算成功的员工数
     */
    int triggerKpiCalc(String schemeCode, String cycleType,
                       LocalDate cycleDate, LocalDate asOfDate, String version);

    /**
     * 触发单个指标的计算（V1.1 Task P3.3 交付）.
     *
     * <p>内部委托 {@code MetricCalcService.calcMetric} 完成 SQL/Groovy 路由、宽表写入、run_task 状态机。
     * 消费方：定时任务（如每日指标计算 job）、运维后台补跑、external 上报触发。
     *
     * @param metricCode 指标编码（必填）
     * @param dataDate   数据日期（必填）
     * @param version    数据版本（必填，通常取自 sys_control.current_version）
     * @return run_task 主键 ID
     * @throws IllegalArgumentException 任一参数为 null
     */
    String triggerMetricCalc(String metricCode, LocalDate dataDate, String version);

    /**
     * 触发历史回算（5 参数简化签名）.
     *
     * <p>等价于下方 7 参数版本传入 metricCodes=null（所有 ACTIVE 指标）且 version=null
     * （Facade 兜底按空串透传）。保留此签名以兼容旧契约；新接入方推荐使用 7 参数版本。
     *
     * @param cycleType 周期类型
     * @param from      起始 cycleDate
     * @param to        结束 cycleDate
     * @param reason    原因 (审计)
     * @param operator  操作人
     * @return 任务 ID
     */
    String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator);

    /**
     * 触发历史回算（7 参数完整签名，2026-07-19 补充，原文档未收录）.
     *
     * <p>委托 {@code HistoryRecalcService.recalc}，按日期范围 × 指标码批量调用 MetricCalcService.calcMetric。
     *
     * @param cycleType   周期类型（用于审计日志；实际按日切分，不按 cycle）
     * @param from        起始日期（含）
     * @param to          截止日期（含）
     * @param metricCodes 指标编码列表（null 或空 → 所有 ACTIVE 指标）
     * @param version     数据版本（必填）
     * @param reason      回算原因（@AuditLog reasonRequired=true）
     * @param operator    发起人 emp_id
     * @return 父级 run_task 主键
     */
    String triggerRecalc(String cycleType, LocalDate from, LocalDate to,
                         java.util.List<String> metricCodes, String version,
                         String reason, String operator);

    /**
     * 查询运行任务状态.
     *
     * @param taskId 任务 ID
     */
    Optional<PerfRunTaskDTO> getRunTask(String taskId);
}
```

> **2026-07-19 代码核实**：源码 `PerfCalcApi` 比本节原文档多 2 处：① `triggerMetricCalc`（单指标计算触发，V1.1 P3.3 交付，上方已补入代码块）；② `triggerRecalc` 的 7 参数重载（原文档只收录 5 参数版本，两者并存，5 参数版本内部委托 7 参数版本）。另需订正：源码 Javadoc 声称"与 `KpiApi.triggerKpiCalc` 语义相同，双入口均可使用"，但**实际 `KpiApi` 接口中不存在 `triggerKpiCalc` 方法**（详见 §3 订正说明），该 Javadoc 本身是过期表述，`triggerKpiCalc` 目前只有 `PerfCalcApi` 这一个入口。

---

## 6. DataTaskApi (外部数据任务上报)

**接口路径:** `com.bank.branch.platform.performance.api.DataTaskApi`

**调用方:** 内部 `DataTaskController` (REST 层), 最终 REST 接口由独立数据同步系统调用

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;

/**
 * 外部数据任务状态上报 API.
 */
public interface DataTaskApi {

    /**
     * 接收外部数据同步任务的完成状态.
     * 幂等, 同一 taskId 重复上报返回相同结果.
     *
     * @param cmd 上报命令
     * @return 受理结果 (taskId / accepted / perfRunTaskId)
     */
    DataTaskReportResultDTO reportDataTaskStatus(DataTaskStatusCmd cmd);
}
```

**DataTaskReportResultDTO 字段:**

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | `String` | 外部系统上报的 taskId（与入参一致） |
| `accepted` | `boolean` | 首次受理为 true，重复上报返回 false（幂等） |
| `perfRunTaskId` | `String` | 本次落库的 perf_run_task.id（幂等场景下为已有记录 ID） |

**返回契约修订（V1.2 Q8.2 同步）：** V1.1 原签名为 `void`，V1.2 执行期补充为 `DataTaskReportResultDTO`，以便外部系统在重复上报时拿到既有的 `perfRunTaskId` 而无需再查询。

---

## 7. AllocApi (分配关系查询)

**接口路径:** `com.bank.branch.platform.performance.api.AllocApi`

**调用方:**
- `customer-marketing-center` — 客户详情页展示当前分配关系
- `report-analytics-center` — 跨维度分配分析、员工 KPI 归集
- `business-application-center` — 资产投放审批时校验申请人与客户的分配关系

**调用约束:**
- 所有方法为**只读同步**调用，P95 < 50 ms
- 单次 `custIds` / `empIds` 批量上限 **500**
- `bizKind = null` 表示跨业务种类汇总，`""` 表示无效参数（抛 `PERF-40002`）
- 查询当前有效分配时，自动从 `sys_control` 取最新已发布版本
- 历史分配查询不走缓存，直接查库 + 按版本过滤

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户业绩分配关系查询 API.
 *
 * <p>职责边界:
 * <ul>
 *   <li>只读查询当前或历史分配关系，不提供任何写操作</li>
 *   <li>所有写操作（新增/调整分配）通过内部 AllocAdjustService 经工作流审批</li>
 *   <li>数据源为 cust_alloc_relation 表 + sys_control 版本控制</li>
 * </ul>
 *
 * <p>数据范围: 调用方自行处理 DATA_SCOPE，本 Api 不做数据范围过滤（由上层 @BizAuth 保证）。
 */
public interface AllocApi {

    /* ==================== 基础查询 ==================== */

    /**
     * 查询客户当前有效的分配关系.
     *
     * @param custId   客户 ID，不可为 null
     * @param bizKind  业务种类，null 表示全部
     * @return 分配关系列表，可能为空列表，不会返回 null
     * @throws IllegalArgumentException custId 为空
     */
    List<CustAllocRelationDTO> getCurrentAllocations(String custId, String bizKind);

    /**
     * 查询客户在指定日期的分配关系 (历史快照).
     *
     * @param custId    客户 ID
     * @param asOfDate  查询截止日期（含当天）
     * @return 分配关系列表（基于 asOfDate 时点的最新已发布版本）
     */
    List<CustAllocRelationDTO> getAllocationHistory(String custId, LocalDate asOfDate);

    /**
     * 查询某员工名下当前负责的客户列表 (当前有效分配).
     *
     * @param empId    员工工号
     * @param bizKind  业务种类，null 表示全部
     * @return 该员工当前负责的所有分配关系
     */
    List<CustAllocRelationDTO> listCustomersByEmp(String empId, String bizKind);

    /* ==================== 批量查询（供 report / customer 调用）==================== */

    /**
     * 批量查询多个客户的当前分配关系.
     *
     * <p>用于客户列表页展示"当前分配人"字段，避免 N+1 查询。
     *
     * @param custIds  客户 ID 集合，不可为空，上限 500
     * @param bizKind  业务种类，null 表示全部
     * @return Key=custId, Value=该客户的分配关系列表；缺失客户不在 Map 中
     * @throws IllegalArgumentException custIds 为空或超限
     */
    Map<String, List<CustAllocRelationDTO>> batchGetCurrentAllocations(Set<String> custIds, String bizKind);

    /**
     * 批量查询多个员工名下的客户数汇总.
     *
     * <p>用于员工工作台展示"我名下客户 N 户"。
     *
     * @param empIds  员工工号集合，上限 500
     * @return Key=empId, Value=客户数（去重后）
     */
    Map<String, Long> countCustomersByEmps(Set<String> empIds);

    /**
     * 批量查询多个员工的分配关系汇总 (供 report 聚合报表).
     *
     * @param empIds   员工工号集合，上限 500
     * @param bizKind  业务种类
     * @param asOfDate 截止日期，null 表示当前最新
     * @return 按员工汇总的分配概况
     */
    List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate);

    /* ==================== 快速判定 ==================== */

    /**
     * 判断某员工对某客户是否存在当前有效的分配关系.
     *
     * <p>用于 business-application-center 校验"申请人是否有权为该客户发起资产投放申请"。
     *
     * @param empId   员工工号
     * @param custId  客户 ID
     * @param bizKind 业务种类，null 表示任一业务种类匹配即视为有效
     * @return true=存在有效分配，false=无分配
     */
    boolean hasAllocation(String empId, String custId, String bizKind);

    /**
     * 统计某员工当前负责的客户总数.
     *
     * @param empId    员工工号
     * @param bizKind  业务种类，null 表示全部
     * @return 客户数（去重）
     */
    long countCustomersOfEmp(String empId, String bizKind);

    /* ==================== 版本查询 ==================== */

    /**
     * 查询当前最新的分配关系版本号.
     *
     * <p>返回 sys_control.current_version (scope_dim='CUST', bizKind 过滤)。
     * 用于前端做版本感知刷新 / report 判定报表数据是否已更新。
     *
     * @param bizKind 业务种类
     * @return 当前版本号（格式 v1/v2/...），若无任何版本返回 null
     */
    AllocVersionDTO getLatestAllocVersion(String bizKind);

    /**
     * 查询某时间点生效的分配关系版本号.
     *
     * @param bizKind   业务种类
     * @param asOfDate  查询时点
     * @return 该时点的版本号信息
     */
    AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate);

    /* ==================== 原业绩分配预览（调整申请页面，2026-07-19 补充） ==================== */

    /**
     * 查询客户「原业绩分配」预览：分别取「按规则分配(RULE)」与「按账号分配(ACCOUNT)」两个维度下
     * 审批通过(APPROVED)的最后一条分配关系调整申请，关联其调整明细返回.
     *
     * <p>数据源为 {@code PERF_ALLOC_ADJUST_APPLY} + {@code PERF_ALLOC_ADJUST_ITEM}（非
     * {@code cust_alloc_relation}），供审批/新增调整申请页面的「原业绩分配」模块展示。
     *
     * @param custId   客户编号（匹配 apply.cust_id）
     * @param allocDim 当前申请的分配维度（RULE / ACCOUNT / null，null 或 RULE 取 RULE+ACCOUNT 两者）
     * @return 预览项列表，可能为空列表，不会返回 null
     */
    List<AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custId, String allocDim);
}
```

> **2026-07-19 代码核实**：源码 `AllocApi` 实际比本节原文档多 1 个方法 `getLastApprovedAllocPreview`（上方已补入代码块），供审批/新增调整申请页面展示"原业绩分配"预览，与 `cust_alloc_relation`（生产分配关系）不同，反映的是"最近一次审批通过的调整申请"快照。对应 DTO `AllocAdjustPreviewItemDTO` 见 §7.4。

### 7.1 AllocApi 补充 DTO

以下 DTO 供 §7 AllocApi 使用，统一放在 `com.bank.branch.platform.performance.api.dto` 包：

#### `AllocSummaryDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `empId` | String | 员工工号 |
| `empName` | String | 员工姓名（冗余展示） |
| `orgCode` | String | 员工所在机构 |
| `bizKind` | String | 业务种类 |
| `custCount` | Long | 负责客户总数（去重） |
| `totalAllocAmount` | BigDecimal | 分配金额汇总（DECIMAL(20,4)） |
| `avgAllocRatio` | BigDecimal | 平均分配比例（DECIMAL(10,4)） |
| `asOfDate` | LocalDate | 数据基准日 |
| `sysControlVersion` | String | 数据版本号 |

#### `AllocVersionDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `bizKind` | String | 业务种类 |
| `scopeDim` | String | 固定 `CUST` |
| `currentVersion` | String | 当前版本号，格式 `v{N}` |
| `latestDataDate` | LocalDate | 版本生效日 |
| `publishedAt` | LocalDateTime | 版本发布时间 |
| `publishedBy` | String | 发布人工号 |

### 7.2 AllocApi 调用示例

```java
// customer-marketing-center 客户详情页调用
@Service
public class CustomerDetailService {
    @Autowired AllocApi allocApi;

    public CustomerDetailVO getDetail(String custId) {
        CustomerDetailVO vo = buildBase(custId);
        List<CustAllocRelationDTO> allocs = allocApi.getCurrentAllocations(custId, null);
        vo.setCurrentManagers(allocs.stream()
            .map(a -> new ManagerVO(a.getEmpId(), a.getEmpName(), a.getAllocRatio()))
            .collect(Collectors.toList()));
        return vo;
    }
}

// report-analytics-center 批量聚合
@Service
public class OrgPerfReportService {
    @Autowired AllocApi allocApi;

    public List<EmpPerfVO> getOrgPerfReport(String orgCode, LocalDate date) {
        List<String> empIds = empQueryApi.listEmpsByOrg(orgCode);
        List<AllocSummaryDTO> summaries = allocApi.batchSummaryByEmps(
            Set.copyOf(empIds), null, date);
        return summaries.stream().map(this::toVO).collect(Collectors.toList());
    }
}

// business-application-center 申请前校验
@Service
public class LoanSubmitService {
    @Autowired AllocApi allocApi;

    public void checkAllocBeforeSubmit(String empId, String custId) {
        if (!allocApi.hasAllocation(empId, custId, "CORPORATE_LOAN")) {
            throw new BizException("BIZ-40301", "您未分配该客户的资产投放权限");
        }
    }
}
```

### 7.3 AllocApi 缓存与性能

| 方法 | 频率 | 缓存策略 | P95 |
|---|---|---|---|
| `getCurrentAllocations` | 高（客户详情页） | Redis 10 min，key=`alloc:cur:{custId}:{bizKind}`；`allocation-adjustment.approved.v1` 事件触发失效 | < 20 ms |
| `getAllocationHistory` | 低 | 不缓存 | < 100 ms |
| `listCustomersByEmp` | 中 | Redis 15 min，key=`alloc:emp:{empId}:{bizKind}` | < 30 ms |
| `batchGetCurrentAllocations` | 中（列表页） | 逐条走单客户缓存 + 未命中走批量查询 | < 100 ms |
| `countCustomersByEmps` | 中（工作台） | Redis 30 min，key=`alloc:empcount:{empIds hash}:{bizKind}` | < 50 ms |
| `batchSummaryByEmps` | 低（报表） | 不缓存，直查库 + `asOfDate` 参数化 | < 300 ms |
| `hasAllocation` | 高（申请前校验） | 继承 `getCurrentAllocations` 的缓存 | < 10 ms |
| `countCustomersOfEmp` | 中 | Redis 15 min | < 20 ms |
| `getLatestAllocVersion` | 高 | Redis 5 min（与 sys_control 同步） | < 5 ms |
| `getAllocVersionAt` | 低 | 不缓存 | < 50 ms |

**缓存失效触发**：
- 监听 `performance.allocation-adjustment.approved.v1` 事件 → 失效相关 `alloc:*` 缓存
- 监听 `performance.sys-control.updated.v1` 事件 → 失效 `alloc:latestver:*` 缓存

### 7.4 AllocApi 原业绩分配预览方法与 DTO（2026-07-19 补充，代码核实）

**方法：** `List<AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custId, String allocDim)`

**调用方：** `performance-engine-center` 内部（`AllocAdjustController`/`TargetAdjustController` 等新建调整申请页面），当前未见其他模块消费，暂列为模块内可复用能力

**`AllocAdjustPreviewItemDTO` 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `allocDim` | String | 分配维度：RULE（按规则分配）/ ACCOUNT（按账号分配） |
| `accountNo` | String | 账号（ACCOUNT 维度取 apply.account_no；RULE 维度为空） |
| `empId` | String | 员工工号（item.emp_id） |
| `username` | String | 员工登录名（PT_USER.USERNAME；解析不到时回退为工号） |
| `empChnName` | String | 员工中文姓名（PT_USER.USERCHNNAME） |
| `orgCode` | String | 所属机构号（员工主机构 ORG_CODE） |
| `orgName` | String | 所属机构名称（员工主机构 ORG_NAME） |
| `ratio` | BigDecimal | 分配比例（0-100） |

---

## 8. CustStatQueryApi (客户财务统计展示表只读查询，2026-07-19 新增)

**接口路径:** `com.bank.branch.platform.performance.api.CustStatQueryApi`

**调用方:** `soap-gateway-center`（callpu 网关 `CASH_GETCUST_INFO` 客户号查名场景）

**职责:** 面向外部渠道的客户号查名入口。数据源 `XAN_M98_CUST_STAT_SHOW3`（外部数仓抽数落地表，由 `performance-engine-center` 持有），跨模块调用方仅通过本 `*Api` 访问，禁止直连其 mapper/表。

```java
package com.bank.branch.platform.performance.api;

import java.util.Optional;

/**
 * 客户财务统计展示表（XAN_M98_CUST_STAT_SHOW3）对外只读查询 Api.
 */
public interface CustStatQueryApi {

    /**
     * 按客户号查客户名称（XAN_M98_CUST_STAT_SHOW3，取一条）.
     *
     * @param custId 客户号（对应 CUST_ID 列；外部渠道前端输入）
     * @return 客户名称；custId 为空或查无匹配时返回 Optional.empty()
     * @deprecated 客户号查名已统一改走客户主档 CUST_MASTER，请使用 {@link #getCustNameFromMaster(String)}
     *             （与 PC 管理端 StatShowController#getCustMasterName 同一口径，见 03 §P.3）。
     */
    @Deprecated
    Optional<String> getCustNameByCustId(String custId);

    /**
     * 按客户编号从客户主档 CUST_MASTER 查客户名称（cust_master.cust_no 列）.
     *
     * <p>替代原 {@link #getCustNameByCustId(String)} 从外部统计表取名的方式，与 PC 管理端
     * "新建调整申请页客户名称反显"（03 §P.3）走同一客户营销中心 {@code CustomerQueryApi.getCustomerByCustNo} 口径。
     * custNo 为空、客户主档无该编号或名称为空白均返回 Optional.empty()（永不返回空白名）。
     *
     * @param custNo 客户编号（cust_master.cust_no；外部渠道前端输入）
     * @return 客户名称（去空白后非空）；客户主档无匹配时返回 Optional.empty()
     */
    Optional<String> getCustNameFromMaster(String custNo);
}
```

**调用约束：** 两方法均只读同步；`getCustNameByCustId` 已废弃但未删除（兼容存量调用方），新对接一律使用 `getCustNameFromMaster`。

---

## 9. EvalQueryApi (eval 考核评价子域对外只读查询，2026-07-19 新增)

**接口路径:** `com.bank.branch.platform.performance.api.EvalQueryApi`

**调用方:** 供 `report-analytics-center` 未来引用（当前未见实际消费方，属预留对外能力）

**实现类:** `com.bank.branch.platform.performance.eval.facade.EvalQueryFacade`

**职责:** eval 考核评价子域（见 03 §S）唯一对外只读入口，禁止被业务模块写操作依赖。

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.EvalTaskSummaryDto;
import java.util.List;

/**
 * 评价查询对外 API（供 report-analytics-center 未来引用）.
 * 只读接口，禁止被业务模块写操作依赖。
 */
public interface EvalQueryApi {

    /**
     * 查询所有评价任务概要.
     *
     * @return 任务概要列表（含 taskId、taskName、status、endTime、targetCount）
     */
    List<EvalTaskSummaryDto> listTaskSummaries();
}
```

**`EvalTaskSummaryDto` 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | Long | 任务ID |
| `taskName` | String | 任务名称 |
| `status` | Integer | 任务状态（0=进行中，1=已结束） |
| `endTime` | LocalDateTime | 截止时间 |
| `targetCount` | int | 被评价人数量 |

---

## 10. PerfApprovalCmdApi (分配关系调整审批「写」，2026-07-19 新增)

**接口路径:** `com.bank.branch.platform.performance.api.PerfApprovalCmdApi`

**调用方:** `soap-gateway-center` 的 `CallPuDispatchService`（面向手机端等外部渠道，callpu 操作码 `PERF_SAVE`=新增申请、`PERF_RECALL`=撤回申请、`PERF_APPR`=审批申请）

**职责:** 面向手机端等外部渠道提交/撤回/审批「分配关系调整」申请，业务规则与 Web 管理端 `POST /api/perf/alloc-adjust/create`（03 §L.1）完全一致，实现类 `PerfApprovalCmdFacade` 委托项目内已有的 `AllocAdjustService`。与 `PerfApprovalQueryApi`（§11）一起构成对外渠道的审批读写入口，调用方禁止直接依赖 perf 的 service/mapper/entity。

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;

public interface PerfApprovalCmdApi {

    /**
     * 提交「分配关系调整」申请（启动审批流）.
     *
     * @param cmd 提交命令（applicant 必填；ownerOrgId 可空，空则按 applicant 反查主机构）
     * @return 申请主键 id（手机端 perfAdjustNo）
     */
    String submitAllocAdjust(AllocAdjustSubmitCmd cmd);

    /**
     * 撤回「分配关系调整」申请（带越权校验，同步取消审批流）.
     *
     * <p>仅申请创建人本人可撤回（operator 必须等于申请创建人），委托
     * {@code AllocAdjustService.withdrawByApplicant}。仅 IN_APPROVAL / DRAFT 状态可撤回。
     *
     * @param perfAdjustNo 申请主键 id（手机端 perfAdjustNo）
     * @param operator     操作人工号（外部渠道传入，必须等于申请创建人）
     * @param reason       撤回原因（可空，空则由实现兜底默认文案）
     */
    void withdrawAllocAdjust(String perfAdjustNo, String operator, String reason);

    /**
     * 审批「分配关系调整」申请（通过 / 驳回，无会话版）.
     *
     * <p>面向手机端等外部渠道（callpu PERF_APPR）：按 perfAdjustNo + 审批人 empId 解析当前待办任务——
     * 候选组/角色可见性即权限校验，审批人看不到该待办（查不到 taskId）则拒绝审批。再按 apprStatus
     * 走通过/驳回，委托 workflow 无会话审批，全程不依赖登录会话。
     *
     * <p>下一步路由（routeTo）：仅在「同意」且当前节点是含排他网关的经办节点时生效——
     * {@code biz_dept_review}（公司部/零售部经办）→ 网关变量 corpRouteTo：LEADER（交部门负责人）/
     * OWNER（交原业绩所属人会签）；{@code finance_review}（资财部经办）→ 网关变量 finRouteTo：
     * LEADER（交资财部负责人）/ END（审批结束）。其余节点忽略 routeTo；经办节点未传 routeTo 时
     * 按默认走最全链路（corp→OWNER、fin→LEADER），保持老渠道兼容不卡流程。
     *
     * @param perfAdjustNo 申请主键 id（手机端 perfAdjustNo）
     * @param empId        审批人工号（外部渠道认证后透传）
     * @param apprStatus   审批结论："1"=通过 / "2"=驳回
     * @param opinion      审批意见（可空，空则由实现兜底默认文案）
     * @param routeTo      经办节点下一步路由选择（可空；非经办节点/驳回时忽略）
     * @throws IllegalStateException    审批人无该待办任务（无权审批/已被处理）
     * @throws IllegalArgumentException apprStatus 非 "1"/"2"
     */
    void approveAllocAdjust(String perfAdjustNo, String empId, String apprStatus, String opinion, String routeTo);
}
```

**`AllocAdjustSubmitCmd` 字段（与 `AllocAdjustCreateReqDTO` 语义一致，差异见下）：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `custType` | String | CORP / RETAIL（决定审批流路由） |
| `custId` | String | 客户编号 |
| `custName` | String | 客户名称（前端反显） |
| `allocDim` | String | RULE / ACCOUNT |
| `bizKind` | String | 业务种类（如 CORP_DEPOSIT / RETAIL_LOAN） |
| `accountNo` | String | 账号（ACCOUNT 维度必填） |
| `ownerOrgId` | String | 归属机构（**可空**——为空时由 Facade 按 applicant 反查主机构，这是与管理端 `AllocAdjustCreateReqDTO.ownerOrgId` 必填的关键差异） |
| `reason` | String | 申请原因 |
| `applicant` | String | 申请人工号（**外部渠道显式传入**，因无平台登录态，管理端由 `CurrentUserApi` 隐式获取） |
| `items` | List\<Item\> | 调整后明细：`empId`/`ratio`/`remark` |
| `originalAllocList` | List\<OriginalItem\> | 原业绩分配（手工录入）：`acctNo`/`empId`/`username`/`empChnName`/`orgCode`/`orgName`/`ratio`；外部渠道仅采集工号/姓名/比例，`acctNo`/`orgCode`/`orgName` 可空（Service 对缺失机构留空、按工号反查补全） |

**调用约束：** `submitAllocAdjust` 委托的落库/审批流路由规则与 Web 端 `AllocAdjustController.create` 完全一致（对公走 `perf_alloc_adjust_corp_v1`，零售走 `perf_alloc_adjust_retail_v1`）；`approveAllocAdjust` 是无会话审批，业务侧需自行保证 `empId` 已经过外部渠道认证。

---

## 11. PerfApprovalQueryApi (分配关系调整审批列表「读」，2026-07-19 新增)

**接口路径:** `com.bank.branch.platform.performance.api.PerfApprovalQueryApi`

**调用方:** **`soap-gateway-center` 的 `CallPuDispatchService`**（已在源码中确认消费，callpu 操作码 `PERF_LIST` 拉取审批列表；`import` 语句见 `CallPuDispatchService.java` 第 9-10 行，字段注入见第 84 行 `private final PerfApprovalQueryApi perfApprovalQueryApi;`）

**职责:** 面向手机端等外部渠道，按员工号拉取该员工的分配关系调整审批列表/我的申请列表/单据详情。实现类 `PerfApprovalQueryFacade` 委托项目内已有的 `AllocAdjustTodoService`/`AllocAdjustDoneService`，权限逻辑与 Web 端 `GET /api/perf/alloc-adjust/my-todos`/`my-done`（03 §L.11/L.12）完全一致（底层均走 workflow `TodoQueryApi` 的 Flowable 待办/已办）。与 `PerfApprovalCmdApi`（§10）一起构成对外渠道的唯一审批入口，调用方禁止直接依赖 perf 的 service/mapper/entity。

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustDetailDTO;

public interface PerfApprovalQueryApi {

    /**
     * 查询某员工的「分配关系调整」审批列表（待审批 + 本人已审批，合并为一个列表）.
     *
     * @param empId    员工号（外部渠道传入；上游 callpu 已完成身份认证）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页大小（&lt;1 归一为默认值）
     * @return 合并去重后按申请时间倒序的分页结果；员工无任何待办/已办时返回空页
     */
    PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(String empId, int pageNo, int pageSize);

    /**
     * 按状态域查询审批列表：statusFilter="PENDING" 仅待办、"DONE" 仅已办、null 合并（同 3 参版）.
     *
     * @param empId        员工号（外部渠道传入；上游 callpu 已完成身份认证）
     * @param statusFilter 状态过滤："PENDING" 仅待办，"DONE" 仅已办，null 合并
     * @param pageNo       页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize     每页大小（&lt;1 归一为默认值）
     * @return 按申请时间倒序的分页结果
     */
    PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(
            String empId, String statusFilter, int pageNo, int pageSize);

    /**
     * 查询某员工"作为申请人"提交的「分配关系调整」申请列表（全状态，含 WITHDRAWN）.
     *
     * <p>区别于 {@link #listAllocAdjustApprovals}（审批人视角）：本方法按 createdBy = empId
     * 过滤，供手机端"我的申请"模块使用。
     *
     * @param empId    申请人 USER_ID（网关已把报文工号转为 USER_ID）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页大小（&lt;1 归一为默认值）
     * @return 按申请时间倒序的分页结果
     */
    PageResult<AllocAdjustApprovalItemDTO> listMyAllocAdjustApplications(String empId, int pageNo, int pageSize);

    /**
     * 查询「分配关系调整」单据详情（手机端详情页：含分配明细 + 审批/撤回能力标志）.
     *
     * @param perfAdjustNo 申请主键
     * @param empId        当前操作员 USER_ID（用于判定 canApprove/canDelete）
     */
    AllocAdjustDetailDTO getAllocAdjustDetail(String perfAdjustNo, String empId);
}
```

**`AllocAdjustApprovalItemDTO` 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `perfAdjustNo` | String | 业绩调整审批编号（= 申请主键 id），手机端 perfAdjustNo |
| `applyNo` | String | 申请编号（业务可读编号 apply_no，备用） |
| `custId` | String | 客户ID |
| `custName` | String | 客户名称（CustomerQueryApi 补齐，查不到为 null） |
| `createdBy` | String | 申请人员工号（created_by） |
| `applyFullname` | String | 申请人姓名（通讯录 AddressBookApi 补齐，查不到为 null） |
| `applyTime` | LocalDateTime | 申请时间（created_time） |
| `status` | String | 申请单状态：DRAFT/IN_APPROVAL/APPROVED/REJECTED |
| `category` | String | 渠道分类：TODO=待本人审批，DONE=本人已审批/已驳回 |

**`AllocAdjustDetailDTO` 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `perfAdjustNo` / `applyNo` | String | 同上 |
| `custId` / `custName` | String | 客户信息 |
| `custType` | String | CORP / RETAIL |
| `allocDim` | String | ACCOUNT / RULE |
| `bizKind` / `accountNo` | String | 业务种类 / 账号 |
| `status` | String | DRAFT/IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN |
| `reason` | String | 调整理由（apply.remark） |
| `createdBy` / `applyFullname` / `applyTime` | — | 申请人信息 |
| `canDelete` | boolean | 申请人本人且可撤回（status∈IN_APPROVAL/DRAFT） |
| `canApprove` | boolean | 当前用户的待办且 IN_APPROVAL |
| `currentNode` | String | 当前所处审批节点中文名（IN_APPROVAL 取 Flowable 活动 userTask 名；终态显示"已完成/已拒绝/已撤回/草稿"） |
| `currentNodeKey` | String | 当前活动节点 KEY（如 `biz_dept_review`/`finance_review`），供经办审批端决定"下一步审批"表单 |
| `nextNode` | String | 下一审批节点中文名（按 callPu 硬编码路由的确定链路静态推算；末节点显示"流程结束"，终态显示"无"） |
| `allocaters` | List\<AllocItem\> | 分配明细：`empId`/`username`/`fullname`/`ratio`/`isOriginal`(1=原分配/2=调整后) |

**调用约束：** 全部方法为只读同步调用；权限逻辑与 Web 端待办/已办完全一致（不重新实现一套权限判断）。

---

## 12. DTO 定义

> **2026-07-19 索引补充**：本次新增的 4 个 `*Api`（§8～§11）及 §7 补充方法涉及的 DTO 已随各自章节就地给出字段表，不在此重复：`AllocAdjustPreviewItemDTO` 见 §7.4；`EvalTaskSummaryDto` 见 §9；`AllocAdjustSubmitCmd` 见 §10；`AllocAdjustApprovalItemDTO`/`AllocAdjustDetailDTO` 见 §11。

### 12.1 MetricDefDTO

```java
public class MetricDefDTO {
    private String metricCode;
    private String metricName;
    private String metricNameEn;
    private String description;
    private String baseDim;         // EMP/ORG/CUST
    private Integer metricLevel;    // 1/2/3
    private String calcFreq;        // D/W/M/Q
    private String calcMode;        // AUTO/MANUAL
    private String calcLogicType;   // SQL/PROC/EXPR/SUMMARY
    private Integer valSlot;
    private String status;          // DRAFT/PUBLISHED/DISABLED
    private String ownerDept;
    private Integer version;
    // getter/setter
}
```

### 12.2 MetricCardDTO

```java
public class MetricCardDTO {
    private String metricCode;
    private String metricName;
    private BigDecimal currentValue;
    private BigDecimal previousValue;   // 上一期实际值
    private BigDecimal targetValue;     // 当前周期目标
    private BigDecimal baseValue;
    private BigDecimal achievementRate; // 达成率 %
    private String unit;                // 单位, 万元/笔/人等
    private Integer sortNo;
    private LocalDate dataDate;
    private BigDecimal mom;             // V1.4 S3.1 新增: 环比变化率 (%, 两位小数, null=不适用/上期为 0/未命中)
    private BigDecimal yoy;             // V1.4 S3.1 新增: 同比变化率 (%, 两位小数, null=不适用/去年同期为 0/未命中)
}
```

### 12.3 KpiResultDTO

```java
public class KpiResultDTO {
    private String id;
    private String empId;
    private String empName;
    private String cycleType;       // MONTHLY/QUARTERLY/YEARLY
    private LocalDate cycleDate;
    private LocalDate asOfDate;
    private String dataVersion;
    private String schemeCode;
    private String schemeName;
    private BigDecimal kpiTotalScore;
    private String detailJson;       // 单项得分明细 JSON
    private LocalDateTime createTime;
}
```

### 12.4 KpiSchemeDTO

```java
public class KpiSchemeDTO {
    private String id;
    private String schemeCode;
    private String schemeName;
    private String cycleType;
    private Boolean openDetail;
    private String status;
    private Integer version;
    private List<KpiItemDTO> items;
}

public class KpiItemDTO {
    private String id;
    private String metricCode;
    private String metricName;
    private BigDecimal weight;
    private BigDecimal multiplier;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private Integer sortNo;
}
```

### 12.5 TargetPlanDTO

```java
public class TargetPlanDTO {
    private String id;
    private String planCode;
    private String planName;
    private String kpiSchemeId;
    private String targetDim;
    private String targetCycle;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private String status;
}
```

### 12.6 TargetValueDTO

```java
public class TargetValueDTO {
    private String id;
    private String planId;
    private String subjectType;
    private String subjectId;
    private String cycleKey;
    private String metricCode;
    private BigDecimal targetValue;
    private BigDecimal baseValue;
}
```

### 12.7 CustAllocRelationDTO

```java
public class CustAllocRelationDTO {
    private String id;
    private String custId;
    private String custName;
    private String allocDim;         // RULE/ACCOUNT
    private String bizKind;
    private String accountNo;
    private String empId;
    private String empName;
    private BigDecimal ratio;        // 0.00~100.00
    private LocalDate effectiveDate;
    private LocalDate endDate;
    private String sourceBatchId;
    private LocalDate sourceProcessDate;
}
```

### 12.8 PerfRunTaskDTO

```java
public class PerfRunTaskDTO {
    private String taskId;
    private String taskType;         // METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC/IMPORT
    private String taskKey;          // 业务键, 如 metricCode 或 batchNo
    private LocalDate dataDate;
    private String dataVersion;
    private String paramsJson;
    private String status;           // PENDING/RUNNING/SUCCESS/FAILED/PARTIAL
    private String startedBy;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMsg;
    private String resultPreviewJson;
}
```

### 12.9 EmpMetricSnapshotDTO / OrgMetricSnapshotDTO / CustMetricSnapshotDTO

```java
public class EmpMetricSnapshotDTO {
    private String empId;
    private LocalDate dataDate;
    private String dataVersion;
    private Map<String, BigDecimal> metrics; // metricCode → value
}
```

`OrgMetricSnapshotDTO` 把 `empId` 换成 `orgCode`, `CustMetricSnapshotDTO` 换成 `custId`, 其余结构相同。

### 12.10 Cmd (命令 DTO)

```java
public class DataTaskStatusCmd {
    @NotBlank @Size(max = 128)
    private String taskId;

    @NotBlank
    private String dataType;          // ALLOC_RELATION / EMP_INDEX_RESULT / ORG_INDEX_RESULT / CUST_INDEX_RESULT

    @NotNull
    private LocalDate dataDate;

    @NotBlank @Size(max = 32)
    private String version;

    @NotBlank
    private String status;            // SUCCESS / FAILED

    private Integer rowCount;
    private String errorMsg;
    private String sourceSystem;
    private Instant reportedAt;
}
```

---

## 13. 调用约束

### 13.1 调用方式

所有接口均使用 Spring `@Autowired` 在同一进程内注入 (模块化单体, 非 RPC), 调用耗时按本地方法计。

### 13.2 性能建议

| 接口 | 性能特征 | 建议 |
|---|---|---|
| `MetricApi.getUserMetricCards` | 高频 | 消费端缓存 5 min, 失效事件: `performance.sys-control.updated.v1` |
| `MetricApi.getMetricDef` | 极高频 | 本模块内部有 Caffeine 本地缓存 |
| `MetricQueryApi.batchQuery*Snapshots` | 大批量 | 单次 ≤100000 行, 超过抛异常 |
| `KpiApi.batchGetKpiTotals` | 中频 | 单次 ≤500 empId |
| `AllocApi.listCustomersByEmp` | 中频 | 按 empId+bizKind 做 Redis 缓存 15 min |

### 13.3 数据一致性

- 所有查询以 `sys_control is_valid=1` 为准, 保证跨模块看到同一份数据快照
- 历史回算期间, 短时间可能出现"部分员工已更新、部分未更新"的不一致, 消费方应有容错
- 不同维度 (EMP/ORG/CUST) 的版本独立, 消费方不要假设维度间严格一致

### 13.4 异常

本契约定义的接口不抛检查异常, 运行时错误统一抛 `BizException(PerfErrorCode)`, 调用方用 `try/catch` 或让 `GlobalExceptionHandler` 处理。

### 13.5 审计

调用方发起的操作若需要审计, 审计职责在调用方, 本模块的 API 不会记录外部审计日志 (本模块的 controller/facade 内部仍然有审计)。

---

## 14. 领域事件（V1.2 交付）

本模块发布的领域事件遵循 `docs/common-dev-guide.md` 中的事件规范, 使用 Spring `ApplicationEventPublisher`, 后续可接入 RocketMQ。

V1.2 通过 `PerfEventPublisher` 发布 4 类事件，所有事件继承 `PerfDomainEvent` 抽象基类。基类字段：

```java
public abstract class PerfDomainEvent {
    /** 事件唯一 ID（UUID 去横线，32 字符）. */
    private final String eventId;
    /** 链路 traceId（从 MDC 读取；为 null 时由订阅者自行补全）. */
    private final String traceId;
    /** 事件产生时间. */
    private final LocalDateTime occurredAt;
    /** 事件 topic，格式 performance.<subject>.<verb>.v<version>. */
    public abstract String topic();
}
```

### 14.1 performance.target-adjustment.approved.v1 （V1.2 Q4 交付）

**发布类:** `TargetAdjustmentApprovedEvent`

**发布时机:** 目标修正审批流通过后，BPMN End Event Listener 调用 `TargetAdjustService.completeApproved` 写回 `perf_target_value` + 标记 `perf_target_adjust_apply.STATUS=APPROVED` 事务提交（AFTER_COMMIT 阶段）。

**载荷（V1.2 实际字段）:**

```java
public class TargetAdjustmentApprovedEvent extends PerfDomainEvent {
    // 基类字段：eventId / traceId / occurredAt / topic()

    private final String applyId;          // 目标调整申请 ID（varchar(32)）
    private final String planId;           // 目标方案 ID（varchar(32)）
    private final String subjectType;      // 主体类型：EMP / ORG
    private final String subjectId;        // 主体 ID (emp_id / org_code)
    private final String cycleKey;         // 周期键，形如 "2026Q2" / "202604" / "2026"
    private final String approvedBy;       // 审批通过人 empId
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| 本模块 `HistoryRecalcService` | 触发未冻结年度的 KPI 历史回算（规划 V1.3） |
| `report-analytics-center` | 刷新报表快照 (可选) |
| 配置缓存 `perf:target_value:*` | 失效 |

### 14.2 performance.allocation-adjustment.approved.v1 （V1.2 Q4 交付）

**发布类:** `AllocationAdjustmentApprovedEvent`

**发布时机:** 客户分配调整审批流通过后，BPMN End Event Listener 调用 `AllocAdjustService.completeApproved` 更新 `cust_alloc_relation` + 标记 `perf_alloc_adjust_apply.STATUS=APPROVED` 事务提交（AFTER_COMMIT 阶段）。

**载荷（V1.2 实际字段）:**

```java
public class AllocationAdjustmentApprovedEvent extends PerfDomainEvent {
    // 基类字段：eventId / traceId / occurredAt / topic()

    private final String applyId;          // 调整申请 ID
    private final String custId;           // 客户 ID
    private final String allocDim;         // 分配维度 (OWNER / SERVICE / CHANNEL)
    private final String bizKind;          // 业务种类 (DEPOSIT / LOAN ...)
    private final int itemCount;           // 本次调整涉及的明细条目数
    private final String approvedBy;       // 审批通过人 empId
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| `cust_alloc_relation` 缓存 `alloc:cur:*` / `alloc:his:*` | 失效 |
| `system-governance-center` 通知子域 | 推送通知给资财部, 提醒线下 CCRM/PCRM 操作 |
| 审计 | 留痕（`audit_log` 表） |

### 14.3 performance.kpi-calc.completed.v1 （V1.2 Q4 交付）

**发布类:** `KpiCalcCompletedEvent`

**发布时机:** `KpiCalcService.calcKpi(schemeCode, cycleType, cycleDate)` 成功写入 `kpi_result` 后（AFTER_COMMIT 阶段）。无论是 `DailyKpiCalcJob` 定时触发还是 `HistoryRecalcService` 回算触发。

**载荷（V1.2 实际字段）:**

```java
public class KpiCalcCompletedEvent extends PerfDomainEvent {
    // 基类字段：eventId / traceId / occurredAt / topic()

    private final String schemeCode;       // KPI 方案编码
    private final String cycleType;        // DAY / WEEK / MONTH / QUARTER / YEAR
    private final LocalDate cycleDate;     // 周期日期
    private final LocalDate asOfDate;      // 截止业务日期
    private final String version;          // 使用的 sys_control 版本号
    private final int empCount;            // 落地员工数 (kpi_result 行数)
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| `report-analytics-center` | 重新生成快照数据, 失效相关缓存 |
| `portal-content-center` | 清理工作台 `perf:card:user:*` 缓存 |

### 14.4 performance.sys-control.updated.v1 （V1.2 Q4 交付）

**发布类:** `SysControlUpdatedEvent`

**发布时机:** `SysControlService.doSwitchVersion`（手动 MANUAL）或 `SysControlService.rollback`（回滚 ROLLBACK）执行成功后（AFTER_COMMIT 阶段）。

**载荷（V1.2 实际字段）:**

```java
public class SysControlUpdatedEvent extends PerfDomainEvent {
    // 基类字段：eventId / traceId / occurredAt / topic()

    private final String scopeDim;         // EMP / ORG / CUST / GLOBAL
    private final String oldVersion;       // 切换前的版本号
    private final String newVersion;       // 切换后的版本号
    private final String publishSource;    // MANUAL / AUTO / ROLLBACK
    private final String publishBy;        // 发布人 empId
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| 本模块 `SysControlUpdatedListener` | 失效 Redis 缓存 (指标定义、工作台卡片、KPI 当前分) |
| `report-analytics-center` | 失效报表汇总缓存 |
| `portal-content-center` | 失效工作台聚合缓存 |

### 14.5 事件发布可靠性

- V1.2 使用 Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` 保证事件在事务提交后发布。
- 订阅者抛出异常不回滚主事务，`PerfEventPublisher` 捕获后只记录 ERROR 日志（V1.3 规划：落 `sys_event_dead_letter` 表留痕）。
- V2 迁移到 RocketMQ 时, 发布端使用"本地消息表 + 定时重发"保证最终一致。

---

## 15. 接口注册与暴露

### 15.1 Facade 实现

每个 `*Api` 在 `com.bank.branch.platform.performance.facade` 下有对应实现类, 标注 `@Service` + 实现接口, 被其他模块 `@Autowired`。

### 15.2 @BizAuth 与内部调用

对外 `*Api` 方法**不**走 `@BizAuth` 鉴权, 因为这是模块间的直接调用, 鉴权已在 controller 层完成。由 `*Api` 触发的"跨模块写操作" (如 `triggerKpiCalc`) 默认以"系统"身份执行, 但必须在审计日志中记录调用方。

### 15.3 版本兼容策略

- 本契约版本 V1, 任何字段增加为非破坏性变更
- 删除字段或修改字段类型必须发布新接口方法, 旧方法标记 `@Deprecated` 且保留至少一个小版本
- DTO 新增字段调用方应用"忽略未知字段"策略, 保证向前兼容

---

## 16. 不对外暴露的内部能力

下列能力仅限本模块内部使用, **不**在 `api` 包中定义:

- 指标计算引擎 (`MetricCalcService`)
- Groovy 执行器 (`GroovyExecutorService`)
- SQL 执行器 (`SqlExecutorService`)
- 级联刷新 (`CascadeRefreshService`)
- 导入执行器 (`PerfImportExecutor`)
- 历史回算引擎 (`HistoryRecalcService`)
- 分配调整申请流程 (`AllocAdjustService`)
- 目标修正申请流程 (`TargetAdjustService`)

这些能力的触发必须经由 Controller 或 facade 层, 或由内部事件驱动。
