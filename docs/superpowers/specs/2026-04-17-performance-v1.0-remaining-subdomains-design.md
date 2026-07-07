# performance-engine-center V1.0 剩余 5 子域开发需求细化设计

| 元数据 | 值 |
|---|---|
| Spec 类型 | 增量设计规格（Incremental Design Spec） |
| 目标模块 | `performance-engine-center`（V1.0 配置与版本骨架） |
| 覆盖范围 | 剩余 5 个子域：指标库 / KPI 方案 / 目标方案 / 运行任务 / 分配关系 |
| 编写日期 | 2026-04-17 |
| 作者 | leid（通过 brainstorming 确认） |
| 与原 spec 的关系 | **增量补充**，不替代原 [2026-04-15-performance-engine-center-v1.0-design.md](2026-04-15-performance-engine-center-v1.0-design.md)（v1.2） |
| 前置审计 | 本 spec 基于 2026-04-17 对已交付骨架 + sys_control 子域的代码审计（0 个 P0/P1 整改项） |

---

## 0. 为什么需要这份 spec

原 V1.0 spec（v1.2）是**全景设计**，覆盖骨架 + 6 个子域 + 35 REST 端点 + 7 对外 Api。当前交付进度：

- ✅ 骨架层（18 任务全部完成）
- ✅ 子域 1 — sys_control（Mapper / Service / Facade / Controller / 并发 IT 全栈完成，含 refactor）
- ⏳ 剩余 5 子域未动：指标库 / KPI 方案 / 目标方案 / 运行任务 / 分配关系

原 plan 的编排假设**6 子代理并行**（P1-A ~ P1-F），但当前交付模式是**单人串行**。本 spec 在原 spec 之上，补齐三类细化内容（原 spec 未覆盖）：

1. **串行交付顺序与风险排序** — 原 plan 是并行，需要重新确定单人顺序
2. **TDD 任务粒度模板** — 原 spec §8 只定义了测试金字塔，未给出 commit 级操作规范
3. **每子域的差异与难点清单 + 验收 DoD** — 原 spec 以全景设计为主，未细化"执行时才会发现的陷阱"

本 spec 不重复原 spec 已有内容（架构 / 分层契约 / DTO 定义 / 错误码 / 缓存 key 规则 / 数据范围策略 / Redis 锁时序 等），读者须配合原 spec 阅读。

---

## 1. 前置审计结论（2026-04-17 审计）

对 `performance-engine-center/` 已交付代码（60 个文件，含骨架 + sys_control 子域）做了逐条对照原 spec v1.2 的审计：

| 维度 | 结果 |
|---|---|
| 骨架层 18 任务 | ✅ 全部满足（7 Api / 14 DTO + 1 Cmd / 8 枚举 / 25 错误码 / 3 SQL / 7 测试基类 / 3 Spring 配置） |
| sys_control 子域 | ✅ 全部满足（Redis 锁 Lua compare-and-del 原子脚本 / initIfAbsent 幂等 / Controller 4 路径 @BizAuth + @AuditLog 精确对齐 / TDD 节奏严格红绿重构） |
| TDD 纪律（git 历史） | ✅ 30 条 commit 严格遵守 `test: red → feat: green [→ refactor]`，无混合 commit |
| 刻意决策 | ✅ CLAUDE.md 明文说明：`PerfBizType` 不定义（复用 common-security `BizType.PERF_CONFIG`）；`SysControl` 不继承 `BaseEntity`（版本控制基础设施特例） |

**整改项**：P0 = 0，P1 = 0，P2 = 2（建议保留，非必改）。

**结论**：剩余工作**无需任何整改前置**，直接进入剩余 5 子域的 TDD 开发。

---

## 2. 串行交付顺序与风险排序

### 2.1 交付顺序

