# performance-engine-center V1.4 迭代计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消化 V1.3 承接的 8 项剩余技术债（+V1.3 末 reviewer 新发现的 V1_2_5 文档勘误 1 项 = 共 9 项），重点完成 WORKFLOW_PARTICIPANT 跨模块落地、Target ScopeColumns DDL 细化、getUserMetricCards mom/yoy 精化三大功能扩展，并修补小改进项。

**Architecture:**
V1.4 不引入新业务能力，持续清债路径：
1. **跨模块能力引入**：workflow-center 新增 `WorkflowQueryApi.queryParticipatedBusinessKeys`，performance 消费此 API 让 `PerfScopeHelper.WORKFLOW_PARTICIPANT` 分支从 V1.2 fail-close 转为真实 scope 过滤
2. **DDL 细化**：`perf_target_plan` / `perf_target_value` 加 `owner_emp_id` / `owner_org_code`，让 Target 数据范围注入从 V1.3 R1 "全部降级 created_by" 切到精确的 SELF/ORG 列
3. **getUserMetricCards 精化**：MetricCardDTO 扩 `previousValue` / `mom` / `yoy` 字段，`cycleKey` 从 V1.3 R2.5 "按年近似" 切到按 cycleType 精确匹配，实现环比/同比查询
4. **小改进**：execute fallback 日志（R4.2）+ cycleType=null 语义（R4.3）+ Controller.list 返回类型语义分析
5. **V1.3 勘误**：V1_2_5 脚本实际仅覆盖 perf_metric_def，CLAUDE.md 及 commit message 曾声称覆盖三表，需勘误或补脚本

**Tech Stack:**
- 无新增依赖（Flowable 7 HistoryService / TaskService 已在 workflow-center）
- 继承 V1.0-V1.3 基础设施：PerfScopeHelper、PerfEventPublisher、BizScopeApi、PerfErrorCode 30 条、6 架构守护、failsafe 分层、Testcontainers-redis

**依赖文档（权威需求来源）:**
- `performance-engine-center/CLAUDE.md` §V1.4 遗留项（8 项清单）
- V1.3 复核报告识别的 V1_2_5 文档不一致（R0.1 范围偏差）
- `docs/modules/performance-engine-center/04-对外API契约.md` §5 MetricApi/MetricQueryApi/PerfCalcApi
- workflow-center 现有 `WorkflowApi` + `WorkflowQueryApi`（参考现有签名风格）

**前置条件（必须已满足）:**
- ✅ V1.0-V1.3 全部交付推送（主分支 master）
- ✅ Facade UOE 清零
- ✅ 6 架构守护绿（BizAuth / NoEntityInController / NoEntityInControllerLocals / NoV11UOE / NoUoeInFacadeTests / PerfErrorCodeTest）
- ✅ 888 测试全绿 + 5 @Disabled（Testcontainers-redis 可选容器）
- ✅ V1.2 + V1.3 技术债消化 24/25（仅 WORKFLOW_PARTICIPANT 延期 V1.4）

---

## 阶段概览

| 阶段 | 任务数 | 目标 | 预估 |
|---|---|---|---|
| S0 | 1 | V1.3 V1_2_5 勘误（文档/或补脚本扩展三表） | 0.5 天 |
| S1 | 3 | WORKFLOW_PARTICIPANT scope 跨模块落地（workflow-center + performance） | 3 天 |
| S2 | 4 | Target ScopeColumns DDL 细化（perf_target_plan/value 加 owner 字段） | 2 天 |
| S3 | 4 | getUserMetricCards 精化（DTO 扩字段 + cycleKey 精确 + mom + yoy） | 3 天 |
| S4 | 3 | 小改进（execute fallback 日志 + cycleType null 语义 + Controller.list 分析） | 1 天 |
| S5 | 3 | V1.4 验收 + 文档同步 + 技术债清算 | 0.5 天 |
| 合计 | 18 | | ~10 天 |

---

## 阶段 S0：V1.3 文字勘误（范围极小）

### Task S0.1：CLAUDE.md "三表 NULL deleted" 误述勘误

**背景（DDL 事实核查）**：plan-document-reviewer 第一轮复核确认：
- `V1_0_0__performance_ddl.sql` / `docs/schema/ddl-performance.sql` 中，**只有 `perf_metric_def` 一张表有 `deleted` 字段**（V1_0_3 补齐）
- `perf_target_plan` / `perf_target_value` / `perf_kpi_item` **根本不存在 `deleted` 字段**
- V1.3 R0.1 的 `V1_2_5__perf_cleanup_null_deleted.sql` 只 UPDATE `perf_metric_def` **完全正确**
- V1.3 R7.3 commit message + 模块 CLAUDE.md 中 "三表 NULL 置 0" 的表述是**纯文字谬误**（不对应任何物理列），没有 schema 级技术债

**结论**：S0.1 本质上只是 V1.3 文档措辞勘误，**不需要** V1_4_0 迁移脚本，**不需要**选项 B。

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（删除或改写"三表"表述）

- [ ] **Step 1：Grep 定位文字残留**

```
Grep pattern="三表|perf_target_plan.*NULL|perf_target_value.*NULL|perf_kpi_item.*NULL" path="performance-engine-center/CLAUDE.md" -n
```

- [ ] **Step 2：修正 CLAUDE.md**

将模块 CLAUDE.md 技术债章节/运维 Runbook 中对 V1_2_5 的描述明确为：

```markdown
- **V1_2_5 NULL deleted 清理（V1.3 R0.1 交付）**：
  - 仅处理 `perf_metric_def`（V1.0 MetricDefService.create 漏填 deleted 字段的特定 bug）
  - `perf_target_plan` / `perf_target_value` / `perf_kpi_item` 根本无 deleted 字段，不存在同类问题
  - V1.3 R7.3 commit message 中 "三表 NULL 置 0" 是误述，V1.4 S0.1 勘误
```

- [ ] **Step 3：Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(perf-v1.4): 勘误 V1_2_5 "三表 NULL 置 0" 误述（Task S0.1）

DDL 核查确认：perf_target_plan / perf_target_value / perf_kpi_item
三表无 deleted 字段，V1.3 R7.3 commit + 模块 CLAUDE.md 的"三表 NULL 置 0"
是纯文字谬误，不对应物理列。V1_2_5 脚本只处理 perf_metric_def 是正确的，
S0.1 仅修文字不改代码。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

**无 Red commit**：本 Task 仅文字勘误，无可测试代码行为。

## 阶段 S1：WORKFLOW_PARTICIPANT 跨模块落地

### Task S1.1：workflow-center 新增 WorkflowQueryApi.queryParticipatedBusinessKeys

**背景**：V1.2 Q7 `PerfScopeHelper` 对 `WORKFLOW_PARTICIPANT` 类型返回 `1=0` fail-close，V1.4 首次真实实现。跨模块依赖：workflow-center 先提供查询 API。

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/WorkflowQueryApi.java`（新增方法）
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/WorkflowQueryApiImpl.java`（新增实现）
- Create: `workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/WorkflowQueryApiParticipantTest.java`

**接口设计**：
```java
/**
 * 查询用户参与过的流程 businessKey 集合（去重）。
 *
 * 参与：作为 assignee（被分配任务）或 involvedUser（评论/抄送）。
 * 按 Flowable HistoryService 查询，含运行中与已完成流程。
 *
 * @param empId 员工 ID（candidate user 名）
 * @param processDefinitionKeyPrefix 流程定义 key 前缀，如 "perf_alloc_adjust_" 限定本模块
 * @param timeWindowDays 时间窗口（天）。null 表示无限制；推荐 ≤ 365
 * @param limit 最大返回数量（兜底，防 OOM）。推荐 ≤ 10000
 * @return businessKey 集合
 */
Set<String> queryParticipatedBusinessKeys(
    String empId, String processDefinitionKeyPrefix,
    Integer timeWindowDays, Integer limit);
```

**Step 0 前置：核查 Flowable involvedUser 是否覆盖候选组**

