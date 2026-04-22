# 绩效计算中心 — 对外 API 契约

> 模块编码: `performance-engine-center`
> 关联文档: `01-功能规格.md` / `02-后端架构.md` / `03-接口设计与报文.md`
> 契约层: 所有跨模块调用必须通过本文件定义的 `*Api` 接口, 禁止直接访问本模块的 mapper/entity/serviceImpl
> 契约位置: `com.bank.branch.platform.performance.api.*`

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
     * 返回该员工当前周期可见的全部指标卡片, 包含标题、当前值、同比、达成率.
     * portal-content-center 的工作台首屏聚合入口, 建议缓存 5 分钟.
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
}
```

### MetricApi 调用约束

| 规则 | 说明 |
|---|---|
| 频率 | `getUserMetricCards` 高频, 建议消费端缓存 5 分钟 |
| 批量 | `getEmpMetricValues` 单次 metricCodes 不超过 100 条 |
| 数据范围 | 不强制应用 `DATA_SCOPE`, 由调用方自己保证传入合法 empId (通常 portal 已基于登录上下文过滤) |
| 版本 | 所有查询以 `sys_control is_valid=1` 的版本为准 |
| 空值 | 返回 `Map` 时, 指标值为 `null` 的条目不出现在 map 中, 调用方自行处理缺失 |

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
     * 支持多员工 × 多日期 × 多指标的矩阵查询, 适合报表生成.
     *
     * @param empIds      员工 ID 列表, 最多 1000 个
     * @param dateFrom    起始日期
     * @param dateTo      结束日期
     * @param metricCodes 指标编码
     * @return 快照列表, 每行对应 (empId, dataDate) 组合
     */
    List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(
        List<String> empIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);

    /**
     * 批量查询机构指标快照.
     */
    List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(
        List<String> orgCodes, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);

    /**
     * 批量查询客户指标快照.
     */
    List<CustMetricSnapshotDTO> batchQueryCustSnapshots(
        List<String> custIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);
}
```

**约束:**

- 该接口绕过缓存, 直连数据库 (从库)
- 单次调用行数不超过 `empIds.size() * (dateTo-dateFrom) * metricCodes.size() ≤ 100000`, 超过抛 `PERF-42207`
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
     * 批量获取某组员工在某 cycle 的 KPI 总分.
     * 报表/排行榜用.
     *
     * @param empIds    员工列表
     * @param cycleType 周期类型
     * @param cycleDate cycle 日期
     * @return Map of empId → kpiTotalScore
     */
    java.util.Map<String, BigDecimal> batchGetKpiTotals(
        List<String> empIds, String cycleType, LocalDate cycleDate);
}
```

**约束:** `batchGetKpiTotals` 单次最多 500 个 empId。

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
     * 触发某日的 KPI 计算.
     * 由 system-governance-center 的定时任务每日 02:00 调用.
     *
     * @param dataDate 数据日期
     * @return 任务 ID
     */
    String triggerKpiCalc(LocalDate dataDate);

    /**
     * 触发历史回算.
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
     * 查询运行任务状态.
     *
     * @param taskId 任务 ID
     */
    Optional<PerfRunTaskDTO> getRunTask(String taskId);
}
```

---

## 6. DataTaskApi (外部数据任务上报)

**接口路径:** `com.bank.branch.platform.performance.api.DataTaskApi`

**调用方:** 内部 `DataTaskController` (REST 层), 最终 REST 接口由独立数据同步系统调用

