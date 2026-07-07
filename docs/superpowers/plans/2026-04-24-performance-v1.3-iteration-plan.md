# performance-engine-center V1.3 迭代计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消化 V1.1/V1.2 累积的 12 项技术债，并对 V1.2 遗留的 5 处 UOE 方法给出实际实现，使 performance-engine-center 在生产运行下具备最低噪音、最小技术债、完全可观测的状态。

**Architecture:**
V1.3 不引入新业务能力，只做"债务清偿"。核心动作：
1. **DDL 兜底**：`perf_run_task` 加 UK(task_key)，给 V1.1 Q6 的 Redis SETNX 幂等做第二道防线；V1.2 V1.0 bug 修复后的历史 NULL 数据清理
2. **Target 数据范围注入**：复用 V1.2 Q7 的 `PerfScopeHelper` + `ScopeColumns` 模式扩展到 TargetValue/TargetPlan
3. **5 处 V1.2 UOE 实现**：MetricQueryApi 批量快照走宽表 + MetricApi 用户卡片走 KPI 结果聚合 + PerfCalcApi.triggerKpiCalc 简单委托
4. **错误码 / DTO 对齐**：新增 IDEMPOTENCY_WAIT_TIMEOUT(§K 补位)、MetricTrialRespDTO 字段对齐 03 §A.5、execute 返回 RunTaskInfoDTO
5. **测试基础设施升级**：Testcontainers-redis 消除 @Disabled、failsafe 分层加速反馈循环
6. **Controller 与 Listener 收敛**：5 个 Controller 的局部 entity 变量迁到 Facade 层统一装配

**Tech Stack:**
- 无新增依赖（Testcontainers-redis 在 common 可能已有；若无则追加 `org.testcontainers:testcontainers:1.19.x`）
- 利用 V1.0-V1.2 既有基础设施：PerfScopeHelper、PerfEventPublisher、BizScopeApi、PerfErrorCode 29 条、4 个架构守护

**依赖文档（权威需求来源）:**
- `docs/modules/performance-engine-center/03-接口设计与报文.md` §A.5（MetricTrialRespDTO 字段权威）、§F.2（triggerRecalc 状态语义）、§K（错误码）
- `docs/modules/performance-engine-center/04-对外API契约.md` §10（事件）、§5（MetricQueryApi）
- `performance-engine-center/CLAUDE.md` §技术债务章节（V1.3 清单）

**前置条件（必须已满足）:**
- ✅ V1.0 整改 + V1.1 + V1.2 全部交付推送（主分支 master）
- ✅ `PerfScopeHelper` + 7 种 DataScopeType（V1.2 Q7）
- ✅ 4 个架构守护测试绿（BizAuth / NoEntityInController / NoV11UOE / PerfErrorCode）
- ✅ surefire `**/*IT.java` 已包含（V1.2 Q8）
- ✅ 842 测试全绿 + 6 @Disabled

---

## 阶段概览

| 阶段 | 任务数 | 目标 | 预估 |
|---|---|---|---|
| R0 | 4 | 基础设施（V1_2_5 NULL deleted 清理 + V1_3_0 UK DDL + failsafe 分层 + CLAUDE.md 勘误） | 1 天 |
| R1 | 3 | Target 数据范围注入（TargetValue + TargetPlan + Controller） | 2 天 |
| R2 | 5 | 5 处 V1.2 UOE 实际实现 | 3 天 |
| R3 | 3 | 错误码 / DTO 细化（IDEMPOTENCY_WAIT_TIMEOUT + MetricTrialRespDTO + execute 返回类型） | 1 天 |
| R4 | 3 | Controller 重构 + P7 status 修复 + cycleType 写入 params_json | 2 天 |
| R5 | 3 | 测试基础设施（Testcontainers-redis + UndoScriptSmokeIT 重写 + V1.0 UOE 资产改行为断言） | 2 天 |
| R6 | 0 | WORKFLOW_PARTICIPANT scope 整体延期 V1.4（已移出 V1.3 范围） | — |
| R7 | 3 | V1.3 验收清单 + 文档同步 + 技术债清算 | 0.5 天 |
| 合计 | 24 | | ~11.5 天 |

---

## 阶段 R0：基础设施（必须先做）

### Task R0.1：V1_2_5 清理历史 NULL deleted 数据

**背景**：V1.0 MetricDefService.create 未初始化 deleted 字段（V1.2 Q8.5b 修复），生产可能已有 `deleted IS NULL` 的孤儿 metric 行，在 `selectByMetricCode(WHERE deleted=0)` 下不可见。

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_2_5__perf_cleanup_null_deleted.sql`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_2_5FlywayIT.java`

- [ ] **Step 1：写失败测试**

```java
// V1_2_5FlywayIT.java
@SpringBootTest(classes = PerformanceFlywayTestBase.Config.class)
@Testcontainers
class V1_2_5FlywayIT extends PerformanceFlywayTestBase {

    @Test
    void migration_fixesNullDeleted() {
        // 预置一条 NULL deleted 的历史行
        jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, status, deleted) " +
            "VALUES ('TEST_NULL_V125', 'TEST_NULL_V125', 't', 'EMP', 150, 'ACTIVE', NULL)");
        // 迁移前：deleted IS NULL 行存在
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM perf_metric_def WHERE deleted IS NULL", Integer.class))
            .isGreaterThanOrEqualTo(1);
        // 重新应用 V1_2_5（flyway.repair 后 migrate）
        flyway.repair(); flyway.migrate();
        // 迁移后：该行 deleted=0
        Integer d = jdbc.queryForObject(
            "SELECT deleted FROM perf_metric_def WHERE id='TEST_NULL_V125'", Integer.class);
        assertThat(d).isZero();
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd performance-engine-center
mvn -Dtest=V1_2_5FlywayIT test
```
预期：FAIL（脚本不存在）

- [ ] **Step 3：创建 V1_2_5 脚本**

```sql
-- V1_2_5__perf_cleanup_null_deleted.sql
-- V1.0 MetricDefService.create bug 修复后的历史 NULL deleted 数据清理（V1.2 Q8.5b 发现）
-- 幂等：只影响 deleted IS NULL 的行，已 = 0 的行不变
UPDATE perf_metric_def SET deleted = 0 WHERE deleted IS NULL;
```

- [ ] **Step 4：运行测试通过**

```bash
mvn -Dtest=V1_2_5FlywayIT test
```
预期：PASS

- [ ] **Step 5：Commit**

```bash
git add performance-engine-center/src/main/resources/sql/V1_2_5__perf_cleanup_null_deleted.sql \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_2_5FlywayIT.java
git commit -m "$(cat <<'EOF'
fix(perf-v1.3): V1_2_5 清理 V1.0 NULL deleted 历史数据（Task R0.1）

V1.0 MetricDefService.create 未初始化 deleted 字段导致生产可能存在 NULL 孤儿行，
Q8.5b 修复但未清理历史数据。V1_2_5 幂等 UPDATE 补救。

EOF
)"
```

---

