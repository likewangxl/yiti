# 报表分析中心 — 对外 API 契约

> **模块**: report-analytics-center
> **版本**: V1.0
> **最后更新**: 2026-04-10

---

## 1. 重要说明

### 1.1 V1 的架构原则：纯只读、不对外暴露 Api
**report-analytics-center 是纯只读支撑域模块，V1 不对外暴露 Api 接口供其他模块调用。**

本文档保留 API 结构仅作为未来扩展预留：
- **V1**：不暴露 Api，所有查询通过 Controller 层 REST 接口直接提供给前端
- **V2（未来）**：如果出现必要场景（例如门户需要定制化报表卡片），再暴露**有限的只读 Api**

### 1.2 为什么 V1 不对外暴露 Api？
| 原因 | 说明 |
|------|------|
| 防止反模式 | 防止 report 模块成为"公共查询中心" |
| 避免环形依赖 | 业务模块依赖 report，而 report 又依赖业务模块，会形成环 |
| 保证单向依赖 | 维持"report 单向调业务模块"的依赖方向 |
| 模块边界清晰 | 每个业务模块自己管理自己的查询接口 |
| 性能隔离 | 报表的聚合查询不应影响业务模块的事务性查询 |

### 1.3 对比：其他模块如何获取数据？
- **错误做法**：业务模块 A 需要客户的汇总数据 → 调用 `report-analytics-center` 的 Api
- **正确做法**：业务模块 A 需要客户的汇总数据 → 直接调用 `customer-marketing-center.CustomerQueryApi`

---

## 2. V1 对外接口范围（仅 REST API）

本模块在 V1 版本**仅通过 REST 接口**对前端暴露能力：

| 接口类别 | 路径前缀 | 用途 |
|---------|---------|------|
| 动态指标查询 | `/api/reports/dynamic-query` | 自助查询 |
| 查询方案管理 | `/api/reports/saved-queries` | 方案 CRUD |
| 仪表盘 | `/api/reports/dashboard/**` | 固定仪表盘 |
| 汇总报表 | `/api/reports/touch-task-summary`, `/api/reports/perf-summary`, `/api/reports/customer-pool-summary` | 预置报表 |
| SQL 探查 | `/api/reports/sql-probe/**` | SQL 探查工具 |
| 异步导出 | `/api/reports/export-tasks/**` | 导出任务 |

**详见：[03-接口设计与报文.md](./03-接口设计与报文.md)**

### 2.1 不对外暴露的内容
- 不对外暴露 Java API 接口（`*Api.java`）
- 不对外暴露 Service 接口
- 不对外暴露 Entity
- 不对外暴露 Mapper
- 其他模块**不能**直接注入 `DynamicQueryService`、`DashboardService` 等

---

## 3. V2 扩展预留：如果未来需要暴露 Api

### 3.1 接口设计原则
- **仅暴露只读接口**：所有方法必须无副作用
- **仅暴露聚合后的结果**：不暴露明细查询
- **带数据范围过滤**：调用方必须传入有效的数据范围参数
- **带权限校验**：内部调用必须传入调用方的身份信息

### 3.2 预留接口定义（仅参考）
```java
package com.bank.branch.platform.report.api;

import com.bank.branch.platform.report.api.dto.DashboardDataDTO;
import com.bank.branch.platform.report.api.dto.DynamicQueryCmd;
import com.bank.branch.platform.report.api.dto.DynamicQueryResultDTO;

/**
 * 报表分析中心对外 API（V2 扩展预留）
 *
 * V1 版本不对外暴露，仅作结构预留
 * V2 版本可能开放给门户中心做定制化报表卡片聚合
 */
public interface ReportApi {

    /**
     * 执行动态查询（内部系统间调用）
     *
     * @param cmd 查询命令（包含维度、对象、指标、数据日期、调用方身份）
     * @return 查询结果
     */
    DynamicQueryResultDTO executeDynamicQuery(DynamicQueryCmd cmd);

    /**
     * 获取仪表盘数据（门户聚合用）
     *
     * @param dashboardKey 仪表盘键（如 PRESIDENT）
     * @param empId        请求人员工 ID
     * @return 仪表盘数据
     */
    DashboardDataDTO getDashboard(String dashboardKey, String empId);
}
```

### 3.3 预留 DTO 定义
```java
package com.bank.branch.platform.report.api.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 动态查询命令（V2 预留）
 */
@Data
public class DynamicQueryCmd {
    /** 调用方员工 ID（用于权限校验） */
    private String callerEmpId;
    /** 维度 */
    private String dim;
    /** 对象 ID 列表 */
    private List<String> subjectIds;
    /** 指标编码列表 */
    private List<String> metricCodes;
    /** 数据日期 */
    private LocalDate dataDate;
}
```