| 顺位 | 子域 | 复杂度 | 关键风险点 | 排在此位的理由 |
|---|---|---|---|---|
| 1 | **指标库**（Metric + MetricRef + MetricSlot + CycleDetect） | ⭐⭐⭐⭐⭐ | Redis 分布式锁（槽位分配）/ 双写一致性（JSON + 独立 ref 表）/ DFS 环路检测 / 层级校验 / 缓存 afterCommit evict | 最复杂，一次性验证全部通用模式；后续 4 子域是模式子集 |
| 2 | **KPI 方案**（KpiScheme + KpiItem） | ⭐⭐⭐ | 跨表引用校验（item.metricCode ∈ perf_metric_def PUBLISHED）/ 父子表单事务 CRUD / 发布完整性校验 | 依赖指标库（校验 metricCode）；复用父子表 + 跨表校验模式 |
| 3 | **目标方案**（TargetPlan + TargetValue） | ⭐⭐⭐ | `INSERT ON DUPLICATE KEY UPDATE` upsert / 批量上限 500 / planId 类型 String 与 04 契约 Long 不一致的技术债 | 依赖 KPI 方案（TargetPlan.kpi_scheme_id → perf_kpi_scheme）；复用父子表模式 |
| 4 | **运行任务**（PerfRunTask） | ⭐⭐ | 只读 + 数据范围过滤（管理员全见 / 普通用户仅见自己 started_by）；V1.0 不提供写入 | 简单，收尾验证数据范围 SQL 片段 |
| 5 | **分配关系**（CustAllocRelation） | ⭐⭐⭐ | 10 查询方法全部实现 / 时间线查询 / 批量缓存合并 / 数据范围 emp_id 过滤 / 派生 sys_control CUST 维度版本号 | 依赖 sys_control（已完成），放最后最稳 |

### 2.2 决策理由

1. **风险前置**：最复杂的指标库排第 1，若架构问题（如槽位锁时序缺陷）出现，可在最早期纠偏
2. **模式通用性**：指标库一次性实现「父子表 / 双写 / Redis 锁 / 缓存 evict / 审计 reason / DFS 图算法」全部通用模式；后续 4 子域是其子集复用
3. **依赖链自然顺序**：KPI → 目标 → 分配 是数据模型引用链（方案 → 目标 → 分配），顺序不变则不需要 Mock 上游
4. **TDD 心智负担收敛**：新模式集中在前 1-2 个子域学习，后 3 个子域是纯复用，心智负担递减

### 2.3 对原 plan 的偏离

- 原 plan 子代理编号（P1-A ~ P1-F）作废，改为串行顺位 1~5
- 原 plan 测试数据前缀隔离策略**保留**（`TEST_SC_*` / `TEST_METRIC_*` / `TEST_KPI_*` / `TEST_TGT_*` / `TEST_RT_*` / `TEST_AR_*`），用途从"子代理冲突隔离"改为"并发 IT 数据清理职责划分"

---

## 3. TDD 任务粒度模板

### 3.1 三元组"单元"定义

**不是每方法一对红绿**（太细），**而是每层一对红绿**：

| 层 | 一对红绿的范围 |
|---|---|
| Mapper 层 | 一个 Entity 的全部标准 CRUD + 2-3 个业务查询一次性写完测试 → 红；最简 XML → 绿 |
| Service 层 | 一个 Service 类的全部 public 方法测试 → 红；最简实现 → 绿 |
| Facade 层 | 一个 ApiImpl 的实现方法 + UOE 占位契约测试 → 红；实现 → 绿 |
| Controller 层 | 一个 Controller 全部端点的 IT（含 401/403/200/400/409 抽样）→ 红；Controller + 请求 DTO → 绿 |
| 并发 IT 层 | 涉及并发的单一场景（槽位分配、upsert）一对红绿，**不含 @Transactional** |

**refactor commit 仅在以下情况出现**（非每层必有）：
- 补红测试暴露边界漏洞（如 sys_control 的 `initIfAbsent 历史均失效返回最近一条`）
- 重复代码 3 次以上可提取（否则 YAGNI）

### 3.2 Commit 消息规范（强制格式）

```
test(perf): red - <子域> <层> <最小特征描述>
feat(perf): green - <子域> <层>, <测试数> <UT|IT> 通过
refactor(perf): <子域> <边界场景> - <修复点>
```

真实示例（已在 sys_control 验证）：
- `test(perf): red - SysControlService UT + SwitchVersionCmd`
- `feat(perf): green - SysControlService, 8 UT 通过`
- `refactor(perf): initIfAbsent 当历史均失效时返回最近一条, 避免 null`

### 3.3 每子域的 TDD commit 预期数量

| 子域 | Mapper IT | Service UT | Facade UT | Controller IT | 并发 IT | refactor | 小计 |
|---|---|---|---|---|---|---|---|
| 指标库 | 1 对 | 4 对 | 1 对 | 1 对 | 1 对 | 1-2 | **18-20** |
| KPI 方案 | 1 对 | 2 对 | 1 对 | 1 对 | — | 1 | **11** |
| 目标方案 | 1 对 | 2 对 | 1 对 | 2 对 | 可选 1 对 | 1 | **14-16** |
| 运行任务 | 1 对 | 1 对 | 1 对 | 1 对 | — | 0-1 | **8-10** |
| 分配关系 | 1 对 | 1 对 | 2 对 | 1 对 | — | 1 | **12-14** |