### Task R0.2：perf_run_task 加 UK(task_key) + 幂等降级

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_3_0__perf_run_task_uk.sql`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/DataTaskService.java`（SETNX 失败时捕获 DuplicateKeyException 降级）
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_3_0FlywayIT.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/DataTaskServiceIdempotentIT.java`（加 Redis 挂掉时 DB UK 兜底场景）

- [ ] **Step 1：写失败测试（UK 存在 + DuplicateKey 降级）**

```java
// V1_3_0FlywayIT.java
@Test
void uk_taskKey_exists() {
    String ukCol = jdbc.queryForObject(
        "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) " +
        "FROM information_schema.STATISTICS " +
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_run_task' " +
        "AND INDEX_NAME='uk_task_key'", String.class);
    assertThat(ukCol).isEqualTo("task_key");
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：创建 V1_3_0 脚本**

```sql
-- V1_3_0__perf_run_task_uk.sql
-- V1.1 Q6 DataTaskService SETNX 幂等的 DB 兜底
-- 避免 Redis 宕机时出现同 taskKey 重复行
--
-- 【生产前置检查 runbook（必须运维在应用前人工执行）】
-- 1) 查重复 task_key：
--    SELECT task_key, COUNT(*) FROM perf_run_task
--    WHERE task_key IS NOT NULL GROUP BY task_key HAVING COUNT(*) > 1;
--    若有结果：按业务规则保留最早 / 最晚一条，DELETE 其余行
--
-- 2) 查空 task_key（MySQL UNIQUE 允许多 NULL，生产不阻塞，但建议审计）：
--    SELECT COUNT(*) FROM perf_run_task WHERE task_key IS NULL;
--
-- 3) 若步骤 1 有重复且清理完毕，再应用本脚本
--
-- 【回滚策略】
-- 若 ALTER 失败（存量重复）：脚本未生效，Flyway 标记 FAILED。
-- 解决：(a) 手工清理重复行；(b) 在 flyway_schema_history 删除 FAILED 行；
-- (c) 重新 mvn flyway:migrate
ALTER TABLE perf_run_task ADD UNIQUE KEY uk_task_key (task_key);
```

> **⚠️ 生产部署检查清单（R7.2 需在 Runbook 章节收录此清单）**：
> - 先跑 `SELECT task_key, COUNT(*) FROM perf_run_task WHERE task_key IS NOT NULL GROUP BY task_key HAVING COUNT(*) > 1;`
> - 有结果 → 手工清理后再 Flyway migrate
> - 无结果 → 直接 migrate

- [ ] **Step 4：DataTaskService 降级逻辑**

在 `DataTaskService.report(cmd)` 的 `insertRunTask` 调用 catch 中加 `DuplicateKeyException` 分支，返回"已接受"的既有 runTaskId：

```java
try {
    perfRunTaskMapper.insert(runTask);
} catch (DuplicateKeyException e) {
    // Redis 宕机时的 DB 兜底：查既有行
    PerfRunTask existing = perfRunTaskMapper.selectByTaskKey(taskKey);
    if (existing == null) {
        // 理论不应到达
        throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, "task_key 冲突但查不到既有行");
    }
    return new DataTaskReportResultDTO(taskKey, false, existing.getId());
}
```

- [ ] **Step 5：运行测试通过**

```bash
mvn -Dtest=V1_3_0FlywayIT,DataTaskServiceIdempotentIT test
```

- [ ] **Step 6：Commit**

```bash
git commit -m "fix(perf-v1.3): perf_run_task 加 uk_task_key + DataTaskService DuplicateKey 降级（Task R0.2）"
```

---

### Task R0.3：failsafe 分层配置

**背景**：V1.2 Q8.3 surefire 扫描 *IT 后测试总数增长到 842，mvn test 反馈时间变长。本任务引入 maven-failsafe-plugin 做 Test（单元）+ IT（集成）分层。

**Files:**
- Modify: 根 `pom.xml`（surefire excludes `**/*IT.java` + failsafe includes `**/*IT.java`）
- Modify: `performance-engine-center/pom.xml`（若需模块内覆盖）

- [ ] **Step 1：读根 pom 现状**

```
Read D:\Project\oneplate\pom.xml
```

- [ ] **Step 2：根 pom 改造**

修改 maven-surefire-plugin：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
        </includes>
        <excludes>
            <exclude>**/*IT.java</exclude>  <!-- V1.3 R0.3：改由 failsafe 执行 -->
        </excludes>
    </configuration>
</plugin>
```

追加 maven-failsafe-plugin：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.2.5</version>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
</plugin>
```

- [ ] **Step 3：验证两阶段**

```bash
cd performance-engine-center
mvn clean test                  # 只跑 *Test / *Tests（应 < 842）
mvn clean verify                # 跑 test + IT（应 = 842）
```

- [ ] **Step 4：Commit**

```bash
git commit -m "chore(perf-v1.3): maven-failsafe-plugin 分层 Test/IT（Task R0.3）"
```

---

### Task R0.4：修订模块 CLAUDE.md uk_task_key 错误声明

**背景**：V1.2 Q8.2 模块 CLAUDE.md 技术债章节"V1.2 已消化"列表中声称 `V1.2 Q0.2 DDL 增加 uk_task_key 幂等唯一键`，但实际 V1_2_0 ~ V1_2_4 脚本 **均未**添加该 UK，生产 `ddl-performance.sql` 的 `perf_run_task` 也只有 4 个普通 KEY 无 UNIQUE。此错误声明由 plan-document-reviewer 第一轮复审发现。

V1.3 R0.2 真实补齐该 UK，R0.4 同步修订模块 CLAUDE.md 把错误声明改为"V1.3 R0.2 补齐"。

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（技术债章节对 uk_task_key 的声明）

- [ ] **Step 1：Grep 定位**

```
Grep pattern="uk_task_key" path="performance-engine-center/CLAUDE.md" -n
```

- [ ] **Step 2：修改**

将"V1.2 Q0.2 DDL 增加 uk_task_key 幂等唯一键"改为：
```markdown
- **perf_run_task.task_key UNIQUE KEY**：V1.3 R0.2 通过 `V1_3_0__perf_run_task_uk.sql` 补齐（V1.2 声称已加但实际未执行，V1.3 勘误）
```

- [ ] **Step 3：Commit**

```bash
git commit -m "docs(perf-v1.3): 勘误模块 CLAUDE.md uk_task_key 声明，V1.3 R0.2 真实补齐（Task R0.4）"
```

---

## 阶段 R1：Target 数据范围注入

### Task R1.1：TargetValue 数据范围注入

**背景**：V1.2 Q7 的 PerfScopeHelper 对 Alloc/Kpi/Metric 做了示范注入，Target 留给 V1.3。本任务扩展 TargetValueService/TargetValueMapper 增加 `pageWithScope` 方法。

**Files:**
- Modify: `.../service/TargetValueService.java`（注入 PerfScopeHelper + CurrentUserApi + 新增 pageWithScope）
- Modify: `.../mapper/PerfTargetValueMapper.java` + XML（新增 selectByConditionWithScope）
- Test: `.../service/TargetValueServiceScopeTest.java`（新建，参照 MetricDefServiceScopeTest）

- [ ] **Step 1：Red 测试**

```java
class TargetValueServiceScopeTest {
    @Mock PerfScopeHelper perfScopeHelper;
    @Mock CurrentUserApi currentUserApi;
    @Mock PerfTargetValueMapper mapper;
    @InjectMocks TargetValueService service;

    @Test
    void pageWithScope_appliesScopeFragment() {
        when(currentUserApi.currentUserId()).thenReturn("USER_A");
        Fragment f = Fragment.of("emp_id = #{scopeParams.ownerEmpId}",
            Map.of("ownerEmpId", "USER_A"));
        when(perfScopeHelper.getFragment("USER_A", "P_PERF_TARGET_VALUE_QUERY", ...))
            .thenReturn(f);

        service.pageWithScope(new TargetValueQueryReq(), "USER_A");

        verify(mapper).selectByConditionWithScope(argThat(cond ->
            "emp_id = #{scopeParams.ownerEmpId}".equals(cond.getScopeFragment())));
    }

    @Test
    void pageWithScope_failClose_returnsEmpty() {
        when(perfScopeHelper.getFragment(...)).thenReturn(Fragment.failClose());
        PageResult<PerfTargetValue> result = service.pageWithScope(req, "USER_B");
        assertThat(result.getList()).isEmpty();
    }
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：Mapper XML 加 scope 分支**

```xml
<select id="selectByConditionWithScope" resultType="PerfTargetValue">
  SELECT ... FROM perf_target_value
  WHERE 1=1
  <if test="scopeFragment != null and scopeFragment != ''">
    AND (${scopeFragment})
  </if>
  ...
  LIMIT #{offset}, #{limit}
</select>
```

Service 层 `pageWithScope` 从 `CurrentUserApi` 取 userId → 调 PerfScopeHelper → 传 Fragment 到 Mapper。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.3): TargetValueService 数据范围注入（Green，Task R1.1）"
```

---

### Task R1.2：TargetPlan 数据范围注入

同 R1.1 模式，扩展 `TargetPlanService.pageWithScope`。

**Files:**
- Modify: `TargetPlanService.java` + `PerfTargetPlanMapper.java` + XML
- Test: `TargetPlanServiceScopeTest.java`

> **ScopeColumns 确定步骤**：先 Read `docs/schema/ddl-performance.sql` 找 `perf_target_plan` 实际列（可能含 `created_by` / `owner_emp_id` / `owner_org_id`），以实际为准。V1.3 先映射 SELF_CREATED → `created_by`、SELF → `owner_emp_id`（若无则降级 `created_by`）、ORG → `owner_org_id`。

- [ ] **Step 1：Red 测试**

```java
class TargetPlanServiceScopeTest {
    @Mock PerfScopeHelper perfScopeHelper;
    @Mock CurrentUserApi currentUserApi;
    @Mock PerfTargetPlanMapper mapper;
    @InjectMocks TargetPlanService service;

    @Test
    void pageWithScope_appliesFragment() {
        when(currentUserApi.currentUserId()).thenReturn("USER_A");
        Fragment f = Fragment.of("created_by = #{scopeParams.ownerEmpId}",
            Map.of("ownerEmpId", "USER_A"));
        when(perfScopeHelper.getFragment(eq("USER_A"), eq("P_PERF_TARGET_PLAN_QUERY"),
            any(ScopeColumns.class))).thenReturn(f);

        service.pageWithScope(new TargetPlanQueryReq());

        verify(mapper).selectByConditionWithScope(argThat(cond ->
            "created_by = #{scopeParams.ownerEmpId}".equals(cond.getScopeFragment())
            && "USER_A".equals(cond.getScopeParams().get("ownerEmpId"))));
    }

    @Test
    void pageWithScope_failClose_returnsEmpty() {
        when(currentUserApi.currentUserId()).thenReturn("USER_B");
        when(perfScopeHelper.getFragment(any(), any(), any())).thenReturn(Fragment.failClose());
        PageResult<TargetPlanDTO> result = service.pageWithScope(new TargetPlanQueryReq());
        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：Green（Service + Mapper + XML）**

```java
// TargetPlanService.java 新增
public PageResult<TargetPlanDTO> pageWithScope(TargetPlanQueryReq req) {
    String userId = currentUserApi.currentUserId();
    Fragment fragment = perfScopeHelper.getFragment(userId, "P_PERF_TARGET_PLAN_QUERY",
        new ScopeColumns("created_by", "owner_emp_id", "created_by", "owner_org_id"));
    if (fragment.isFailClose()) {
        return PageResult.empty();
    }
    TargetPlanCondWithScope cond = toCond(req, fragment);
    List<PerfTargetPlan> list = mapper.selectByConditionWithScope(cond);
    int total = mapper.countByConditionWithScope(cond);
    return PageResult.of(list.stream().map(TargetPlanAssembler::toDto).toList(), total);
}
```

XML 同 R1.1 模式：`<if test="scopeFragment != null and scopeFragment != ''"> AND (${scopeFragment}) </if>` + `#{scopeParams.xxx}` 参数预编译。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): TargetPlanService 数据范围注入失败测试（Red，Task R1.2）"
git commit -m "feat(perf-v1.3): TargetPlanService 数据范围注入（Green，Task R1.2）"
```

---

### Task R1.3：TargetValue/TargetPlan Controller 改造使用 pageWithScope

**Files:**
- Modify: `TargetValueController.java` list 方法
- Modify: `TargetPlanController.java` list 方法
- Test: `TargetValueControllerScopeIT.java` / `TargetPlanControllerScopeIT.java`

- [ ] **Step 1：Red IT**

```java
class TargetValueControllerScopeIT {
    @Autowired MockMvc mockMvc;
    @MockBean TargetValueService targetValueService;

    @Test
    void list_delegatesToPageWithScope() throws Exception {
        when(targetValueService.pageWithScope(any())).thenReturn(PageResult.empty());
        mockMvc.perform(get("/api/perf/target-value/list"))
            .andExpect(status().isOk());
        verify(targetValueService).pageWithScope(any()); // V1.3 改造后应调 pageWithScope，非旧 page
    }
}
```

- [ ] **Step 2：失败**（当前 Controller 调 `page`，不调 `pageWithScope`）

- [ ] **Step 3：Green**

```java
// TargetValueController.java
@GetMapping("/list")
@BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
public ResponseWrapper<TargetValueDTO> list(TargetValueQueryReq req) {
    // V1.3 R1.3：切换到 pageWithScope 注入 BizScopeApi 数据范围
    return ResponseWrapper.page(targetValueService.pageWithScope(req));
}
```

TargetPlanController 同构改造。CurrentUserApi 由 Service 内部取（Controller 不感知）。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): TargetValue/TargetPlan Controller list 端点启用数据范围失败 IT（Red，Task R1.3）"
git commit -m "feat(perf-v1.3): TargetValue/TargetPlan Controller list 端点启用数据范围（Green，Task R1.3）"
```

---

## 阶段 R2：5 处 V1.2 UOE 实际实现

### Task R2.1：PerfCalcApi.triggerKpiCalc 实现

**背景**：该方法本质是 `KpiCalcService.calcScheme` 的 Api 暴露。V1.2 因 "计算入口多处暴露"担忧保留 UOE，V1.3 决定统一走 Api（下游调用方唯一入口）。

**Files:**
- Modify: `PerfCalcApiImpl.java`（替换 UOE）
- Test: `PerfCalcApiImplTriggerTest.java`（已有，扩测 triggerKpiCalc）

- [ ] **Step 1：Red**

```java
@Test
void triggerKpiCalc_delegatesToKpiCalcService() {
    when(kpiCalcService.calcScheme("SCHEME_001", "Q", cycleDate, asOfDate, "v1"))
        .thenReturn(10);
    int count = api.triggerKpiCalc("SCHEME_001", "Q", cycleDate, asOfDate, "v1");
    assertThat(count).isEqualTo(10);
    verify(kpiCalcService).calcScheme(eq("SCHEME_001"), eq("Q"), eq(cycleDate), eq(asOfDate), eq("v1"));
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：Green**

```java
// PerfCalcApiImpl.java
@Override
public int triggerKpiCalc(String schemeCode, String cycleType,
                          LocalDate cycleDate, LocalDate asOfDate, String version) {
    return kpiCalcService.calcScheme(schemeCode, cycleType, cycleDate, asOfDate, version);
}
```

同步更新 `NoV11UOEArchTest` 允许 UOE 数量减 1（若断言数量）。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.3): PerfCalcApi.triggerKpiCalc 实现（Green，Task R2.1）"
```

---

### Task R2.2：MetricQueryApi.batchQueryEmpSnapshots 实现

**背景**：大批量员工指标快照查询（供 report-analytics 使用）。

**接口参考**（V1.0 原定义）：
```java
interface MetricQueryApi {
    /**
     * 批量查询员工指标快照。
     * @param empIds 员工 ID 列表（≤ 500）
     * @param metricCodes 指标编码列表（≤ 50）
     * @param dataDate 数据日期
     * @param version 版本
     * @return 员工 ID → 指标编码 → 数值 三级映射
     */
    Map<String, Map<String, BigDecimal>> batchQueryEmpSnapshots(
        List<String> empIds, List<String> metricCodes, LocalDate dataDate, String version);
}
```

**Files:**
- Modify: `MetricQueryApiImpl.java`（替换 UOE）
- Test: `MetricQueryApiImplTest.java`

**实现要点**：
1. 校验 empIds.size ≤ 500（`BATCH_QUERY_EXCEEDS_LIMIT`）
2. 校验 metricCodes.size ≤ 50
3. 批量查 `perf_metric_def` 得到 code → (val_slot, base_dim=EMP) 映射
4. 对每个 metricCode 调 `EmpIndexResultMapper.selectSlotValuesByEmps(empIds, dataDate, version, slot)`
5. 组装三级 Map

- [ ] **Step 1-5：TDD + commit**

```bash
git commit -m "feat(perf-v1.3): MetricQueryApi.batchQueryEmpSnapshots 实现（Green，Task R2.2）"
```

---

### Task R2.3 / R2.4：batchQueryOrgSnapshots / batchQueryCustSnapshots

同 R2.2 模式，分别查 OrgIndexResult / CustIndexResult 宽表。注意维度键：
- ORG：org_code（非 org_id）
- CUST：cust_id

**Files:**
- Modify: `MetricQueryApiImpl.java`
- Test: `MetricQueryApiImplTest.java` 扩展 batchQueryOrg/CustSnapshots 用例

- [ ] **Step 1：Red 测试（合并 R2.3 + R2.4 共 4 测试）**

```java
@Test
void batchQueryOrgSnapshots_returnsOrgCode_to_metricCode_to_value_map() {
    List<String> orgCodes = List.of("ORG_001", "ORG_002");
    List<String> metricCodes = List.of("M_ORG_DEP_TOTAL");
    LocalDate dataDate = LocalDate.of(2026, 4, 1);

    when(metricDefMapper.selectByCodes(metricCodes)).thenReturn(List.of(
        metric("M_ORG_DEP_TOTAL", 10, "ORG")));
    when(orgIndexResultMapper.selectSlotValuesByOrgs(orgCodes, dataDate, "v1", 10))
        .thenReturn(List.of(new MetricValueRow("ORG_001", new BigDecimal("1000")),
                            new MetricValueRow("ORG_002", new BigDecimal("2000"))));

    Map<String, Map<String, BigDecimal>> result =
        api.batchQueryOrgSnapshots(orgCodes, metricCodes, dataDate, "v1");

    assertThat(result).hasSize(2);
    assertThat(result.get("ORG_001").get("M_ORG_DEP_TOTAL")).isEqualByComparingTo("1000");
    assertThat(result.get("ORG_002").get("M_ORG_DEP_TOTAL")).isEqualByComparingTo("2000");
}

