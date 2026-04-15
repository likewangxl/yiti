# performance-engine-center V1.0 设计文档（配置与版本骨架）

| 元数据 | 值 |
|---|---|
| Spec 类型 | 设计规格（Design Spec） |
| 目标模块 | `performance-engine-center`（绩效计算中心） |
| 交付版本 | V1.0（配置与版本骨架） |
| 编写日期 | 2026-04-15 |
| 作者 | Claude Code + leid（通过 brainstorming 确认） |
| 交付策略 | 纵切分期（B 方案） + 骨架先行 + 子域并行 + 集成收敛（C 方案） |
| 后续版本 | V1.1（计算与导入）、V1.2（业务流程与回算） |

---

## 0. 背景与范围

### 0.1 分期策略

`performance-engine-center` 是一个"核心域"大模块，包含 9 个子域、17 张表、53+ REST 接口、6 个对外 Api、4 类领域事件、5 个定时任务。一次性交付风险过高，本项目将模块拆为三个独立 spec + plan 交付：

- **V1.0 配置与版本骨架**（本 spec）：配置态表与契约、版本管理基础设施、CRUD 能力，**不含任何计算、不发起流程、不消费事件、不对外发布事件**
- **V1.1 计算与导入**：指标执行（SQL+Groovy+级联刷新）、KPI 计算、数据导入、外部数据上报
- **V1.2 业务流程与回算**：分配关系调整审批、目标修正审批、历史回算、高危导出接口

### 0.2 V1.0 交付范围

**纳入**：
- 9 张业务表 + 4 张宽表（仅 DDL 落地）
- 配置类 CRUD（指标定义、KPI 方案、目标方案）
- 只读查询接口（运行任务日志、分配关系）
- 指标层级校验、环路检测、槽位分配
- sys_control 版本管理基础（查询 + 手工切换 + 初始化）
- 4 个对外 Api：MetricApi、MetricQueryApi、TargetApi、AllocApi

**不纳入**：
- 任何指标执行能力（SQL 执行器、Groovy 沙箱、级联刷新）
- 任何 KPI 计算、历史回算、导入能力
- 任何流程发起、事件订阅、事件发布
- 任何定时任务
- KpiApi、PerfCalcApi、DataTaskApi（留待 V1.1）
- 所有导出接口（留待 V1.2）

### 0.3 关键决策摘要（brainstorming 阶段确认）

| 决策项 | 方案 | 理由 |
|---|---|---|
| 测试策略 | 方案 D：Unit + 本地真 MySQL (`onepl`) | Windows 下 Docker 慢；本地 MySQL 已就绪；MyBatis XML 动态 SQL 需真库验证 |
| Redis 缓存 | 方案 B：V1.0 上配置表缓存 | 读写比 > 100:1；V1.1 要复用 |
| 指标引用关系存储 | 方案 C：双写（独立表为主 + JSON 冗余） | 独立表支持 DFS，JSON 供详情，同事务保证一致性 |
| 配置表数据范围 | 方案 A：`SCOPE_ALL` + `@BizAuth` 资源级控制 | 配置为平台级全局数据 |
| 交付节奏 | 方案 C：骨架 + 6 子域并行 + 集成收敛 | 消灭共享文件冲突，最大化并行 |

---

## 1. 架构总览

### 1.1 模块定位

`performance-engine-center` V1.0 承担**配置态**领域对象的持久化与契约声明，为 V1.1 计算引擎、V1.2 业务流程打下"**可以配置、可以查询、可以校验**"的基础。

### 1.2 分层契约

严格遵循已完成模块（auth/governance/workflow）的 5 层结构：

```
Controller (REST)
    ↓ 仅调用 facade，绝不直接调 service
Facade (对外 Api 实现 + 用例编排)
    ↓ 组合多个 service
Service (单一职责，事务边界)
    ↓ 通过 mapper 访问 DB
Mapper (MyBatis，模块私有)
    ↓
Entity (贫血模型)
```

### 1.3 对外边界

| 方向 | 内容 |
|---|---|
| **入站** | 32 个 REST 端点，路径前缀 `/api/perf/*` |
| **出站（对其他模块）** | 4 个 Api：`MetricApi`、`MetricQueryApi`、`TargetApi`、`AllocApi`（V1.0 仅声明与实现，无现有消费者） |
| **依赖（上游）** | auth（CurrentUserApi/BizScopeApi/OrgApi/EmpQueryApi）、governance（DictApi/AuditApi/ConfigApi） |
| **V1.0 不依赖** | workflow、customer-marketing |
| **V1.0 不包含** | 任何定时任务、任何事件发布/订阅、任何外部系统交互 |

### 1.4 并发/事务基线

- **版本切换**：Redis 分布式锁 `perf:sys_control:switch:{scope_dim}`（TTL 30s）+ DB UK 双保险
- **槽位分配**：DB UK `uk_base_dim_slot` + 悲观锁 `SELECT FOR UPDATE` 或乐观重试
- **配置 CRUD**：`@Transactional(rollbackFor=Exception.class)`，REQUIRED 传播

---

## 2. 包结构设计

### 2.1 模块根结构

```
performance-engine-center/
├── pom.xml
├── CLAUDE.md                          # 模块级开发指南
└── src/
    ├── main/
    │   ├── java/com/bank/branch/platform/performance/
    │   │   ├── api/                   # 对外契约
    │   │   ├── config/                # Spring 配置
    │   │   ├── controller/            # REST 控制器
    │   │   ├── facade/                # 对外 Api 实现
    │   │   ├── service/               # 业务逻辑
    │   │   ├── mapper/                # MyBatis Mapper 接口
    │   │   ├── entity/                # 贫血模型
    │   │   ├── enums/                 # 枚举 + 错误码
    │   │   └── exception/
    │   └── resources/
    │       ├── mapper/                # MyBatis XML
    │       └── sql/                   # V1.0 初始化 SQL
    └── test/
        ├── java/com/bank/branch/platform/performance/
        │   ├── controller/            # Controller IT
        │   ├── facade/                # Facade UT
        │   ├── service/               # Service UT
        │   ├── mapper/                # Mapper IT
        │   └── support/               # 测试工具
        └── resources/
            ├── application-test.yml
            └── test-data/
```