```java
package com.bank.branch.platform.report.api.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 动态查询结果（V2 预留）
 */
@Data
public class DynamicQueryResultDTO {
    private String dim;
    private LocalDate dataDate;
    private String dataVersion;
    private List<String> metricCodes;
    private List<Map<String, Object>> rows;
    private Integer rowCount;
}
```

```java
package com.bank.branch.platform.report.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 仪表盘数据 DTO（V2 预留）
 */
@Data
public class DashboardDataDTO {
    private String dashboardKey;
    private LocalDate dataDate;
    private String dataVersion;
    private Map<String, Object> widgets;    // key=widgetKey, value=widget data
    private Map<String, BigDecimal> summaryMetrics;
}
```

---

## 4. 调用方向约束

### 4.1 单向依赖原则
```
┌─────────────────────────┐
│ report-analytics-center │  ← 只调用其他模块，不被调用
└──────────┬──────────────┘
           │  调用（只读）
           ▼
┌──────────────────────────┐
│  customer-marketing-*    │
│  performance-engine-*    │
│  system-governance-*     │
│  auth-permission-*       │
└──────────────────────────┘
```

### 4.2 违规示例
```java
// ❌ 错误：customer 模块调用 report
package com.bank.branch.platform.customer.service;

@Service
public class CustomerReportService {
    @Resource
    private ReportApi reportApi;    // 违规！customer 不能依赖 report

    public void badExample() {
        reportApi.executeDynamicQuery(...);
    }
}
```

```java
// ❌ 错误：workflow 模块直接调用 report 的 Controller
package com.bank.branch.platform.workflow.listener;

@Component
public class WorkflowListener {
    public void onApprovalComplete() {
        // 违规！通过 HTTP 调用 report 接口
        restTemplate.postForEntity("/api/reports/dynamic-query", ...);
    }
}
```

### 4.3 正确示例
```java
// ✅ 正确：report 调用 customer 的只读 Api
package com.bank.branch.platform.report.service;

@Service
public class DashboardServiceImpl implements DashboardService {
    @Resource
    private CustomerQueryApi customerQueryApi;      // 允许
    @Resource
    private PerformanceQueryApi performanceQueryApi; // 允许

    public PresidentDashboardRespDTO getPresidentDashboard() {
        // 调用 customer 的只读查询
        List<CustomerDTO> topCustomers = customerQueryApi.listTopCustomersByContribution(10);
        // 调用 performance 的只读查询
        List<KpiSnapshotDTO> deposits = performanceQueryApi.listDepositTrend(12);
        // 在 report 本地聚合
        return assemble(topCustomers, deposits);
    }
}
```

### 4.4 Code Review 硬规则
- 任何在 `customer-*`、`performance-*`、`workflow-*`、`portal-*`、`business-*` 模块的 `pom.xml` 中引入 `report-analytics-center` 的 PR **必须拒绝**
- 任何在以上模块的代码中导入 `com.bank.branch.platform.report.*` 包的 PR **必须拒绝**
- 扫描规则可配置到 CI 中：`grep -r "import com.bank.branch.platform.report" customer-*/src workflow-*/src ...`

---

## 5. DTO 定义（V1 仅用于 Controller 层）

### 5.1 请求 DTO
位于 `com.bank.branch.platform.report.dto.req` 包下：

| DTO 类名 | 用途 |
|---------|------|
| `DynamicQueryReqDTO` | 动态查询请求 |
| `DynamicQueryExportReqDTO` | 动态查询导出请求 |
| `SavedQueryCreateReqDTO` | 创建查询方案 |
| `SavedQueryUpdateReqDTO` | 更新查询方案 |
| `SqlProbeExecuteReqDTO` | SQL 探查执行 |
| `SqlProbeHistoryQueryReqDTO` | SQL 探查历史查询 |
| `TouchTaskSummaryReqDTO` | 触达任务汇总查询 |
| `PerfSummaryReqDTO` | 绩效汇总查询 |
| `CustomerPoolSummaryReqDTO` | 客户池汇总查询 |

### 5.2 响应 DTO
位于 `com.bank.branch.platform.report.dto.resp` 包下：