@Test
void batchQueryOrgSnapshots_exceedsLimit_throwsPerfException() {
    List<String> orgs = IntStream.range(0, 501).mapToObj(i -> "O_" + i).toList();
    assertThatThrownBy(() -> api.batchQueryOrgSnapshots(orgs, List.of("M1"), date, "v1"))
        .isInstanceOf(PerfException.class)
        .hasFieldOrPropertyWithValue("errorCode",
            PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT.getCode());
}

@Test
void batchQueryCustSnapshots_returnsCustId_map() { /* 同构 */ }

@Test
void batchQueryCustSnapshots_metricNotFound_throws() {
    when(metricDefMapper.selectByCodes(any())).thenReturn(List.of()); // 空
    assertThatThrownBy(() -> api.batchQueryCustSnapshots(custIds, codes, date, "v1"))
        .hasFieldOrPropertyWithValue("errorCode", "PERF-40001");
}
```

- [ ] **Step 2：失败**（UOE 仍在）

- [ ] **Step 3：Green**

```java
@Override
public Map<String, Map<String, BigDecimal>> batchQueryOrgSnapshots(
        List<String> orgCodes, List<String> metricCodes, LocalDate dataDate, String version) {
    validateBatch(orgCodes, 500, metricCodes, 50);
    Map<String, PerfMetricDef> codeToDef = loadMetricDefs(metricCodes, "ORG");
    Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
    for (String code : metricCodes) {
        PerfMetricDef def = codeToDef.get(code);
        List<MetricValueRow> rows = orgIndexResultMapper.selectSlotValuesByOrgs(
            orgCodes, dataDate, version, def.getValSlot());
        for (MetricValueRow row : rows) {
            result.computeIfAbsent(row.getKey(), k -> new LinkedHashMap<>())
                  .put(code, row.getValue());
        }
    }
    return result;
}
// batchQueryCustSnapshots 同构
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit（R2.3 + R2.4 合并两个 commit）**