**关键发现**（plan-document-reviewer 第一轮提出）：Flowable 7 `HistoricProcessInstanceQuery.involvedUser(empId)` **仅覆盖 assignee + owner + 显式 `addUserIdentityLink` 的用户**，**不自动覆盖 candidateGroup 内成员**。workflow-center 现有 BPMN 用 `flowable:candidateGroups="${xxxGroup}"`（组级候选），这意味着"仅候选未领取"的任务可能漏数据。

Read workflow-center 现有查询实现（`TodoQueryService` 或 `WorkflowTodoFacade`）看它怎么处理候选组：
```
Glob workflow-center/src/main/java/**/TodoQueryService.java
Glob workflow-center/src/main/java/**/*TodoFacade*.java
Grep pattern="involvedUser\|candidateGroup\|assignee" path="workflow-center/src/main/java" -l
```

**两种可能实现模式**：
- **模式 A**（involvedUser 够用）：若 workflow-center 已经用 `involvedUser` 查 todo 且覆盖候选组（可能通过 Flowable 的"任务被 claim 后自动登记为 involvedUser"机制），则本 Task 直接复用
- **模式 B**（需扩展）：若 workflow-center 用 `TaskService` + `taskCandidateOrAssigned` 分两路查，则 queryParticipatedBusinessKeys 也需分两路查：
  1. `HistoricProcessInstance.involvedUser(empId)` 查 assignee 历史
  2. `TaskService.createTaskQuery().taskCandidateUser(empId)` 查候选中任务 → 其 processInstanceId → 再查 businessKey
  3. 合并去重

Step 0 结果写入 commit message body，成为实现决策依据。

**实现骨架**（模式 A，若 Step 0 确认 involvedUser 够用）：
```java
@Override
public Set<String> queryParticipatedBusinessKeys(String empId, String prefix,
                                                  Integer days, Integer limit) {
    if (StringUtils.isBlank(empId)) {
        return Collections.emptySet();
    }
    int effectiveLimit = (limit == null || limit <= 0) ? 10000 : Math.min(limit, 10000);

    HistoricProcessInstanceQuery q = historyService.createHistoricProcessInstanceQuery()
        .involvedUser(empId);

    if (StringUtils.isNotBlank(prefix)) {
        q = q.processDefinitionKeyLike(prefix + "%");
    }
    if (days != null && days > 0) {
        Date after = Date.from(Instant.now().minus(Duration.ofDays(days)));
        q = q.startedAfter(after);
    }

    return q.listPage(0, effectiveLimit).stream()
        .map(HistoricProcessInstance::getBusinessKey)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new));
}
```

**实现骨架**（模式 B，若 Step 0 发现需扩展）：
```java
@Override
public Set<String> queryParticipatedBusinessKeys(String empId, String prefix,
                                                  Integer days, Integer limit) {
    if (StringUtils.isBlank(empId)) return Collections.emptySet();
    int effectiveLimit = (limit == null || limit <= 0) ? 10000 : Math.min(limit, 10000);
    Date after = days != null && days > 0
        ? Date.from(Instant.now().minus(Duration.ofDays(days))) : null;

    // 路径 1：assignee + owner + involvedUser
    HistoricProcessInstanceQuery q1 = historyService.createHistoricProcessInstanceQuery()
        .involvedUser(empId);
    if (StringUtils.isNotBlank(prefix)) q1 = q1.processDefinitionKeyLike(prefix + "%");
    if (after != null) q1 = q1.startedAfter(after);
    Set<String> keys = q1.listPage(0, effectiveLimit).stream()
        .map(HistoricProcessInstance::getBusinessKey)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    // 路径 2：候选中未领取任务（根据 Step 0 的 TodoQueryService 复用模式补齐）
    List<String> userGroups = bizScopeApi.getUserGroups(empId); // 或 workflow-center 自己的方式
    if (!userGroups.isEmpty()) {
        List<Task> pendingTasks = taskService.createTaskQuery()
            .taskCandidateGroupIn(userGroups)
            .active()
            .list();
        for (Task t : pendingTasks) {
            if (keys.size() >= effectiveLimit) break;
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(t.getProcessInstanceId())
                .singleResult();
            if (pi != null && StringUtils.isNotBlank(pi.getBusinessKey())
                && (StringUtils.isBlank(prefix) || pi.getProcessDefinitionKey().startsWith(prefix))) {
                keys.add(pi.getBusinessKey());
            }
        }
    }
    return keys;
}
```

**选择原则**：优先模式 A（实现最简），Step 0 IT 验证若发现候选组漏数据则切模式 B。

- [ ] **Step 1：Red 测试**

```java
class WorkflowQueryApiParticipantTest {
    @Autowired WorkflowQueryApi workflowQueryApi;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void queryParticipatedBusinessKeys_returnsEmptyForUnknownUser() {
        Set<String> keys = workflowQueryApi.queryParticipatedBusinessKeys(
            "UNKNOWN_EMP", "perf_alloc_adjust_", 30, 100);
        assertThat(keys).isEmpty();
    }

    @Test
    void queryParticipatedBusinessKeys_returnsInvolvedKeys() {
        // 启动 2 个流程实例（perf_alloc_adjust_corp_v1），以 E001 为 assignee
        ProcessInstance p1 = runtimeService.startProcessInstanceByKey(
            "perf_alloc_adjust_corp_v1", "BK_001",
            Map.of("assignee", "E001"));
        // 模拟 E001 claim + complete task
        ...

        Set<String> keys = workflowQueryApi.queryParticipatedBusinessKeys(
            "E001", "perf_alloc_adjust_", 30, 100);
        assertThat(keys).contains("BK_001");
    }

    @Test
    void queryParticipatedBusinessKeys_filteredByPrefix() {
        // 启动 perf_alloc_adjust + perf_target_adjust 两个流程
        // 查询 prefix="perf_alloc_adjust_" 应只返回 alloc 流程
    }

    @Test
    void queryParticipatedBusinessKeys_limitEnforced() {
        // 启动 >10000 流程（mock 或小 limit 参数）
        Set<String> keys = workflowQueryApi.queryParticipatedBusinessKeys(
            "E001", "perf_alloc_adjust_", null, 5);
        assertThat(keys).hasSizeLessThanOrEqualTo(5);
    }
}
```

- [ ] **Step 2：Red 失败（方法未实现）**

```bash
cd workflow-center
mvn -Dtest=WorkflowQueryApiParticipantTest test
```
预期：编译失败或 UOE

- [ ] **Step 3：Green 实现**

修改 `WorkflowQueryApi.java` 加方法签名；修改 `WorkflowQueryApiImpl.java` 加实现（上述代码骨架）。

- [ ] **Step 4：通过**