**剩余 5 子域 commit 总量预估：63-71 个**（约 2-3 周单人串行）。

### 3.4 每层红测试的"必写清单"（硬性）

**Mapper IT 红必含**：
1. insert 成功 → selectById 查回
2. UK 冲突 → 抛 `DuplicateKeyException`
3. 分页查询（`selectByCondition` + `countByCondition`）
4. 动态 SQL 的不同 `<if>` 分支至少各覆盖 1 次

**Service UT 红必含**（Mockito）：
1. 正常主路径
2. 每个业务校验失败分支（对应一个 PERF-xxxxx）
3. 幂等性（若声明）
4. 依赖全部 mock

**Facade UT 红必含**：
1. V1.0 实现方法：调 Service + Entity→DTO 转换 + 缓存注解存在
2. UOE 占位方法：契约测试 `assertThatThrownBy(...).isInstanceOf(UnsupportedOperationException.class)`
3. 含 Redis 锁的方法：锁获取失败 → PERF-40904；Service 异常 → 仍释放锁

**Controller IT 红必含**（MockMvc + 真实 @BizAuth）：
1. 200 成功
2. 401 未登录
3. 403 权限不足
4. 400 参数校验失败（@Valid）
5. 409 业务冲突（UK / 状态不允许）

**并发 IT 红必含**（仅指标库槽位 + 目标值 upsert 涉及）：
1. 两线程同时执行 → 只一个成功
2. 独立前缀数据，`@AfterEach` 显式清理
3. 不继承 `PerformanceMapperTestBase`，改继承 `PerformanceConcurrentTestBase`

### 3.5 注解加入时机（硬性）

| 注解 | 加入时机 |
|---|---|
| `@BizAuth(bizType=PERF_CONFIG, action=...)` | Controller green commit 必加 |
| `@AuditLog(action=..., resourceType=..., reasonRequired=true)` | Controller green commit 必加（高危操作） |
| `@Cacheable(cacheNames=..., key=...)` | Facade green commit 必加 |
| `@Transactional(rollbackFor=Exception.class)` | Service green commit 必加 |
| `TransactionSynchronizationManager.afterCommit` evict | Facade green commit 必加（涉及缓存时） |

**禁止**：green 期写核心逻辑，refactor 期补注解。注解是业务正确性的一部分，必须在绿期就存在。

---

## 4. 每子域的差异与难点清单（不重复原 spec）

### 4.1 指标库子域（风险最高）

**数据层陷阱**
- `perf_metric_def.ref_metric_codes` 字段：按 `ddl-performance.sql` 实际类型读取（MySQL 8 的 JSON 或 TEXT），Entity 用 `String`，不用 `JsonNode`；Service 用 bootstrap 已有 `ObjectMapper` 序列化，不自建
- **无 DB UK 的槽位唯一性**：`IDX(val_slot)` 是普通索引不是 UK（v1.2 决策），靠 Redis 锁 + Service 业务校验双保险；开发者容易误以为"有 UK 兜底"省略锁实现 —— 这是本子域最易错点
- L1/L2/L3 槽位区间（1-100/101-150/151-200）是**业务约定非 DB 约束**，校验在 `MetricSlotService.allocSlot`，DB 不会拒绝越界槽位

**槽位分配算法的正确实现**
- 锁 key `perf:slot-alloc:{baseDim}`（每维度一把锁），**不是每指标一把锁**
- 算法：`SELECT val_slot FROM perf_metric_def WHERE base_dim=? AND val_slot IS NOT NULL` 取占用集 → 在 `[levelStart, levelEnd]` 找最小未用 slot → INSERT；**不是 `MAX(val_slot)+1`**（释放过的 slot 要回收）
- 释放只允许 DISABLED 指标；reason 必填；抛 PERF-40905

**DFS 环路检测的测试设计**
- `MetricCycleDetectService.checkNoCycle(existingGraph, newNode, newRefs)` 做成**纯函数**（Map + String 入参）
  - 优点：Service UT 全 mock 图，无需 DB，100+ 边界可快速覆盖
  - 边界必测：自引用 A→A / 二元环 A↔B / 三元环 A→B→C→A / 菱形无环 A→B→D, A→C→D / 深度链
- 层级校验先于环路检测：`checkLevelConstraint` 失败直接抛 PERF-40911，不进入图遍历