```bash
git commit -m "test(perf-v1.3): MetricQueryApi.batchQueryOrg/CustSnapshots 失败测试（Red，Task R2.3+R2.4）"
git commit -m "feat(perf-v1.3): MetricQueryApi.batchQueryOrg/CustSnapshots 实现（Green，Task R2.3+R2.4）"
```

---

### Task R2.5：MetricApi.getUserMetricCards 实现

**背景**：用户工作台卡片需要综合目标 + 实绩 + 同环比。最复杂的一个 UOE。

**接口参考**（V1.0 原定义）：
```java
/**
 * 查询用户工作台指标卡片。
 *
 * @param empId 员工 ID
 * @param cycleType 周期类型（Q/M/Y）
 * @param cycleDate 周期日期
 * @return 卡片列表（metricCode / target / actual / achievementRate / mom / yoy）
 */
List<UserMetricCardDTO> getUserMetricCards(String empId, String cycleType, LocalDate cycleDate);
```

**Files:**
- Modify: `MetricApiImpl.java`（替换 UOE）
- Create: `.../api/dto/UserMetricCardDTO.java`（若不存在）
- Test: `MetricApiImplCardsTest.java`

**实现思路**：
1. 通过 `perf_kpi_scheme` 查该员工当期应考核的 metric 清单
2. 对每个 metric：
   - target = `perf_target_value`（emp_id + metric_code + cycle_key）
   - actual = `emp_index_result`（emp_id + metric_code + data_date=cycleDate）
   - achievementRate = actual / target * 100
   - mom（环比）= 上月同日 actual → 当前 actual 变化率
   - yoy（同比）= 去年同日 actual → 当前 actual 变化率
3. 返回 DTO 列表

**简化策略**：V1.3 初版可只提供 target + actual + achievementRate；mom/yoy 留 V1.4（标注注释）。或者全量实现，对 mom/yoy 查询性能不敏感（卡片数量 ≤ 20）。

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.3): MetricApi.getUserMetricCards 实现（Green，Task R2.5）"
```

---

## 阶段 R3：错误码 / DTO 细化

### Task R3.1：新增 IDEMPOTENCY_WAIT_TIMEOUT 错误码

**背景**：V1.1 Q6 DataTaskService 幂等等待超时使用 `CALC_JOB_FAILED(PERF-50007)` 语义不清。

**Files:**
- Modify: `PerfErrorCode.java`（新增 PERF-50003 IDEMPOTENCY_WAIT_TIMEOUT）
- Modify: `PerfErrorCodeTest.java`（断言新增 + enumSize 29 → 30）
- Modify: `DataTaskService.java`（幂等超时分支改用新错误码）
- Modify: `docs/modules/performance-engine-center/03-接口设计与报文.md` §K.4（追加一行）

- [ ] **Step 1：Red 测试**

```java
// PerfErrorCodeTest.java 追加
@Test
void idempotencyWaitTimeout_exists_and_30codesTotal() {
    PerfErrorCode code = PerfErrorCode.IDEMPOTENCY_WAIT_TIMEOUT;
    assertThat(code.getCode()).isEqualTo("PERF-50003");
    assertThat(code.getMessage()).contains("幂等等待超时");
    assertThat(PerfErrorCode.values()).hasSize(30);
}
```

- [ ] **Step 2：失败**（常量不存在）

- [ ] **Step 3：Green**

```java
// PerfErrorCode.java 追加
IDEMPOTENCY_WAIT_TIMEOUT("PERF-50003", "幂等等待超时（Redis 锁释放后仍无 DB 记录）"),
```

```java
// DataTaskService.java 替换
// 原：throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, ...)
// 新：
throw new PerfException(PerfErrorCode.IDEMPOTENCY_WAIT_TIMEOUT,
    "taskKey=" + taskKey + " 等待 3s 后仍未见 DB 记录");