```bash
mvn -Dtest=WorkflowQueryApiParticipantTest test
```
预期：4 PASS

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(workflow-v1): WorkflowQueryApi.queryParticipatedBusinessKeys 新增跨模块查询 API（Task S1.1）"
```

**注意**：此 commit 属于 workflow-center 模块，前缀为 `feat(workflow-v1):`（若 workflow-center 有版本号用对应）。这是 V1.4 唯一跨模块 commit，消费方为 performance。

---

### Task S1.2：ScopeColumns record 扩 bizKeyCol + PerfScopeHelper WORKFLOW_PARTICIPANT 真实实现

**背景**：V1.2 Q7 的 `ScopeColumns` 是 4 字段 record `(ownerEmpCol, assigneeCol, createdByCol, ownerOrgCol)`。V1.4 WORKFLOW_PARTICIPANT 需要第 5 个 `bizKeyCol`（业务主键列，通常对应表的 `business_key`）。这是破坏性 record 签名变更，所有现有调用点必须显式补 null 或对应列名。

**Files:**
- Modify: `performance-engine-center/pom.xml`（确认 workflow-center 依赖已存在 - V1.2 Q0 已加）
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/scope/PerfScopeHelper.java`（ScopeColumns record 加 bizKeyCol + WORKFLOW_PARTICIPANT 分支改为调 WorkflowQueryApi）
- Modify: 8 处 ScopeColumns 构造器调用点（V1.2 Q7 + V1.3 R1/R2 的示范实现）：
  - `AllocRelationService`
  - `KpiApiImpl`
  - `MetricDefService`
  - `TargetValueService`（V1.3 R1.1）
  - `TargetPlanService`（V1.3 R1.2）
  - 相应 Test 类
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/scope/PerfScopeHelperTest.java`（更新 WORKFLOW_PARTICIPANT 测试）

**ScopeColumns record 新签名**：
```java
public record ScopeColumns(
    String ownerEmpCol,     // SELF
    String assigneeCol,     // SELF_ASSIGNED
    String createdByCol,    // SELF_CREATED
    String ownerOrgCol,     // ORG / ORG_SUBTREE
    String bizKeyCol        // V1.4 新增：WORKFLOW_PARTICIPANT（通常 "business_key"）
) {}
```

**8 处调用点补 bizKeyCol（大多 null）**：
- AllocRelationService / TargetValueService / TargetPlanService / MetricDefService / KpiApiImpl：bizKeyCol = null（这些业务表无 business_key 列）
- Q2/Q3 的 AllocAdjustService.pageDto / TargetAdjustService.pageDto（Task S1.3 处理）：bizKeyCol = "business_key"

**PerfScopeHelper 实现**：
```java
case WORKFLOW_PARTICIPANT -> {
    // V1.4 S1.2：从 fail-close 切到真实查询
    if (columns.bizKeyCol() == null) {
        // 防御：本表不具备 business_key 列，无法参与 workflow scope 过滤
        yield Fragment.failClose();
    }
    String empId = ctx.empId();
    String prefix = resolvePrefixByBizType(ctx.bizType());  // 如 "perf_alloc_adjust_"
    Set<String> businessKeys = workflowQueryApi.queryParticipatedBusinessKeys(
        empId, prefix, 180, 5000);  // 180 天窗口 + 5000 上限

    if (businessKeys.isEmpty()) {
        yield Fragment.failClose();  // 无参与记录 → 退化安全
    }

    Map<String, Object> params = new LinkedHashMap<>();
    StringBuilder sql = new StringBuilder();
    int i = 0;
    for (String key : businessKeys) {
        params.put("bizKey" + i, key);
        if (i > 0) sql.append(", ");
        sql.append("#{scopeParams.bizKey").append(i).append("}");
        i++;
    }
    yield Fragment.of(columns.bizKeyCol() + " IN (" + sql + ")", params);
}
```

**resolvePrefixByBizType(ctx.bizType())**（新增私有方法）：
```java
private static String resolvePrefixByBizType(String bizType) {
    if (bizType == null) return "";
    return switch (bizType) {
        case "BIZ_PERF_ALLOC_ADJUST" -> "perf_alloc_adjust_";
        case "BIZ_PERF_TARGET_ADJUST" -> "perf_target_adjust_";
        default -> "";  // 查全部 performance 流程
    };
}
```

- [ ] **Step 1：Red 测试**

```java
@Test
void workflowParticipant_queriesWorkflowApi_andReturnsInClauseFragment() {
    DataScopeContext ctx = workflowParticipantCtx("E001", "BIZ_PERF_ALLOC_ADJUST");
    when(workflowQueryApi.queryParticipatedBusinessKeys(
        eq("E001"), eq("perf_alloc_adjust_"), eq(180), eq(5000)))
        .thenReturn(Set.of("BK_001", "BK_002"));

    Fragment f = helper.getFragment("E001", "P_PERF_ALLOC_ADJ_QUERY", ctx,
        new ScopeColumns(null, null, null, null, "business_key"));

    assertThat(f.getSql()).contains("business_key IN");
    assertThat(f.getSql()).contains("#{scopeParams.bizKey0}")
        .contains("#{scopeParams.bizKey1}");
    assertThat(f.getParams()).containsEntry("bizKey0", "BK_001");
    assertThat(f.getParams()).containsEntry("bizKey1", "BK_002");
}

@Test
void workflowParticipant_emptyBusinessKeys_failClose() {
    when(workflowQueryApi.queryParticipatedBusinessKeys(any(), any(), any(), any()))
        .thenReturn(Set.of());
    Fragment f = helper.getFragment("E001", "resource", workflowParticipantCtx("E001", "BIZ_PERF_ALLOC_ADJUST"),
        new ScopeColumns(null, null, null, null, "business_key"));
    assertThat(f.getSql()).isEqualTo("1=0");
}

@Test
void workflowParticipant_bizKeyColNull_failCloseDefensive() {
    Fragment f = helper.getFragment("E001", "resource", workflowParticipantCtx("E001", "BIZ_PERF_ALLOC_ADJUST"),
        new ScopeColumns(null, null, null, null, null));  // bizKeyCol=null
    assertThat(f.getSql()).isEqualTo("1=0");
    verify(workflowQueryApi, never()).queryParticipatedBusinessKeys(any(), any(), any(), any());
}
```

- [ ] **Step 2：Red 失败**（WorkflowQueryApi 未注入，ScopeColumns 仍 4 字段）

- [ ] **Step 3：Green 实现**

1. 修改 `ScopeColumns` record 签名（4 → 5 字段）
2. 修改 8 处既有调用：
   - `AllocRelationService`：`new ScopeColumns("emp_id", "emp_id", "emp_id", "emp_id", null)` 加第 5 个 null
   - 其他 4 处同样加 null
3. 注入 `WorkflowQueryApi` 到 `PerfScopeHelper`
4. 新增 `resolvePrefixByBizType` 静态方法
5. 改写 `WORKFLOW_PARTICIPANT` 分支（上述代码）

- [ ] **Step 4：验证通过**

```bash
mvn -pl performance-engine-center clean verify
```
预期：所有原有测试 + 新增 3 个测试全绿

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): WORKFLOW_PARTICIPANT scope 真实实现 + ScopeColumns 第 5 字段失败测试（Red，Task S1.2）"
git commit -m "feat(perf-v1.4): PerfScopeHelper WORKFLOW_PARTICIPANT 消费 WorkflowQueryApi + ScopeColumns 扩 bizKeyCol（Green，Task S1.2）"
```

---

### Task S1.3：AllocAdjust / TargetAdjust Service 消费 WORKFLOW_PARTICIPANT scope

**背景**：V1.2 Q2/Q3 的 AllocAdjustService / TargetAdjustService 列表查询已集成 PerfScopeHelper，但 WORKFLOW_PARTICIPANT 分支以前是 fail-close。V1.3 WORKFLOW_PARTICIPANT 实现后，这两个 Service 的 pageDto 若传 `bizKeyCol = "business_key"`，就能让 WORKFLOW_PARTICIPANT 角色用户看到自己参与过的调整申请。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java`（pageDto 方法内构造 ScopeColumns 时 bizKeyCol = "business_key"）
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/TargetAdjustService.java`（同上）
- Modify: `AllocAdjustServiceTest.java` / `TargetAdjustServiceTest.java`（加 WORKFLOW_PARTICIPANT 路径测试 2 case）
- Modify: `PerfAllocAdjustApplyMapper.xml` / `PerfTargetAdjustApplyMapper.xml`（selectByConditionWithScope 已支持 ${scopeFragment}，无需改）

- [ ] **Step 1：Red 测试**

```java
// AllocAdjustServiceTest 新增 2 case
@Test
void pageDto_withWorkflowParticipantCtx_appliesBusinessKeyInClause() {
    when(currentUserApi.currentUserId()).thenReturn("USER_A");
    Fragment f = Fragment.of("business_key IN (#{scopeParams.bizKey0})",
        Map.of("bizKey0", "BK_001"));
    when(perfScopeHelper.getFragment(eq("USER_A"), eq("P_PERF_ALLOC_ADJUST_QUERY"),
        any(DataScopeContext.class),
        argThat(cols -> "business_key".equals(cols.bizKeyCol()))))
        .thenReturn(f);

    service.pageDto(new AllocAdjustQueryReq(), 1, 20);

    verify(mapper).selectByConditionWithScope(argThat(cond ->
        cond.getScopeFragment().contains("business_key IN")));
}

@Test
void pageDto_workflowParticipantWithNoParticipation_returnsEmpty() {
    // 模拟 PerfScopeHelper 返回 fail-close
    when(perfScopeHelper.getFragment(any(), any(), any(), any()))
        .thenReturn(Fragment.failClose());

    PageResult<AllocAdjustRespDTO> result = service.pageDto(new AllocAdjustQueryReq(), 1, 20);
    assertThat(result.getList()).isEmpty();
}
```