**双写一致性实现细节**
- `MetricDefService.create` 事务内顺序：INSERT perf_metric_def（含 ref_metric_codes JSON）→ `MetricRefService.setRefs`（DELETE + INSERT perf_metric_ref）→ afterCommit evict 缓存
- 事务失败：JSON 和 ref 表一起回滚，无 compensation
- **禁止**在 Service 内调 Facade 的 `@Cacheable` 方法（Spring AOP 自调用失效），evict 必须走 `TransactionSynchronizationManager.registerSynchronization`

**缓存 evict 规则**

| 操作 | evict 目标 |
|---|---|
| create/update/disable 单条 | `perf:metric_def:{metricCode}` + `perf:metric_def:list:{baseDim}` |
| 批量操作 | 涉及的全部单 key + 涉及维度的 list key |
| 槽位释放 | 同上（指标状态已变） |

全部 afterCommit，**不得在事务内 evict**。

---

### 4.2 KPI 方案子域

**父子表 CRUD 的单事务约束**
- `KpiSchemeService.create(scheme, items)`：单事务 INSERT scheme → 遍历 INSERT items；任何一项失败整体回滚
- `updateItem` / `removeItem` 独立事务
- **易漏**：新增 item 时必须校验 `metric_code ∈ perf_metric_def AND status=PUBLISHED` — 这是跨模块引用校验（调用 `MetricDefService.getByCodeOrNull`），抛 PERF-40906

**发布态流转**
- 唯一路径 `DRAFT → PUBLISHED`；发布时二次校验所有 item 的 metric 发布态（防并发：A 发 KPI 的同时 B 禁用某 metric）
- `PUBLISHED → DISABLED` 需 reason 必填；若有下游 `perf_target_plan.kpi_scheme_id` 引用，允许禁用但记审计警告（不阻断，因 plan 可引用历史 snapshot）

**权重校验的反直觉规则**
- `sum(item.weight)` **不强制 = 100**（原 spec §4.5）；DTO 验证层**不要加此校验**，否则 V1.1 计算时会拦截合法方案
- `weight=0` 允许（该 item 不计分）

**缓存 evict 链**
- `perf:kpi_scheme:{schemeId}` 和 `perf:kpi_scheme:list` **一起 evict**；scheme 或 item 任一变更都触发

---

### 4.3 目标方案子域

**planId 技术债对齐**
- 04 契约文档写 `Long planId`，`perf_target_plan.id = varchar(32)`（v1.2 不改 DDL）
- V1.0 所有签名用 **`String planId`**：Controller `@PathVariable String id` / Service / Facade / DTO 字段 / Mapper
- 这是**已知技术债**，不要"对齐 04 契约"改回 Long

**upsert 的 MyBatis 写法**

```xml
<insert id="upsertBatch">
  INSERT INTO perf_target_value (...)
  VALUES <foreach collection="list" item="item" separator=",">
    (...)
  </foreach>
  ON DUPLICATE KEY UPDATE
    target_value = VALUES(target_value),
    base_value = VALUES(base_value),
    updated_time = NOW(),
    updated_by = VALUES(updated_by)
</insert>
```

- UK：`(plan_id, subject_type, subject_id, cycle_key, metric_code)`
- 禁用 `INSERT IGNORE`（静默跳过违反 upsert 语义）

**批量 500 上限**
- DTO `@Size(max=500)` + Service 兜底 `if (list.size() > 500) throw PERF-40910`（双保险）

**日期 + 跨表校验**
- `effective_date <= expire_date`（Service 层，DTO 跨字段校验麻烦）
- Plan 必须引用 PUBLISHED 状态的 KPI 方案（复用 PERF-40906）

---

### 4.4 运行任务子域（最轻）

**纯只读原则**
- Service 只有 4 个 public 方法：`getById / getByTaskNo / page / countByTypeAndDate`
- 无 insert/update/delete（V1.1 由计算引擎通过**内部**方法写入，不暴露在 V1.0 Service）

**数据范围的 SQL 片段**
- 管理员：无过滤
- 普通用户：`AND started_by = #{currentUserId}`
- 实现：MyBatis XML `<if test="dataScopeFilter != null">${dataScopeFilter}</if>`，filter 片段由 `auth-permission-center.DataScopeApi.resolveScope` 注入（`${}` 是唯一合法的非 `#{}` 场景）

**V1.0 的 UOE 边界**
- `PerfCalcApiImpl`：`triggerKpiCalc` / `triggerRecalc` UOE；`getRunTask` V1.0 实现
- **易错**：3 方法全实现 或 全 UOE，都是错的