### 2.2 `api/` 包（对外契约）

```
api/
├── MetricApi.java
├── MetricQueryApi.java
├── TargetApi.java
├── AllocApi.java
└── dto/
    ├── MetricDefDTO.java
    ├── MetricRefDTO.java
    ├── KpiSchemeDTO.java              # 预置，KpiApi V1.1 暴露
    ├── KpiItemDTO.java
    ├── TargetPlanDTO.java
    ├── TargetValueDTO.java
    ├── CustAllocRelationDTO.java
    ├── AllocSummaryDTO.java
    └── AllocVersionDTO.java
```

### 2.3 `service/` 包

```
service/
├── SysControlService.java
├── MetricDefService.java
├── MetricRefService.java
├── MetricSlotService.java            # 槽位分配专用
├── MetricCycleDetectService.java     # 环路检测专用
├── KpiSchemeService.java
├── KpiItemService.java
├── TargetPlanService.java
├── TargetValueService.java
├── AllocRelationService.java
└── PerfRunTaskService.java
```

### 2.4 `enums/` 包（错误码与业务枚举）

```
enums/
├── PerfErrorCode.java                # 所有 PERF-* 错误码
├── PerfBizType.java                  # PERF_CONFIG / PERF_QUERY / ...
├── BaseDimEnum.java                  # EMP / ORG / CUST
├── MetricLevelEnum.java              # 1 / 2 / 3
├── CalcLogicTypeEnum.java            # SQL / PROC / EXPR / SUMMARY
├── CycleTypeEnum.java                # MONTHLY / QUARTERLY / YEARLY
├── MetricStatusEnum.java             # DRAFT / PUBLISHED / DISABLED
└── RunTaskStatusEnum.java
```

### 2.5 关键约定

- 包按"职责"切分，不按"子域"切分（与 workflow-center 保持一致）
- DTO 与 Entity 严格分离；Entity 不得泄漏到 api 包或 controller
- 错误码集中在 `PerfErrorCode` 枚举
- 测试资源 `application-test.yml` 不复制 bootstrap，只覆写必要差异

---

## 3. 数据层设计

### 3.1 DDL 来源与策略

- **权威来源**：`docs/schema/ddl-performance.sql`（已与 `docs/modules/performance-engine-center/05-表结构DDL.md` 对齐）
- **微调内容**：
  - 与项目审计字段规范对齐（`created_by/created_time/updated_by/updated_time/deleted`）
  - 补全缺失的唯一键与索引
- **交付文件**：`src/main/resources/sql/V1_0_0__performance_ddl.sql`
- **执行方式**：V1.0 手工执行一次；V1.1 考虑引入 Flyway

### 3.2 V1.0 表分类

| 分类 | 表 | Entity | Mapper | V1.0 使用 |
|---|---|---|---|---|
| **配置表** | `sys_control`, `perf_metric_def`, `perf_metric_ref`, `perf_kpi_scheme`, `perf_kpi_item`, `perf_target_plan` | ✓ | ✓ | CRUD + 查询 |
| **业务数据表** | `perf_target_value`, `cust_alloc_relation` | ✓ | ✓ | CRUD（目标值）/ 只读（分配关系） |
| **日志表** | `perf_run_task` | ✓ | ✓ | 仅查询 |
| **宽表（仅建表）** | `emp_index_result`, `org_index_result`, `cust_index_result`, `kpi_result` | ✗ | ✗ | 仅 DDL 落地 |

共 13 张表。

### 3.3 Entity 规范

- 统一继承 `BaseEntity`（来自 common-db），携带审计字段：`id` / `created_by` / `created_time` / `updated_by` / `updated_time` / `deleted` / `version`
- 驼峰↔下划线由 MyBatis `map-underscore-to-camel-case: true` 自动转换
- 日期类型：`LocalDate` / `LocalDateTime` / `Instant`（不用 `java.util.Date`）
- 金额/比例：`BigDecimal`
- JSON 字段：`String`（由 Service 层负责序列化/反序列化，避免 TypeHandler 复杂度）
- 枚举字段：DB 存 `varchar`，Entity 用 `String`，Service 与 DTO 层转换

### 3.4 Mapper 规范

**通用接口**：
```java
int insert(Entity e);
int insertBatch(@Param("list") List<Entity> list);
int updateById(Entity e);
int updateByIdSelective(Entity e);
int deleteById(@Param("id") String id, @Param("updatedBy") String op);
Entity selectById(@Param("id") String id);
List<Entity> selectByIds(@Param("ids") List<String> ids);
List<Entity> selectByCondition(@Param("cond") XxxQueryCond cond);
long countByCondition(@Param("cond") XxxQueryCond cond);
```

**XML 约束**：
- `<resultMap>` 明确声明，不用隐式自动映射
- 查询 SQL 禁用 `SELECT *`
- 动态 SQL 使用 `<where>` `<if>` 组合
- 分页由 common-db 的 `PageInterceptor` 统一处理，Mapper 不手写 `LIMIT`
- 数据范围 SQL 片段通过 `<if test="scope == 'XXX'">` 动态拼接

### 3.5 关键索引与约束