（TargetAdjustServiceTest 同构，2 case）

- [ ] **Step 2：Red 失败**（当前 pageDto 的 ScopeColumns 只有 4 字段）

- [ ] **Step 3：Green**

```java
// AllocAdjustService.pageDto 修改 ScopeColumns 构造
ScopeColumns cols = new ScopeColumns(
    "created_by",        // SELF（Alloc 无明确 owner 列，暂用 created_by）
    "created_by",        // SELF_ASSIGNED
    "created_by",        // SELF_CREATED
    "owner_org_id",      // ORG
    "business_key"       // WORKFLOW_PARTICIPANT（V1.4 S1.3 新增）
);

// TargetAdjustService.pageDto 同构
```

- [ ] **Step 4：验证通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): AllocAdjust/TargetAdjust pageDto WORKFLOW_PARTICIPANT 路径失败测试（Red，Task S1.3）"
git commit -m "feat(perf-v1.4): AllocAdjust/TargetAdjust pageDto 集成 WORKFLOW_PARTICIPANT scope（Green，Task S1.3）"
```

---

## 阶段 S2：Target ScopeColumns DDL 细化

### Task S2.1：V1_4_0 DDL 加 owner 字段

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_4_0__perf_target_owner_cols.sql`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_4_0FlywayIT.java`

**DDL 内容**：
```sql
-- V1_4_0__perf_target_owner_cols.sql
-- V1.4 S2.1：为 perf_target_plan / perf_target_value 增加 owner_emp_id / owner_org_code 字段
-- 精化 Target 数据范围过滤（V1.3 R1 降级到 created_by 的根治）

ALTER TABLE perf_target_plan
  ADD COLUMN owner_emp_id VARCHAR(32) NULL COMMENT '归属员工（SELF scope 列）' AFTER remark,
  ADD COLUMN owner_org_code VARCHAR(50) NULL COMMENT '归属机构（ORG scope 列）' AFTER owner_emp_id,
  ADD INDEX idx_owner_emp(owner_emp_id),
  ADD INDEX idx_owner_org(owner_org_code);

ALTER TABLE perf_target_value
  ADD COLUMN owner_emp_id VARCHAR(32) NULL COMMENT '归属员工（SELF scope 列）' AFTER remark,
  ADD COLUMN owner_org_code VARCHAR(50) NULL COMMENT '归属机构（ORG scope 列）' AFTER owner_emp_id,
  ADD INDEX idx_owner_emp(owner_emp_id),
  ADD INDEX idx_owner_org(owner_org_code);

-- 历史数据回填（owner_emp_id = created_by 作为兜底，运维后续按业务纠正）
-- 【小数据量兼容版本】：单次 UPDATE，仅适用于 < 10 万行
UPDATE perf_target_plan SET owner_emp_id = created_by WHERE owner_emp_id IS NULL;
UPDATE perf_target_value SET owner_emp_id = created_by WHERE owner_emp_id IS NULL;
```

**【生产分批 runbook（行数 > 100 万时必须）】**：

Flyway 本脚本适用于小中数据量（< 10 万）。若生产库 perf_target_value 超过 100 万行，单次 UPDATE 会产生长事务 + 行锁竞争，应改走"分批存储过程"：

```sql
-- 分批回填脚本（runbook/perf_target_value_backfill_batch.sql，手动执行）
DELIMITER //
CREATE PROCEDURE batch_backfill_target_value_owner()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE batch_size INT DEFAULT 10000;
  WHILE done = 0 DO
    UPDATE perf_target_value
    SET owner_emp_id = created_by
    WHERE owner_emp_id IS NULL
    LIMIT 10000;
    IF ROW_COUNT() = 0 THEN SET done = 1; END IF;
    -- 可选：每批间隔 100ms 降压
    SELECT SLEEP(0.1);
  END WHILE;
END//
DELIMITER ;

-- 执行
CALL batch_backfill_target_value_owner();

-- 清理
DROP PROCEDURE batch_backfill_target_value_owner;
```

**【备选方案：pt-online-schema-change】**：若 ADD COLUMN + ADD INDEX 在生产触发长 metadata lock，用 Percona Toolkit：
```bash
pt-online-schema-change --alter "ADD COLUMN owner_emp_id VARCHAR(32) NULL, ..." \
  D=onepl,t=perf_target_value --execute