---

### 4.5 分配关系子域（方法最多）

**V1.0 不做 evict**
- 10 V1.0 实现方法，全部只做 TTL 自然过期；V1.2 分配调整审批后才引入 evict
- TTL 语义：10min（单客户）/ 15min（员工名下客户）/ 30min（员工客户数）—— 越粗粒度 TTL 越长

**时间线查询的 SQL 陷阱**
- `WHERE effective_date <= ? AND (end_date IS NULL OR end_date >= ?)` — `end_date IS NULL` 必须用 OR 分支，**不能**用 `IFNULL(end_date, '9999-12-31') >= ?`（会让索引失效）
- 三个 IDX（cust_id / emp_id / effective_date），MySQL 通常走 cust_id 最优

**batchGetCurrentAllocations 的缓存合并**

```
1. 遍历 custIds，查 Redis perf:alloc:cur:{custId}:{bizKind}
2. 命中的放 result Map
3. 未命中的 ids 集中，一次 SQL IN 查询
4. 查询结果写回 Redis + 合并到 result
```

**禁止**：遍历 custIds 逐个查 Redis 再逐个查 DB（N+1）。

**sys_control 派生版本号**
- `getLatestAllocVersion`：`SELECT current_version FROM sys_control WHERE scope_dim='CUST' AND is_valid=1`
- `getAllocVersionAt`：按 `latest_data_date` 过滤历史；若 as_of_date 无对应版本，返回最接近的历史版本
- `AllocVersionDTO` 字段：`bizKind / asOfDate / version / publishTime / publishSource`

**V1.0 写入被禁**
- `DataTaskApi.reportDataTaskStatus` UOE（V1.1 才接入外部数据同步）
- **不**给 `CustAllocRelation` 写 insert/update Service 方法

---

## 5. 每子域的验收标准（Definition of Done）

### 5.0 通用前置（所有子域共同）

- [ ] 无 `System.out.println` / 无被注释掉的代码 / 无未解决 TODO
- [ ] 中文注释 UTF-8（IDE 可见，非乱码）
- [ ] 所有 public 方法有 JavaDoc（至少一句）
- [ ] 构造器注入（`@RequiredArgsConstructor`），禁 `@Autowired` 字段注入
- [ ] `mvn clean test -pl performance-engine-center` 全绿
- [ ] `mvn clean package -pl performance-engine-center -am` 通过
- [ ] git 历史显示 `test: red → feat: green [→ refactor]` 节奏，**无混合 commit**
- [ ] 涉及并发 IT 时，每个并发 IT 必须在 `@AfterEach` 或 `@Sql(AFTER_TEST_METHOD)` 显式清理自己的数据前缀（如 `CONCUR_METRIC_*` / `CONCUR_TGT_*`），不依赖事务回滚

### 5.1 指标库子域 DoD

**文件清单（新增）**
- `entity/PerfMetricDef.java` / `entity/PerfMetricRef.java`
- `mapper/PerfMetricDefMapper.java` + `.xml` / `mapper/PerfMetricRefMapper.java` + `.xml`
- `service/MetricDefService.java` / `MetricRefService.java` / `MetricSlotService.java` / `MetricCycleDetectService.java`
- `service/cmd/`：Create/Update/Query MetricCmd 等
- `facade/MetricApiImpl.java` / `MetricQueryApiImpl.java`
- `controller/MetricDefController.java` + `controller/dto/`
- `support/MetricTestDataBuilder.java`