| 表 | 关键约束 | 目的 |
|---|---|---|
| `sys_control` | `UK(scope_dim, latest_data_date)` + `IDX(scope_dim, is_valid)` | 版本切换原子性 |
| `perf_metric_def` | `UK(metric_code)` + `UK(base_dim, val_slot, deleted)` | 编码与槽位唯一 |
| `perf_metric_ref` | `UK(metric_code, ref_metric_code)` + `IDX(ref_metric_code)` | 防重 + 反向查询 |
| `perf_kpi_scheme` | `UK(scheme_code, deleted)` | 编码唯一 |
| `perf_kpi_item` | `UK(scheme_id, metric_code)` | 项内指标不重复 |
| `perf_target_plan` | `UK(plan_code, deleted)` | 编码唯一 |
| `perf_target_value` | `UK(plan_id, subject_type, subject_id, cycle_key, metric_code)` | 目标值唯一 |
| `cust_alloc_relation` | `IDX(cust_id)` + `IDX(emp_id)` + `IDX(effective_date)` | 三向查询 |
| `perf_run_task` | `UK(task_no)` + `IDX(task_type, data_date)` + `IDX(started_by, created_time)` | 任务标识 |

### 3.6 不在 V1.0 做的

- 宽表分区（`PARTITION BY RANGE`）——V1.1 接入计算数据时再做
- MySQL 触发器、存储过程（所有业务逻辑在 Java 代码）
- 外键约束（按项目约定不用外键）

---

## 4. 领域服务设计

### 4.1 sys_control 子域

| Service | 关键方法 |
|---|---|
| `SysControlService` | `getCurrentVersion(scopeDim)` / `listVersionHistory(scopeDim, limit)` / `switchVersion(SwitchVersionCmd)` / `initIfAbsent(scopeDim, dataDate)` |

**关键设计**：
- `switchVersion` 原子性：Redis 分布式锁 → 事务内 `UPDATE old.is_valid=0` + `INSERT new.is_valid=1` → UK 兜底
- V1.0 仅支持 `MANUAL` + `INIT` 两种触发源
- Redis 锁在事务外获取/释放

### 4.2 指标库子域（核心复杂子域）

| Service | 关键方法 |
|---|---|
| `MetricDefService` | `create` / `update` / `publish` / `disable(reason)` / `getByCode` / `page` |
| `MetricRefService` | `setRefs(metricCode, refMetricCodes)` / `listRefsOf` / `listWhoRef` |
| `MetricSlotService` | `allocSlot(baseDim, metricLevel, preferredSlot)` / `releaseSlot(baseDim, slot, operator)` / `listOccupied(baseDim)` |
| `MetricCycleDetectService` | `checkNoCycle(metricCode, refCodes)` / `checkLevelConstraint(metricLevel, refCodes)` |

**槽位分配策略**：
- L1 指标：slot 1~100（每维度独立）
- L2 指标：slot 101~150
- L3 指标：slot 151~200
- 算法：`SELECT FOR UPDATE` 锁住跟踪行 → 找最小可用 → INSERT → UK 兜底
- 手工指定 slot：校验可用性；被占用则抛 `PERF-40901`

**引用关系双写一致性**：
- `MetricDefService.create/update` 内调用 `MetricRefService.setRefs` → 同事务
  - `UPDATE perf_metric_def.ref_metric_codes = ?(JSON)`
  - `DELETE FROM perf_metric_ref WHERE metric_code=?` + `INSERT INTO perf_metric_ref ...`
- 读场景：详情/编辑读 JSON；DFS 图遍历读独立表

**环路检测（DFS）**：
- 构造临时图（假设新关系已存在），以 `metricCode` 为起点 DFS
- 能回到自身则抛 `PERF-40902`
- 层级校验：L2 不能引用 L3；L3 不能引用 L1；同级不能互引 → `PERF-40911`
- 该 Service 为纯函数风格，便于纯单元测试

**生命周期流转**：
- `DRAFT → PUBLISHED`：校验通过后方可发布
- `PUBLISHED → DISABLED`：槽位**不释放**（V1.0 规则），需 `@AuditLog(reason required)`
- `DISABLED → 删除`：仅当无下游引用时允许（实为逻辑删）

### 4.3 KPI 方案子域

| Service | 关键方法 |
|---|---|
| `KpiSchemeService` | `create` / `update` / `publish(schemeId)` / `disable` / `getByCode` / `page` |
| `KpiItemService` | `addItem` / `updateItem` / `removeItem(reason)` / `listItems` |

**关键设计**：
- 方案发布前：校验所有 `metric_code` 在 `perf_metric_def` 存在且为 `PUBLISHED`
- 不强制 sum(weight)=100（文档明示）
- 删除项/删除方案：必填 reason

### 4.4 目标方案子域

| Service | 关键方法 |
|---|---|
| `TargetPlanService` | `create` / `update` / `getByCode` / `page` |
| `TargetValueService` | `upsert(UpsertTargetValueCmd)` / `upsertBatch(List)` / `get` / `queryByPlan` |

**关键设计**：
- V1.0 不支持 Excel 导入
- `upsert` 基于 UK `uk_plan_subject_cycle_metric` 使用 `INSERT ... ON DUPLICATE KEY UPDATE`
- `upsertBatch` 上限 500 条，超过抛 `PERF-40910`
- 生效/失效日期校验：`effective_date <= expire_date`

### 4.5 运行任务日志子域（仅查询）

| Service | 关键方法 |
|---|---|
| `PerfRunTaskService` | `getById(taskId)` / `getByTaskNo` / `page(RunTaskQueryCond)` / `countByTypeAndDate` |

**关键设计**：
- V1.0 不提供写入方法（V1.1 由计算引擎触发）
- 查询支持过滤：taskType / status / dataDate 范围 / startedBy / createdTime 范围
- 数据范围：管理员全见，普通用户仅见自己启动的任务

### 4.6 分配关系子域（只读）