| DTO 类名 | 用途 |
|---------|------|
| `QueryDimensionRespDTO` | 维度与指标树 |
| `MetricTreeNodeDTO` | 指标树节点 |
| `DynamicQueryRespDTO` | 动态查询结果 |
| `MetricColumnDTO` | 指标列定义 |
| `SavedQueryRespDTO` | 查询方案 |
| `PresidentDashboardRespDTO` | 分行行长仪表盘 |
| `ChartDataDTO` | 图表数据 |
| `ChartSeriesDTO` | 图表系列 |
| `OrgRankingItemDTO` | 机构排名项 |
| `TopCustomerDTO` | Top 客户 |
| `TouchTaskSummaryRespDTO` | 触达任务汇总 |
| `OrgTaskSummaryDTO` | 按机构汇总 |
| `TypeTaskSummaryDTO` | 按类型汇总 |
| `DailyTaskSummaryDTO` | 每日汇总 |
| `PerfSummaryRespDTO` | 绩效汇总 |
| `PerfSummaryItemDTO` | 绩效汇总项 |
| `CustomerPoolSummaryRespDTO` | 客户池汇总 |
| `CustomerLayerDTO` | 客户分层 |
| `SqlProbeExecuteRespDTO` | SQL 探查结果 |
| `SqlProbeHistoryRespDTO` | SQL 探查历史 |
| `SchemaWhitelistRespDTO` | Schema 白名单 |
| `TableSchemaDTO` | 表结构 |
| `ColumnSchemaDTO` | 列结构 |
| `ExportTaskRespDTO` | 导出任务初始响应 |
| `ExportTaskStatusRespDTO` | 导出任务状态 |

### 5.3 DTO 命名规范
- 请求 DTO：以 `ReqDTO` 结尾
- 响应 DTO：以 `RespDTO` 结尾
- 通用 DTO：以 `DTO` 结尾（不带前缀）
- V1 的 DTO 放在 `dto.req` / `dto.resp` 包下，**不放在 `api.dto` 包下**
- 如果将来 V2 暴露 Api，再将对外 DTO 单独迁移到 `api.dto` 包下

### 5.4 DTO 层职责分离
| 包路径 | 用途 | 可被外部引用 |
|--------|------|------------|
| `com.bank.branch.platform.report.dto.req` | Controller 请求 DTO | 否 |
| `com.bank.branch.platform.report.dto.resp` | Controller 响应 DTO | 否 |
| `com.bank.branch.platform.report.api.dto` | V2 对外 Api DTO（V1 为空） | 是（V2 起） |

---

## 6. 领域事件

### 6.1 发布的事件
**无**。report-analytics-center 是纯只读模块，不发布任何领域事件。

### 6.2 为什么不发布事件？
- 查询操作不改变业务状态
- 没有其他模块需要订阅"某人查了什么"
- 审计由 `system-governance-center.AuditApi` 统一记录，不需要事件

### 6.3 如果未来需要发布事件
可能的场景：
- SQL 探查高危操作告警事件：`report.sql-probe.high-risk.v1`
- 大批量导出事件：`report.export.large-batch.v1`

这些事件应该发送到 `system-governance-center`，由治理中心统一处理告警，**不**直接发送给业务模块。

---

## 7. 订阅的外部事件

### 7.1 事件订阅清单
| 事件 Topic | 来源模块 | 处理逻辑 |
|-----------|---------|---------|
| `performance.kpi-calc.completed.v1` | performance-engine-center | 刷新本地缓存（指标数据） |
| `performance.sys-control.updated.v1` | performance-engine-center | 清除版本相关缓存（仪表盘等） |
| `auth.permission.updated.v1` | auth-permission-center | 清除用户权限/机构树缓存 |
| `customer.top-customer.updated.v1` | customer-marketing-center | 清除 Top 客户缓存 |

### 7.2 事件处理原则
- **幂等处理**：同一事件重复投递必须能正确处理
- **不阻塞主流程**：事件处理失败不应影响业务模块
- **Fail-Open**：事件处理失败时，缓存自然过期即可
- **异步处理**：使用 `@Async` 或 MQ 消费者处理