```

**Flyway 脚本职责仅小数据量场景**，生产大表迁移由运维按 runbook 手动执行，Flyway 历史版本号 1.4.0 不变（仅标记 DDL 已应用）。

- [ ] **Step 1：Red 测试**

```java
class V1_4_0FlywayIT extends PerformanceFlywayTestBase {
    @Test
    void v1_4_0_addsOwnerColumns_toTargetPlanAndValue() {
        // 查 information_schema.COLUMNS 断言 4 列存在
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() " +
            "AND ((TABLE_NAME='perf_target_plan' AND COLUMN_NAME IN ('owner_emp_id','owner_org_code')) " +
            "  OR (TABLE_NAME='perf_target_value' AND COLUMN_NAME IN ('owner_emp_id','owner_org_code')))",
            Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void v1_4_0_ownerEmpId_backfilled_fromCreatedBy() {
        // 前置：插 2 条 created_by='USER_A' 记录
        ...
        // 验证：owner_emp_id = 'USER_A'
        ...
    }
}
```

- [ ] **Step 2：Red 失败**（V1_4_0 脚本尚未创建）

```bash
cd performance-engine-center
mvn -Dtest=V1_4_0FlywayIT test
```
预期：FAIL（4 字段不存在 / Flyway history 无 1.4.0）

- [ ] **Step 3：Green 创建 V1_4_0 脚本**（使用上方 DDL + runbook）

- [ ] **Step 4：运行测试通过**

```bash
mvn -Dtest=V1_4_0FlywayIT test
```
预期：PASS

- [ ] **Step 5：Commit（Red + Green 双 commit）**

```bash
git commit -m "test(perf-v1.4): V1_4_0 Target owner 字段 Flyway 失败测试（Red，Task S2.1）"
git commit -m "fix(perf-v1.4): V1_4_0 perf_target_plan/value 加 owner_emp_id/owner_org_code + 分批回填 runbook（Green，Task S2.1）"
```

**【Rollback 应急 SQL】**（若 Flyway ALTER 失败需手动回退）：

```sql
-- rollback_V1_4_0.sql（放 performance-engine-center/src/main/resources/undo-scripts/）
-- 应急回滚：卸载 V1_4_0 DDL 变更，flyway_schema_history 同步清理
ALTER TABLE perf_target_plan DROP INDEX idx_owner_emp;
ALTER TABLE perf_target_plan DROP INDEX idx_owner_org;
ALTER TABLE perf_target_plan DROP COLUMN owner_org_code;
ALTER TABLE perf_target_plan DROP COLUMN owner_emp_id;

ALTER TABLE perf_target_value DROP INDEX idx_owner_emp;
ALTER TABLE perf_target_value DROP INDEX idx_owner_org;
ALTER TABLE perf_target_value DROP COLUMN owner_org_code;
ALTER TABLE perf_target_value DROP COLUMN owner_emp_id;

-- 清理 Flyway 历史（让后续 migrate 重新尝试应用）
DELETE FROM flyway_schema_history WHERE version = '1.4.0';
```

---

### Task S2.2：PerfTargetPlan / PerfTargetValue Entity + Mapper 字段补齐

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfTargetPlan.java`（加 ownerEmpId / ownerOrgCode）
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfTargetValue.java`（同上）
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfTargetPlanMapper.xml`（BASE_COLUMNS + insert / selectByCondition / selectByConditionWithScope 4 处 SQL）
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfTargetValueMapper.xml`（同上）
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/cmd/UpsertTargetValueCmd.java`（加 ownerEmpId / ownerOrgCode Cmd 字段）
- Test: `PerfTargetPlanMapperIT.java` / `PerfTargetValueMapperIT.java`（新增 2 case / 2 case = 4 IT）

- [ ] **Step 1：Red 测试**

```java
// PerfTargetPlanMapperIT 新增
@Test
void insert_andSelectById_roundtrips_ownerFields() {
    PerfTargetPlan plan = new PerfTargetPlan();
    plan.setId("TEST_TP_S22");
    plan.setPlanCode("TEST_S22");
    plan.setKpiSchemeId("SCHEME_X");
    plan.setOwnerEmpId("USER_A");             // V1.4 新字段
    plan.setOwnerOrgCode("BRANCH_01");        // V1.4 新字段
    // ... 其他必填字段
    mapper.insert(plan);

    PerfTargetPlan loaded = mapper.selectById("TEST_TP_S22");
    assertThat(loaded.getOwnerEmpId()).isEqualTo("USER_A");
    assertThat(loaded.getOwnerOrgCode()).isEqualTo("BRANCH_01");
}

@Test
void selectByConditionWithScope_filtersByOwnerEmpId() {
    // 预置：2 行不同 owner_emp_id
    // 调 selectByConditionWithScope 传 scopeFragment "owner_emp_id = #{scopeParams.ownerEmpId}"
    // 断言只返回匹配行
}
```

- [ ] **Step 2：Red 失败**（字段不存在）

- [ ] **Step 3：Green**

- Entity 加 `private String ownerEmpId; private String ownerOrgCode;` + Lombok @Data
- XML BASE_COLUMNS 追加 `owner_emp_id, owner_org_code`
- XML insert 语句追加对应字段 + `#{ownerEmpId}, #{ownerOrgCode}`
- XML select 自动 include BASE_COLUMNS 无需手工改
- UpsertTargetValueCmd 同样加 2 字段 + assembler 映射

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): TargetPlan/TargetValue owner 字段 Entity+Mapper IT 失败测试（Red，Task S2.2）"
git commit -m "feat(perf-v1.4): PerfTargetPlan/PerfTargetValue Entity + Mapper 补齐 owner 字段（Green，Task S2.2）"
```

---

### Task S2.3：TargetPlanService / TargetValueService ScopeColumns 升级

**Files:**
- Modify: `TargetValueService.pageWithScope`（ScopeColumns 映射从全部 created_by 改为精确 owner_emp_id / owner_org_code）
- Modify: `TargetPlanService.pageWithScope`（同上）
- Modify: `TargetValueServiceScopeTest.java` / `TargetPlanServiceScopeTest.java`（SELF/ORG 场景断言新列）

**ScopeColumns 新映射**：
```java
new ScopeColumns(
    "owner_emp_id",      // ownerEmpCol (SELF)
    "owner_emp_id",      // assigneeCol（复用）
    "created_by",        // createdByCol (SELF_CREATED 保持不变)
    "owner_org_code",    // ownerOrgCol (ORG / ORG_SUBTREE)
    null                 // bizKeyCol (TargetValue/TargetPlan 无 business_key)
)
```

- [ ] **Step 1：Red 测试（更新既有 V1.3 R1 断言）**

```java
// TargetValueServiceScopeTest 更新
@Test
void pageWithScope_SELF_appliesOwnerEmpIdFilter() {  // V1.4 改：原来测 created_by
    when(currentUserApi.currentUserId()).thenReturn("USER_A");
    Fragment f = Fragment.of("owner_emp_id = #{scopeParams.ownerEmpId}",
        Map.of("ownerEmpId", "USER_A"));
    when(perfScopeHelper.getFragment(eq("USER_A"), eq("P_PERF_TARGET_VALUE_QUERY"),
        any(DataScopeContext.class),
        argThat(cols -> "owner_emp_id".equals(cols.ownerEmpCol())
            && "owner_org_code".equals(cols.ownerOrgCol()))))
        .thenReturn(f);

    service.pageWithScope(new TargetValueQueryReq());

    verify(mapper).selectByConditionWithScope(argThat(cond ->
        "owner_emp_id = #{scopeParams.ownerEmpId}".equals(cond.getScopeFragment())));
}

@Test
void pageWithScope_ORG_appliesOwnerOrgCodeFilter() {
    // 类似，断言 ScopeColumns.ownerOrgCol() == "owner_org_code"
}
```

- [ ] **Step 2：Red 失败**（V1.3 R1 传的 ScopeColumns 全是 created_by）

- [ ] **Step 3：Green**

```java
// TargetValueService.pageWithScope 修改
ScopeColumns cols = new ScopeColumns(
    "owner_emp_id",   // V1.4 S2.3 从 created_by 切
    "owner_emp_id",
    "created_by",     // SELF_CREATED 保持
    "owner_org_code", // V1.4 S2.3 从 created_by 切
    null              // bizKeyCol: TargetValue 无
);
```

TargetPlanService 同构。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): TargetValue/TargetPlan ScopeColumns 精化失败测试（Red，Task S2.3）"
git commit -m "feat(perf-v1.4): TargetValue/TargetPlan ScopeColumns 切到精确 owner 列（Green，Task S2.3）"
```

---

### Task S2.4：V1.3 R1 降级策略清单在 CLAUDE.md 清除

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（V1.4 遗留项 #5 "Target ScopeColumns 降级到 created_by" 标为已消化）

- [ ] **Step 1-2：纯文档更新 + commit**

```bash
git commit -m "docs(perf-v1.4): 标记 ScopeColumns Target 降级项为 V1.4 S2 已消化（Task S2.4）"
```

---

## 阶段 S3：getUserMetricCards 精化

### Task S3.1：MetricCardDTO 扩字段 previousValue / mom / yoy

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/dto/MetricCardDTO.java`（+ 3 字段）
- Modify: `MetricApiImplCardsTest.java`（新增 2 case 字段存在断言）

**字段**：
```java
private BigDecimal previousValue;   // 上期 actual（按 cycleType 对齐）
private BigDecimal mom;             // 环比变化率（百分比，保留 2 位小数）
private BigDecimal yoy;             // 同比变化率（百分比，保留 2 位小数）
```

- [ ] **Step 1：Red 测试**

```java
// MetricApiImplCardsTest 新增
@Test
void metricCardDTO_hasExtendedFields() throws Exception {
    MetricCardDTO dto = MetricCardDTO.builder()
        .metricCode("M_X")
        .currentValue(new BigDecimal("120"))
        .targetValue(new BigDecimal("100"))
        .previousValue(new BigDecimal("100"))    // V1.4 S3.1 新字段
        .mom(new BigDecimal("20.00"))             // V1.4 S3.1 新字段
        .yoy(new BigDecimal("50.00"))             // V1.4 S3.1 新字段
        .build();
    String json = objectMapper.writeValueAsString(dto);
    assertThat(json).contains("\"previousValue\"").contains("\"mom\"").contains("\"yoy\"");
}

@Test
void metricCardDTO_extendedFields_allowsNull() {
    // V1.3 R2.5 简化只填 target/actual/achievementRate，V1.4 S3.1 新字段必须允许 null
    MetricCardDTO dto = MetricCardDTO.builder()
        .metricCode("M_X")
        .currentValue(new BigDecimal("100"))
        .targetValue(new BigDecimal("100"))
        .build();
    assertThat(dto.getPreviousValue()).isNull();
    assertThat(dto.getMom()).isNull();
    assertThat(dto.getYoy()).isNull();
}
```

- [ ] **Step 2：Red 失败**（字段不存在）

- [ ] **Step 3：Green**

```java
// MetricCardDTO.java 追加 3 字段 + @JsonFormat(pattern = "#,###.##") 可选
@Data
@Builder
public class MetricCardDTO {
    private String metricCode;
    private String metricName;
    private BigDecimal currentValue;
    private BigDecimal targetValue;
    private BigDecimal achievementRate;
    // V1.4 S3.1 新增
    private BigDecimal previousValue;
    private BigDecimal mom;
    private BigDecimal yoy;
    // 其他既有字段保留
}
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): MetricCardDTO previousValue/mom/yoy 字段存在断言（Red，Task S3.1）"
git commit -m "feat(perf-v1.4): MetricCardDTO 扩 3 字段预留 mom/yoy 查询（Green，Task S3.1）"
```

---

### Task S3.2：cycleKey 按 cycleType 精确匹配

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java`（cycleKey 从 `latestDate.getYear()` 改为按 cycleType 组装）
- Modify: `MetricApiImplCardsTest.java`（4 种 cycleType 各 1 case）