| Service | 关键方法 |
|---|---|
| `AllocRelationService` | 10 个查询方法（对应 AllocApi） |

**关键设计**：
- V1.0 不提供任何写入（写入在 V1.2）
- 时间线查询核心：`WHERE effective_date <= ? AND (end_date IS NULL OR end_date >= ?)`
- `getLatestAllocVersion` / `getAllocVersion`：从 `sys_control` 的 CUST 维度派生
- 数据范围简化：管理员全见 + 普通用户仅见 `emp_id = 当前 empId` 的行

### 4.7 Service 层通用约定

- 所有 public 写方法 `@Transactional(rollbackFor = Exception.class)` REQUIRED
- Service 入口防御性校验（`Objects.requireNonNull` + 业务校验）
- 不抛 `RuntimeException`，统一抛 `PerfException(PerfErrorCode.XXX)`
- 每个 Service 方法 DEBUG 级记录入参、INFO 级记录关键动作
- 审计通过 `@AuditLog` 走 AOP，**不在 Service 内手动调用 AuditApi**
- 构造器注入（`@RequiredArgsConstructor`），不字段注入

---

## 5. 对外 API 层设计（Facade）

### 5.1 Facade 层职责

1. 实现 `api/*Api.java` 接口
2. Entity → DTO 转换（通过 `*Assembler`）
3. 多 Service 编排
4. 应用缓存
5. **不承担事务**（事务在 Service 层）

### 5.2 四个对外 Api 契约

#### MetricApi（2 个方法）

```java
Optional<MetricDefDTO> getMetricDef(String metricCode);
boolean isMetricExists(String metricCode);
```

#### MetricQueryApi（3 个方法）

```java
List<MetricDefDTO> getMetrics(List<String> metricCodes);
List<MetricDefDTO> getMetricsByBaseDim(String baseDim);
List<MetricDefDTO> getRefMetrics(String metricCode);
```

#### TargetApi（4 个方法）

```java
Optional<TargetPlanDTO> getTargetPlan(String planCode);
Optional<TargetPlanDTO> getTargetPlanById(String planId);
Optional<BigDecimal> getTargetValue(
    String planId, String subjectType, String subjectId, String cycleKey, String metricCode);
Map<String, BigDecimal> getTargetValues(
    String planId, String subjectType, String subjectId, String cycleKey);
```

#### AllocApi（10 个方法）

```java
List<CustAllocRelationDTO> getCurrentAllocations(String custId, String bizKind);
List<CustAllocRelationDTO> getAllocationsAt(String custId, LocalDate asOfDate, String bizKind);
AllocSummaryDTO getAllocSummary(String custId, LocalDate asOfDate);
List<CustAllocRelationDTO> getAllocsByEmp(String empId, LocalDate asOfDate);
List<CustAllocRelationDTO> getAllocsByOrg(String orgCode, LocalDate asOfDate);
Long countCustsOfEmp(String empId, LocalDate asOfDate);
Long countCustsOfOrg(String orgCode, LocalDate asOfDate);
Map<String, BigDecimal> getAllocRatioMap(String custId, LocalDate asOfDate);
AllocVersionDTO getLatestAllocVersion();
Optional<AllocVersionDTO> getAllocVersion(String versionId);
```

### 5.3 DTO 设计约束

- 命名以 `DTO` 结尾，放在 `api/dto/` 包
- V1.0 采用 `class + Lombok @Data/@Builder` 方案（与已完成模块一致）
- 枚举字段用 `String`
- `LocalDate`/`LocalDateTime`/`BigDecimal` 透传
- 暴露 `val_slot`、`ref_metric_codes` 等内部实现字段（报表和工作台需要）

### 5.4 Facade 实现规范

```java
@Service
@RequiredArgsConstructor
public class MetricApiImpl implements MetricApi {
    private final MetricDefService metricDefService;
    private final MetricAssembler assembler;
    
    @Override
    @Cacheable(cacheNames = "perf:metric_def", key = "#metricCode")
    public Optional<MetricDefDTO> getMetricDef(String metricCode) {
        if (!StringUtils.hasText(metricCode)) return Optional.empty();
        PerfMetricDef entity = metricDefService.getByCodeOrNull(metricCode);
        return Optional.ofNullable(entity).map(assembler::toDTO);
    }
}
```

**约定**：
- 容忍 null 入参（返回 `Optional.empty()` 或空集合）
- 异常统一向上抛 `PerfException`
- 每个 Facade 配对 `XxxAssembler` 静态方法类

### 5.5 版本兼容

- V1.0 暴露的 Api 一旦发布即为稳定契约
- 删除/重命名需走 deprecation 流程；新增方法允许
- V1.1 新增 `KpiApi`、`PerfCalcApi`、`DataTaskApi` 以及 `MetricApi.getUserMetricCards`

---

## 6. REST 控制器设计

### 6.1 Controller 层职责

1. HTTP 参数绑定、`@Valid` 校验
2. `@BizAuth` 权限声明（每方法必填）
3. `@AuditLog` 审计埋点（高危操作必填）
4. 调用 Facade → 封装 `ResponseWrapper<T>` 返回
5. **不写任何业务逻辑**

### 6.2 V1.0 所有 REST 端点清单（共 32 个）