**必须存在的测试（方法级）**
- [ ] `MetricDefMapperIT.insertAndSelectById_ok`
- [ ] `MetricDefMapperIT.insert_whenMetricCodeDup_throwsDuplicateKey`
- [ ] `MetricCycleDetectServiceUT.checkNoCycle_selfRef_throws40902`
- [ ] `MetricCycleDetectServiceUT.checkNoCycle_twoNodeLoop_A_B_A_throws40902`
- [ ] `MetricCycleDetectServiceUT.checkNoCycle_threeNodeLoop_A_B_C_A_throws40902`
- [ ] `MetricCycleDetectServiceUT.checkNoCycle_diamondNoCycle_ok`
- [ ] `MetricCycleDetectServiceUT.checkLevelConstraint_L2RefL3_throws40911`
- [ ] `MetricCycleDetectServiceUT.checkLevelConstraint_L3RefL1_throws40911`
- [ ] `MetricSlotServiceUT.allocSlot_L1FirstAllocation_returns1`
- [ ] `MetricSlotServiceUT.allocSlot_L1HasGap_fillsSmallestFreeSlot`
- [ ] `MetricSlotServiceUT.allocSlot_L2_returnsFrom101Range`
- [ ] `MetricSlotServiceUT.releaseSlot_whenStatusNotDisabled_throws40905`
- [ ] `MetricSlotConcurrentIT.allocSlot_concurrentTwoThreads_onlyOneWins`（**不含 @Transactional**）
- [ ] `MetricDefServiceIT.create_withRefs_writesBothJsonAndRefTable`
- [ ] `MetricDefServiceIT.update_withRefsChange_clearsOldRefs`
- [ ] `MetricApiImplTest.getUserMetricCards_throwsUOE`（契约）
- [ ] `MetricApiImplTest.getEmpMetricValues_throwsUOE`（契约）
- [ ] `MetricApiImplTest.getMetricDef_hitsCacheOnSecondCall`
- [ ] `MetricDefControllerIT.post_whenUnauthenticated_returns401`
- [ ] `MetricDefControllerIT.post_whenLackBizAuth_returns403`
- [ ] `MetricDefControllerIT.delete_whenReasonMissing_returns400`
- [ ] `MetricDefControllerIT.post_whenMetricCodeDup_returns409`（UK 冲突路径）
- [ ] `MetricDefControllerIT.post_whenSuccess_returns200_andAuditLogRecorded`

**注解检查**
- [ ] `MetricDefController` 所有方法 `@BizAuth(bizType=PERF_CONFIG, action=...)`
- [ ] POST/PUT/DELETE + slot/release + status 变更 `@AuditLog(reasonRequired=true)`
- [ ] `MetricDefService` 写方法 `@Transactional(rollbackFor=Exception.class)`
- [ ] `MetricApiImpl.getMetricDef / listMetrics` 有 `@Cacheable`
- [ ] `MetricDefService.create/update/disable` 通过 `TransactionSynchronizationManager` afterCommit evict

**git commit 预期**：18-20（最多 2 个 refactor）

---

### 5.2 KPI 方案子域 DoD

**文件清单**
- `entity/PerfKpiScheme.java` / `entity/PerfKpiItem.java`
- `mapper/PerfKpiSchemeMapper.java` + `.xml` / `mapper/PerfKpiItemMapper.java` + `.xml`
- `service/KpiSchemeService.java` / `KpiItemService.java`
- `facade/KpiApiImpl.java`
- `controller/KpiSchemeController.java` + 对应 DTO

**必须存在的测试**
- [ ] `KpiSchemeMapperIT.insert_whenSchemeCodeDup_throwsDuplicateKey`
- [ ] `KpiItemMapperIT.insert_whenSchemeIdMetricCodeDup_throwsDuplicateKey`
- [ ] `KpiSchemeServiceUT.publish_whenItemReferMissingMetric_throws40906`
- [ ] `KpiSchemeServiceUT.publish_whenItemReferDraftMetric_throws40906`
- [ ] `KpiSchemeServiceUT.publish_whenAllItemsMetricPublished_succeeds`
- [ ] `KpiSchemeServiceUT.create_withItems_writesBothTablesSameTransaction`
- [ ] `KpiSchemeServiceUT.create_itemInsertFails_rollbacksScheme`
- [ ] `KpiItemServiceUT.addItem_whenDuplicateMetricInScheme_throws`
- [ ] `KpiApiImplTest.getKpiScheme_hitsCache`
- [ ] `KpiApiImplTest.getCurrentKpiTotal_throwsUOE`
- [ ] `KpiApiImplTest.getCurrentKpiResult_throwsUOE`
- [ ] `KpiApiImplTest.getKpiHistory_throwsUOE`
- [ ] `KpiSchemeControllerIT.publish_whenUnpublished_returns200`
- [ ] `KpiSchemeControllerIT.delete_whenReasonMissing_returns400`
- [ ] `KpiSchemeControllerIT.deleteItem_whenReasonMissing_returns400`

**注解检查**
- [ ] POST/PUT/DELETE/publish/deleteItem 方法 `@BizAuth` + 高危 `@AuditLog(reasonRequired=true)`
- [ ] `KpiSchemeService.create/update/publish/disable` 有 `@Transactional`
- [ ] `KpiApiImpl.getKpiScheme / getKpiSchemeById` 有 `@Cacheable`，状态变更 afterCommit evict