```

§K.4 追加：
```markdown
| `PERF-50003` | 500 | 幂等等待超时（Redis 锁释放后仍无 DB 记录） |
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): IDEMPOTENCY_WAIT_TIMEOUT 常量存在性断言（Red，Task R3.1）"
git commit -m "feat(perf-v1.3): 新增 PERF-50003 IDEMPOTENCY_WAIT_TIMEOUT + DataTaskService 替换（Green，Task R3.1）"
```

---

### Task R3.2：MetricTrialRespDTO 字段对齐 03 §A.5

**背景**：V1.1 P3 reviewer 提出 `samples → sampleRows` + 补 `taskId/startedAt/endedAt/status/errorMsg`。

**Files:**
- Modify: `MetricTrialRespDTO.java`（字段重命名 + 新增 5 字段 + @Deprecated 兼容）
- Modify: `MetricTrialService.java`（返回对象装配新字段）
- Modify: `MetricTrialControllerIT.java` / `MetricTrialServiceTest.java`（对齐新字段名）

**破坏性变更兼容策略**：保留旧 `samples` getter 加 `@Deprecated` + `@JsonAlias` 让前端过渡期既能读 `samples` 也能读 `sampleRows`，1 个版本后删除。

- [ ] **Step 1：Red 测试**

```java
@Test
void respDTO_hasSampleRows_andLegacySamplesAlias() throws Exception {
    MetricTrialRespDTO dto = MetricTrialRespDTO.builder()
        .taskId("TASK_001")
        .metricCode("M_X")
        .sampleSize(5)
        .sampleRows(List.of(Map.of("base_key", "E1", "metric_value", "100")))
        .status("SUCCESS")
        .startedAt(LocalDateTime.now())
        .endedAt(LocalDateTime.now())
        .build();
    String json = objectMapper.writeValueAsString(dto);
    assertThat(json).contains("\"sampleRows\"").contains("\"taskId\"")
        .contains("\"status\"").contains("\"startedAt\"").contains("\"endedAt\"");
    // @JsonAlias 允许反序列化 samples
    MetricTrialRespDTO parsed = objectMapper.readValue(
        "{\"taskId\":\"T\",\"samples\":[]}", MetricTrialRespDTO.class);
    assertThat(parsed.getSampleRows()).isEmpty();
}
```

- [ ] **Step 2：失败**（当前字段名 samples，无 taskId/status/startedAt/endedAt/errorMsg）

- [ ] **Step 3：Green**

```java
@Data
@Builder
public class MetricTrialRespDTO {
    private String taskId;                              // V1.3 新增
    private String metricCode;
    private Integer sampleSize;
    private Integer totalRows;
    private String status;                              // V1.3 新增：RUNNING/SUCCESS/FAILED
    private LocalDateTime startedAt;                    // V1.3 新增
    private LocalDateTime endedAt;                      // V1.3 新增
    private String errorMsg;                            // V1.3 新增
    private BigDecimal exprResult;
    private Long executionMillis;

    @JsonAlias({"samples"})                             // V1.3 R3.2：兼容旧前端请求
    private List<Map<String, Object>> sampleRows;

    /** @deprecated V1.3 改名为 sampleRows，V1.4 删除 */
    @Deprecated
    @JsonIgnore
    public List<Map<String, Object>> getSamples() { return sampleRows; }
}
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): MetricTrialRespDTO sampleRows + 元数据字段 (Red，Task R3.2)"
git commit -m "fix(perf-v1.3): MetricTrialRespDTO 字段对齐 03 §A.5 + @JsonAlias 兼容旧 samples（Green，Task R3.2）"
```

---

### Task R3.3：MetricDefController.execute 返回 RunTaskInfoDTO

**背景**：V1.1 P3 return `Map<String,Object>` → V1.3 改为 `RunTaskInfoDTO`（回归 03 §A.6 契约；04 契约 §5 原本就应为 `R<RunTaskInfoDTO>`，V1.1 偏离，V1.3 回归）。

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/RunTaskInfoDTO.java`
- Modify: `MetricDefController.execute` 返回类型 `ResponseWrapper<Map<String,Object>>` → `ResponseWrapper<RunTaskInfoDTO>`
- Modify: `MetricExecuteControllerIT.java`（断言字段名变为 DTO 属性）

- [ ] **Step 1：Red 测试**

```java
@Test
void execute_returnsRunTaskInfoDTO_notMap() throws Exception {
    String body = mockMvc.perform(post("/api/perf/metrics/M_X/execute")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"dataDate\":\"2026-04-01\",\"reason\":\"test\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode data = objectMapper.readTree(body).get("data");
    assertThat(data.has("taskId")).isTrue();
    assertThat(data.has("status")).isTrue();
    assertThat(data.has("metricCode")).isTrue();
    // 验证是结构化对象（非 Map 的平面 KV）
    assertThat(data.isObject()).isTrue();
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：Green**

```java
// RunTaskInfoDTO.java
@Data
@Builder
public class RunTaskInfoDTO {
    private String taskId;
    private String status;       // RUNNING / SUCCESS / FAILED
    private String metricCode;
    private LocalDate dataDate;
    private String version;
}

// MetricDefController.java
@PostMapping("/{metricCode}/execute")
@BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
@AuditLog(action = "METRIC_EXECUTE", resourceType = "PERF_METRIC_RUN", reasonRequired = true)
public ResponseWrapper<RunTaskInfoDTO> execute(@PathVariable String metricCode,
                                                @Valid @RequestBody MetricExecuteReqDTO req) {
    // V1.3 R3.3：回归 03 §A.6 契约 RunTaskInfoDTO，替代 V1.1 临时 Map<String,Object>
    String taskId = req.isCascade()
        ? cascadeRefresher.refreshCascade(metricCode, req.getDataDate(), req.resolveVersion())
        : metricCalcService.calcMetric(metricCode, req.getDataDate(), req.resolveVersion());
    PerfRunTask task = perfRunTaskMapper.selectById(taskId);
    return ResponseWrapper.ok(RunTaskInfoDTO.builder()
        .taskId(taskId).status(task.getStatus())
        .metricCode(metricCode).dataDate(req.getDataDate()).version(req.resolveVersion())
        .build());
}
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): MetricDefController.execute 返回 RunTaskInfoDTO 失败 IT（Red，Task R3.3）"
git commit -m "fix(perf-v1.3): MetricDefController.execute 返回 RunTaskInfoDTO 回归 03 §A.6（Green，Task R3.3）"
```

---

## 阶段 R4：Controller 重构 + P7 修复

### Task R4.1：5 Controller 局部变量 entity 重构

**背景**：V1.0 Phase D reviewer 指出 5 个 Controller 方法体内仍使用 entity 作为中间变量。V1.3 迁到 Facade/Service 层。`NoEntityInControllerArchTest` 只守护返回类型，不守护局部变量，V1.3 加强守护。

**涉及 Controller**（通过 Grep 确认）：
```
Grep pattern="import com.bank.branch.platform.performance.entity" path="performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller" -l
```
预期：KpiSchemeController / TargetPlanController / TargetValueController / AllocRelationController / PerfRunTaskController（可能还有 Controller；以 Grep 为准）

**Files:**
- Modify: 上述 5 个 Controller（去除 entity import + 局部变量改为 DTO）
- Modify: 对应 Service/Facade 增加 `xxxDTO` 版本方法（若不存在）
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/arch/NoEntityInControllerLocalsArchTest.java`