#### MetricDefController（10 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/metrics` | PERF_METRIC_CONFIG / READ | ✗ |
| GET | `/api/perf/metrics/{metricCode}` | PERF_METRIC_CONFIG / READ | ✗ |
| POST | `/api/perf/metrics` | PERF_METRIC_CONFIG / WRITE | ✓ (CREATE) |
| PUT | `/api/perf/metrics/{metricCode}` | PERF_METRIC_CONFIG / WRITE | ✓ (UPDATE) |
| DELETE | `/api/perf/metrics/{metricCode}` | PERF_METRIC_CONFIG / DELETE | ✓ (reason 必填) |
| PUT | `/api/perf/metrics/{metricCode}/status` | PERF_METRIC_CONFIG / STATUS_CHANGE | ✓ (reason 必填) |
| GET | `/api/perf/metrics/{metricCode}/refs` | PERF_METRIC_CONFIG / READ | ✗ |
| GET | `/api/perf/metrics/{metricCode}/ref-by` | PERF_METRIC_CONFIG / READ | ✗ |
| GET | `/api/perf/metrics/val-slots` | PERF_METRIC_CONFIG / READ | ✗ |
| POST | `/api/perf/metrics/{metricCode}/slot/release` | PERF_METRIC_CONFIG / MANAGE | ✓ (reason 必填) |

#### KpiSchemeController（9 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/kpi-schemes` | PERF_KPI_CONFIG / READ | ✗ |
| GET | `/api/perf/kpi-schemes/{id}` | PERF_KPI_CONFIG / READ | ✗ |
| POST | `/api/perf/kpi-schemes` | PERF_KPI_CONFIG / WRITE | ✓ |
| PUT | `/api/perf/kpi-schemes/{id}` | PERF_KPI_CONFIG / WRITE | ✓ |
| DELETE | `/api/perf/kpi-schemes/{id}` | PERF_KPI_CONFIG / DELETE | ✓ (reason 必填) |
| POST | `/api/perf/kpi-schemes/{id}/publish` | PERF_KPI_CONFIG / PUBLISH | ✓ |
| POST | `/api/perf/kpi-schemes/{id}/items` | PERF_KPI_CONFIG / WRITE | ✗ |
| PUT | `/api/perf/kpi-schemes/{id}/items/{itemId}` | PERF_KPI_CONFIG / WRITE | ✗ |
| DELETE | `/api/perf/kpi-schemes/{id}/items/{itemId}` | PERF_KPI_CONFIG / WRITE | ✓ (reason 必填) |

#### TargetPlanController（4 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/target-plans` | PERF_TARGET_CONFIG / READ | ✗ |
| GET | `/api/perf/target-plans/{id}` | PERF_TARGET_CONFIG / READ | ✗ |
| POST | `/api/perf/target-plans` | PERF_TARGET_CONFIG / WRITE | ✓ |
| PUT | `/api/perf/target-plans/{id}` | PERF_TARGET_CONFIG / WRITE | ✓ |

#### TargetValueController（3 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/target-values` | PERF_TARGET_VALUE / READ | ✗ |
| POST | `/api/perf/target-values` | PERF_TARGET_VALUE / WRITE | ✓ |
| POST | `/api/perf/target-values/batch` | PERF_TARGET_VALUE / WRITE | ✓ |

#### AllocRelationController（3 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/alloc-relations` | PERF_ALLOC_QUERY / READ | ✗ |
| GET | `/api/perf/alloc-relations/history` | PERF_ALLOC_QUERY / READ | ✗ |
| GET | `/api/perf/alloc-relations/summary` | PERF_ALLOC_QUERY / READ | ✗ |

#### PerfRunTaskController（2 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/run-tasks` | PERF_RUN_TASK_QUERY / READ | ✗ |
| GET | `/api/perf/run-tasks/{taskId}` | PERF_RUN_TASK_QUERY / READ | ✗ |

#### SysControlController（4 个端点）

| 方法 | 路径 | @BizAuth | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/sys-control` | PERF_SYS_CONTROL / READ | ✗ |
| GET | `/api/perf/sys-control/history` | PERF_SYS_CONTROL / READ | ✗ |
| POST | `/api/perf/sys-control/init` | PERF_SYS_CONTROL / MANAGE | ✓ (reason 必填) |
| POST | `/api/perf/sys-control/switch-version` | PERF_SYS_CONTROL / MANAGE | ✓ (reason 必填) |

**小计**：10 + 9 + 4 + 3 + 3 + 2 + 4 = **35 个端点**（第 6 章节 6.2 中示例列出 9 个 KpiScheme 端点含 items 3 个；实际端点数见本表）

实际 V1.0 REST 端点总数：**35 个**。

### 6.3 BizType 枚举

```java
public enum PerfBizType {
    PERF_METRIC_CONFIG,   // 指标库配置
    PERF_KPI_CONFIG,      // KPI 方案配置
    PERF_TARGET_CONFIG,   // 目标方案配置
    PERF_TARGET_VALUE,    // 目标值管理
    PERF_ALLOC_QUERY,     // 分配关系查询
    PERF_RUN_TASK_QUERY,  // 任务日志查询
    PERF_SYS_CONTROL      // 版本控制管理
}
```

### 6.4 请求/响应模型规范

- 请求 DTO：`*ReqDTO`（查询 `*QueryReqDTO`、命令 `Create*ReqDTO`/`Update*ReqDTO`）
- 放在 `controller/dto/` 包或复用 `api/dto/`
- Bean Validation：`@NotNull`、`@NotBlank`、`@Size`、`@Pattern`、`@Valid`
- 枚举字段 `String` + 自定义 `@DictValue` 或 `@EnumValid`
- 分页请求继承 `PageRequest`
- 响应：`ResponseWrapper<T>` / `ResponseWrapper<PageResult<T>>`

### 6.5 Knife4j 文档

- 每个 Controller `@Tag(name = "绩效-xxx")`
- 每个方法 `@Operation(summary = "...")`
- 关键字段 `@Schema(description = "...")`
- 必须通过 Knife4j UI (`http://localhost:8080/doc.html`) 联调通过

---

## 7. 横切关注点设计

### 7.1 错误码规范（`PerfErrorCode`）

格式：`PERF-{HTTP状态后两位}{序号3位}`