### 7.3 示例：sys_control 更新事件处理
```java
@Component
@Slf4j
public class SysControlUpdatedListener {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理 sys_control 版本更新事件
     * 清除所有与数据版本相关的缓存
     */
    @EventListener
    @Async
    public void onSysControlUpdated(SysControlUpdatedEvent event) {
        log.info("收到 sys_control 更新事件, dataVersion={}", event.getDataVersion());
        try {
            // 清除仪表盘缓存
            Set<String> dashboardKeys = redisTemplate.keys("report:dashboard:*");
            if (dashboardKeys != null && !dashboardKeys.isEmpty()) {
                redisTemplate.delete(dashboardKeys);
            }
            // 清除汇总报表缓存
            Set<String> summaryKeys = redisTemplate.keys("report:summary:*");
            if (summaryKeys != null && !summaryKeys.isEmpty()) {
                redisTemplate.delete(summaryKeys);
            }
            // 清除有效版本缓存
            redisTemplate.delete("report:valid-data-version");

            log.info("sys_control 更新事件处理完成, 清除了 {} 个缓存",
                (dashboardKeys != null ? dashboardKeys.size() : 0) +
                (summaryKeys != null ? summaryKeys.size() : 0));
        } catch (Exception e) {
            // Fail-Open：事件处理失败不抛出
            log.error("sys_control 更新事件处理失败", e);
        }
    }
}
```

---

## 8. 模块边界检查清单

### 8.1 对外暴露检查
- [ ] V1 不对外暴露任何 `*Api` 接口
- [ ] V1 不对外暴露 `Service` 接口（保持 package-private 或 public 但不被外部引用）
- [ ] V1 不对外暴露 `Entity`、`Mapper`
- [ ] Controller 仅面向前端，不面向其他 Java 模块

### 8.2 依赖方向检查
- [ ] `pom.xml` 中只依赖 `common`、`auth-api`、`governance-api`、`customer-api`、`performance-api`
- [ ] 不依赖 `workflow-api`、`portal-api`、`business-api`
- [ ] Import 扫描：无 `com.bank.branch.platform.workflow.*`、`com.bank.branch.platform.portal.*`、`com.bank.branch.platform.business.*` 的引用

### 8.3 数据库访问检查
- [ ] Mapper 仅访问 `rpt_*`、`sql_probe_*` 表
- [ ] 不直接 JOIN 其他模块的业务表
- [ ] SQL 探查使用独立的只读数据源

### 8.4 事件检查
- [ ] 未发布任何领域事件
- [ ] 仅订阅只读场景的事件（用于缓存失效）

---

## 9. 兼容性承诺

### 9.1 V1 版本
- 所有 REST 接口的 URL 结构稳定
- 响应 DTO 字段只增不减
- 错误码稳定

### 9.2 V2 可能的变更
- 新增 `ReportApi` 对外 Java 接口
- 新增 `api.dto` 包下的 DTO
- REST 接口可能新增，但不会删除 V1 的接口
- 字段可能新增，但不会修改已有字段的类型或含义

---

## 10. 参考文档
- [01-功能规格.md](./01-功能规格.md) — 业务功能详述
- [02-后端架构.md](./02-后端架构.md) — 后端分层与错误码
- [03-接口设计与报文.md](./03-接口设计与报文.md) — 完整接口定义
- `docs/common-dev-guide.md` — 共享开发规范
- `project_ana.md` 第 4.5 节 — 报表功能业务描述
- `project_ana_技术方案与架构拆分.md` — 架构设计

---

## V1.0 交付状态备注（2026-04-25）

**V1.0 已交付**：25 PT_RESOURCE 全部落地（24 实现 + 1 占位 = R_RPT_SQL_EXP V1.1+ 启用）。

**契约红线维持**：本文档 §1.1 "本模块不暴露任何 `*Api` 接口" 在 V1.0 交付后**继续生效**：
- `report-analytics-center/src/main/java/com/bank/branch/platform/report/api/` 目录下仅有 `package-info.java` 占位
- 没有任何生产 `*Api` / `*QueryApi` 类
- 由 `RptModuleStructureArchTest` 架构守护（任何未来提交在 `api/` 包下新增 `*Api.java` 都会编译期触发架构测试失败）

**V1.0 跨模块依赖侧**：报表只读消费 4 个上游模块的 *Api（共 10 个接口）：
- auth-permission-center: `CurrentUserApi` / `BizScopeApi` / `OrgApi`
- system-governance-center: `DictApi` / `AuditApi` / `FileApi`
- performance-engine-center: `MetricApi` / `KpiApi`
- customer-marketing-center: `CustomerQueryApi` / `TouchTaskQueryApi`

**Plan 来源**：`docs/superpowers/plans/2026-04-25-report-analytics-center-v1.0-plan.md`（Milestone M0-M6）

**测试基线（M6 末）**：surefire 103 + failsafe 70 = 173 全绿，6 架构守护全绿，Flyway V1_0_0 → V1_0_7 共 8 脚本.