- [ ] **Step 1：Red 架构测试**

```java
// NoEntityInControllerLocalsArchTest.java
@AnalyzeClasses(packages = "com.bank.branch.platform.performance.controller")
class NoEntityInControllerLocalsArchTest {
    @ArchTest
    static final ArchRule controllers_shouldNot_import_entity_classes =
        noClasses().that().resideInAPackage("..performance.controller..")
            .should().dependOnClassesThat().resideInAPackage("..performance.entity..")
            .because("Controller 层不得感知 entity（包括局部变量、import）V1.3 加强守护");
}
```

- [ ] **Step 2：失败**（5 个 Controller 仍 import entity）

- [ ] **Step 3：Green**

对每个 Controller：
- 删除 `import com.bank.branch.platform.performance.entity.*`
- 方法体中 `PerfXxx entity = service.xxx(id)` 改为 `XxxDTO dto = service.getXxxById(id)`（Service 返回 DTO）
- Service 若仍返回 entity，在 Service 层加 `@Named XxxDTO findDtoById(String id)` 方法（内部转换后返回）

示例（TargetValueController.batch 现有 `new PerfTargetValue()` 构造）：
```java
// V1.2 残留
PerfTargetValue target = new PerfTargetValue();
target.setPlanId(cmd.getPlanId());
// V1.3 迁到 Service
BatchUpsertResult result = targetValueService.batchUpsertFromCmd(cmd);
```

- [ ] **Step 4：通过**（架构测试绿 + 5 Controller 测试无回归）

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): Controller 禁止依赖 entity 的架构守护（Red，Task R4.1）"
git commit -m "refactor(perf-v1.3): 5 Controller 局部变量 entity 迁到 Facade/Service（Green，Task R4.1）"
```

---

### Task R4.2：P7 triggerRecalc Controller status 读真实终态

**背景**：V1.2 Q7 reviewer 提出 Controller 硬编码返回 "RUNNING"，实际父 run_task 已是终态（HistoryRecalcService 同步执行完成）。

**Files:**
- Modify: `PerfCalcController.recalc()`（读父 task 真实 status）
- Modify: `PerfCalcControllerRecalcIT.java`（断言 status 与实际 task 一致）

- [ ] **Step 1：Red 测试**

```java
@Test
void recalc_returnsRealStatus_notHardcodedRunning() throws Exception {
    when(perfCalcApi.triggerRecalc(anyString(), any(), any(), eq(null),
        anyString(), anyString(), anyString())).thenReturn("PARENT_TASK_ID");
    when(perfRunTaskMapper.selectById("PARENT_TASK_ID"))
        .thenReturn(PerfRunTask.builder().id("PARENT_TASK_ID").status("SUCCESS").build());

    String body = mockMvc.perform(post("/api/perf/recalc")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{...}"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).contains("\"status\":\"SUCCESS\"").doesNotContain("\"RUNNING\"");
}
```

- [ ] **Step 2：失败**（当前硬编码 "RUNNING"）

- [ ] **Step 3：Green**

```java
// PerfCalcController.recalc()
String parentTaskId = perfCalcApi.triggerRecalc(...);
PerfRunTask parentTask = perfRunTaskMapper.selectById(parentTaskId);
String realStatus = parentTask != null ? parentTask.getStatus() : "RUNNING";
return ResponseWrapper.ok(RecalcRespDTO.builder()
    .taskId(parentTaskId).status(realStatus).build());
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): PerfCalcController.recalc status 读真实终态失败 IT（Red，Task R4.2）"
git commit -m "fix(perf-v1.3): P7 recalc Controller 响应 status 读真实终态（Green，Task R4.2）"
```

---

### Task R4.3：cycleType 参数写入 params_json

**背景**：V1.2 Q7 reviewer 提出 `HistoryRecalcService` 透传 cycleType 但只记录到 log，未写入 run_task.params_json，运维无法从 DB 审计回溯。

**Files:**
- Modify: `HistoryRecalcService.insertParentTask`（params_json 包含 cycleType）
- Modify: `HistoryRecalcServiceTest.java`（断言 params_json 结构）

- [ ] **Step 1：Red 测试**

```java
@Test
void recalc_writesCycleTypeIntoParamsJson() {
    service.recalc(startDate, endDate, List.of("M_A"), "v1", "原因", "operator", "QUARTERLY");

    ArgumentCaptor<PerfRunTask> cap = ArgumentCaptor.forClass(PerfRunTask.class);
    verify(perfRunTaskMapper).insert(cap.capture());
    String paramsJson = cap.getValue().getParamsJson();
    assertThat(paramsJson).contains("\"cycleType\":\"QUARTERLY\"");
    assertThat(paramsJson).contains("\"startDate\"").contains("\"endDate\"");
}
```

- [ ] **Step 2：失败**（当前 params_json 只含 startDate/endDate/metricCount 等，不含 cycleType）

- [ ] **Step 3：Green**

```java
// HistoryRecalcService.insertParentTask 中 paramsJson 组装增加 cycleType
String paramsJson = String.format(
    "{\"startDate\":\"%s\",\"endDate\":\"%s\",\"metricCount\":%d,\"dateCount\":%d," +
    "\"cycleType\":\"%s\",\"reason\":\"%s\"}",
    startDate, endDate, metricCount, dateCount, cycleType, escape(reason));
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): HistoryRecalcService 写 cycleType 到 params_json 失败测试（Red，Task R4.3）"
git commit -m "fix(perf-v1.3): HistoryRecalcService 写入 cycleType 到 params_json（Green，Task R4.3）"
```

---

## 阶段 R5：测试基础设施

### Task R5.1：Testcontainers-redis 接入

**背景**：V1.2 Q8 将 2 个 IT（`DataTaskServiceIdempotentIT` + `SysControlConcurrentIT`）标 `@Disabled`（reviewer 第二轮修正：实际是 2 个 Redis 依赖 + 1 个 UndoSmoke 共 3 个 @Disabled，其中 UndoSmoke 在 R5.2 处理）。V1.3 引入 Testcontainers-redis 自动启动容器。

**Files:**
- **Step 0（读根 pom 核实）**：
  ```
  Read D:\Project\oneplate\pom.xml  # 查是否已有 testcontainers-bom
  ```
  若已有 testcontainers-bom，则 `testcontainers-redis` 依赖不需要 `<version>`（继承 BOM）；若没有，则补独立 version。
- Modify: `performance-engine-center/pom.xml`（追加 testcontainers-redis 依赖）
- Create: `PerformanceRedisTestBase.java`（继承 PerformanceMapperTestBase，追加 Redis 容器）
- Modify: 2 个 @Disabled IT（DataTaskServiceIdempotentIT + SysControlConcurrentIT）取消 @Disabled 并继承 PerformanceRedisTestBase

**依赖**（根 BOM 未有时的完整坐标）：
```xml
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>testcontainers-redis</artifactId>
    <version>2.2.2</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 1：Red 测试**