| 错误码 | 场景 |
|---|---|
| `PERF-40001` | 请求参数非法 |
| `PERF-40002` | 分页参数越界 |
| `PERF-40401` | 指标不存在 |
| `PERF-40402` | KPI 方案不存在 |
| `PERF-40403` | 目标方案不存在 |
| `PERF-40901` | 槽位已被占用 |
| `PERF-40902` | 指标引用形成环路 |
| `PERF-40903` | 指标编码已存在 |
| `PERF-40904` | 版本切换并发冲突 |
| `PERF-40905` | 指标非 DRAFT 状态不可编辑 SQL |
| `PERF-40906` | 方案未发布不可绑定目标 |
| `PERF-40910` | 目标值批量上限 500 |
| `PERF-40911` | 引用层级违规 |
| `PERF-50001` | 未预期服务端错误 |
| `PERF-50002` | 下游依赖异常 |

### 7.2 缓存策略

**Key 命名**：`perf:<domain>:<key>[:<subkey>]`

| Key 模式 | TTL | 数据 | evict 触发 |
|---|---|---|---|
| `perf:metric_def:{metricCode}` | 5 min | 单个指标 | create/update/disable/delete |
| `perf:metric_def:list:{baseDim}` | 5 min | 维度指标列表 | 该维度任一变更 |
| `perf:kpi_scheme:{schemeId}` | 5 min | KPI 方案详情 | create/update/publish/delete/item 变更 |
| `perf:kpi_scheme:list` | 5 min | 启用方案列表 | 方案状态变更 |
| `perf:target_plan:{planId}` | 5 min | 目标方案 | create/update |
| `perf:sys_control:{scopeDim}` | 60 s | 当前有效版本 | switchVersion 成功后 |
| `perf:alloc:cur:{custId}:{bizKind}` | 15 min | 客户当前分配关系 | V1.0 不 evict |
| `perf:alloc:ver:latest` | 5 min | 最新分配版本号 | V1.0 不 evict |

**事务后失效**：所有 evict 通过 `TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit` 回调触发。

### 7.3 审计埋点

**路径**：`@AuditLog` → AOP → 发布 `AuditLogEvent` → `AuditApi.log()` 落库

| 资源类型 | 触发场景 | reason 要求 |
|---|---|---|
| `PERF_METRIC_DEF` | CREATE/UPDATE/DELETE/STATUS_CHANGE/SLOT_RELEASE | UPDATE 涉及 SQL 改动时必填；DELETE/STATUS_CHANGE/SLOT_RELEASE 必填 |
| `PERF_METRIC_REF` | 随 DEF UPDATE 合并记录 | — |
| `PERF_KPI_SCHEME` | CREATE/UPDATE/DELETE/PUBLISH | DELETE 必填 |
| `PERF_KPI_ITEM` | DELETE | DELETE 必填 |
| `PERF_TARGET_PLAN` | CREATE/UPDATE | — |
| `PERF_TARGET_VALUE` | WRITE/BATCH_WRITE | — |
| `PERF_SYS_CONTROL` | INIT/SWITCH | 必填 |

### 7.4 数据范围应用策略

| 表 | 策略 |
|---|---|
| 6 张配置表 | `SCOPE_ALL`，仅 `@BizAuth` 资源级控制 |
| `perf_target_value` | V1.0 简化：管理员全见 + 普通用户不可见（`@BizAuth(action=MANAGE)`） |
| `perf_run_task` | 按 `started_by` 过滤：管理员全见 + 普通用户仅见自己 |
| `cust_alloc_relation` | V1.0 简化：管理员全见 + 普通用户仅见 `emp_id = 当前 empId` |

### 7.5 事务边界

| 场景 | 事务声明 |
|---|---|
| Service 写方法 | `@Transactional(rollbackFor = Exception.class)` REQUIRED |
| Service 查询方法 | `@Transactional(readOnly = true)` 或不加 |
| Facade 层 | 不加或 `@Transactional(readOnly = true)` |
| `create/update` 调 `setRefs` | 同事务（REQUIRED） |
| `switchVersion` | 同事务完成双操作；Redis 锁在事务外 |
| `upsertBatch` | 单事务；500 条上限 |

### 7.6 幂等性

| 操作 | 幂等手段 |
|---|---|
| 创建指标 | DB UK + DuplicateKeyException 转 `PERF-40903` |
| 槽位分配 | DB UK + 悲观锁 + 失败重试一次 |
| 目标值 upsert | DB UK + `INSERT ... ON DUPLICATE KEY UPDATE` |
| 版本切换 | Redis 锁 + DB UK + 事务原子 |
| sys_control init | `initIfAbsent` 语义 |

### 7.7 日志规范

- 前缀 `[PERF][<子域>]`
- TraceId 自动注入 MDC
- Service 入口 DEBUG；关键动作 INFO；异常 ERROR
- 慢 SQL > 5s 由 common-db 自动 WARN
- `account_no` 查询输出脱敏（保留后 4 位）

---

## 8. 测试设计（TDD 驱动）

### 8.1 测试金字塔

- Service UT：~100 个（最多）
- Mapper IT：~60 个
- Controller IT：~30 个
- Facade UT：~12 个

### 8.2 测试分层约定

| 层 | 框架 | 配置 | 数据库 | 事务 |
|---|---|---|---|---|
| Service UT | JUnit 5 + Mockito + AssertJ | 纯单元 | 全 mock | 无 |
| Mapper IT | `@SpringBootTest` + `@MybatisTest` | 真 MyBatis | 真 `onepl` | `@Transactional + @Rollback(true)` |
| Facade UT | JUnit 5 + Mockito | 纯单元 | 全 mock | 无 |
| Controller IT | `@SpringBootTest(MOCK)` + MockMvc | 完整上下文 | 真 `onepl` | `@Transactional + @Rollback(true)` |