**Step 0：验证现有 cycleKey 存储格式（避免计划猜测）**

```
Grep pattern="setCycleKey\|cycle_key\|cycleKey" path="performance-engine-center/src/test" -A 1
Grep pattern="cycleKey" path="performance-engine-center/src/main/resources/sql" -A 1
Grep pattern="SEED_" path="performance-engine-center/src/main/resources/sql/V1_2_3__perf_seed_data.sql"
```

按实际发现的格式作为权威（DDL 注释仅给了 "2026 或 2026Q1" 示例，MONTHLY/WEEKLY 具体格式以 Grep 到的测试 insert / 种子 SQL 为准）。

**cycleKey 组装规则**（以 Step 0 为准，若 Grep 结果一致则采用下列默认）：
- cycleType=YEARLY → `"2026"`
- cycleType=QUARTERLY → `"2026Q1"`（起始日期所在季度）
- cycleType=MONTHLY → `"202601"`（YYYYMM，以 Grep 结果为准）
- cycleType=WEEKLY → `"2026W01"`（年周，以 Grep 结果为准）

若 Grep 发现实际格式不同（如 `2026-01` 或 `2026M01`），以 Grep 结果为准并在 Green commit message 注明。

- [ ] **Step 1：Red 测试**

```java
@Test
void getUserMetricCards_yearlyCycleType_buildsYearKey() {
    LocalDate date = LocalDate.of(2026, 7, 15);
    api.getUserMetricCards("E001", "YEARLY", date);
    verify(perfTargetValueMapper).selectByKey(eq("E001"), eq("M_X"), eq("2026"));  // 按 Grep 发现格式
}

@Test
void getUserMetricCards_quarterlyCycleType_buildsQuarterKey() {
    LocalDate q2 = LocalDate.of(2026, 5, 15);  // 2026 Q2
    api.getUserMetricCards("E001", "QUARTERLY", q2);
    verify(perfTargetValueMapper).selectByKey(eq("E001"), eq("M_X"), eq("2026Q2"));
}

@Test
void getUserMetricCards_monthlyCycleType_buildsMonthKey() {
    LocalDate jul = LocalDate.of(2026, 7, 15);
    api.getUserMetricCards("E001", "MONTHLY", jul);
    verify(perfTargetValueMapper).selectByKey(eq("E001"), eq("M_X"), eq("202607"));  // 按 Grep 格式
}

@Test
void getUserMetricCards_weeklyCycleType_buildsWeekKey() {
    LocalDate week = LocalDate.of(2026, 1, 15);  // 2026 W03
    api.getUserMetricCards("E001", "WEEKLY", week);
    verify(perfTargetValueMapper).selectByKey(eq("E001"), eq("M_X"), startsWith("2026W"));
}
```

- [ ] **Step 2：Red 失败**（当前按 year 的简单逻辑无法通过上述 4 case）

- [ ] **Step 3：Green**

```java
private String buildCycleKey(String cycleType, LocalDate date) {
    if (cycleType == null) return String.valueOf(date.getYear());
    return switch (cycleType.toUpperCase()) {
        case "YEARLY" -> String.valueOf(date.getYear());
        case "QUARTERLY" -> date.getYear() + "Q" + ((date.getMonthValue() - 1) / 3 + 1);
        case "MONTHLY" -> String.format("%04d%02d", date.getYear(), date.getMonthValue());
        case "WEEKLY" -> String.format("%04dW%02d",
            date.get(WeekFields.ISO.weekBasedYear()),
            date.get(WeekFields.ISO.weekOfWeekBasedYear()));
        default -> String.valueOf(date.getYear());
    };
}
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): cycleKey 按 cycleType 精确匹配失败测试（Red，Task S3.2）"
git commit -m "feat(perf-v1.4): MetricApi.getUserMetricCards cycleKey 精确匹配 YEARLY/QUARTERLY/MONTHLY/WEEKLY（Green，Task S3.2）"
```

---

### Task S3.3：mom（环比）查询实现

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java`（计算 mom = (current - previous) / previous * 100）
- **复用** `EmpIndexResultMapper.selectSlotValue`（V1.1 P1 已有），传入上期 cycleDate 即可，**无需新增 Mapper 方法**
- Modify: `MetricApiImplCardsTest.java`（新增 3 case）

**上期计算（根据 cycleType）**：
- YEARLY → `previousDate = cycleDate.minusYears(1)`
- QUARTERLY → `previousDate = cycleDate.minusMonths(3)`
- MONTHLY → `previousDate = cycleDate.minusMonths(1)`
- WEEKLY → `previousDate = cycleDate.minusWeeks(1)`

**mom 计算规则**（BigDecimal 精度 + 除零保护）：
```java
private BigDecimal calculateMom(BigDecimal current, BigDecimal previous) {
    if (current == null || previous == null) return null;
    if (previous.compareTo(BigDecimal.ZERO) == 0) return null;  // 除零保护：上期 0 则 mom 无意义
    // mom = (current - previous) / |previous| * 100
    return current.subtract(previous)
        .divide(previous.abs(), 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP);
}
```

- [ ] **Step 1：Red 测试**

```java
@Test
void getUserMetricCards_calculatesMom_whenPreviousValueAvailable() {
    // 预置：M_X 当期 actual=120, 上期 actual=100
    LocalDate current = LocalDate.of(2026, 4, 1);
    LocalDate previous = LocalDate.of(2026, 1, 1);  // QUARTERLY 上一季

    when(sysControlService.getCurrentVersion("EMP")).thenReturn("v20260401");
    when(empIndexResultMapper.selectSlotValue("E001", current, "v20260401", 5))
        .thenReturn(new BigDecimal("120"));
    when(empIndexResultMapper.selectSlotValue("E001", previous, "v20260401", 5))
        .thenReturn(new BigDecimal("100"));

    List<MetricCardDTO> cards = api.getUserMetricCards("E001", "QUARTERLY", current);

    assertThat(cards).hasSize(1);
    assertThat(cards.get(0).getPreviousValue()).isEqualByComparingTo("100");
    assertThat(cards.get(0).getMom()).isEqualByComparingTo("20.00");  // (120-100)/100*100
}

@Test
void getUserMetricCards_momZeroWhenPreviousIsZero_returnsNullSafely() {
    when(empIndexResultMapper.selectSlotValue(eq("E001"), any(), any(), eq(5)))
        .thenReturn(new BigDecimal("120")).thenReturn(BigDecimal.ZERO);
    List<MetricCardDTO> cards = api.getUserMetricCards("E001", "QUARTERLY", LocalDate.of(2026, 4, 1));
    assertThat(cards.get(0).getMom()).isNull();
}

@Test
void getUserMetricCards_momNullWhenPreviousNotFound() {
    // 上期宽表查询返回 null
    when(empIndexResultMapper.selectSlotValue(eq("E001"), any(), any(), eq(5)))
        .thenReturn(new BigDecimal("120")).thenReturn(null);
    List<MetricCardDTO> cards = api.getUserMetricCards("E001", "QUARTERLY", LocalDate.of(2026, 4, 1));
    assertThat(cards.get(0).getPreviousValue()).isNull();
    assertThat(cards.get(0).getMom()).isNull();
}
```

- [ ] **Step 2：Red 失败**

- [ ] **Step 3：Green**

```java
// MetricApiImpl.getUserMetricCards 内部
LocalDate previousDate = calculatePreviousDate(cycleDate, cycleType);  // S3.2 补完整
BigDecimal previousValue = empIndexResultMapper.selectSlotValue(
    empId, previousDate, version, metric.getValSlot());