```java
@SpringBootTest(classes = {PerformanceFlywayTestBase.Config.class, PerfTestConfig.class})
@Testcontainers
class PerformanceRedisTestBaseSanityTest extends PerformanceRedisTestBase {
    @Autowired RedisTemplate<String, String> redisTemplate;

    @Test
    void redis_isAvailable() {
        redisTemplate.opsForValue().set("perf:test:ping", "PONG");
        assertThat(redisTemplate.opsForValue().get("perf:test:ping")).isEqualTo("PONG");
    }
}
```

- [ ] **Step 2：失败**（Testcontainers-redis 未引入）

- [ ] **Step 3：Green**

```java
// PerformanceRedisTestBase.java
@Testcontainers
public abstract class PerformanceRedisTestBase extends PerformanceMapperTestBase {
    @Container
    protected static final RedisContainer REDIS =
        new RedisContainer("redis:6.2.14-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry reg) {
        reg.add("spring.data.redis.host", REDIS::getHost);
        reg.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }
}

// DataTaskServiceIdempotentIT.java 移除 @Disabled + extends PerformanceRedisTestBase
// SysControlConcurrentIT.java 同上
```

- [ ] **Step 4：通过**（在有 Docker 的环境下两 IT 真正执行）

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): PerformanceRedisTestBase + Testcontainers-redis 接入失败测试（Red，Task R5.1）"
git commit -m "test(perf-v1.3): Testcontainers-redis 接入 + 2 Redis IT 取消 @Disabled（Green，Task R5.1）"
```

---

### Task R5.2：UndoScriptSmokeIT 重写

**背景**：V1.2 Q8.5c 将 UndoScriptSmokeIT 标 @Disabled，原因是 Flyway 基线已爬升到 V1_2_x，而 undo 脚本只覆盖 V1_0_3 / V1_0_4，导致 @BeforeEach 的 "undo → delete history → migrate" 循环失效。V1.3 重写策略：
1. 取消 @Disabled
2. 只覆盖 V1_0_3 undo（V1_0_4 的 PT_RESOURCE undo 已与 V1_2_4 合并无意义，删除其断言）
3. @BeforeEach：从 V1_0_2 起跑 migrate 到 V1_0_3 稳定态
4. @AfterEach：跑 V1_2_x 完整迁移恢复全局状态

**Files:**
- Modify: `UndoScriptSmokeIT.java`

- [ ] **Step 1：Red 测试**

```java
// UndoScriptSmokeIT.java（移除 @Disabled）
class UndoScriptSmokeIT extends PerformanceFlywayTestBase {

    @BeforeEach
    void rollbackToV102() {
        // 清理 V1_0_3+ 的 flyway_schema_history 行 + 跑 V1_0_3 undo + 重新 migrate 到 V1_0_3
        jdbc.execute("DELETE FROM flyway_schema_history WHERE version >= '1.0.3'");
        executeSqlScript("src/main/resources/undo-scripts/V1_0_3__undo.sql");
        flyway.migrate(); // 重新应用 V1_0_3 一次（回到 V1_0_3 稳定态）
    }

    @Test
    void v1_0_3_undo_revertsSchema_andReMigrationWorks() {
        // V1_0_3 undo 应移除 sys_control.remark 等字段 + 还原索引
        assertThat(columnExists("sys_control", "remark")).isFalse();
        // 再跑一次 migrate 恢复 V1_0_3
        flyway.migrate();
        assertThat(columnExists("sys_control", "remark")).isTrue();
    }

    @AfterEach
    void restoreToLatest() {
        // 跑 migrate 到当前最新（V1_2_5 或后续）
        flyway.migrate();
    }
}
```

- [ ] **Step 2：失败**（@Disabled 未移除）

- [ ] **Step 3：Green**

- 移除 @Disabled 注解
- 实现 @BeforeEach/@AfterEach（上面 Red 骨架即最终实现）
- 删除 V1_0_4 相关断言（因 V1_0_4 的 PT_RESOURCE 已在 V1_2_4 重组）

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): UndoScriptSmokeIT 重写支持当前 Flyway 基线（Red + Green，Task R5.2）"
```

---

### Task R5.3：V1.0 UOE 测试资产改行为断言

**背景**：V1.1 P8 测试里有大量 "xxx_throwsUOE" 断言（固化 "V1.x delivered" 占位行为），V1.3 UOE 被替换后这些测试失去意义（会变成 Red，影响回归）。替换为真实行为断言。

**涉及文件**：
- `KpiApiImplTest.triggerKpiCalc_throwsUOE`（V1.2 Q8 发现时改 message 为 "V1.2 delivered"，V1.3 替换为真实路径断言）
- `MetricQueryApiImplTest.batchQuery*Snapshots_throwsUoe` × 3（V1.3 R2.2/R2.3/R2.4 实现后替换）
- `PerfCalcApiImplTest.triggerKpiCalc_throwsUOE`（V1.3 R2.1 实现后替换）
- `MetricApiImplTest.getUserMetricCards_throwsUOE`（若存在，V1.3 R2.5 实现后替换）

**Files:**
- Modify: 上述 4-5 个测试文件中的 UOE 断言

- [ ] **Step 1：Red 测试（整体 grep 列清单）**

```bash
# 前置：grep 找到所有残留 UOE 断言
# Grep pattern="throwsUOE|throws_Uoe|assertThatThrownBy.*UnsupportedOperationException" path="performance-engine-center/src/test"
```

对每个 UOE 断言写新的行为断言（例如 triggerKpiCalc 应委托 KpiCalcService）：
```java
// 原：
@Test
void triggerKpiCalc_throwsUOE() {
    assertThatThrownBy(() -> api.triggerKpiCalc(...))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("V1.2 delivered");
}

// 新 V1.3：
@Test
void triggerKpiCalc_delegatesToKpiCalcService() {
    when(kpiCalcService.calcScheme("S", "Q", date, date, "v1")).thenReturn(5);
    assertThat(api.triggerKpiCalc("S", "Q", date, date, "v1")).isEqualTo(5);
    verify(kpiCalcService).calcScheme("S", "Q", date, date, "v1");
}
```

- [ ] **Step 2：失败**（Red 测试尚未生效，UOE 断言仍在）

- [ ] **Step 3：Green**