**git commit 预期**：11（最多 1 个 refactor）

---

### 5.3 目标方案子域 DoD

**文件清单**
- `entity/PerfTargetPlan.java`（**id String**）/ `entity/PerfTargetValue.java`
- `mapper/PerfTargetPlanMapper.java` + `.xml` / `mapper/PerfTargetValueMapper.java` + `.xml`
- `service/TargetPlanService.java` / `TargetValueService.java`
- `facade/TargetApiImpl.java`（**全部 V1.0 实现，无 UOE**）
- `controller/TargetPlanController.java` / `TargetValueController.java` + 对应 DTO

**必须存在的测试**
- [ ] `TargetPlanMapperIT.insert_whenPlanCodeDup_throwsDuplicateKey`
- [ ] `TargetValueMapperIT.upsert_newRow_inserts`
- [ ] `TargetValueMapperIT.upsert_existingRow_updates`
- [ ] `TargetValueMapperIT.upsertBatch_mix_newAndExisting_ok`
- [ ] `TargetPlanServiceUT.create_whenEffectiveAfterExpire_throws40001`
- [ ] `TargetPlanServiceUT.create_whenEffectiveEqualsExpire_succeeds`（边界：`effective_date = expire_date` 合法，锁定 `<=` 语义）
- [ ] `TargetPlanServiceUT.create_whenKpiSchemeDraft_throws40906`
- [ ] `TargetValueServiceUT.upsertBatch_whenSizeExceeds500_throws40910`
- [ ] `TargetValueServiceUT.upsertBatch_whenAllSuccess_returnsAffectedCount`
- [ ] `TargetApiImplTest.getTargetPlan_hitsCache`
- [ ] `TargetApiImplTest.getTargetPlanById_whenStringPlanId_ok`（验证 planId=String）
- [ ] `TargetApiImplTest.getTargetValue_returnsEmpty_whenNotFound`
- [ ] `TargetPlanControllerIT.put_whenReasonMissing_returns200`（update 不强制 reason）
- [ ] `TargetValueControllerIT.batchPost_when501Items_returns400`
- [ ] `TargetValueControllerIT.batchPost_when500Items_returns200`

**注解检查**
- [ ] Plan `@PathVariable` 为 `String id`，Entity `id` 为 `String`，Mapper `#{id}` 传 String
- [ ] `TargetPlanService.create/update` 有 `@Transactional`
- [ ] `TargetApiImpl.getTargetPlan / getTargetPlanById` 有 `@Cacheable`

**git commit 预期**：14-16（最多 1 个 refactor）

---

### 5.4 运行任务子域 DoD

**文件清单**
- `entity/PerfRunTask.java`
- `mapper/PerfRunTaskMapper.java` + `.xml`
- `service/PerfRunTaskService.java`（只读，4 方法）
- `facade/PerfCalcApiImpl.java`（1 实现 + 2 UOE）
- `controller/PerfRunTaskController.java` + 对应 QueryDTO

**必须存在的测试**
- [ ] `PerfRunTaskMapperIT.selectByTaskNo_whenNotFound_returnsNull`
- [ ] `PerfRunTaskMapperIT.selectByCondition_withDataScopeFilter_onlyShowsOwnTasks`
- [ ] `PerfRunTaskServiceUT.page_whenAdmin_returnsAll`
- [ ] `PerfRunTaskServiceUT.page_whenRegularUser_returnsOnlyOwnStarted`
- [ ] `PerfCalcApiImplTest.triggerKpiCalc_throwsUOE`
- [ ] `PerfCalcApiImplTest.triggerRecalc_throwsUOE`
- [ ] `PerfCalcApiImplTest.getRunTask_whenExists_returnsOptional`
- [ ] `PerfRunTaskControllerIT.get_whenUnauthenticated_returns401`
- [ ] `PerfRunTaskControllerIT.list_regularUser_onlyShowsOwnStarted`

**注解检查**
- [ ] 两个 GET 方法 `@BizAuth`，**无** `@AuditLog`（读操作不记审计）
- [ ] MyBatis XML 使用 `${dataScopeFilter}` 片段注入

**git commit 预期**：8-10（可能 0 个 refactor）

---

### 5.5 分配关系子域 DoD

**文件清单**
- `entity/CustAllocRelation.java`
- `mapper/CustAllocRelationMapper.java` + `.xml`
- `service/AllocRelationService.java`（10 public 查询方法）
- `facade/AllocApiImpl.java`（10 方法全实现）/ `DataTaskApiImpl.java`（1 UOE）
- `controller/AllocRelationController.java` + 对应 QueryDTO

