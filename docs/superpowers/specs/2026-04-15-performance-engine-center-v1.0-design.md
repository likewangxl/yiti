# performance-engine-center V1.0 设计文档（配置与版本骨架）

| 元数据 | 值 |
|---|---|
| Spec 类型 | 设计规格（Design Spec） |
| 目标模块 | `performance-engine-center`（绩效计算中心） |
| 交付版本 | V1.0（配置与版本骨架） |
| 编写日期 | 2026-04-15 |
| 修订版本 | v1.2（2026-04-15，根据环境探针结果回退 v1.1 的 DDL 调整 4 项 + planId 类型回 String） |
| 作者 | leid（通过 brainstorming 确认） |
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
- **对外 Api 全部 7 个接口（MetricApi/MetricQueryApi/KpiApi/TargetApi/PerfCalcApi/DataTaskApi/AllocApi）签名与 DTO 类在 V1.0 定型**；配置类方法 V1.0 实现，计算类方法 V1.0 抛 `UnsupportedOperationException("V1.1 delivered")` 占位（详见 §5）

**不纳入**：
- 任何指标执行能力（SQL 执行器、Groovy 沙箱、级联刷新）
- 任何 KPI 计算、历史回算、导入能力
- 任何流程发起、事件订阅、事件发布
- 任何定时任务
- 所有导出接口（留待 V1.2）

### 0.3 关键决策摘要

| 决策项 | 方案 | 理由 |
|---|---|---|
| 测试策略 | 方案 D：Unit + 本地真 MySQL (`onepl`) | Windows 下 Docker 慢；本地 MySQL 已就绪；MyBatis XML 动态 SQL 需真库验证 |
| Redis 缓存 | 方案 B：V1.0 上配置表缓存 | 读写比 > 100:1；V1.1 要复用 |
| 指标引用关系存储 | 方案 C：双写（独立表为主 + JSON 冗余） | 独立表支持 DFS，JSON 供详情，同事务保证一致性 |
| 配置表数据范围 | 方案 A：`SCOPE_ALL` + `@BizAuth` 资源级控制 | 配置为平台级全局数据 |
| 交付节奏 | 方案 C：骨架 + 6 子域并行 + 集成收敛 | 消灭共享文件冲突，最大化并行 |
| **对外 Api 契约策略** | **权威对齐 + UOE 占位** | **V1.0 定型 7 个 Api 签名，V1.1 仅替换 UOE 为真实实现；避免未来破坏性改动** |
| **planId 数据类型** | **统一 `String`（与生产 DDL 对齐）** | **v1.2 修订**：环境探针发现 onepl 库中 `perf_target_plan.id = varchar(32)` 且表已有业务依赖；04 契约的 `Long planId` 在本期标注为**技术债**（后续由架构师同步修正 04 契约），V1.0 TargetApi 方法签名一律用 `String planId` |

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
    ↓ 组合多个 service；Redis 分布式锁在此层申请与释放（事务外）
Service (单一职责，事务边界)
    ↓ 通过 mapper 访问 DB
Mapper (MyBatis，模块私有)
    ↓