```java
package com.bank.branch.platform.performance.api;

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
     */
    void reportDataTaskStatus(DataTaskStatusCmd cmd);
}
```

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
}
```

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

---

## 8. DTO 定义

### 8.1 MetricDefDTO

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

### 8.2 MetricCardDTO

```java
public class MetricCardDTO {
    private String metricCode;
    private String metricName;
    private BigDecimal currentValue;
    private BigDecimal previousValue;   // 同比/环比参考
    private BigDecimal targetValue;     // 当前周期目标
    private BigDecimal baseValue;
    private BigDecimal achievementRate; // 达成率 %
    private String unit;                // 单位, 万元/笔/人等
    private Integer sortNo;
    private LocalDate dataDate;
}
```

### 8.3 KpiResultDTO

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

### 8.4 KpiSchemeDTO

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

### 8.5 TargetPlanDTO

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

### 8.6 TargetValueDTO

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

### 8.7 CustAllocRelationDTO

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

### 8.8 PerfRunTaskDTO

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

### 8.9 EmpMetricSnapshotDTO / OrgMetricSnapshotDTO / CustMetricSnapshotDTO

```java
public class EmpMetricSnapshotDTO {
    private String empId;
    private LocalDate dataDate;
    private String dataVersion;
    private Map<String, BigDecimal> metrics; // metricCode → value
}
```

`OrgMetricSnapshotDTO` 把 `empId` 换成 `orgCode`, `CustMetricSnapshotDTO` 换成 `custId`, 其余结构相同。

### 8.10 Cmd (命令 DTO)

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

## 9. 调用约束

### 9.1 调用方式

所有接口均使用 Spring `@Autowired` 在同一进程内注入 (模块化单体, 非 RPC), 调用耗时按本地方法计。

### 9.2 性能建议

| 接口 | 性能特征 | 建议 |
|---|---|---|
| `MetricApi.getUserMetricCards` | 高频 | 消费端缓存 5 min, 失效事件: `performance.sys-control.updated.v1` |
| `MetricApi.getMetricDef` | 极高频 | 本模块内部有 Caffeine 本地缓存 |
| `MetricQueryApi.batchQuery*Snapshots` | 大批量 | 单次 ≤100000 行, 超过抛异常 |
| `KpiApi.batchGetKpiTotals` | 中频 | 单次 ≤500 empId |
| `AllocApi.listCustomersByEmp` | 中频 | 按 empId+bizKind 做 Redis 缓存 15 min |

### 9.3 数据一致性

- 所有查询以 `sys_control is_valid=1` 为准, 保证跨模块看到同一份数据快照
- 历史回算期间, 短时间可能出现"部分员工已更新、部分未更新"的不一致, 消费方应有容错
- 不同维度 (EMP/ORG/CUST) 的版本独立, 消费方不要假设维度间严格一致

### 9.4 异常

本契约定义的接口不抛检查异常, 运行时错误统一抛 `BizException(PerfErrorCode)`, 调用方用 `try/catch` 或让 `GlobalExceptionHandler` 处理。

### 9.5 审计

调用方发起的操作若需要审计, 审计职责在调用方, 本模块的 API 不会记录外部审计日志 (本模块的 controller/facade 内部仍然有审计)。

---

## 10. 领域事件

本模块发布的领域事件遵循 `docs/common-dev-guide.md` 中的事件规范, 使用 Spring `ApplicationEventPublisher`, 后续可接入 RocketMQ。

### 10.1 performance.target-adjustment.approved.v1

**发布时机:** 目标修正审批流通过, `TargetAdjustCompletedListener` 执行完 `perf_target_value` 更新后

**载荷:**

```java
public class TargetAdjustmentApprovedEvent {
    private String eventId;
    private String traceId;
    private Instant occurredAt;
    private String applyNo;          // TA_20260410_0001
    private String planId;
    private String subjectType;
    private String subjectId;
    private String cycleKey;
    private List<String> adjustedMetricCodes;
    private String approvedBy;
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| 本模块 `HistoryRecalcService` | 触发未冻结年度的 KPI 历史回算 |
| `report-analytics-center` | 刷新报表快照 (可选) |

### 10.2 performance.allocation-adjustment.approved.v1

**发布时机:** 分配关系调整审批流通过, `AllocAdjustCompletedListener` 标记申请单为 APPROVED 之后

**载荷:**

```java
public class AllocationAdjustmentApprovedEvent {
    private String eventId;
    private String traceId;
    private Instant occurredAt;
    private String applyNo;           // AA_20260410_0001
    private String custId;
    private String custType;          // CORP / RETAIL
    private String allocDim;
    private List<String> bizKinds;
    private String accountNo;
    private LocalDate effectiveDate;
    private String approvedBy;
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| 本模块 `ExternalDataTaskListener` | 等待次日外部系统上报同步结果 |
| `system-governance-center` 通知子域 | 推送通知给资财部, 提醒线下 CCRM/PCRM 操作 |
| 审计 | 留痕 |

### 10.3 performance.kpi-calc.completed.v1

**发布时机:** KPI 批量计算任务 (`DailyKpiCalcJob` 或 `HistoryRecalcService`) 成功完成后

**载荷:**

```java
public class KpiCalcCompletedEvent {
    private String eventId;
    private String traceId;
    private Instant occurredAt;
    private String taskId;
    private LocalDate dataDate;
    private String dataVersion;
    private List<String> cycleTypes;    // 本次计算涉及的 cycle
    private Integer affectedEmpCount;
    private Boolean recalc;              // true 表示历史回算, false 表示日常计算
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| `report-analytics-center` | 重新生成快照数据, 失效相关缓存 |
| `portal-content-center` | 清理工作台 `perf:card:user:*` 缓存 |

### 10.4 performance.sys-control.updated.v1

**发布时机:** `sys_control` 新版本发布 (无论 TIMER / IMPORT / RECALC / MANUAL)

**载荷:**

```java
public class SysControlUpdatedEvent {
    private String eventId;
    private String traceId;
    private Instant occurredAt;
    private String dim;               // EMP/ORG/CUST
    private LocalDate latestDataDate;
    private String currentVersion;
    private String publishSource;     // TIMER/IMPORT/RECALC/MANUAL
    private String publishBy;
}
```

**消费方:**

| 消费者 | 动作 |
|---|---|
| 本模块 `SysControlUpdatedListener` | 失效 Redis 缓存 (指标定义、工作台卡片、KPI 当前分) |
| `report-analytics-center` | 失效报表汇总缓存 |
| `portal-content-center` | 失效工作台聚合缓存 |

### 10.5 事件发布可靠性

- V1 使用 Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` 保证事件在事务提交后发布
- 事件订阅者抛出异常, 不回滚主事务, 只记录错误日志并在 `sys_event_dead_letter` 表中留痕
- V2 迁移到 RocketMQ 时, 发布端使用"本地消息表 + 定时重发"保证最终一致

---

## 11. 接口注册与暴露

### 11.1 Facade 实现

每个 `*Api` 在 `com.bank.branch.platform.performance.facade` 下有对应实现类, 标注 `@Service` + 实现接口, 被其他模块 `@Autowired`。

### 11.2 @BizAuth 与内部调用

对外 `*Api` 方法**不**走 `@BizAuth` 鉴权, 因为这是模块间的直接调用, 鉴权已在 controller 层完成。由 `*Api` 触发的"跨模块写操作" (如 `triggerKpiCalc`) 默认以"系统"身份执行, 但必须在审计日志中记录调用方。

### 11.3 版本兼容策略

- 本契约版本 V1, 任何字段增加为非破坏性变更
- 删除字段或修改字段类型必须发布新接口方法, 旧方法标记 `@Deprecated` 且保留至少一个小版本
- DTO 新增字段调用方应用"忽略未知字段"策略, 保证向前兼容

---

## 12. 不对外暴露的内部能力

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