BigDecimal mom = calculateMom(currentValue, previousValue);
return MetricCardDTO.builder()
    ...
    .previousValue(previousValue)
    .mom(mom)
    .build();
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): getUserMetricCards mom 环比查询失败测试（Red，Task S3.3）"
git commit -m "feat(perf-v1.4): getUserMetricCards 实现 mom 环比计算（Green，Task S3.3）"
```

---

### Task S3.4：yoy（同比）查询实现

**Files:**
- Modify: `MetricApiImpl.getUserMetricCards`（计算 yoy = (current - yearAgo) / yearAgo * 100）
- Modify: `MetricApiImplCardsTest.java`（新增 2 case）

**同期计算**：`yearAgoDate = cycleDate.minusYears(1)`（所有 cycleType 统一 -1 年）。

**yoy 计算规则**（同 mom，除零保护 + 2 位小数）：
```java
private BigDecimal calculateYoy(BigDecimal current, BigDecimal yearAgo) {
    if (current == null || yearAgo == null) return null;
    if (yearAgo.compareTo(BigDecimal.ZERO) == 0) return null;
    return current.subtract(yearAgo)
        .divide(yearAgo.abs(), 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP);
}
```

- [ ] **Step 1：Red 测试**

```java
@Test
void getUserMetricCards_calculatesYoy_whenYearAgoAvailable() {
    LocalDate current = LocalDate.of(2026, 4, 1);
    LocalDate yearAgo = LocalDate.of(2025, 4, 1);

    when(empIndexResultMapper.selectSlotValue("E001", current, "v", 5))
        .thenReturn(new BigDecimal("150"));
    when(empIndexResultMapper.selectSlotValue("E001", yearAgo, "v", 5))
        .thenReturn(new BigDecimal("100"));

    List<MetricCardDTO> cards = api.getUserMetricCards("E001", "QUARTERLY", current);

    assertThat(cards.get(0).getYoy()).isEqualByComparingTo("50.00");  // (150-100)/100*100
}

@Test
void getUserMetricCards_yoyNullWhenYearAgoNotFound() {
    // 上一年查询返回 null
    when(empIndexResultMapper.selectSlotValue(eq("E001"), any(), any(), eq(5)))
        .thenReturn(new BigDecimal("150")).thenReturn(null);  // 当期有，去年无
    List<MetricCardDTO> cards = api.getUserMetricCards("E001", "QUARTERLY", LocalDate.of(2026, 4, 1));
    assertThat(cards.get(0).getYoy()).isNull();
}
```

- [ ] **Step 2：Red 失败**

- [ ] **Step 3：Green**

```java
// MetricApiImpl.getUserMetricCards 扩 yoy 计算
LocalDate yearAgoDate = cycleDate.minusYears(1);
BigDecimal yearAgoValue = empIndexResultMapper.selectSlotValue(
    empId, yearAgoDate, version, metric.getValSlot());
BigDecimal yoy = calculateYoy(currentValue, yearAgoValue);

return MetricCardDTO.builder()
    ...
    .yoy(yoy)
    .build();
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): getUserMetricCards yoy 同比查询失败测试（Red，Task S3.4）"
git commit -m "feat(perf-v1.4): getUserMetricCards 实现 yoy 同比计算（Green，Task S3.4）"
```

---

## 阶段 S4：小改进项

### Task S4.1：execute fallback 日志（R4.2 reviewer 建议）

**背景**：V1.3 R4.2 Reviewer 建议 `PerfCalcController.recalc` 的 Optional.empty fallback 到 "RUNNING" 时加 log.warn，便于排查极端竞态（Service 未 commit 时的可观测性）。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/PerfCalcController.java`（fallback 分支加 log.warn）
- Modify: `PerfCalcControllerRecalcIT.java`（新增 1 case 捕获日志输出）

- [ ] **Step 1：Red 测试**

```java
// PerfCalcControllerRecalcIT 新增
@Test
void recalc_whenTaskMissing_logsWarning(CapturedOutput output) {  // @ExtendWith(OutputCaptureExtension.class)
    when(perfCalcApi.triggerRecalc(anyString(), any(), any(), eq(null),
        anyString(), anyString(), anyString())).thenReturn("PARENT_TASK_ID");
    when(perfCalcApi.getRunTask("PARENT_TASK_ID")).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/perf/recalc")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{...}"))
        .andExpect(status().isOk());

    assertThat(output.getAll())
        .contains("[recalc]")
        .contains("PARENT_TASK_ID")
        .contains("查不到");
}
```

- [ ] **Step 2：Red 失败**（日志没输出）

- [ ] **Step 3：Green**

```java
// PerfCalcController.recalc 内
Optional<PerfRunTaskDTO> taskOpt = perfCalcApi.getRunTask(parentTaskId);
if (taskOpt.isEmpty()) {
    log.warn("[recalc] 父 task {} 查不到，退化 RUNNING 状态（可能 Service 未 commit）", parentTaskId);
}
String realStatus = taskOpt.map(PerfRunTaskDTO::getStatus).orElse("RUNNING");
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): PerfCalcController.recalc fallback 日志断言（Red，Task S4.1）"
git commit -m "fix(perf-v1.4): PerfCalcController.recalc fallback 加 warn 日志（Green，Task S4.1）"
```

---

### Task S4.2：cycleType=null 语义明确化（R4.3 reviewer 建议）

**背景**：V1.3 R4.3 Reviewer 建议 HistoryRecalcService 的 params_json 中，cycleType=null 时改为跳过该字段（而非输出 `"cycleType":""`）。运维查询 `IS NULL` vs `=''` 有语义区别。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/HistoryRecalcService.java`（insertParentTask 的 params_json 拼接逻辑）
- Modify: `HistoryRecalcServiceTest.java`（更新既有断言 + 新增 1 case）

- [ ] **Step 1：Red 测试**

```java
@Test
void recalc_cycleTypeNull_skipsFieldInParamsJson() {
    service.recalc(startDate, endDate, List.of("M_A"), "v1", "原因", "op", null);

    ArgumentCaptor<PerfRunTask> cap = ArgumentCaptor.forClass(PerfRunTask.class);
    verify(perfRunTaskMapper).insert(cap.capture());
    String paramsJson = cap.getValue().getParamsJson();

    // V1.4 新语义：cycleType=null 时不出现在 JSON 中
    assertThat(paramsJson).doesNotContain("cycleType");
    // 既有字段仍在
    assertThat(paramsJson).contains("startDate").contains("reason");
}

