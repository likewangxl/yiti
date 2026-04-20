# Performance Three-Step Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `1 -> 2 -> 3` 顺序完成 `performance-engine-center` 的指标库 Controller 闭环、模块级回归、KPI 子域完整闭环，并在每个实现任务后执行“规格符合性 review -> 代码质量 review”双门禁。

**Architecture:** 采用阶段闭环推进：先把已存在的指标库中间层收口到 HTTP 入口，再以模块级回归作为闸门，最后完成 KPI 方案配置垂直切片。实现时严格以当前仓库中的 `performance-engine-center` 代码、`KpiApi.java`、DDL 与 `V1_0_1__performance_resources.sql` 为准；旧文档里与当前代码冲突的 `DRAFT/PUBLISHED`、`Long schemeId`、额外执行型接口都不作为本计划范围。所有任务必须 TDD、最小改动、频繁提交，并由独立 reviewer 做两轮复审。

**Tech Stack:** Spring Boot 3.2.3、JDK 17、MyBatis 3.0.3、MySQL 8、Redis 6、JUnit 5、Mockito、AssertJ、MockMvc、Lombok

---

## 0. 实施前置与文件边界

### 当前代码事实

- 指标库中间层已存在：`PerfMetricDef`、`PerfMetricRef`、对应 Mapper/XML、`MetricCycleDetectService`、`MetricSlotService`、`MetricRefService`、`MetricDefService`、`MetricApiImpl`、`MetricQueryApiImpl`、`MetricLifecycleFacade`
- 当前缺口主要在入口层：`MetricDefController`、请求 DTO、Controller IT
- KPI 子域尚未开始完整闭环
- 当前工作区可能存在无关脏改动，实施时只能触碰 `performance-engine-center/**` 和本计划文档本身

### 文件结构锁定

**Step 1 预期新增 / 修改**

- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateMetricReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateMetricReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ChangeStatusReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ReleaseSlotReqDTO.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricRequestDtoValidationTest.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java`（仅在 Controller 收口发现确有必要时）
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/PerfTestConfig.java`（仅在 Controller IT 需要更细 mock 时）

**Step 3 预期新增 / 修改**

- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiScheme.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiItem.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfKpiSchemeMapper.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfKpiItemMapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfKpiSchemeMapper.xml`
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfKpiItemMapper.xml`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/KpiTestDataBuilder.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiSchemeService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiItemService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/CreateKpiSchemeCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpdateKpiSchemeCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/AddKpiItemCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpdateKpiItemCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/KpiApiImpl.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/assembler/KpiAssembler.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/KpiSchemeController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateKpiSchemeReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateKpiSchemeReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/AddKpiItemReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateKpiItemReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/PublishKpiSchemeReqDTO.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfKpiSchemeMapperIT.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfKpiItemMapperIT.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiSchemeServiceTest.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiItemServiceTest.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/KpiApiImplTest.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/KpiSchemeControllerIT.java`

**严格边界**

- 不修改 `business-application-center/**`、`bootstrap/**`、`docs/modules/**` 等无关脏改动
- 不把 `Target / RunTask / Alloc` 提前实现
- 不实现 `MetricApi` 与 `KpiApi` 中标记为 V1.1/UOE 的结果类能力

---

## 1. 总执行规则（对所有任务都生效）

- [ ] **Step 1: 记录当前工作区基线**

Run: `git status --short`
Expected: 可见已有无关脏改动；后续实现只允许新增/修改 `performance-engine-center/**` 与 `docs/superpowers/plans/2026-04-18-performance-three-step-closure-impl.md`

- [ ] **Step 2: 锁定当前 truth source**

阅读并以以下文件为准，不再回退到旧文档里的漂移定义：

- `performance-engine-center/src/main/resources/sql/V1_0_0__performance_ddl.sql`
- `performance-engine-center/src/main/resources/sql/V1_0_1__performance_resources.sql`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/MetricApi.java`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/KpiApi.java`

- [ ] **Step 3: 对每个实现任务强制执行同一完成门禁**

每个实现任务完成后，必须按以下顺序执行，未通过不得进入下一任务：

1. 实现 subagent 自测与自检
2. 独立 `spec reviewer`
3. 独立 `code quality reviewer`

- [ ] **Step 4: commit 规则**

所有任务都采用：

- `test(perf): red - ...`
- `feat(perf): green - ...`
- 如确有必要，再补 `refactor(perf): ...`

禁止混合 commit。

---

## 2. Step 1 — 指标库 Controller 完整闭环

### Task 1: 请求 DTO 与校验测试

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateMetricReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateMetricReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ChangeStatusReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ReleaseSlotReqDTO.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricRequestDtoValidationTest.java`

- [ ] **Step 1: 先写 DTO 校验红测试**

```java
@Test
void createMetricReq_whenMetricCodeLowercase_shouldViolation() {}

@Test
void changeStatusReq_whenReasonBlank_shouldViolation() {}

@Test
void releaseSlotReq_whenReasonBlank_shouldViolation() {}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricRequestDtoValidationTest'`
Expected: FAIL，提示缺少 DTO 类型或断言不成立

- [ ] **Step 3: 实现最小 DTO**

实现字段以当前资源脚本和现有 Service/Facade 能力为准：

```java
public class CreateMetricReqDTO {
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z_0-9]+$")
    private String metricCode;
    @NotBlank @Size(max = 200)
    private String metricName;
    @NotBlank
    private String baseDim;
    @NotNull @Min(1) @Max(3)
    private Integer metricLevel;
    private Integer preferredSlot;
    // 其余字段与 CreateMetricDefCmd 对齐
}
```

- [ ] **Step 4: 运行测试确认转绿**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricRequestDtoValidationTest'`
Expected: PASS

- [ ] **Step 5: 提交 red/green**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateMetricReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateMetricReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ChangeStatusReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ReleaseSlotReqDTO.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricRequestDtoValidationTest.java
git commit -m "test(perf): red - metric request dto validation"
git commit -m "feat(perf): green - metric request dto validation"
```

- [ ] **Step 6: 双 review**

按 `subagent-driven-development` 顺序执行：

1. 规格符合性 review：字段与 `CreateMetricDefCmd` / `UpdateMetricDefCmd` 对齐，且不引入旧文档里的 `procName / ownerDept / remark`
2. 代码质量 review：校验注解精简、无重复、无额外 DTO

---

### Task 2: MetricDefController 读接口闭环

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java`（仅当读接口缺少查询辅助方法时）

- [ ] **Step 1: 先写读接口 IT 红测试**

至少覆盖：

```java
@Test void list_shouldReturnPageResult() {}
@Test void getByCode_whenExists_returns200() {}
@Test void getByCode_whenMissing_returnsBizError() {}
@Test void listRefs_whenExists_returns200() {}
@Test void listRefBy_whenExists_returns200() {}
@Test void listSlots_returns200() {}
```

同时增加一个反射断言，验证 Controller 读方法存在 `@BizAuth(bizType = BizType.PERF_CONFIG, action = READ/LIST)`，避免在当前测试装配下伪造 401/403。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricDefControllerIT'`
Expected: FAIL，提示缺少 Controller 或路由未注册

- [ ] **Step 3: 实现最小读接口 Controller**

读接口必须与 `P_PERF_METRIC_*` 资源对齐：

- `GET /api/perf/metrics`
- `GET /api/perf/metrics/{metricCode}`
- `GET /api/perf/metrics/{metricCode}/refs`
- `GET /api/perf/metrics/{metricCode}/ref-by`
- `GET /api/perf/metrics/val-slots`

返回统一使用 `ResponseWrapper.success(...)` 或 `ResponseWrapper.page(...)`。

- [ ] **Step 4: 补最小查询粘合代码**

若现有服务缺少只读辅助方法，仅补最小方法，例如：

```java
public List<String> listRefCodes(String metricCode) {}
public List<String> listRefByCodes(String metricCode) {}
public Set<Integer> listOccupied(String baseDim) {}
```

禁止为读接口引入新的 facade 层抽象。

- [ ] **Step 5: 运行测试确认转绿**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricDefControllerIT'`
Expected: PASS

- [ ] **Step 6: 提交 red/green**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java
git commit -m "test(perf): red - metric controller read endpoints"
git commit -m "feat(perf): green - metric controller read endpoints"
```

- [ ] **Step 7: 双 review**

规格 review 检查：端点路径、返回结构、`@BizAuth` 与 `P_PERF_METRIC_LIST / GET / REFS / RBY / SLOT` 对齐。  
质量 review 检查：Controller 只做编排，不重复业务逻辑。

---

### Task 3: MetricDefController 写接口闭环

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java`（只补真正缺失的 update/disable 查询辅助）

- [ ] **Step 1: 先写写接口红测试**

至少覆盖：

```java
@Test void create_whenPayloadInvalid_returns400() {}
@Test void create_whenMetricCodeDup_returnsBizError() {}
@Test void create_whenSuccess_returns200() {}
@Test void update_whenSuccess_returns200() {}
@Test void delete_whenReasonMissing_returns400() {}
@Test void statusChange_whenReasonMissing_returns400() {}
@Test void releaseSlot_whenMetricNotDisabled_returnsBizError() {}
```

再补一个反射断言，验证写接口的 `@AuditLog` 与 `reasonRequired=true` 只加在高危端点上。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricDefControllerIT'`
Expected: FAIL，提示 POST/PUT/DELETE 路由或审计约束未满足

- [ ] **Step 3: 扩展 `MetricLifecycleFacade` 仅保留需要的生命周期方法**

优先补：

```java
public PerfMetricDef createMetric(CreateMetricDefCmd cmd) {}
public PerfMetricDef updateMetric(UpdateMetricDefCmd cmd) {}
public void disableMetric(String metricCode, String reason, String operator) {}
```

只有创建走 Redis 槽位锁；更新/停用/释放槽位不新增多余锁。

- [ ] **Step 4: 实现写接口 Controller**

必须对齐资源脚本：

- `POST /api/perf/metrics`
- `PUT /api/perf/metrics/{metricCode}`
- `DELETE /api/perf/metrics/{metricCode}`
- `PUT /api/perf/metrics/{metricCode}/status`
- `POST /api/perf/metrics/{metricCode}/slot/release`

建议映射：

- create / update -> `BizAction.WRITE`
- delete -> `BizAction.DELETE`
- status change / slot release -> `BizAction.CONFIG`

高危端点统一：

```java
@AuditLog(action = "...", resourceType = "METRIC_DEF", reasonRequired = true)
```

- [ ] **Step 5: 运行测试确认转绿**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricDefControllerIT'`
Expected: PASS

- [ ] **Step 6: 提交 red/green**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java
git commit -m "test(perf): red - metric controller write endpoints"
git commit -m "feat(perf): green - metric controller write endpoints"
```

- [ ] **Step 7: 双 review**

规格 review 检查：5 个写端点与 `P_PERF_METRIC_ADD / UPD / DEL / STAT / SREL` 完全对齐。  
质量 review 检查：锁仍然只在 facade 层，Controller 不直接操作 Redis。

---

### Task 4: Step 1 收口与 Step 2 模块级回归

**Files:**
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/PerfTestConfig.java`（仅当需要更细 mock）
- Modify: `performance-engine-center/**`（仅修复回归暴露问题）

- [ ] **Step 1: 补齐指标库 Controller 最终缺口测试**

补至少这些边界：

```java
@Test void list_whenNoData_returnsEmptyPage() {}
@Test void getRefs_whenMetricMissing_returnsBizError() {}
@Test void getRefBy_whenMetricMissing_returnsBizError() {}
@Test void create_concurrentTwoRequestsSameBaseDim_minimalLockProof() {}
```

- [ ] **Step 2: 运行指标库相关测试集**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricRequestDtoValidationTest,MetricDefControllerIT,PerfMetricDefMapperIT,PerfMetricRefMapperIT,MetricCycleDetectServiceTest,MetricSlotServiceTest,MetricSlotConcurrentIT,MetricRefServiceTest,MetricDefServiceTest,MetricApiImplTest,MetricQueryApiImplTest,MetricLifecycleFacadeTest'`
Expected: PASS

- [ ] **Step 3: 执行 Step 2 的模块级回归**

Run: `mvn -q -pl performance-engine-center test`
Expected: PASS

- [ ] **Step 4: 若回归失败，只修 performance 模块内因 Step 1 引入的问题**

优先排查：

- Spring Controller 装配
- MockMvc 测试环境 Bean
- MyBatis XML
- 现有 sys_control 测试回归

- [ ] **Step 5: 回归绿后提交**

```bash
git add performance-engine-center
git commit -m "feat(perf): green - metric controller closure and module regression"
```

- [ ] **Step 6: 双 review**

规格 review：确认 Step 1 已完成且 Step 2 回归绿灯。  
质量 review：确认没有把 `trial-run / execute / trial-history` 等 V1.1 端点混进本轮。

---

## 3. Step 3 — KPI 子域完整闭环（仅方案配置闭环）

### Task 5: KPI 数据层（Entity / Mapper / XML / Mapper IT）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiScheme.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiItem.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfKpiSchemeMapper.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfKpiItemMapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfKpiSchemeMapper.xml`
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfKpiItemMapper.xml`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/KpiTestDataBuilder.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfKpiSchemeMapperIT.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfKpiItemMapperIT.java`

- [ ] **Step 1: 先写 Mapper IT 红测试**

至少覆盖：

```java
@Test void insertScheme_thenSelectById_ok() {}
@Test void insertScheme_whenSchemeCodeDup_throwsDuplicateKey() {}
@Test void insertItem_thenSelectBySchemeId_ok() {}
@Test void insertItem_whenSchemeMetricDup_throwsDuplicateKey() {}
@Test void deleteItem_thenSelectEmpty() {}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='PerfKpiSchemeMapperIT,PerfKpiItemMapperIT'`
Expected: FAIL，提示缺少实体/Mapper/XML

- [ ] **Step 3: 实现最小实体与 Mapper 接口**

必须对齐 DDL，而不是旧文档：

```java
class PerfKpiScheme {
    String id;
    String schemeCode;
    String schemeName;
    String cycleType;
    Boolean openDetail;
    String status; // ACTIVE / DISABLED
    String createdBy;
    LocalDateTime createdTime;
    String updatedBy;
    LocalDateTime updatedTime;
}
```

```java
class PerfKpiItem {
    String id;
    String schemeId;
    String metricCode;
    BigDecimal weight;
    BigDecimal multiplier;
    BigDecimal minScore;
    BigDecimal maxScore;
    LocalDateTime createdTime;
}
```

- [ ] **Step 4: 实现最小 XML**

必须包含：

- `selectById`
- `selectBySchemeCode`
- `selectByCondition`
- `countByCondition`
- `insert`
- `updateByIdSelective`
- `deleteById`
- `selectBySchemeId`
- `selectBySchemeAndMetric`
- `deleteById`

- [ ] **Step 5: 运行测试确认转绿**

Run: `mvn -q -pl performance-engine-center test -Dtest='PerfKpiSchemeMapperIT,PerfKpiItemMapperIT'`
Expected: PASS

- [ ] **Step 6: 提交 red/green**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiScheme.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiItem.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfKpiSchemeMapper.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfKpiItemMapper.java performance-engine-center/src/main/resources/mapper/performance/PerfKpiSchemeMapper.xml performance-engine-center/src/main/resources/mapper/performance/PerfKpiItemMapper.xml performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/KpiTestDataBuilder.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfKpiSchemeMapperIT.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfKpiItemMapperIT.java
git commit -m "test(perf): red - kpi mapper it"
git commit -m "feat(perf): green - kpi mapper layer"
```

- [ ] **Step 7: 双 review**

规格 review：确认仅覆盖 `perf_kpi_scheme / perf_kpi_item`。  
质量 review：确认字段、状态、唯一键完全对齐 DDL。

---

### Task 6: KPI Service 闭环

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiSchemeService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiItemService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/CreateKpiSchemeCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpdateKpiSchemeCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/AddKpiItemCmd.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpdateKpiItemCmd.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiSchemeServiceTest.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiItemServiceTest.java`

- [ ] **Step 1: 先写 KpiItemService 红测试**

至少覆盖：

```java
@Test void addItem_whenMetricMissing_throws40906() {}
@Test void addItem_whenMetricDisabled_throws40906() {}
@Test void addItem_whenDuplicateMetricInScheme_throws40909() {}
@Test void updateItem_whenExists_updatesRangeAndWeight() {}
@Test void deleteItem_whenExists_deletes() {}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiItemServiceTest'`
Expected: FAIL

- [ ] **Step 3: 实现最小 `KpiItemService`**

规则：

- 依赖 `MetricDefService.getByCodeOrNull`
- 只允许引用 `status=ACTIVE` 的指标
- 使用 `(schemeId, metricCode)` 防重
- `weight=0` 合法
- 不做 `sum(weight)=100` 校验

- [ ] **Step 4: 运行测试确认转绿并提交**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiItemServiceTest'`
Expected: PASS

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiItemService.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/AddKpiItemCmd.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpdateKpiItemCmd.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiItemServiceTest.java
git commit -m "test(perf): red - kpi item service"
git commit -m "feat(perf): green - kpi item service"
```

- [ ] **Step 5: 先写 KpiSchemeService 红测试**

至少覆盖：

```java
@Test void create_withItems_writesParentAndChildren() {}
@Test void publish_whenNoItems_throwsInvalidState() {}
@Test void publish_whenItemMetricDisabled_throws40906() {}
@Test void publish_whenAllItemsActive_succeeds() {}
@Test void disable_whenStatusNotActive_throws40905() {}
@Test void page_returnsPageResult() {}
```

- [ ] **Step 6: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiSchemeServiceTest'`
Expected: FAIL

- [ ] **Step 7: 实现最小 `KpiSchemeService`**

规则：

- 创建时可带 items，父子同事务
- `publish` 本质是发布校验 + 将 `status` 置为 `ACTIVE`
- 新建方案默认置为 `DISABLED`，防止绕过发布校验
- 停用走 `status=DISABLED`

- [ ] **Step 8: 运行测试确认转绿并提交**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiSchemeServiceTest'`
Expected: PASS

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiSchemeService.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/CreateKpiSchemeCmd.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpdateKpiSchemeCmd.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiSchemeServiceTest.java
git commit -m "test(perf): red - kpi scheme service"
git commit -m "feat(perf): green - kpi scheme service"
```

- [ ] **Step 9: 双 review**

规格 review：确认仅做方案配置，不实现 KPI 结果查询。  
质量 review：确认事务边界在 service 层，未引入额外抽象。

---

### Task 7: KpiApiImpl Facade 闭环

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/KpiApiImpl.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/assembler/KpiAssembler.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/KpiApiImplTest.java`

- [ ] **Step 1: 先写 Facade 红测试**

至少覆盖：

```java
@Test void getKpiScheme_returnsOptional() {}
@Test void getKpiSchemeById_returnsOptional() {}
@Test void getCurrentKpiTotal_throwsUoe() {}
@Test void getCurrentKpiResult_throwsUoe() {}
@Test void getKpiHistory_throwsUoe() {}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiApiImplTest'`
Expected: FAIL

- [ ] **Step 3: 实现最小 Facade**

要求：

- 只实现 `getKpiScheme` / `getKpiSchemeById`
- 结果类接口继续 `UnsupportedOperationException("V1.1 delivered")`
- 为 `schemeCode` / `schemeId` 查询补 `@Cacheable`

```java
@Cacheable(cacheNames = "perf:kpi_scheme", key = "#schemeId")
public Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId) {}
```

- [ ] **Step 4: 运行测试确认转绿**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiApiImplTest'`
Expected: PASS

- [ ] **Step 5: 提交 red/green**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/KpiApiImpl.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/assembler/KpiAssembler.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/KpiApiImplTest.java
git commit -m "test(perf): red - kpi api facade"
git commit -m "feat(perf): green - kpi api facade"
```

- [ ] **Step 6: 双 review**

规格 review：确认未实现 `getCurrentKpiTotal / getCurrentKpiResult / getKpiHistory`。  
质量 review：确认 DTO 装配与缓存键清晰，无额外 facade。

---

### Task 8: KpiSchemeController 与 Controller IT 闭环

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/KpiSchemeController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateKpiSchemeReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateKpiSchemeReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/AddKpiItemReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateKpiItemReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/PublishKpiSchemeReqDTO.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/KpiSchemeControllerIT.java`

- [ ] **Step 1: 先写 Controller IT 红测试**

端点必须对齐资源脚本中的 9 条：

- `GET /api/perf/kpi-schemes`
- `GET /api/perf/kpi-schemes/{id}`
- `POST /api/perf/kpi-schemes`
- `PUT /api/perf/kpi-schemes/{id}`
- `DELETE /api/perf/kpi-schemes/{id}`
- `POST /api/perf/kpi-schemes/{id}/publish`
- `POST /api/perf/kpi-schemes/{id}/items`
- `PUT /api/perf/kpi-schemes/{id}/items/{itemId}`
- `DELETE /api/perf/kpi-schemes/{id}/items/{itemId}`

至少覆盖：

```java
@Test void list_shouldReturnPageResult() {}
@Test void create_whenInvalidPayload_returns400() {}
@Test void publish_whenReasonMissing_returns400() {}
@Test void delete_whenReasonMissing_returns400() {}
@Test void deleteItem_whenReasonMissing_returns400() {}
@Test void getById_whenExists_returns200() {}
```

另外增加反射断言，验证：

- 读接口：`READ / LIST`
- 写接口：`WRITE`
- 发布：`CONFIG`
- 删除：`DELETE`

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiSchemeControllerIT'`
Expected: FAIL

- [ ] **Step 3: 实现最小请求 DTO**

重点：

- `CreateKpiSchemeReqDTO` 可带 `items`
- `PublishKpiSchemeReqDTO` 含 `reason`
- 删除接口原因使用 `@RequestParam("reason")`，避免额外 DELETE body DTO

- [ ] **Step 4: 实现最小 Controller**

约束：

- 读接口 `@BizAuth(... LIST/READ)`
- 新增/编辑/新增项/编辑项 `@BizAuth(... WRITE)` + `@AuditLog`
- 删除与发布 `reasonRequired=true`
- 仍以当前 `KpiApi.java` 为 truth source，Controller 不暴露 KPI 结果查询接口

- [ ] **Step 5: 运行测试确认转绿**

Run: `mvn -q -pl performance-engine-center test -Dtest='KpiSchemeControllerIT'`
Expected: PASS

- [ ] **Step 6: 提交 red/green**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/KpiSchemeController.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateKpiSchemeReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateKpiSchemeReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/AddKpiItemReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateKpiItemReqDTO.java performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/PublishKpiSchemeReqDTO.java performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/KpiSchemeControllerIT.java
git commit -m "test(perf): red - kpi controller endpoints"
git commit -m "feat(perf): green - kpi controller endpoints"
```

- [ ] **Step 7: 双 review**

规格 review：确认 9 个端点与 `P_PERF_KPI_*` 资源逐一对齐。  
质量 review：确认未把 Target/RunTask/Alloc 端点混入。

---

### Task 9: Step 3 收口与最终模块级回归

**Files:**
- Modify: `performance-engine-center/**`（仅修复 KPI 子域回归问题）

- [ ] **Step 1: 运行 KPI 相关测试集**

Run: `mvn -q -pl performance-engine-center test -Dtest='PerfKpiSchemeMapperIT,PerfKpiItemMapperIT,KpiItemServiceTest,KpiSchemeServiceTest,KpiApiImplTest,KpiSchemeControllerIT'`
Expected: PASS

- [ ] **Step 2: 执行 Step 3 完成后的模块级回归**

Run: `mvn -q -pl performance-engine-center test`
Expected: PASS

- [ ] **Step 3: 如失败，只修 performance 模块内被 KPI 子域破坏的能力**

重点检查：

- sys_control 测试是否被 Spring 上下文影响
- 指标库 Controller/Service 是否被 KPI 装配影响
- MyBatis mapper 扫描与 XML 命名是否冲突

- [ ] **Step 4: 回归绿后提交**

```bash
git add performance-engine-center
git commit -m "feat(perf): green - kpi closure and final module regression"
```

- [ ] **Step 5: 最终 review**

在所有任务完成后，再派一个最终 code reviewer 对整个 `performance-engine-center` 增量实现做总审查，确认：

- 范围只覆盖 1 / 2 / 3
- 所有 TDD 证据完整
- 模块级回归已通过

---

## 4. 统一验证命令清单

- 指标库 DTO 校验：`mvn -q -pl performance-engine-center test -Dtest='MetricRequestDtoValidationTest'`
- 指标库 Controller：`mvn -q -pl performance-engine-center test -Dtest='MetricDefControllerIT'`
- 指标库组合测试：

```bash
mvn -q -pl performance-engine-center test -Dtest='MetricRequestDtoValidationTest,MetricDefControllerIT,PerfMetricDefMapperIT,PerfMetricRefMapperIT,MetricCycleDetectServiceTest,MetricSlotServiceTest,MetricSlotConcurrentIT,MetricRefServiceTest,MetricDefServiceTest,MetricApiImplTest,MetricQueryApiImplTest,MetricLifecycleFacadeTest'
```

- KPI 数据层：`mvn -q -pl performance-engine-center test -Dtest='PerfKpiSchemeMapperIT,PerfKpiItemMapperIT'`
- KPI Service：`mvn -q -pl performance-engine-center test -Dtest='KpiItemServiceTest,KpiSchemeServiceTest'`
- KPI Facade/Controller：`mvn -q -pl performance-engine-center test -Dtest='KpiApiImplTest,KpiSchemeControllerIT'`
- 模块级回归：`mvn -q -pl performance-engine-center test`

---

## 5. 执行提醒

- 任何 subagent 遇到无关脏改动冲突，立即停止并回报主控
- 任何 reviewer 发现越界实现，直接打回，不做“顺手接受”
- 如果某个任务需要更多上下文，必须回到主控补上下文后重派，不能硬猜
- 实现时优先复用已有 `SysControlController`、`SysControlFacade`、`Metric*` 测试基座模式
- 若旧模块文档与当前代码冲突，以当前代码、DDL、资源脚本和本 plan 为准