### 8.3 测试基础设施

**基类**：
- `PerformanceMapperTestBase`
- `PerformanceControllerTestBase`
- `PerformanceServiceTestBase`

**工具**：
- `TestDataBuilder`（Fluent Builder）
- `MockCurrentUserHelper`
- `TestDbCleaner`

**测试配置**：`src/test/resources/application-test.yml`（连接本地 `onepl`）

### 8.4 TDD 闭环示例

```
红 (Red)：写测试 → 编译失败或断言失败 → 提交 "test: red - allocSlot"
绿 (Green)：最小实现让测试通过 → 提交 "feat: green - allocSlot basic"
重构 (Refactor)：添加边界与并发场景 → 提交 "refactor: allocSlot concurrent"
```

CLAUDE.md TDD 红线：
- 严禁"先写实现再补测试"
- 测试必须先于实现提交
- 红→绿→重构三步单独提交，便于 code-reviewer 审查

### 8.5 关键场景测试清单

**sys_control**：
- `switchVersion_concurrentTwoThreads_onlyOneWins`
- `switchVersion_whenUkViolation_shouldThrow40904`
- `initIfAbsent_whenExists_shouldNotInsertDup`

**指标库**：
- `allocSlot_basicAllocation` × 4 个边界
- `checkNoCycle_simpleSelfRef_shouldThrow40902`
- `checkNoCycle_indirectRef_A_B_A_shouldThrow40902`
- `checkLevelConstraint_L2RefL3_shouldThrow40911`
- `create_whenMetricCodeDup_shouldThrow40903`
- `update_withRefsChange_shouldUpdateBothJsonAndRefTable`

**KPI 方案**：
- `publish_whenItemReferMissingMetric_shouldThrow`
- `publish_whenItemReferDraftMetric_shouldThrow`
- `addItem_whenDuplicate_shouldThrow`

**目标方案**：
- `upsertBatch_whenSizeExceeds500_shouldThrow40910`
- `upsertBatch_whenAllSuccess_shouldReturnCount`
- `upsert_existingRow_shouldUpdate`

**分配关系**：
- `getCurrentAllocations_excludesExpired`
- `getAllocSummary_sumRatio_equals100`
- `listAllocsByEmp_appliesScopeFilter`

**Controller**（每个控制器抽样）：
- 401（未登录）/ 403（权限不足）/ 200（成功）/ 409（业务冲突）

### 8.6 测试数据隔离

- `@Transactional + @Rollback(true)` 全局回滚
- `@BeforeEach` 构造测试专用数据
- 测试数据前缀分配避免并行 IT 冲突：
  - sys_control 子代理：`TEST_SC_*`
  - 指标库子代理：`TEST_METRIC_*`
  - KPI 子代理：`TEST_KPI_*`
  - 目标子代理：`TEST_TGT_*`
  - 任务子代理：`TEST_RT_*`
  - 分配子代理：`TEST_AR_*`

---

## 9. 子代理交付编排

### 9.1 总览

```
阶段 0 (串行) ─► 阶段 1 (并行×6) ─► 阶段 2 (串行) ─► 阶段 3 (串行)
  1 子代理          6 子代理             1 子代理         1 子代理
  骨架搭建          子域全栈             集成收敛        代码审查
```

### 9.2 阶段 0：骨架搭建（P0）

**职责**：
1. 创建 `performance-engine-center/` 目录结构
2. 编写 `pom.xml`（common 5 子模块 + auth + governance，不含 workflow/customer-marketing）
3. 创建空包 + package-info
4. 创建 `PerformanceAutoConfiguration`、`PerformanceMyBatisConfig`、`PerformanceRedisConfig`
5. 定义全部枚举与 `PerfErrorCode`
6. 编写 `PerfException`
7. 编写三个 SQL 脚本（DDL + Resources + Dicts）
8. 编写 `CLAUDE.md`
9. 编写测试基础设施（基类、工具、配置）

**验收**：
- `mvn clean install -pl performance-engine-center` 通过
- 本地 MySQL `onepl` 13 张表存在
- 枚举可 import

### 9.3 阶段 1：子域全栈并行（P1-1 ~ P1-6）

| 子代理 | 主要交付物 |
|---|---|
| **P1-SysControl** | SysControl Entity/Mapper/XML/Service/Controller + 测试（含并发测试） |
| **P1-Metric** | Metric + MetricRef Entity/Mapper/XML、4 个 Service、2 个 Facade、MetricDefController(10)、2 个对外 Api + 4 DTO + 全测试 |
| **P1-Kpi** | KpiScheme + KpiItem Entity/Mapper/XML、2 个 Service、KpiSchemeController(9)、2 个 DTO + 全测试 |
| **P1-Target** | TargetPlan + TargetValue Entity/Mapper/XML、2 个 Service、TargetApi Facade、2 个 Controller(4+3)、TargetApi + 2 DTO + 全测试 |
| **P1-RunTask** | PerfRunTask Entity/Mapper/XML、Service（仅查询）、Controller(2) + 测试 |
| **P1-Alloc** | CustAllocRelation Entity/Mapper/XML、AllocRelationService(10 方法)、AllocApi Facade、Controller(3)、AllocApi + 3 DTO + 全测试 |

**共同约定**：
- 严格 TDD：先红 → 绿 → 重构
- 测试数据前缀见 8.6
- `mvn clean test -pl performance-engine-center` 必须全绿
- 禁止修改 pom.xml、bootstrap、其他子代理的文件
- 新增 `PerfErrorCode` 在末尾追加

### 9.4 阶段 2：集成收敛（P2）