**必须存在的测试**
- [ ] `AllocRelationMapperIT.getCurrentAllocations_excludesExpired`
- [ ] `AllocRelationMapperIT.getAllocationHistory_asOfDate_returnsActiveAtThatTime`
- [ ] `AllocRelationMapperIT.listCustomersByEmp_withDataScopeFilter_onlyOwnEmp`
- [ ] `AllocRelationServiceUT.batchGetCurrentAllocations_hitsCacheForSomeMissesDb_fillsBoth`
- [ ] `AllocRelationServiceUT.getAllocSummary_sumRatio_equals100_exactly`
- [ ] `AllocRelationServiceUT.hasAllocation_whenExists_returnsTrue`
- [ ] `AllocRelationServiceUT.countCustomersByEmps_batchOk`
- [ ] `AllocRelationServiceUT.getLatestAllocVersion_derivesFromSysControlCust`
- [ ] `AllocRelationServiceUT.getAllocVersionAt_whenNoExactMatch_returnsMostRecentBeforeAsOfDate`（只向过去回退，不选未来版本）
- [ ] `AllocApiImplTest.getCurrentAllocations_hitsCache`
- [ ] `AllocApiImplTest.batchGetCurrentAllocations_cacheMergesBatchQuery`
- [ ] `DataTaskApiImplTest.reportDataTaskStatus_throwsUOE`
- [ ] `AllocRelationControllerIT.summary_regularUser_onlyOwnEmp`

**注解检查**
- [ ] 3 个 GET 方法 `@BizAuth(PERF_CONFIG, READ)`，**无** `@AuditLog`
- [ ] `AllocApiImpl.getCurrentAllocations / listCustomersByEmp / countCustomersOfEmp / getLatestAllocVersion` 有 `@Cacheable`（按原 spec §5.2.7 TTL）
- [ ] V1.0 **不**实现 evict（TTL 自然过期）

**git commit 预期**：12-14（最多 1 个 refactor）

---

### 5.6 全模块集成验收（5 子域全部完成后）

- [ ] `mvn clean package` 仓库根目录全绿
- [ ] bootstrap 启动，Knife4j UI 显示全部 **35 个 perf 端点**
- [ ] curl 冒烟（admin token）：
  - `GET /api/perf/sys-control?scopeDim=EMP` → 200
  - `GET /api/perf/metrics?pageNo=1&pageSize=10` → 200
  - `GET /api/perf/kpi-schemes?pageNo=1&pageSize=10` → 200
  - `GET /api/perf/target-plans?pageNo=1&pageSize=10` → 200
  - `GET /api/perf/run-tasks?pageNo=1&pageSize=10` → 200
  - `GET /api/perf/alloc-relations?custId=C001&bizKind=DEPOSIT` → 200
- [ ] PT_RESOURCE 表已有 35 条 `P_PERF_*`（V1_0_1 已执行）
- [ ] sys_dict_item 已有对应字典项（V1_0_2 已执行）
- [ ] 累计 git commit：**63-71**（sys_control 已有 12 / 剩余 51-59）

---

## 6. 与原 V1.0 spec 的关系

本 spec 是原 spec 的**增量补充**，两者并存：

| 维度 | 原 V1.0 spec v1.2 | 本 spec |
|---|---|---|
| 定位 | 全景设计（架构 / DTO / 错误码 / 缓存 key / Redis 锁时序） | 剩余 5 子域的开发操作细节 |
| 颗粒 | 架构级 / 模块级 | 方法级 / commit 级 / 测试级 |
| 交付假设 | 6 子代理并行 | 1 人串行 |
| 可读独立性 | 可独立阅读 | **必须配合原 spec 阅读** |

**发生冲突时**：以**本 spec 为准**（因为本 spec 基于审计和当前交付实际情况）；原 spec 的 §9 子代理编排章节**在单人串行模式下作废**。

---

## 7. 下一步

本 spec 写完后：

1. 提交到 git（`docs(spec): 绩效中心 V1.0 剩余 5 子域细化设计`）
2. 走 `spec-document-reviewer` 审查循环（最多 3 轮），修复发现的问题
3. 审查通过后由用户人工复核
4. 调用 `writing-plans` 产出同日期命名的 plan 文件 `docs/superpowers/plans/2026-04-17-performance-v1.0-remaining-subdomains-impl.md`
5. 按 plan 进入 TDD 实施（单人串行，从顺位 1 指标库开始）