@Test
void recalc_cycleTypeNotNull_includesField_V14_behaviorUnchanged() {
    service.recalc(startDate, endDate, List.of("M_A"), "v1", "原因", "op", "QUARTERLY");
    String paramsJson = capturedParamsJson();
    assertThat(paramsJson).contains("\"cycleType\":\"QUARTERLY\"");
}
```

- [ ] **Step 2：Red 失败**（当前 null 也输出 `"cycleType":""`）

- [ ] **Step 3：Green**

```java
// HistoryRecalcService.insertParentTask（params_json 拼接重构）
StringBuilder json = new StringBuilder("{");
json.append("\"startDate\":\"").append(startDate).append("\",");
json.append("\"endDate\":\"").append(endDate).append("\",");
json.append("\"metricCount\":").append(metricCount).append(",");
json.append("\"dateCount\":").append(dateCount);
if (StringUtils.isNotBlank(cycleType)) {
    json.append(",\"cycleType\":\"").append(escape(cycleType)).append("\"");
}
json.append(",\"reason\":\"").append(escape(reason)).append("\"}");
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.4): HistoryRecalcService params_json cycleType null 语义（Red，Task S4.2）"
git commit -m "fix(perf-v1.4): HistoryRecalcService cycleType=null 跳过字段而非空串（Green，Task S4.2）"
```

---

### Task S4.3：Controller.list 返回类型语义分析

**背景**：V1.3 R7.3 登记的遗留项。实际上 V1.2 Q8.5d 已 reviewer 确认"common-web `ResponseWrapper.page(PageResult<T>)` 返回 `ResponseWrapper<T>` 是契约设计"，不应改。

**V1.4 S4.3 决策**：**仅文档化**，不改代码。在 `CLAUDE.md` 明确"V1.4 确认此项不是 bug，是 common-web 既有契约"。

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（V1.4 遗留项 #6 标注为"已澄清：common-web 契约，非 bug"）

- [ ] **Step 1-2：纯文档更新 + commit**

```bash
git commit -m "docs(perf-v1.4): Controller.list 返回类型澄清为 common-web 契约设计（Task S4.3）"
```

---

## 阶段 S5：V1.4 收尾

### Task S5.1：全量回归 + 架构守护

- [ ] **Step 1：跑全量**

```bash
cd performance-engine-center
mvn clean verify
```
预期：
- surefire ≥ 549 + V1.4 新增 ≈ 580+
- failsafe ≥ 339 + V1.4 IT 新增 ≈ 360+
- 合计 ≥ 940 全绿

**跨模块 workflow-center 测试计数**（单独统计）：
```bash
cd workflow-center
mvn clean verify
```
预期：workflow-center 既有测试 + V1.4 S1.1 新增 `WorkflowQueryApiParticipantTest` 4 case 全绿

- [ ] **Step 2：6 架构守护绿**

```bash
mvn -Dtest=BizAuthConsistencyArchTest,NoEntityInControllerArchTest,NoEntityInControllerLocalsArchTest,NoV11UOEArchTest,NoUoeInFacadeTestsArchTest,PerfErrorCodeTest test
```

- [ ] **Step 3：空 commit**

```bash
git commit --allow-empty -m "chore(perf-v1.4): S5.1 全量回归通过（Task S5.1）"
```

---

### Task S5.2：V1.4 文档同步

**Files:**
- Modify: `CLAUDE.md`（根）：performance 状态 "V1.3 已交付" → "V1.4 已交付（WORKFLOW_PARTICIPANT + Target DDL + mom/yoy）"
- Modify: `performance-engine-center/CLAUDE.md`：顶部版本 V1.3 → V1.4；分期策略；运维 Runbook 追加 V1_4_0 前置检查
- Modify: `docs/modules/performance-engine-center/04-对外API契约.md`：MetricApi.getUserMetricCards 更新（含 mom/yoy 字段 + cycleType 精确匹配）
- Modify: `docs/modules/workflow-center/04-对外API契约.md` 或类似（若有）：新增 WorkflowQueryApi.queryParticipatedBusinessKeys 契约

- [ ] **Step 1-3：文档修改 + commit**

```bash
git commit -m "docs(perf-v1.4): V1.4 交付后文档全量同步（Task S5.2）"
```

---

### Task S5.3：V1.4 技术债清算

**V1.4 消化清单**（8 项 + S0.1 勘误 = 9 项）：
- [x] S0.1 V1_2_5 文档勘误
- [x] S1 WORKFLOW_PARTICIPANT scope 跨模块落地
- [x] S2 Target ScopeColumns DDL 细化 + 精化映射
- [x] S3.1 MetricCardDTO mom/yoy/previousValue 字段
- [x] S3.2 cycleKey 按 cycleType 精确匹配
- [x] S3.3 mom 环比查询
- [x] S3.4 yoy 同比查询
- [x] S4.1 execute fallback 日志
- [x] S4.2 cycleType=null 语义
- [x] S4.3 Controller.list 类型澄清（非 bug）

**V1.5+ 遗留清单**（明确登记）：
- MetricTrialRespDTO @Deprecated `getSamples()` V1.5 彻底删除
- 若出现其他新发现技术债

**Files:**
- Modify: `performance-engine-center/CLAUDE.md` 技术债章节

- [ ] **Step 1-2：清算 + commit**

```bash
git commit -m "docs(perf-v1.4): V1.4 技术债清算（Task S5.3）"
```

---

## 验收清单

V1.4 交付后必须全部满足：

- [ ] `mvn clean verify` 全绿（≥ 940 tests）
- [ ] 6 架构守护绿（BizAuth / NoEntityInController / NoEntityInControllerLocals / NoV11UOE / NoUoeInFacadeTests / PerfErrorCodeTest）
- [ ] WORKFLOW_PARTICIPANT scope 非 fail-close（真实查询 Flowable History，候选组覆盖）
- [ ] perf_target_plan / perf_target_value 新增 owner_emp_id / owner_org_code 列 + 索引
- [ ] MetricCardDTO 扩 3 字段（previousValue / mom / yoy）
- [ ] getUserMetricCards 按 cycleType 精确匹配 cycleKey（YEARLY/QUARTERLY/MONTHLY/WEEKLY 四种）
- [ ] PerfErrorCode 仍 30 条（V1.4 不新增）
- [ ] ScopeColumns record 升级到 5 字段（含 bizKeyCol）
- [ ] Flyway 链路完整：V1_0_0 → V1_0_1 → V1_0_2 → V1_0_3 → V1_0_4 → V1_1_0 → V1_1_1 → V1_2_0 → V1_2_1 → V1_2_2 → V1_2_3 → V1_2_4 → V1_2_5 → V1_3_0 → **V1_4_0**（15 脚本无缺号）
- [ ] 文档三份同步（根 CLAUDE.md / 模块 CLAUDE.md / 04 契约）

---

## 风险与回滚

| 风险 | 严重度 | 应对 |
|---|---|---|
| S1.1 Flowable `involvedUser` 不覆盖候选组成员 | 高 | Step 0 先 Read workflow-center 现有 TodoQueryService 确认候选组处理模式；若发现候选组漏数据，切模式 B（involvedUser + taskCandidateGroupIn 合并） |
| S1.2 WORKFLOW_PARTICIPANT IN 子句过长（如 5000 businessKey） | 中 | 默认 limit=5000，SQL 主流 DB 单 IN 5000 项可接受；若超规 V1.5 改分页查询或临时表 |
| S2.1 V1_4_0 DDL 生产数据量大 ALTER 阻塞 | 高 | ADD COLUMN + ADD INDEX 在 MySQL 8 是 online DDL；UPDATE 回填 > 100 万行时按 S2.1 runbook 走分批存储过程；千万级表用 pt-online-schema-change |
| S2.3 既有 V1.3 R1 测试（降级到 created_by）行为变更 | 高 | V1.4 S2.3 Red 阶段先更新测试断言（从 `created_by = #{...}` 改为 `owner_emp_id = #{...}`），确保既有测试同步升级 |
| S2.3 测试种子数据无新字段 | 中 | V1_4_0 Flyway 回填保证 Testcontainers/本地库数据有 owner_emp_id（= created_by 兜底）；既有 Mapper IT 需扩充断言覆盖新字段 |
| S3.3/S3.4 mom/yoy 查询性能 | 中 | 卡片数量 ≤ 20，每个卡片额外 2 次宽表点查 = **40 次**串行（非 60）；宽表有 UK，p99 < 10ms，总耗时预期 < 400ms，在 API < 500ms 目标内；若 p95 > 500ms V1.5 加 L2 cache |
| S3.3/S3.4 BigDecimal 除零 | 低 | `calculateMom` / `calculateYoy` 显式 `previous.compareTo(ZERO) == 0` 返回 null；2 位小数 `setScale(2, HALF_UP)` |
| S3.3/S3.4 负值 previous 的除法符号 | 低 | 用 `previous.abs()` 做分母，避免"本期上升但 mom 为负"的语义倒置 |
| S1.1 跨模块 commit 顺序 | 低 | workflow-center + performance 同 Maven monorepo。S1.1 先提交 workflow-center 方法（独立 commit），S1.2 再提交 performance 消费（独立 commit），build 不断链 |

---

## 与前四份计划的关系

- **V1.0 整改**（2026-04-22-performance-v1.0-rectification-plan.md）：28 commit 已交付
- **V1.1 迭代**（2026-04-22-performance-v1.1-iteration-plan.md）：53 commit 已交付
- **V1.2 迭代**（2026-04-22-performance-v1.2-iteration-plan.md）：58 commit 已交付
- **V1.3 迭代**（2026-04-24-performance-v1.3-iteration-plan.md）：40 commit 已交付
- **V1.4 本计划**：9 项技术债消化 + 18 Task（预计 **38-42 commit**，因 S1.2 ScopeColumns 扩 record 需 8 处调用点同步 + S2.3 既有 V1.3 R1 测试需跟随升级）

V1.4 后 performance-engine-center 技术债清零（仅剩 V1.5 @Deprecated getSamples 清理 1 项）。