逐个替换 UOE 断言为行为断言，删除 `throwsUOE` 测试名，重命名为 `delegatesTo*` / `returnsSnapshotMap` 等业务语义命名。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.3): V1.0 UOE 测试资产改行为断言（Green，Task R5.3）"
```

---

## 阶段 R6：WORKFLOW_PARTICIPANT scope（整体延期 V1.4）

**决策（plan-document-reviewer 第一轮复审后确认）**：

经核查 workflow-center 现有 `WorkflowApi` 只提供 `startProcess` + 少量查询方法，`WorkflowQueryApi` 9 个方法全部围绕 "待办 / 完成 / 详情 / 历史" 导向，**没有"查询用户参与过哪些 businessKey"的方法**。

V1.3 推进路径有两条：
- A. 跨模块协作，先在 workflow-center 新增 API（跨模块 commit + push），再回 perf 做 PerfScopeHelper 实现
- B. 整体延期 V1.4，V1.3 保持 WORKFLOW_PARTICIPANT fail-close 现状

**选 B**。理由：
1. V1.3 已有 23 Task 集中在本模块清债，避免跨模块协作拉长周期
2. WORKFLOW_PARTICIPANT 是低优先级（V1.2 Q7 reviewer 已标）
3. workflow-center API 扩展独立价值，应由专门 Task 推进

**V1.4 规划条目**（在 R7.3 技术债清算章节登记）：
- `WorkflowQueryApi.queryParticipatedBusinessKeys(userId, bizType, timeWindow)`
- `PerfScopeHelper.WORKFLOW_PARTICIPANT` 分支消费上述 API

**V1.3 本阶段无 Task**（R6.1 移除）。

---

## 阶段 R7：V1.3 验收与文档收尾

### Task R7.1：全量回归 + 测试统计

- [ ] **Step 1：跑全量 verify（surefire + failsafe）**

```bash
cd performance-engine-center
mvn clean verify
```

预期：
- 单元测试（surefire）：× 个（< 842）
- 集成测试（failsafe）：× 个（V1.3 新增 10-15 个 + V1.2 共 +323 = 约 340 个）
- 总计：约 870+ 个全绿

- [ ] **Step 2：架构守护 5 个绿**（R4.1 新增 NoEntityInControllerLocalsArchTest）

```bash
mvn -Dtest=BizAuthConsistencyArchTest,NoEntityInControllerArchTest,NoEntityInControllerLocalsArchTest,NoV11UOEArchTest,PerfErrorCodeTest test
```

- [ ] **Step 3：Commit（空提交收尾）**

```bash
git commit --allow-empty -m "chore(perf-v1.3): 阶段 R7.1 全量回归通过（Task R7.1）"
```

---

### Task R7.2：V1.3 文档同步

**Files:**
- Modify: `CLAUDE.md`（根）：performance 状态 "V1.2 已交付" → "V1.3 已交付（技术债清偿）"
- Modify: `performance-engine-center/CLAUDE.md`：版本 V1.2 → V1.3、分期策略表 V1.3 本期交付、UOE 清单清零或仅剩 V1.4+、技术债章节大幅删减
- Modify: `docs/modules/performance-engine-center/04-对外API契约.md`：MetricQueryApi 3 个 snapshot 方法契约补全 + MetricApi.getUserMetricCards 契约补全 + PerfCalcApi.triggerKpiCalc 契约补全

- [ ] **Step 1-3：文档修改 + commit**

```bash
git commit -m "docs(perf-v1.3): V1.3 交付后文档全量同步（Task R7.2）"
```

---

### Task R7.3：V1.3 验收清单 + 技术债清算

**Files:**
- Modify: `performance-engine-center/CLAUDE.md` 技术债章节

**V1.3 消化清单**：
- [x] R0.1 V1.0 NULL deleted 历史数据清理（V1_2_5）
- [x] R0.2 perf_run_task UK(task_key)（V1_3_0）
- [x] R0.3 failsafe 分层
- [x] R0.4 模块 CLAUDE.md uk_task_key 勘误
- [x] R1 Target 数据范围注入（TargetValue + TargetPlan + Controller 三级）
- [x] R2.1 PerfCalcApi.triggerKpiCalc 实现
- [x] R2.2 MetricQueryApi.batchQueryEmpSnapshots 实现
- [x] R2.3 / R2.4 MetricQueryApi.batchQueryOrg/CustSnapshots 实现
- [x] R2.5 MetricApi.getUserMetricCards 实现
- [x] R3.1 IDEMPOTENCY_WAIT_TIMEOUT (PERF-50003) 新增 + DataTaskService 替换
- [x] R3.2 MetricTrialRespDTO 字段对齐 03 §A.5（@JsonAlias 兼容）
- [x] R3.3 MetricDefController.execute 返回 RunTaskInfoDTO 回归 03 §A.6
- [x] R4.1 5 Controller 局部 entity 重构 + ArchUnit 加强守护
- [x] R4.2 P7 Controller status 读真实终态
- [x] R4.3 cycleType 写入 params_json
- [x] R5.1 Testcontainers-redis 接入（2 个 Redis IT 取消 @Disabled）
- [x] R5.2 UndoScriptSmokeIT 重写
- [x] R5.3 V1.0 UOE 测试资产改行为断言

**V1.3 延期到 V1.4**（正式登记）：
- `WorkflowQueryApi.queryParticipatedBusinessKeys` 新增（workflow-center 跨模块）
- `PerfScopeHelper.WORKFLOW_PARTICIPANT` 实际实现
- `MetricApi.getUserMetricCards` mom / yoy 计算（若 R2.5 简化方案只做 target+actual）
- V1.3 R3.2 @Deprecated `getSamples` 1 版本后删除（V1.5 回头清理）
- testcontainers-redis 对 CI Docker 环境的条件化依赖（`@EnabledIfDockerAvailable` 细化）

- [ ] **Step 1：模块 CLAUDE.md 技术债章节删除已消化项 + 标记延期项**

- [ ] **Step 2：Commit**

```bash
git commit -m "docs(perf-v1.3): V1.3 技术债清算（Task R7.3）"
```

---

## 验收清单

V1.3 交付后必须全部满足：

- [ ] `mvn clean test`（surefire 单元）在 `performance-engine-center` 全绿
- [ ] `mvn clean verify`（surefire + failsafe 集成）全绿
- [ ] Facade UOE 清零（或仅剩有明确 V1.4+ 归属的方法）
- [ ] 4 个架构守护测试绿
- [ ] 根 CLAUDE.md + 模块 CLAUDE.md + 04 契约文档一致
- [ ] V1.2 → V1.3 Flyway 链路 V1_2_5 / V1_3_0 无缝应用
- [ ] 模块 CLAUDE.md 技术债章节 V1.3 已消化项全部清除
- [ ] PerfErrorCode 30 条（V1.2 29 + R3.1 新增 1）
- [ ] BUILDS 稳定（`mvn clean verify` 可重复执行）

---

## 风险与回滚

| 风险 | 严重度 | 应对 |
|---|---|---|
| R0.2 UK(task_key) 存量重复行导致 ALTER 失败 | 高 | R0.2 脚本注释含 runbook 前置检查；生产运维须先清理重复 task_key |
| R0.2 V1_3_0 迁移失败阻塞 V1_3_x 后续脚本 | 高 | Flyway FAILED → 手工删除 history 行 + 清理重复 + 重跑；**严禁**直接从下个版本跳过 |
| R1.1 / R1.2 Target 表列名与 Kpi/Metric 示范模板不对齐 | 中 | ScopeColumns 映射前先 Read `docs/schema/ddl-performance.sql` 的 `perf_target_plan` / `perf_target_value` 实际列；若字段不匹配，写入 Javadoc 说明降级路径（如 SELF → created_by） |
| R2.5 getUserMetricCards mom/yoy 查询性能 | 中 | V1.3 初版只实现 target+actual，mom/yoy 标注 V1.4 |
| R3.2 MetricTrialRespDTO 字段变更对前端破坏 | 高 | `@JsonAlias({"samples"})` + @Deprecated getter 兼容 1 版本 + 04 契约同步 |
| R3.3 execute 返回类型 Map → DTO 对前端破坏 | 中 | OpenAPI/Knife4j 前端可见变更；R7.2 需广播前端 |
| R5.1 Testcontainers-redis 对 CI Docker 依赖 | 中 | CI 若无 Docker，保留 @Disabled 但加 `@EnabledIfDockerAvailable` JUnit5 注解而非删测试 |
| failsafe 改造后 CI 配置不一致 | 低 | 统一 `mvn verify` 在所有 CI pipeline |
| R6.1 整体延期 V1.4（已非 V1.3 Task） | 中 | 确保 V1.4 计划登记 workflow-center 跨模块新 API |

---

## 与前三份计划的关系

- **V1.0 整改计划**（2026-04-22-performance-v1.0-rectification-plan.md）：修复 V1.0 骨架与文档的偏离，28 commit 已交付
- **V1.1 迭代计划**（2026-04-22-performance-v1.1-iteration-plan.md）：指标计算/KPI/导入/上报/回算主能力，53 commit 已交付
- **V1.2 迭代计划**（2026-04-22-performance-v1.2-iteration-plan.md）：流程/事件/导出/数据范围，58 commit 已交付
- **V1.3 本计划**：技术债清偿 + UOE 实现，24 task / 预计 ~30 commit

V1.3 后 performance-engine-center 应达到"生产长期可维护"状态，无明显遗留项。