Entity (贫血模型)
```

### 1.3 对外边界

| 方向 | 内容 |
|---|---|
| **入站** | **35 个 REST 端点**，路径前缀 `/api/perf/*` |
| **出站（对其他模块）** | **7 个对外 Api**：`MetricApi`、`MetricQueryApi`、`KpiApi`、`TargetApi`、`PerfCalcApi`、`DataTaskApi`、`AllocApi` — V1.0 全部定型签名；实现状态见 §5 |
| **依赖（上游）** | auth（CurrentUserApi/BizScopeApi/OrgApi/EmpQueryApi）、governance（DictApi/AuditApi/ConfigApi） |
| **V1.0 不依赖** | workflow、customer-marketing |
| **V1.0 不包含** | 任何定时任务、任何事件发布/订阅、任何外部系统交互 |

### 1.4 并发/事务基线

- **版本切换**：`SysControlFacade.switchVersion` 层申请 Redis 分布式锁 `perf:sys_control:switch:{scope_dim}`（TTL 30s），锁内调用 `SysControlService.doSwitchVersion(@Transactional)`，锁在 finally 释放；DB UK 双保险
- **槽位分配（v1.2 修订）**：**Redis 分布式锁** `perf:slot-alloc:{baseDim}`（TTL 30s）+ Service 层 `SELECT MAX(val_slot) + 1` + 业务校验确认未占用（无 DB UK 兜底，因现有 DDL 仅是 KEY 索引）
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
    │   │   ├── api/                   # 7 个对外契约 + DTO
    │   │   ├── config/                # Spring 配置
    │   │   ├── controller/            # REST 控制器
    │   │   ├── facade/                # 对外 Api 实现 + 锁包装
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
├── MetricApi.java                # 7 方法
├── MetricQueryApi.java           # 3 方法
├── KpiApi.java                   # 5 方法
├── TargetApi.java                # 4 方法
├── PerfCalcApi.java              # 3 方法
├── DataTaskApi.java              # 1 方法
├── AllocApi.java                 # 10 方法
└── dto/
    ├── MetricDefDTO.java
    ├── MetricCardDTO.java
    ├── EmpMetricSnapshotDTO.java
    ├── OrgMetricSnapshotDTO.java
    ├── CustMetricSnapshotDTO.java
    ├── KpiResultDTO.java
    ├── KpiSchemeDTO.java
    ├── KpiItemDTO.java
    ├── TargetPlanDTO.java
    ├── TargetValueDTO.java
    ├── PerfRunTaskDTO.java
    ├── CustAllocRelationDTO.java
    ├── AllocSummaryDTO.java
    ├── AllocVersionDTO.java
    └── cmd/
        └── DataTaskStatusCmd.java
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

### 2.4 `enums/` 包

```
enums/
├── PerfErrorCode.java                # 所有 PERF-* 错误码
├── PerfBizType.java                  # PERF_METRIC_CONFIG / ...
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

### 3.1 DDL 来源与策略（v1.2 修订：接受现有表结构）

- **权威来源**：`docs/schema/ddl-performance.sql`（onepl 库中已部署）
- **v1.2 决策**：**不对现有 DDL 做任何结构性修改**（环境探针确认 13 张表已在 onepl 生产就绪）。spec v1.1 提出的 4 项 DDL 调整**全部回退**：
  | 原 v1.1 调整项 | v1.2 决策 | 业务语义兜底 |
  |---|---|---|
  | `perf_target_plan.id` 改为 `bigint` | **保持 `varchar(32)`** | TargetApi 方法签名用 `String planId` |
  | `perf_target_value.plan_id` 改为 `bigint` | **保持 `varchar(32)`** | 同上 |
  | `perf_metric_def` 补 `deleted` 列 + UK 含 deleted | **保持现状**（无 deleted 列） | 逻辑删除通过 `status=DISABLED` 表达；槽位释放语义仍由 Service 层 `MetricSlotService.releaseSlot` 控制 |
  | 其他配置表 UK 含 `deleted` | **保持现状** | 同上；重复 metric_code/scheme_code/plan_code 通过 Service 层业务校验拦截 |
- **V1_0_0 脚本职责**：**不再做 DDL 变更**，改为"幂等验证脚本"，`CREATE TABLE IF NOT EXISTS` 方式保证新环境部署时表结构存在（实际 onepl 已有表，脚本几乎空跑）
- **交付文件**：`src/main/resources/sql/V1_0_0__performance_ddl.sql`（源自 `docs/schema/ddl-performance.sql`，一字不改作为基线副本）
- **技术债记录**：`04-对外API契约.md` 的 `Long planId` 与 DDL 的 `varchar(32)` 冲突**不在本期解决**，由架构师后续统一；V1.0 内部与下游消费方均使用 `String planId`

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
- JSON 字段：`String`（由 Service 层负责序列化/反序列化）；统一使用 bootstrap 已注入的全局 `ObjectMapper` Bean（通过构造器注入），避免本模块自建
- 枚举字段：DB 存 `varchar`，Entity 用 `String`，Service 与 DTO 层转换
- **主键类型**（v1.2 修正）：配置表与业务表统一用 `String(id varchar(32))`（与生产 DDL 一致）；**仅宽表**（`emp/org/cust_index_result` / `kpi_result`）用 `Long(id bigint AUTO_INCREMENT)`

### 3.4 Mapper 规范

**通用接口**：
```java
int insert(Entity e);
int insertBatch(@Param("list") List<Entity> list);
int updateById(Entity e);
int updateByIdSelective(Entity e);
int deleteById(@Param("id") Serializable id, @Param("updatedBy") String op);
Entity selectById(@Param("id") Serializable id);
List<Entity> selectByIds(@Param("ids") List<? extends Serializable> ids);
List<Entity> selectByCondition(@Param("cond") XxxQueryCond cond);
long countByCondition(@Param("cond") XxxQueryCond cond);
```

**XML 约束**：
- `<resultMap>` 明确声明，不用隐式自动映射
- 查询 SQL 禁用 `SELECT *`
- 动态 SQL 使用 `<where>` `<if>` 组合
- 分页由 common-db 的 `PageInterceptor` 统一处理，Mapper 不手写 `LIMIT`
- 数据范围 SQL 片段通过 `<if test="scope == 'XXX'">` 动态拼接

### 3.5 关键索引与约束（v1.2：与生产 DDL 严格对齐）

| 表 | 关键约束 | 目的 |
|---|---|---|
| `sys_control` | `UK(scope_dim, latest_data_date)` + `IDX(scope_dim, is_valid)` | 版本切换原子性 |
| `perf_metric_def` | `UK(metric_code)` + `IDX(base_dim, metric_level)` + `IDX(status)` + `IDX(val_slot)` | 编码唯一；**槽位唯一由 Service 层业务校验 + Redis 悲观锁保证**（非 DB UK） |
| `perf_metric_ref` | `UK(metric_code, ref_metric_code)` + `IDX(ref_metric_code)` | 防重 + 反向查询 |
| `perf_kpi_scheme` | `UK(scheme_code)` + `IDX(status)` | 编码唯一 |
| `perf_kpi_item` | `UK(scheme_id, metric_code)` + `IDX(scheme_id)` | 项内指标不重复 |
| `perf_target_plan` | `UK(plan_code)` + `IDX(status)`；`id varchar(32)` | 编码唯一；**planId 为 String** |
| `perf_target_value` | `UK(plan_id, subject_type, subject_id, cycle_key, metric_code)`；`plan_id varchar(32)` | 目标值唯一 |
| `cust_alloc_relation` | `IDX(cust_id)` + `IDX(emp_id)` + `IDX(effective_date)` | 三向查询 |
| `perf_run_task` | `IDX(task_type)` + `IDX(status)` + `IDX(started_by)` + `IDX(created_time)` | 任务查询 |

**影响说明**：
- 无 `deleted` 字段 → Entity 中也**不声明** `deleted` 字段；逻辑删除语义通过 `status=DISABLED` 实现
- 槽位唯一性保障改为：**Redis 分布式锁** `perf:slot-alloc:{baseDim}`（TTL 30s）+ Service 层检查 + 重试机制（见 §4.2）

### 3.6 不在 V1.0 做的

- 宽表分区（`PARTITION BY RANGE`）——V1.1 接入计算数据时再做
- MySQL 触发器、存储过程（所有业务逻辑在 Java 代码）
- 外键约束（按项目约定不用外键）

---

## 4. 领域服务设计

### 4.1 sys_control 子域（含 Redis 锁正确时序）

| Service | 关键方法 |
|---|---|
| `SysControlService` | `getCurrentVersion(scopeDim)` / `listVersionHistory(scopeDim, limit)` / `doSwitchVersion(SwitchVersionCmd)` (**@Transactional**) / `initIfAbsent(scopeDim, dataDate)` |

**关键设计**：

1. **版本切换的锁时序**（修正 review #4）
   - Redis 锁**不能**在 `@Transactional` 方法内申请（AOP 先开事务再入方法）
   - 正确分层：
     ```
     SysControlFacade.switchVersion(cmd):
       String lockKey = "perf:sys_control:switch:" + cmd.scopeDim
       String token = UUID.randomUUID().toString()
       boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(30))
       if (!locked) throw new PerfException(PERF-40904)
       try {
         sysControlService.doSwitchVersion(cmd)    // 内部 @Transactional
       } finally {
         // Lua 脚本原子比对 token 再删除，防误删其他节点的锁
         redisTemplate.execute(unlockScript, keys, args)
       }
     ```
   - `SysControlService.doSwitchVersion` 事务内：`UPDATE old.is_valid=0` + `INSERT new.is_valid=1`；UK 兜底
2. V1.0 仅支持 `MANUAL` + `INIT` 两种触发源
3. `initIfAbsent`：幂等，已存在直接跳过

### 4.2 指标库子域（核心复杂子域）

| Service | 关键方法 |
|---|---|
| `MetricDefService` | `create` / `update` / `publish` / `disable(reason)` / `getByCode` / `page` / `delete(reason)` |
| `MetricRefService` | `setRefs(metricCode, refMetricCodes)` / `listRefsOf` / `listWhoRef` |
| `MetricSlotService` | `allocSlot(baseDim, metricLevel, preferredSlot)` / `releaseSlot(baseDim, slot, operator, reason)` / `listOccupied(baseDim)` |
| `MetricCycleDetectService` | `checkNoCycle(metricCode, refCodes)` / `checkLevelConstraint(metricLevel, refCodes)` |

**槽位分配策略**：
- L1 指标：slot 1~100（每维度独立）
- L2 指标：slot 101~150
- L3 指标：slot 151~200
- 算法（v1.2 修订）：Facade 层 Redis 锁 `perf:slot-alloc:{baseDim}`（TTL 30s，Lua 释放）→ Service 层 `@Transactional` 内 `SELECT val_slot FROM perf_metric_def WHERE base_dim=? AND val_slot IS NOT NULL` 取当前占用集 → 找最小可用槽位 → INSERT；锁在 finally 释放
- 手工指定 slot：校验可用性；被占用则抛 `PERF-40901`

**槽位释放语义**（修正 review #9）：
- 默认："PUBLISHED → DISABLED" 转换时**不自动释放**槽位（避免新老指标数据混淆）
- `POST /metrics/{code}/slot/release`：**仅管理员**对 `DISABLED` 状态指标**强制释放**槽位，`reason` 必填，审计记录
- `MetricSlotService.releaseSlot` 在 Service 层校验：指标必须为 DISABLED 状态，否则抛 `PERF-40905`
- 释放后该槽位在 UK 下可被新指标复用（因 UK 含 `deleted`）

**引用关系双写一致性**（决策 ② 方案 C）：
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
- `PUBLISHED → DISABLED`：槽位保留，需 `@AuditLog(reason required)`
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
| `TargetPlanService` | `create` / `update` / `getByCode` / `getById(Long planId)` / `page` |
| `TargetValueService` | `upsert(UpsertTargetValueCmd)` / `upsertBatch(List)` / `get(Long planId, ...)` / `queryByPlan` |

**关键设计**：
- V1.0 不支持 Excel 导入
- `upsert` 基于 UK `uk_plan_subject_cycle_metric_deleted` 使用 `INSERT ... ON DUPLICATE KEY UPDATE`
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

### 4.6 分配关系子域（只读，支撑 AllocApi）

| Service | 关键方法 |
|---|---|
| `AllocRelationService` | 10 个查询方法（对应 AllocApi 全部方法） |

**关键设计**：
- V1.0 不提供任何写入（写入在 V1.2）
- 时间线查询核心：`WHERE effective_date <= ? AND (end_date IS NULL OR end_date >= ?)`
- `getLatestAllocVersion` / `getAllocVersionAt`：从 `sys_control` 的 CUST 维度派生
- 数据范围简化：管理员全见 + 普通用户仅见 `emp_id = 当前 empId` 的行

### 4.7 Service 层通用约定

- 所有 public 写方法 `@Transactional(rollbackFor = Exception.class)` REQUIRED
- Service 入口防御性校验（`Objects.requireNonNull` + 业务校验）
- 不抛 `RuntimeException`，统一抛 `PerfException(PerfErrorCode.XXX)`
- 每个 Service 方法 DEBUG 级记录入参、INFO 级记录关键动作
- 审计通过 `@AuditLog` 走 AOP，**不在 Service 内手动调用 AuditApi**
- 构造器注入（`@RequiredArgsConstructor`），不字段注入

---

## 5. 对外 API 层设计（7 个 Api 权威对齐）

### 5.1 Facade 层职责

1. 实现 `api/*Api.java` 接口
2. Entity → DTO 转换（通过 `*Assembler`）
3. 多 Service 编排
4. 应用缓存
5. **Redis 分布式锁申请/释放**（涉及事务切换场景）
6. **不承担业务事务**（事务在 Service 层）

### 5.2 V1.0 对外 Api 契约（按 `04-对外API契约.md` 权威签名）

#### 5.2.1 MetricApi（7 方法）

| 方法 | V1.0 状态 | 说明 |
|---|---|---|
| `List<MetricCardDTO> getUserMetricCards(String empId)` | **UOE 占位** | 需要结果数据，V1.1 实现 |
| `Optional<MetricDefDTO> getMetricDef(String metricCode)` | **V1.0 实现** | 配置查询，缓存 5 min |
| `List<MetricDefDTO> getMetricDefs(List<String> metricCodes)` | **V1.0 实现** | 批量配置查询，上限 100 |
| `List<MetricDefDTO> listMetrics(String baseDim, Integer metricLevel)` | **V1.0 实现** | 维度+级别查询，缓存 5 min |
| `Map<String, BigDecimal> getEmpMetricValues(String empId, LocalDate dataDate, List<String> metricCodes)` | **UOE 占位** | 需要宽表数据，V1.1 实现 |
| `Map<String, BigDecimal> getOrgMetricValues(String orgCode, LocalDate dataDate, List<String> metricCodes)` | **UOE 占位** | V1.1 |
| `Map<String, BigDecimal> getCustMetricValues(String custId, LocalDate dataDate, List<String> metricCodes)` | **UOE 占位** | V1.1 |

#### 5.2.2 MetricQueryApi（3 方法，report 专用）

| 方法 | V1.0 状态 |
|---|---|
| `List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(List<String> empIds, LocalDate from, LocalDate to, List<String> metricCodes)` | **UOE 占位**（需宽表数据） |
| `List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(...)` | **UOE 占位** |
| `List<CustMetricSnapshotDTO> batchQueryCustSnapshots(...)` | **UOE 占位** |

#### 5.2.3 KpiApi（5 方法）

| 方法 | V1.0 状态 |
|---|---|
| `BigDecimal getCurrentKpiTotal(String empId, String cycleType)` | **UOE 占位**（需 KPI 结果） |
| `Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType)` | **UOE 占位** |
| `List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to)` | **UOE 占位** |
| `Optional<KpiSchemeDTO> getKpiScheme(String schemeCode)` | **V1.0 实现**（方案配置查询） |
| `Optional<KpiSchemeDTO> getKpiSchemeById(Long schemeId)` | **V1.0 实现** |

#### 5.2.4 TargetApi（4 方法，**planId 统一 `String`** — v1.2 与生产 DDL 对齐）

| 方法 | V1.0 状态 |
|---|---|
| `Optional<TargetPlanDTO> getTargetPlan(String planCode)` | **V1.0 实现** |
| `Optional<TargetPlanDTO> getTargetPlanById(String planId)` | **V1.0 实现** |
| `Optional<BigDecimal> getTargetValue(String planId, String subjectType, String subjectId, String cycleKey, String metricCode)` | **V1.0 实现** |
| `List<TargetValueDTO> listTargetValues(String planId, String subjectType, String subjectId, String cycleKey)` | **V1.0 实现** |

**注意**：04 契约文档使用 `Long planId`，但 v1.2 修订基于生产 DDL 事实（`varchar(32)`）改回 `String`，列为技术债由后续架构统一处理。

#### 5.2.5 PerfCalcApi（3 方法）

| 方法 | V1.0 状态 |
|---|---|
| `String triggerKpiCalc(LocalDate dataDate)` | **UOE 占位**（无计算能力） |
| `String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator)` | **UOE 占位** |
| `Optional<PerfRunTaskDTO> getRunTask(String taskId)` | **V1.0 实现**（任务查询复用 PerfRunTaskService） |

#### 5.2.6 DataTaskApi（1 方法）

| 方法 | V1.0 状态 |
|---|---|
| `void reportDataTaskStatus(DataTaskStatusCmd cmd)` | **UOE 占位**（V1.1 接入外部数据上报） |

#### 5.2.7 AllocApi（10 方法，全部 V1.0 实现）

| 方法 | V1.0 状态 |
|---|---|
| `List<CustAllocRelationDTO> getCurrentAllocations(String custId, String bizKind)` | **V1.0 实现**，缓存 10 min |
| `List<CustAllocRelationDTO> getAllocationHistory(String custId, LocalDate asOfDate)` | **V1.0 实现**，不缓存 |
| `List<CustAllocRelationDTO> listCustomersByEmp(String empId, String bizKind)` | **V1.0 实现**，缓存 15 min |
| `Map<String, List<CustAllocRelationDTO>> batchGetCurrentAllocations(Set<String> custIds, String bizKind)` | **V1.0 实现**，单客户缓存命中 + 批量查询 |
| `Map<String, Long> countCustomersByEmps(Set<String> empIds)` | **V1.0 实现**，缓存 30 min |
| `List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate)` | **V1.0 实现**，不缓存 |
| `boolean hasAllocation(String empId, String custId, String bizKind)` | **V1.0 实现**，继承 getCurrentAllocations 缓存 |
| `long countCustomersOfEmp(String empId, String bizKind)` | **V1.0 实现**，缓存 15 min |
| `AllocVersionDTO getLatestAllocVersion(String bizKind)` | **V1.0 实现**，缓存 5 min |
| `AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate)` | **V1.0 实现**，不缓存 |

### 5.3 UOE 占位实现规范

```java
@Override
public Map<String, BigDecimal> getEmpMetricValues(
        String empId, LocalDate dataDate, List<String> metricCodes) {
    throw new UnsupportedOperationException(
        "MetricApi.getEmpMetricValues will be delivered in performance-engine-center V1.1");
}
```

**约定**：
- 所有 UOE 占位方法**仍需通过契约单测**（Mockito + 验证抛出 `UnsupportedOperationException`）
- 上层调用方（portal/report/customer）在 V1.0 若误调将得到明确信号，不是"返回 empty 静默失败"
- V1.1 交付时逐个替换实现，对外接口契约不变

### 5.4 DTO 设计约束

- 命名以 `DTO` 结尾，放在 `api/dto/` 包
- V1.0 采用 `class + Lombok @Data/@Builder` 方案（与已完成模块一致）
- 枚举字段用 `String`
- `LocalDate`/`LocalDateTime`/`BigDecimal` 透传
- 暴露 `valSlot`、`refMetricCodes` 等内部实现字段（报表和工作台需要）

### 5.5 Facade 实现规范

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

    @Override
    public List<MetricCardDTO> getUserMetricCards(String empId) {
        throw new UnsupportedOperationException(
            "MetricApi.getUserMetricCards will be delivered in V1.1");
    }
}
```

### 5.6 版本兼容

- V1.0 暴露的 Api 一旦发布即为稳定契约（7 个 Api 全部签名定型）
- V1.1 仅**替换 UOE 为真实实现**，不改签名
- 删除/重命名需走正式 deprecation 流程；新增方法允许

---

## 6. REST 控制器设计

### 6.1 Controller 层职责

1. HTTP 参数绑定、`@Valid` 校验
2. `@BizAuth` 权限声明（每方法必填）
3. `@AuditLog` 审计埋点（高危操作必填）
4. 调用 Facade → 封装 `ResponseWrapper<T>` 返回
5. **不写任何业务逻辑**

### 6.2 V1.0 所有 REST 端点清单（**共 35 个**）

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
| POST | `/api/perf/metrics/{metricCode}/slot/release` | PERF_METRIC_CONFIG / MANAGE | ✓ (reason 必填；仅 DISABLED 状态允许) |

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

**合计：10 + 9 + 4 + 3 + 3 + 2 + 4 = 35 个端点**

### 6.3 BizType 枚举 + PT_RESOURCE 前缀规则（修正 review #7、#10）

#### BizType 定义（与审计 `resourceType` 的映射关系）

```java
public enum PerfBizType {
    PERF_METRIC_CONFIG,   // → 审计 resourceType: PERF_METRIC_DEF / PERF_METRIC_REF
    PERF_KPI_CONFIG,      // → PERF_KPI_SCHEME / PERF_KPI_ITEM
    PERF_TARGET_CONFIG,   // → PERF_TARGET_PLAN
    PERF_TARGET_VALUE,    // → PERF_TARGET_VALUE
    PERF_ALLOC_QUERY,     // → (V1.0 只读，无写审计)
    PERF_RUN_TASK_QUERY,  // → (V1.0 只读)
    PERF_SYS_CONTROL      // → PERF_SYS_CONTROL
}
```

**映射原则**：
- `PerfBizType` 是"资源域"粒度，供 @BizAuth 鉴权使用
- `resourceType`（审计）是"表/实体"粒度，供 @AuditLog 切入使用
- 一个 BizType 可对应多个 resourceType（一对多）

#### PT_RESOURCE ID 命名规则

- **前缀**：`P_PERF_*`（遵循项目 `A_/G_/W_` 规则，`P` 表示 performance 模块）
- **长度**：每条 ≤ 20 字符
- **示例**：
  - `P_PERF_METRIC_LIST` / `P_PERF_METRIC_GET` / `P_PERF_METRIC_ADD` / `P_PERF_METRIC_UPD` / `P_PERF_METRIC_DEL` / `P_PERF_METRIC_STAT`
  - `P_PERF_METRIC_REFS` / `P_PERF_METRIC_RBY` / `P_PERF_METRIC_SLOT` / `P_PERF_METRIC_SREL`
  - `P_PERF_KPI_LIST` / `P_PERF_KPI_GET` / `P_PERF_KPI_ADD` / `P_PERF_KPI_UPD` / `P_PERF_KPI_DEL` / `P_PERF_KPI_PUB`
  - `P_PERF_KPI_ITEM_ADD` / `P_PERF_KPI_ITEM_UPD` / `P_PERF_KPI_ITEM_DEL`
  - `P_PERF_TGT_P_LIST` / `P_PERF_TGT_P_GET` / `P_PERF_TGT_P_ADD` / `P_PERF_TGT_P_UPD`
  - `P_PERF_TGT_V_LIST` / `P_PERF_TGT_V_ADD` / `P_PERF_TGT_V_BAT`
  - `P_PERF_ALLOC_CUR` / `P_PERF_ALLOC_HIS` / `P_PERF_ALLOC_SUM`
  - `P_PERF_RT_LIST` / `P_PERF_RT_GET`
  - `P_PERF_SC_GET` / `P_PERF_SC_HIS` / `P_PERF_SC_INIT` / `P_PERF_SC_SW`
- **登记文件**：`V1_0_1__performance_resources.sql` 共 35 行 INSERT

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
| `PERF-40904` | 版本切换并发冲突（Redis 锁获取失败） |
| `PERF-40905` | 状态不允许操作（如非 DISABLED 不可释放槽位） |
| `PERF-40906` | 方案未发布不可绑定目标 |
| `PERF-40910` | 目标值批量上限 500 |
| `PERF-40911` | 引用层级违规 |
| `PERF-50001` | 未预期服务端错误 |
| `PERF-50002` | 下游依赖异常 |

### 7.2 缓存策略

**Key 命名**：`perf:<domain>:<key>[:<subkey>]`

| Key 模式 | TTL | 数据 | evict 触发 |
|---|---|---|---|
| `perf:metric_def:{metricCode}` | 5 min | 单个指标 | create/update/disable/delete/status_change |
| `perf:metric_def:list:{baseDim}` | 5 min | 维度指标列表 | 该维度任一指标 create/update/disable/delete/status_change |
| `perf:kpi_scheme:{schemeId}` | 5 min | KPI 方案详情（含 items） | create/update/publish/delete/item 变更 |
| `perf:kpi_scheme:list` | 5 min | 启用方案列表 | 方案状态变更 |
| `perf:target_plan:{planId}` | 5 min | 目标方案 | create/update |
| `perf:sys_control:{scopeDim}` | 60 s | 当前有效版本 | switchVersion 成功后 |
| `perf:alloc:cur:{custId}:{bizKind}` | 10 min | 客户当前分配关系 | V1.0 不 evict（V1.2 分配调整审批后 evict） |
| `perf:alloc:emp:{empId}:{bizKind}` | 15 min | 员工名下客户 | V1.0 不 evict |
| `perf:alloc:empcount:{empIds hash}:{bizKind}` | 30 min | 员工客户数批量 | V1.0 不 evict |
| `perf:alloc:latestver:{bizKind}` | 5 min | 最新分配版本号 | V1.0 不 evict |

**事务后失效**：所有 evict 通过 `TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit` 回调触发。

### 7.3 审计埋点

**路径**：`@AuditLog` → AOP → 发布 `AuditLogEvent` → `AuditApi.log()` 落库

**BizType ↔ resourceType 映射**：见 §6.3

| resourceType | 触发场景 | reason 要求 |
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

### 7.5 事务边界与 Redis 锁正确时序

| 场景 | 锁 / 事务策略 |
|---|---|
| Service 写方法 | `@Transactional(rollbackFor = Exception.class)` REQUIRED |
| Service 查询方法 | `@Transactional(readOnly = true)` 或不加 |
| Facade 层 | 不加事务或 `@Transactional(readOnly = true)` |
| Facade 层**申请 Redis 锁** | 在 `@Transactional` 外层；获取成功后调用 Service 事务方法；finally 释放 |
| `create/update` 调 `setRefs` | 同事务（REQUIRED） |
| `switchVersion` | Facade 锁外层 → Service 事务内完成 UPDATE+INSERT；Facade finally 释放锁 |
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

- Service UT：~100 个
- Mapper IT：~60 个
- Controller IT：~30 个
- Facade UT：~14 个（含 UOE 占位契约测试）

### 8.2 测试分层与事务策略（修正 review #5）

| 层 | 框架 | 配置 | 数据库 | 事务策略 |
|---|---|---|---|---|
| Service UT | JUnit 5 + Mockito + AssertJ | 纯单元 | 全 mock | 无 |
| **Mapper IT（单线程）** | `@SpringBootTest` + `@MybatisTest` | 真 MyBatis | 真 `onepl` | `@Transactional + @Rollback(true)`（默认） |
| **Mapper IT（并发测试）** | 同上 + `@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "cleanup.sql")` | 真 MyBatis | 真 `onepl` | **不使用 `@Transactional`**；用数据前缀 + `AFTER_TEST_METHOD` 的 `@Sql` 清理 |
| Facade UT | JUnit 5 + Mockito | 纯单元 | 全 mock | 无 |
| Controller IT | `@SpringBootTest(MOCK)` + MockMvc | 完整上下文 | 真 `onepl` | `@Transactional + @Rollback(true)` |

**并发测试例外规则**：
- Spring 事务与多线程不兼容（线程本地绑定）
- 涉及并发的测试类（如 `SysControlMapperConcurrentIT`、`MetricSlotConcurrentIT`）**必须**：
  - 不标注 `@Transactional`
  - 使用独立数据前缀（如 `CONCURRENT_SC_`）
  - 在 `@AfterEach` 或 `@Sql(executionPhase = AFTER_TEST_METHOD, ...)` 显式 DELETE 清理
- 单线程 Mapper IT 继续使用 `@Transactional + @Rollback`，两者测试类文件分离

### 8.3 测试基础设施

**基类**：
- `PerformanceMapperTestBase`（单线程 IT 用，含 `@Transactional`）
- `PerformanceConcurrentTestBase`（并发 IT 用，不含事务，含 `@AfterEach` 前缀清理）
- `PerformanceControllerTestBase`
- `PerformanceServiceTestBase`

**工具**：
- `TestDataBuilder`（Fluent Builder）
- `MockCurrentUserHelper`
- `TestDbCleaner`（按前缀批量清理）

**@Cacheable 测试处理**：
- Facade UT 默认禁用 Spring Cache（通过 `@Import(NoOpCacheConfiguration.class)` 覆盖）
- 避免 UT 变成集成测试

### 8.4 TDD 闭环示例

```
红 (Red)：写测试 → 编译失败或断言失败 → 提交 "test: red - allocSlot"
绿 (Green)：最小实现让测试通过 → 提交 "feat: green - allocSlot basic"
重构 (Refactor)：添加边界与并发场景 → 提交 "refactor: allocSlot concurrent"
```

CLAUDE.md TDD 红线：
- 严禁"先写实现再补测试"
- 测试必须先于实现提交
- 红→绿→重构三步单独提交

### 8.5 关键场景测试清单

**sys_control**（含并发）：
- `SysControlMapperIT.switchVersion_whenUkViolation_shouldThrow`（单线程，@Transactional）
- `SysControlConcurrentIT.switchVersion_concurrentTwoThreads_onlyOneWins`（**不含 @Transactional**）
- `SysControlFacadeUT.switchVersion_whenLockAcquireFailed_shouldThrow40904`（UT，mock Redis）
- `SysControlServiceUT.initIfAbsent_whenExists_shouldNotInsertDup`

**指标库**：
- `MetricSlotServiceUT.allocSlot_basicAllocation` × 4 边界
- `MetricSlotConcurrentIT.allocSlot_concurrentTwoThreads_onlyOneWins`（**不含 @Transactional**）
- `MetricCycleDetectServiceUT.checkNoCycle_simpleSelfRef_shouldThrow40902`
- `MetricCycleDetectServiceUT.checkNoCycle_indirectRef_A_B_A_shouldThrow40902`
- `MetricCycleDetectServiceUT.checkLevelConstraint_L2RefL3_shouldThrow40911`
- `MetricDefMapperIT.create_whenMetricCodeDup_shouldThrow40903`
- `MetricDefServiceIT.update_withRefsChange_shouldUpdateBothJsonAndRefTable`

**KPI 方案**：
- `KpiSchemeServiceUT.publish_whenItemReferMissingMetric_shouldThrow`
- `KpiSchemeServiceUT.publish_whenItemReferDraftMetric_shouldThrow`
- `KpiItemServiceUT.addItem_whenDuplicate_shouldThrow`

**目标方案**：
- `TargetValueServiceUT.upsertBatch_whenSizeExceeds500_shouldThrow40910`
- `TargetValueServiceUT.upsertBatch_whenAllSuccess_shouldReturnCount`
- `TargetValueMapperIT.upsert_existingRow_shouldUpdate`

**分配关系**：
- `AllocRelationMapperIT.getCurrentAllocations_excludesExpired`
- `AllocRelationServiceUT.getAllocSummary_sumRatio_equals100`
- `AllocRelationServiceUT.listCustomersByEmp_appliesScopeFilter`

**UOE 占位契约测试**（Facade UT）：
- `MetricApiImplTest.getUserMetricCards_shouldThrowUnsupportedOperationException`
- `KpiApiImplTest.getCurrentKpiTotal_shouldThrowUOE`
- `PerfCalcApiImplTest.triggerKpiCalc_shouldThrowUOE`
- ... 每个 UOE 方法都有一个契约测试

**Controller 层抽样**：
- 401（未登录）/ 403（权限不足）/ 200（成功）/ 409（业务冲突）

### 8.6 测试数据隔离

- 单线程 IT：`@Transactional + @Rollback(true)` 全局回滚
- 并发 IT：独立数据前缀 + `@AfterEach` / `@Sql(AFTER_TEST_METHOD)` 清理
- 子代理间并行的前缀分配：
  - sys_control 子代理：`TEST_SC_*` / 并发前缀 `CONCUR_SC_*`
  - 指标库子代理：`TEST_METRIC_*` / `CONCUR_METRIC_*`
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
5. 定义全部枚举与 `PerfErrorCode`（含全部 `PERF-*` 错误码）
6. 编写 `PerfException`
7. **定义 7 个对外 Api 接口文件（含完整方法签名）+ 全部 DTO 类文件**（子代理在阶段 1 各自实现 Facade，但接口文件必须在阶段 0 统一定义好）
8. 编写三个 SQL 脚本：
   - `V1_0_0__performance_ddl.sql`（13 张表 DDL，含 §3.1 的 4 项调整）
   - `V1_0_1__performance_resources.sql`（35 条 PT_RESOURCE，遵循 `P_PERF_*` 规则）
   - `V1_0_2__performance_dicts.sql`（10 类字典 + 对应字典项）
9. 编写 `CLAUDE.md`
10. 编写测试基础设施（基类、工具、配置）

**验收**：
- `mvn clean install -pl performance-engine-center` 通过
- 本地 MySQL `onepl` 13 张表存在（含 §3.1 调整）
- 枚举、Api 接口、DTO 可被其他子代理 import

### 9.3 阶段 1：子域全栈并行（P1-1 ~ P1-6）

| 子代理 | 主要交付物 |
|---|---|
| **P1-SysControl** | SysControl Entity/Mapper/XML/Service/Facade/Controller + 测试（含并发 IT） |
| **P1-Metric** | Metric + MetricRef Entity/Mapper/XML、4 Service、`MetricApiImpl`+`MetricQueryApiImpl`（UOE 占位方法实现）、MetricDefController(10) + 全测试 |
| **P1-Kpi** | KpiScheme + KpiItem Entity/Mapper/XML、2 Service、`KpiApiImpl`（`getKpiScheme`/`getKpiSchemeById` 实现 + 3 个 UOE 占位）、KpiSchemeController(9) + 全测试 |
| **P1-Target** | TargetPlan + TargetValue Entity/Mapper/XML、2 Service、`TargetApiImpl`（全部实现）、2 Controller(4+3) + 全测试 |
| **P1-RunTask** | PerfRunTask Entity/Mapper/XML、Service（仅查询）、`PerfCalcApiImpl`（`getRunTask` 实现 + 2 个 UOE 占位）、Controller(2) + 测试 |
| **P1-Alloc** | CustAllocRelation Entity/Mapper/XML、AllocRelationService(10 方法)、`AllocApiImpl`（全部实现）+`DataTaskApiImpl`（1 个 UOE 占位）、Controller(3) + 全测试 |

**共同约定**：
- 严格 TDD：先红 → 绿 → 重构
- 测试数据前缀见 8.6
- `mvn clean test -pl performance-engine-center` 必须全绿
- 禁止修改 pom.xml、bootstrap、其他子代理的文件
- 新增 `PerfErrorCode` 在末尾追加
- UOE 占位方法**必须**有契约测试验证抛出 UnsupportedOperationException

### 9.4 阶段 2：集成收敛（P2）

**职责**：
1. 修改 `bootstrap/pom.xml` 添加 `performance-engine-center` 依赖
2. 确认 `@ComponentScan`/`@MapperScan` 覆盖 performance 包
3. 修改根 `pom.xml` 注册 `<module>performance-engine-center</module>`
4. 统一审查 `PerfErrorCode`（去重、冲突）
5. 验证 `mvn clean package` 通过
6. **执行 3 个 SQL 脚本**（onepl 库）：
   - `V1_0_0__performance_ddl.sql`（仅首次，已执行可跳过）
   - `V1_0_1__performance_resources.sql`（INSERT ON DUPLICATE KEY UPDATE 模式，可重复执行）
   - `V1_0_2__performance_dicts.sql`（同上）
7. 启动 bootstrap，验证 Knife4j UI 显示全部 35 个端点
8. 执行冒烟测试（curl 或 Knife4j）：
   - `GET /api/perf/sys-control?scopeDim=EMP`
   - `GET /api/perf/metrics?pageNo=1&pageSize=10`
   - `GET /api/perf/kpi-schemes?pageNo=1&pageSize=10`
   - `GET /api/perf/target-plans?pageNo=1&pageSize=10`
   - `POST /api/perf/sys-control/init`（成功后验证 sys_control 表有三条 is_valid=1 记录）
9. 运行全量 `mvn clean test`

**验收**：
- `mvn clean package` 通过
- bootstrap 可启动，Knife4j 展示全部 35 个端点
- PT_RESOURCE 表新增 35 条 `P_PERF_*` 资源
- 字典表新增 10 类字典 + 对应项
- 冒烟测试全绿
- 全量 mvn test 通过

### 9.5 阶段 3：代码审查（P3）

使用 `code-reviewer` 子代理，审查重点：

1. TDD 节奏（git 历史体现红→绿→重构）
2. CLAUDE.md 规范（`@BizAuth`、`@AuditLog`、跨模块调用、中文注释、UTF-8）
3. 设计文档对照（35 端点、7 个 Api、UOE 占位完整性）
4. 错误码完整性
5. 缓存一致性（事务后 evict）
6. SQL 安全（`#{}` vs `${}`）
7. 空值处理（Optional）
8. 测试质量（并发测试事务策略是否正确，UOE 契约测试是否齐全）

**产出**：
- `docs/superpowers/sessions/<date>-perf-v1.0-code-review.md`
- 问题清单（Must Fix / Should Fix / Nice to Have）
- Must Fix 由相关子代理修复

### 9.6 交付物总览

| 分类 | 数量 |
|---|---|
| Java 源文件 | ~135（含 7 个 Api 接口 + 15 个 DTO） |
| MyBatis XML | 9 |
| SQL 脚本 | 3 |
| 测试文件 | ~65（含并发 IT 与 UOE 契约测试） |
| 配置文件 | 3 |
| 文档 | 1（CLAUDE.md） |
| REST 端点 | **35** |
| 对外 Api | **7**（共 33 方法：V1.0 实现 20 方法 + V1.1 UOE 占位 13 方法） |
| 数据表 | 13 |
| PT_RESOURCE 登记 | 35 条 `P_PERF_*` |
| 字典 | 10 类 |

### 9.7 完成标准（Definition of Done）

1. ✅ `mvn clean package` 通过
2. ✅ `mvn clean test` 全部通过，覆盖率达标（Service 行覆盖 ≥ 80%，分支 ≥ 70%）
3. ✅ bootstrap 可启动，Knife4j 正常展示全部 35 端点
4. ✅ 冒烟测试 5 个关键端点返回 200
5. ✅ Code-reviewer 子代理无 Must Fix
6. ✅ git 历史体现 TDD 节奏
7. ✅ PT_RESOURCE（35 条 `P_PERF_*`）、字典（10 类）、sys_control 初始数据就位
8. ✅ 模块级 CLAUDE.md 已撰写
9. ✅ 所有 UOE 占位方法有契约测试

---

## 10. 风险与缓解

| 风险 | 级别 | 缓解 |
|---|---|---|
| 阶段 1 六个子代理同时改 `PerfErrorCode` | 中 | 约定末尾追加，阶段 2 统一去重 |
| Mapper IT 并行跑导致数据冲突 | 中 | 数据前缀隔离（§8.6）+ `@Transactional + @Rollback`；并发测试单独处理 |
| Redis 未就绪导致缓存相关测试失败 | 低 | `application-test.yml` 允许 Redis 降级 |
| DDL 与 Entity 字段不一致 | 中 | Mapper IT 强制命中每个字段，MyBatis 映射失败即报错 |
| bootstrap 启动失败（依赖冲突） | 中 | 阶段 2 必须执行启动冒烟测试 |
| TDD 节奏被子代理忽略 | 高 | code-reviewer 审查 git 历史，Must Fix |
| 槽位分配并发场景漏测 | 中 | 专门的 `MetricSlotConcurrentIT`（不含 @Transactional） |
| **V1.0 契约偏离权威 04 文档致 V1.1 被迫改动** | 高 → **已消除** | v1.1 修订：7 个 Api 全部对齐 04 契约签名 + UOE 占位策略 |
| **并发测试与 @Transactional 冲突** | 高 → **已消除** | v1.1 修订：并发测试独立基类 + 数据前缀 + `@Sql(AFTER_TEST_METHOD)` 清理 |
| **Redis 锁时序错误导致锁未包住事务** | 高 → **已消除** | v1.1 修订：明确 Facade 层申请锁，事务 Service 被锁包围 |
| **planId 类型三边不一致** | 中 → **技术债** | v1.2 修订：对齐生产 DDL 用 `String`，04 契约的 `Long` 标为技术债；TargetApi 方法签名统一 String |

---

## 11. 后续计划（V1.1 / V1.2 预告）

**V1.1 计算与导入**：
- 指标执行（SQL 执行器 + Groovy 沙箱 + 级联刷新）
- KPI 计算引擎
- 数据导入（统一入口，三种类型）
- 外部数据上报接收（`/api/data-task/status`）
- 定时任务（日终指标/KPI 计算）
- **替换所有 UOE 占位**为真实实现（MetricApi 4 个计算方法、KpiApi 3 个结果方法、MetricQueryApi 3 个快照方法、PerfCalcApi 2 个触发方法、DataTaskApi 1 个上报方法）

**V1.2 业务流程与回算**：
- 分配关系调整审批（对公/零售分流）
- 目标修正审批
- 历史回算引擎
- 所有导出接口（4 个高危）
- 事件发布（TargetAdjustApprovedEvent、AllocAdjustApprovedEvent、KpiCalcCompletedEvent）
- 分配关系缓存的 evict 触发

---

## 12. 附录

### 12.1 V1.0 vs V1.1 Api 方法矩阵

| Api | V1.0 实现 | V1.0 UOE 占位 | 总计 |
|---|---|---|---|
| MetricApi | `getMetricDef`, `getMetricDefs`, `listMetrics`（3） | `getUserMetricCards`, `getEmpMetricValues`, `getOrgMetricValues`, `getCustMetricValues`（4） | 7 |
| MetricQueryApi | — | `batchQueryEmpSnapshots`, `batchQueryOrgSnapshots`, `batchQueryCustSnapshots`（3） | 3 |
| KpiApi | `getKpiScheme`, `getKpiSchemeById`（2） | `getCurrentKpiTotal`, `getCurrentKpiResult`, `getKpiHistory`（3） | 5 |
| TargetApi | `getTargetPlan`, `getTargetPlanById`, `getTargetValue`, `listTargetValues`（4） | — | 4 |
| PerfCalcApi | `getRunTask`（1） | `triggerKpiCalc`, `triggerRecalc`（2） | 3 |
| DataTaskApi | — | `reportDataTaskStatus`（1） | 1 |
| AllocApi | 全部 10 方法 | — | 10 |
| **合计** | **20 方法** | **13 方法** | **33 方法** |

### 12.2 相关文档

- 功能规格：`docs/modules/performance-engine-center/01-功能规格.md`
- 后端架构：`docs/modules/performance-engine-center/02-后端架构.md`
- 接口设计：`docs/modules/performance-engine-center/03-接口设计与报文.md`
- **对外 API 契约（权威）**：`docs/modules/performance-engine-center/04-对外API契约.md`
- 表结构 DDL：`docs/modules/performance-engine-center/05-表结构DDL.md`
- 并发与事务：`docs/modules/performance-engine-center/06-并发与事务策略.md`
- 审计要求：`docs/modules/performance-engine-center/07-审计要求.md`
- 初始化数据：`docs/modules/performance-engine-center/08-初始化数据清单.md`
- 依赖契约：`docs/modules/performance-engine-center/09-依赖契约摘要.md`
- DDL 权威源：`docs/schema/ddl-performance.sql`
- 共通开发规范：`docs/common-dev-guide.md`
- 项目根规范：`CLAUDE.md`

### 12.3 变更历史

| 日期 | 版本 | 变更 | 作者 |
|---|---|---|---|
| 2026-04-15 | v1.0 | 初稿，经 5 轮澄清 + 9 节分节确认后定稿 | leid |
| 2026-04-15 | v1.1 | 根据首轮 spec review 意见修订 10 项：Api 权威对齐+UOE 占位、planId 统一 Long、DDL UK 补齐、Redis 锁时序修正、并发测试例外策略、端点数统一 35、PT_RESOURCE 前缀 `P_PERF_*`、阶段 2 集成 SQL 执行、槽位释放语义明确、BizType/resourceType 映射表 | leid |
| 2026-04-15 | v1.2 | **环境探针后回退 v1.1 的 4 项 DDL 调整**：① `perf_target_plan.id` 保持 `varchar(32)`（TargetApi 改回 `String planId`），② 配置表不加 `deleted` 列，③ 配置表 UK 不含 `deleted`，④ 槽位唯一由 Redis 锁 + Service 业务校验保证（无 DB UK）；PT_RESOURCE 实际列名确认（`RESOURCE_URL/RESOURCE_METHOD/MENU_NAME/SYS_CODE`，无 `BIZ_TYPE/ACTION` 字段，BizType 存于 `pt_role_biz_scope` 表）；字典表实际为 `sys_dict + sys_dict_item` | leid |