**职责**：
1. 修改 `bootstrap/pom.xml` 添加 `performance-engine-center` 依赖
2. 确认 `@ComponentScan`/`@MapperScan` 覆盖 performance 包
3. 修改根 `pom.xml` 注册 `<module>`
4. 统一审查 `PerfErrorCode`（去重、冲突）
5. 验证 `mvn clean package` 通过
6. 启动 bootstrap，验证 Knife4j UI
7. 执行冒烟测试：
   - `GET /api/perf/sys-control?scopeDim=EMP`
   - `GET /api/perf/metrics?pageNo=1&pageSize=10`
   - `GET /api/perf/kpi-schemes?pageNo=1&pageSize=10`
   - `GET /api/perf/target-plans?pageNo=1&pageSize=10`
   - `POST /api/perf/sys-control/init`
8. 运行全量 `mvn clean test`

**验收**：
- `mvn clean package` 通过
- bootstrap 可启动，Knife4j 展示全部 35 个端点
- 冒烟测试全绿
- 全量 mvn test 通过

### 9.5 阶段 3：代码审查（P3）

使用 `superpowers:code-reviewer` 子代理，审查重点：

1. TDD 节奏（git 历史体现红→绿→重构）
2. CLAUDE.md 规范（`@BizAuth`、`@AuditLog`、跨模块调用、中文注释、UTF-8）
3. 设计文档对照
4. 错误码完整性
5. 缓存一致性（事务后 evict）
6. SQL 安全（`#{}` vs `${}`）
7. 空值处理（Optional）
8. 测试质量

**产出**：
- `docs/superpowers/sessions/<date>-perf-v1.0-code-review.md`
- 问题清单（Must Fix / Should Fix / Nice to Have）
- Must Fix 由相关子代理修复

### 9.6 交付物总览

| 分类 | 数量 |
|---|---|
| Java 源文件 | ~120 |
| MyBatis XML | 9 |
| SQL 脚本 | 3 |
| 测试文件 | ~60 |
| 配置文件 | 3 |
| 文档 | 1（CLAUDE.md） |
| REST 端点 | 35 |
| 对外 Api | 4（共 19 方法） |
| 数据表 | 13 |
| PT_RESOURCE 登记 | 35 |
| 字典 | 10 类 |

### 9.7 完成标准（Definition of Done）

1. ✅ `mvn clean package` 通过
2. ✅ `mvn clean test` 全部通过，覆盖率达标（Service 行覆盖 ≥ 80%，分支 ≥ 70%）
3. ✅ bootstrap 可启动，Knife4j 正常展示
4. ✅ 冒烟测试 5 个关键端点返回 200
5. ✅ Code-reviewer 子代理无 Must Fix
6. ✅ git 历史体现 TDD 节奏
7. ✅ PT_RESOURCE、字典、sys_control 初始数据就位
8. ✅ 模块级 CLAUDE.md 已撰写

---

## 10. 风险与缓解

| 风险 | 级别 | 缓解 |
|---|---|---|
| 阶段 1 六个子代理同时改 `PerfErrorCode` | 中 | 约定末尾追加，阶段 2 统一去重 |
| Mapper IT 并行跑导致数据冲突 | 中 | 数据前缀隔离（8.6）+ `@Transactional + @Rollback` |
| Redis 未就绪导致缓存相关测试失败 | 低 | `application-test.yml` 允许 Redis 降级（`spring.data.redis.enabled` 条件）|
| DDL 与 Entity 字段不一致 | 中 | Mapper IT 强制命中每个字段，MyBatis 映射失败即报错 |
| bootstrap 启动失败（依赖冲突） | 中 | 阶段 2 必须执行启动冒烟测试 |
| TDD 节奏被子代理忽略 | 高 | code-reviewer 审查 git 历史，Must Fix |
| 槽位分配并发场景漏测 | 中 | Mapper IT 必须包含并发测试用例 |

---

## 11. 后续计划（V1.1 / V1.2 预告）

**V1.1 计算与导入**（下一轮 brainstorm 再详细设计）：
- 指标执行（SQL 执行器 + Groovy 沙箱 + 级联刷新）
- KPI 计算引擎
- 数据导入（统一入口，三种类型）
- 外部数据上报接收（`/api/data-task/status`）
- 定时任务（日终指标/KPI 计算）
- KpiApi、PerfCalcApi、DataTaskApi 暴露

**V1.2 业务流程与回算**：
- 分配关系调整审批（对公/零售分流）
- 目标修正审批
- 历史回算引擎
- 所有导出接口（4 个高危）
- 事件发布（TargetAdjustApprovedEvent、AllocAdjustApprovedEvent、KpiCalcCompletedEvent）

---

## 12. 附录

### 12.1 相关文档

- 功能规格：`docs/modules/performance-engine-center/01-功能规格.md`
- 后端架构：`docs/modules/performance-engine-center/02-后端架构.md`
- 接口设计：`docs/modules/performance-engine-center/03-接口设计与报文.md`
- 对外 API 契约：`docs/modules/performance-engine-center/04-对外API契约.md`
- 表结构 DDL：`docs/modules/performance-engine-center/05-表结构DDL.md`
- 并发与事务：`docs/modules/performance-engine-center/06-并发与事务策略.md`
- 审计要求：`docs/modules/performance-engine-center/07-审计要求.md`
- 初始化数据：`docs/modules/performance-engine-center/08-初始化数据清单.md`
- 依赖契约：`docs/modules/performance-engine-center/09-依赖契约摘要.md`
- DDL 权威源：`docs/schema/ddl-performance.sql`
- 共通开发规范：`docs/common-dev-guide.md`
- 项目根规范：`CLAUDE.md`

### 12.2 变更历史

| 日期 | 版本 | 变更 | 作者 |
|---|---|---|---|
| 2026-04-15 | v1.0 | 初稿，经 5 轮澄清 + 9 节分节确认后定稿 | Claude Code + leid |
