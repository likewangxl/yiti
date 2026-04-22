# performance-engine-center V1.2 迭代计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 V1.2 流程与事件能力——分配关系调整审批（对公/零售）、目标修正审批、sys_control 回滚、4 类领域事件发布、批量导出、定时清理任务，补齐数据范围过滤闭环。

**Architecture:**
V1.2 首次引入 `workflow-center` 与 `customer-marketing-center` 依赖，通过 `WorkflowApi` 启动 BPMN 流程、订阅"流程已批"事件后写入落地表。事件发布采用 Spring `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` 保证事务后可靠发布；事件 DTO 独立于 Spring，便于后续切换到 Kafka。数据范围过滤通过注入 `BizScopeApi`，在 SQL 拼接阶段透传 orgPath 条件。

**Tech Stack:**
- 新增模块依赖：`workflow-center` API、`customer-marketing-center` API（用于客户/机构校验）
- 新增注解：`@TransactionalEventListener`（Spring 原生）
- BPMN：Flowable 7.0.1（workflow-center 已集成）
- 事件对象：独立 POJO，遵循 `performance.<topic>.<verb>.v1` 命名
- 保持：V1.1 既有依赖

**依赖文档（权威需求来源）:**
- `docs/modules/performance-engine-center/01-功能规格.md` §5.4（目标修正审批）、§7.3（分配调整审批）、§2.3（版本回滚）
- `docs/modules/performance-engine-center/03-接口设计与报文.md` §E（调整申请）、§F.8（回滚）、§J.7（导出）
- `docs/modules/performance-engine-center/04-对外API契约.md` §10（事件契约）
- `docs/modules/performance-engine-center/07-审计要求.md` §1.7（SYS_CONTROL_SWITCH 高危）、§1.8（调整审批）
- `docs/modules/performance-engine-center/08-初始化数据清单.md` §4（BPMN 流程配置）、§2-3（示例指标/方案）
- `docs/modules/performance-engine-center/09-依赖契约摘要.md` §1（跨模块依赖）

**前置条件（必须完成）:**
- ✅ V1.1 全部任务已交付
- ✅ workflow-center 已完成且提供 `WorkflowApi.startProcess / WorkflowApi.queryBusinessKey` 等接口
- ✅ customer-marketing-center 已完成且提供 `CustomerQueryApi.getCustomerById / OrgApi.getOrgById`
- ✅ auth-permission-center 提供 `BizScopeApi.getScopeSqlFragment(userId, resourceId)`
- ✅ 宽表 Entity/Mapper（V1.1 已建）

---

## 阶段概览

| 阶段 | 任务数 | 目标 | 预估 |
|---|---|---|---|
| Q0 | 3 | pom 依赖 + V1.2 Flyway 基线 + BPMN 占位 | 1 天 |
| Q1 | 3 | sys_control 回滚接口 + 事件发布骨架 | 1.5 天 |
| Q2 | 6 | 分配关系调整审批（对公 + 零售） | 3 天 |
| Q3 | 4 | 目标修正审批 | 2 天 |
| Q4 | 4 | 4 类领域事件发布 | 1.5 天 |
| Q5 | 3 | 定时清理任务 | 1 天 |
| Q6 | 4 | 4 个导出接口 | 2 天 |
| Q7 | 3 | 数据范围 BizScopeApi 注入 | 1.5 天 |
| Q8 | 2 | 示例种子数据 + 文档收尾 | 0.5 天 |
| 合计 | 32 | | ~14 天 |

---

## 阶段 Q0：基础设施

### Task Q0.1：pom 依赖补齐

**Files:**
- Modify: `performance-engine-center/pom.xml`
- Test: `.../config/V12DependencyPresenceTest.java`

- [ ] **Step 1：失败测试**

```java
@Test
void workflowApi_beanAvailable() {
    assertThat(ctx.getBean("workflowApi")).isNotNull();
}
@Test
void customerQueryApi_beanAvailable() {
    assertThat(ctx.getBean("customerQueryApi")).isNotNull();
}
@Test
void orgApi_beanAvailable() {
    assertThat(ctx.getBean("orgApi")).isNotNull();
}
@Test
void bizScopeApi_beanAvailable() {
    assertThat(ctx.getBean("bizScopeApi")).isNotNull();
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：pom 追加**

```xml
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>workflow-center</artifactId>
    <version>${project.version}</version>
    <exclusions>
        <!-- 只用 API 包，不引入 workflow 的实现细节 -->
    </exclusions>
</dependency>
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>customer-marketing-center</artifactId>
    <version>${project.version}</version>
</dependency>
```

（若 workflow-center 的 api 已拆为 `workflow-center-api` 子模块，则优先引用 api 包避免循环依赖）

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "chore(perf-v1.2): 引入 workflow-center 与 customer-marketing-center 依赖"
```

---

### Task Q0.2：V1_2_0 Flyway 基线 + 调整申请/明细表

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_2_0__perf_v12_adjust_tables.sql`
- Test: `.../migration/V1_2_0FlywayIT.java`

> DDL 清单（参考 05 §2.7-§2.10 + 生产 `ddl-performance.sql`）：
> - `perf_alloc_adjust_apply`（调整主单）
> - `perf_alloc_adjust_item`（调整明细，批量）
> - `perf_target_adjust_apply`（目标修正主单）

- [ ] **Step 1：失败测试**

```java
@Test
void v12Tables_created() {
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables " +
        "WHERE table_schema=DATABASE() AND table_name IN " +
        "('perf_alloc_adjust_apply','perf_alloc_adjust_item','perf_target_adjust_apply')",
        Integer.class)).isEqualTo(3);
}
```

- [ ] **Step 2：失败（如果生产 DDL 已有这些表，则此测试在 V1.0 就绿；测试里再加字段完整性断言）**

- [ ] **Step 3：脚本**

```sql
-- V1_2_0__perf_v12_adjust_tables.sql
-- 若已在 V1_0_0 预建则只做字段校准；若未建则 CREATE
CREATE TABLE IF NOT EXISTS perf_alloc_adjust_apply (
    id VARCHAR(32) PRIMARY KEY,
    apply_code VARCHAR(64) UNIQUE NOT NULL,
    adjust_type VARCHAR(20) NOT NULL COMMENT 'CORP/RETAIL',
    reason VARCHAR(500) NOT NULL,
    applicant VARCHAR(32) NOT NULL,
    business_key VARCHAR(64) NOT NULL COMMENT 'workflow business_key',
    status VARCHAR(20) NOT NULL COMMENT 'DRAFT/RUNNING/APPROVED/REJECTED/WITHDRAWN',
    effective_date DATE NOT NULL,
    apply_time DATETIME NOT NULL,
    approve_time DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_business_key(business_key),
    INDEX idx_status_type(status, adjust_type)
);

CREATE TABLE IF NOT EXISTS perf_alloc_adjust_item (
    id VARCHAR(32) PRIMARY KEY,
    apply_id VARCHAR(32) NOT NULL,
    cust_id VARCHAR(32) NOT NULL,
    old_emp_id VARCHAR(32) NULL,
    new_emp_id VARCHAR(32) NOT NULL,
    old_org_id VARCHAR(32) NULL,
    new_org_id VARCHAR(32) NOT NULL,
    ratio DECIMAL(8,4) DEFAULT 1.0000,
    reason VARCHAR(200) NULL,
    INDEX idx_apply(apply_id),
    INDEX idx_cust(cust_id)
);

CREATE TABLE IF NOT EXISTS perf_target_adjust_apply (
    id VARCHAR(32) PRIMARY KEY,
    apply_code VARCHAR(64) UNIQUE NOT NULL,
    target_plan_id VARCHAR(32) NOT NULL,
    emp_id VARCHAR(32) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    old_target_value DECIMAL(18,2) NOT NULL,
    new_target_value DECIMAL(18,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    applicant VARCHAR(32) NOT NULL,
    business_key VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    apply_time DATETIME NOT NULL,
    approve_time DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_business_key(business_key)
);
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.2): V1_2_0 调整申请/明细表 Flyway 基线"
```

---

### Task Q0.3：三个 BPMN 流程占位 + 测试环境部署

**Files:**
- Create: `performance-engine-center/src/main/resources/bpmn/perf_alloc_adjust_corp_v1.bpmn20.xml`
- Create: `performance-engine-center/src/main/resources/bpmn/perf_alloc_adjust_retail_v1.bpmn20.xml`
- Create: `performance-engine-center/src/main/resources/bpmn/perf_target_adjust_v1.bpmn20.xml`
- Create: `performance-engine-center/src/main/java/.../config/PerfBpmnDeployConfig.java`
- Test: `.../config/PerfBpmnDeployConfigIT.java`

- [ ] **Step 1：失败测试（启动后流程已部署）**

```java
@SpringBootTest
class PerfBpmnDeployConfigIT {
    @Autowired WorkflowApi workflowApi;
    @Test
    void processesDefined() {
        assertThat(workflowApi.isProcessDefined("perf_alloc_adjust_corp_v1")).isTrue();
        assertThat(workflowApi.isProcessDefined("perf_alloc_adjust_retail_v1")).isTrue();
        assertThat(workflowApi.isProcessDefined("perf_target_adjust_v1")).isTrue();
    }
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：BPMN XML（最简流程：Start → 支行主管审批 → 分行主管审批 → End）**

`perf_alloc_adjust_corp_v1.bpmn20.xml`：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:flowable="http://flowable.org/bpmn"
    targetNamespace="http://bank.branch.platform/perf">
  <process id="perf_alloc_adjust_corp_v1" name="对公分配调整" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow sourceRef="start" targetRef="branch_mgr_review"/>
    <userTask id="branch_mgr_review" name="支行主管审批"
              flowable:candidateGroups="${branchMgrGroup}"/>
    <sequenceFlow sourceRef="branch_mgr_review" targetRef="hq_mgr_review"/>
    <userTask id="hq_mgr_review" name="分行主管审批"
              flowable:candidateGroups="${hqMgrGroup}"/>
    <sequenceFlow sourceRef="hq_mgr_review" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>
```

`PerfBpmnDeployConfig` 使用 Flowable `RepositoryService` 在启动时部署 3 个 BPMN。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.2): 部署 3 个 BPMN 流程（对公/零售/目标修正）"
```

---

## 阶段 Q1：sys_control 回滚 + 事件发布骨架

### Task Q1.1：PerfDomainEvent 基础 POJO + 发布器

**Files:**
- Create: `.../event/PerfDomainEvent.java`（抽象基类）
- Create: `.../event/SysControlUpdatedEvent.java`
- Create: `.../event/KpiCalcCompletedEvent.java`
- Create: `.../event/TargetAdjustmentApprovedEvent.java`
- Create: `.../event/AllocationAdjustmentApprovedEvent.java`
- Create: `.../event/PerfEventPublisher.java`
- Test: `.../event/PerfEventPublisherTest.java`

**事件契约（04 §10）：**
```java
public abstract class PerfDomainEvent {
    private final String eventId = IdUtil.nano();
    private final String traceId;
    private final LocalDateTime occurredAt = LocalDateTime.now();
    public abstract String topic();
}

public class SysControlUpdatedEvent extends PerfDomainEvent {
    private String scopeDim, oldVersion, newVersion, publishSource, publishBy;
    public String topic() { return "performance.sys-control.updated.v1"; }
}
```

- [ ] **Step 1：失败测试**

```java
@Test
void publisher_publishes_afterCommit_only() {
    AtomicInteger count = new AtomicInteger();
    applicationContext.addApplicationListener((ApplicationListener<SysControlUpdatedEvent>) e -> count.incrementAndGet());

    TransactionTemplate tx = new TransactionTemplate(txManager);
    tx.execute(status -> {
        publisher.publish(new SysControlUpdatedEvent(...));
        assertThat(count.get()).isZero();  // 事务内不触发
        return null;
    });
    assertThat(count.get()).isOne(); // 事务后触发
}

@Test
void publisher_skipsEvent_onRollback() {
    // 事务回滚时事件不发布
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```java
@Component
public class PerfEventPublisher {
    private final ApplicationEventPublisher delegate;
    public void publish(PerfDomainEvent e) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCommit() { delegate.publishEvent(e); }
                });
        } else {
            delegate.publishEvent(e);
        }
    }
}
```

```bash
git commit -m "feat(perf-v1.2): PerfEventPublisher 事务后发布 + 4 类事件 POJO"
```

---

### Task Q1.2：sys_control.rollback Service + Controller

**Files:**
- Modify: `.../service/SysControlService.java`（新增 rollback 方法）
- Modify: `.../controller/SysControlController.java`（新增 POST /rollback）
- Create: `.../controller/dto/RollbackReqDTO.java`
- Test: `.../controller/SysControlRollbackControllerIT.java`

- [ ] **Step 1：失败 IT**

```java
@Test
void rollback_revertsCurrentVersionToPrevious() {
    // Given: 当前 version=v2，历史 version=v1
    post("/api/perf/sys-control/rollback",
        new RollbackReqDTO("GLOBAL", "v1", "紧急回滚", "admin"))
        .andExpect(status().isOk());
    SysControl sc = sysControlService.query("GLOBAL");
    assertThat(sc.getCurrentVersion()).isEqualTo("v1");
    assertThat(sc.getPublishSource()).isEqualTo("ROLLBACK");
}

@Test
void rollback_requiresReason() {
    post("/api/perf/sys-control/rollback",
        new RollbackReqDTO("GLOBAL", "v1", "", "admin"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERF-40020"));
}
```

- [ ] **Step 2-5：实现**

关键点：
- `@AuditLog(action="SYS_CONTROL_ROLLBACK", reasonRequired=true)`
- `@BizAuth(bizType=PERF_CONFIG, action="ROLLBACK")`
- 成功后发布 `SysControlUpdatedEvent`（publishSource=ROLLBACK）

```bash
git commit -m "feat(perf-v1.2): sys_control 版本回滚接口 + 高危审计"
```

---

### Task Q1.3：SysControlUpdatedEvent 发布回归

**Files:**
- Modify: `.../service/SysControlService.java`（switch 和 rollback 两条路径都发事件）
- Test: `.../service/SysControlServiceEventIT.java`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.2): sys_control 切换/回滚均发布 SysControlUpdatedEvent"
```

---

## 阶段 Q2：分配调整审批

### Task Q2.1：AllocAdjustApply Entity + Mapper

**Files:**
- Create: `.../entity/PerfAllocAdjustApply.java`
- Create: `.../entity/PerfAllocAdjustItem.java`
- Create: `.../mapper/PerfAllocAdjustApplyMapper.java` + xml
- Create: `.../mapper/PerfAllocAdjustItemMapper.java` + xml
- Test: `.../mapper/PerfAllocAdjustMapperIT.java`

- [ ] **Step 1-5：标准 CRUD + 状态机 TDD**

```bash
git commit -m "feat(perf-v1.2): AllocAdjustApply/Item Entity + Mapper"
```

---

### Task Q2.2：AllocAdjustService 提交申请

**Files:**
- Create: `.../service/adjust/AllocAdjustService.java`
- Create: `.../service/adjust/cmd/SubmitAllocAdjustCmd.java`
- Test: `.../service/adjust/AllocAdjustServiceSubmitIT.java`

**流程：**
1. 校验申请人对所选客户有操作权限（走 `BizScopeApi`）
2. 校验每个客户存在（`CustomerQueryApi.getCustomerById`）
3. 落 `perf_alloc_adjust_apply` + `perf_alloc_adjust_item`
4. 调 `WorkflowApi.startProcess("perf_alloc_adjust_corp_v1"/"retail_v1", businessKey, variables)`
5. 更新 apply.status = RUNNING

- [ ] **Step 1：失败 IT**

```java
@Test
void submit_corpAdjust_createsApplyAndStartsWorkflow() {
    SubmitAllocAdjustCmd cmd = SubmitAllocAdjustCmd.builder()
        .adjustType("CORP")
        .reason("年度调整")
        .applicant("U001")
        .effectiveDate(LocalDate.of(2026,5,1))
        .items(List.of(item("C001","E001","E002")))
        .build();
    String applyId = service.submit(cmd);
    PerfAllocAdjustApply apply = mapper.selectById(applyId);
    assertThat(apply.getStatus()).isEqualTo("RUNNING");
    verify(workflowApi).startProcess(eq("perf_alloc_adjust_corp_v1"), eq(apply.getBusinessKey()), anyMap());
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): AllocAdjustService 提交申请 + 启动 BPMN"
```

---

### Task Q2.3：AllocAdjustCompletedListener 流程完成监听

**Files:**
- Create: `.../listener/AllocAdjustCompletedListener.java`
- Test: `.../listener/AllocAdjustCompletedListenerIT.java`

**触发：** 订阅 workflow-center 发布的 `WorkflowCompletedEvent`，筛选 `processDefinitionKey in ('perf_alloc_adjust_corp_v1','perf_alloc_adjust_retail_v1')`。

**动作：**
1. 读 apply_id（从 businessKey 解出）
2. 判断 finalDecision（APPROVED / REJECTED / WITHDRAWN）
3. 若 APPROVED：对每个 item 调 `AllocRelationService.updateAllocation(custId, newEmpId, newOrgId, effectiveDate)`
4. 更新 apply.status；发布 `AllocationAdjustmentApprovedEvent`

- [ ] **Step 1：失败 IT**

```java
@Test
void workflowCompleted_approved_updatesAllocRelations() {
    // Given: apply 已提交，流程运行中
    // When: 模拟 workflow 发布 WorkflowCompletedEvent(approved)
    listener.handle(new WorkflowCompletedEvent("perf_alloc_adjust_corp_v1",
        apply.getBusinessKey(), "APPROVED"));
    // Then
    CustAllocRelation rel = allocRelationMapper.selectByCust("C001", LocalDate.of(2026,5,1));
    assertThat(rel.getEmpId()).isEqualTo("E002");
    assertThat(mapper.selectById(apply.getId()).getStatus()).isEqualTo("APPROVED");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): AllocAdjustCompletedListener 审批通过落地分配变更"
```

---

### Task Q2.4：AllocAdjustController + 4 个端点

**Files:**
- Create: `.../controller/AllocAdjustController.java`
- Test: `.../controller/AllocAdjustControllerIT.java`

> 03 §E.1-E.4：`POST /create` / `GET /{id}` / `GET /list` / `POST /{id}/withdraw`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.2): AllocAdjustController 4 个端点"
```

---

### Task Q2.5：零售分配调整支持

**Files:**
- Modify: `.../service/adjust/AllocAdjustService.java`
- Test: `.../service/adjust/AllocAdjustRetailIT.java`

- [ ] **Step 1：失败测试（retail 走另一条 BPMN）**

```java
@Test
void submit_retailAdjust_startsRetailWorkflow() {
    SubmitAllocAdjustCmd cmd = builder().adjustType("RETAIL").build();
    service.submit(cmd);
    verify(workflowApi).startProcess(eq("perf_alloc_adjust_retail_v1"), any(), any());
}
```

- [ ] **Step 2-5：按 adjustType 路由**

```bash
git commit -m "feat(perf-v1.2): 零售分配调整走独立 BPMN"
```

---

### Task Q2.6：AllocationAdjustmentApprovedEvent 发布验证

**Files:**
- Test: `.../listener/AllocAdjustEventPublishIT.java`

- [ ] **Step 1：失败测试**

```java
@Test
void approved_publishesEvent_withItemCount() {
    listener.handle(completedEvent);
    ArgumentCaptor<AllocationAdjustmentApprovedEvent> cap = ArgumentCaptor.forClass(AllocationAdjustmentApprovedEvent.class);
    verify(eventPublisher).publish(cap.capture());
    assertThat(cap.getValue().getItemCount()).isEqualTo(3);
    assertThat(cap.getValue().topic()).isEqualTo("performance.allocation-adjustment.approved.v1");
}
```

- [ ] **Step 2-5：补齐 listener 发布逻辑**

```bash
git commit -m "feat(perf-v1.2): 审批通过发布 AllocationAdjustmentApprovedEvent"
```

---

## 阶段 Q3：目标修正审批

### Task Q3.1：TargetAdjustApply Entity/Mapper

**Files:**
- Create: `.../entity/PerfTargetAdjustApply.java`
- Create: `.../mapper/PerfTargetAdjustApplyMapper.java` + xml
- Test: `.../mapper/PerfTargetAdjustApplyMapperIT.java`

- [ ] **Step 1-5：标准 CRUD + commit**

```bash
git commit -m "feat(perf-v1.2): TargetAdjustApply Entity + Mapper"
```

---

### Task Q3.2：TargetAdjustService + Listener + Controller

**Files:**
- Create: `.../service/adjust/TargetAdjustService.java`
- Create: `.../listener/TargetAdjustCompletedListener.java`
- Create: `.../controller/TargetAdjustController.java`
- Test: `.../service/adjust/TargetAdjustServiceIT.java` / `.../listener/TargetAdjustCompletedListenerIT.java` / `.../controller/TargetAdjustControllerIT.java`

> 流程与 Q2 对称，单独一条 BPMN `perf_target_adjust_v1`。
> 落地动作：审批通过后修改 `perf_target_value` 表对应的 (planId, empId, metricCode) 的值。

- [ ] **Step 1-5：TDD 对每个组件独立 commit**

```bash
git commit -m "feat(perf-v1.2): TargetAdjust 全链路（Service + Listener + Controller）"
```

---

### Task Q3.3：TargetAdjustmentApprovedEvent 发布

**Files:**
- Modify: `.../listener/TargetAdjustCompletedListener.java`
- Test: `.../listener/TargetAdjustEventPublishIT.java`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.2): 审批通过发布 TargetAdjustmentApprovedEvent"
```

---

### Task Q3.4：03 §E.1-E.4 全部 4 端点覆盖回归

- [ ] **Step 1-2：Grep 检查端点完整性**

```
Grep pattern="@(Post|Get)Mapping.*perf/(alloc-adjust|target-adjust)" path="performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller"
```

对照 03 §E 清单，列差异。

- [ ] **Step 3-5：补齐缺失端点 + commit**

```bash
git commit -m "feat(perf-v1.2): 调整申请端点清单全量对齐 03 §E"
```

---

## 阶段 Q4：KpiCalcCompletedEvent + 剩余事件

### Task Q4.1：KpiCalcService 发布完成事件

**Files:**
- Modify: `.../service/KpiCalcService.java`
- Test: `.../service/KpiCalcServiceEventIT.java`

- [ ] **Step 1：失败测试**

```java
@Test
void calcScheme_completes_publishesEvent() {
    service.calcScheme("SCHEME_001", date, version);
    ArgumentCaptor<KpiCalcCompletedEvent> cap = ArgumentCaptor.forClass(KpiCalcCompletedEvent.class);
    verify(eventPublisher).publish(cap.capture());
    KpiCalcCompletedEvent e = cap.getValue();
    assertThat(e.getSchemeId()).isEqualTo("SCHEME_001");
    assertThat(e.getDataDate()).isEqualTo(date);
    assertThat(e.getEmpCount()).isGreaterThan(0);
    assertThat(e.topic()).isEqualTo("performance.kpi-calc.completed.v1");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): KPI 计算完成发布 KpiCalcCompletedEvent"
```

---

### Task Q4.2：事件 Payload JSON 序列化契约测试

**Files:**
- Test: `.../event/EventJsonSerializationTest.java`

- [ ] **Step 1：失败测试（事件 JSON 字段名与 04 §10 约定完全一致）**

```java
@Test
void sysControlUpdatedEvent_jsonSchema() throws Exception {
    SysControlUpdatedEvent e = new SysControlUpdatedEvent("GLOBAL","v1","v2","MANUAL","admin");
    String json = objectMapper.writeValueAsString(e);
    assertThatJson(json).isEqualTo("""
        {
          "eventId": "${json-unit.any-string}",
          "traceId": "${json-unit.any-string}",
          "occurredAt": "${json-unit.any-string}",
          "topic": "performance.sys-control.updated.v1",
          "scopeDim": "GLOBAL",
          "oldVersion": "v1",
          "newVersion": "v2",
          "publishSource": "MANUAL",
          "publishBy": "admin"
        }""");
}
// 其余 3 类事件同样断言
```

- [ ] **Step 2-5：调整事件 POJO 字段名 + @JsonProperty 注解 + commit**

```bash
git commit -m "test(perf-v1.2): 4 类领域事件 JSON 契约测试"
```

---

### Task Q4.3：事件发布失败降级

**Files:**
- Modify: `.../event/PerfEventPublisher.java`
- Test: `.../event/PerfEventPublisherFailureTest.java`

- [ ] **Step 1：失败测试（消费者异常不应影响主事务）**

```java
@Test
void listenerException_doesNot_rollbackMainTx() {
    // 注入一个永远抛异常的 listener
    String id = service.doSwitchVersion(cmd);  // 事务提交成功
    assertThat(mapper.selectById(id)).isNotNull();  // 数据已落
    // 异常已被 catch 并 log.error
}
```

- [ ] **Step 2-5：publisher 的 afterCommit 代码 try-catch + 记录错误 + commit**

```bash
git commit -m "feat(perf-v1.2): 事件发布失败降级，不影响主事务"
```

---

### Task Q4.4：事件订阅 Demo + 文档

**Files:**
- Create: `performance-engine-center/src/test/java/.../event/PerfEventConsumerDemoIT.java`

> 仅示例用，演示下游模块如何订阅。

- [ ] **Step 1-5：写一个演示 listener 订阅 4 类事件 + commit**

```bash
git commit -m "test(perf-v1.2): 事件订阅 Demo 示例"
```

---

## 阶段 Q5：定时清理任务

### Task Q5.1：SysControlCleanupJob

**Files:**
- Create: `.../job/SysControlCleanupJob.java`
- Test: `.../job/SysControlCleanupJobTest.java`

**逻辑：** 保留最近 N 个历史版本（默认 12 个），其余清理到 `sys_control_history` 归档表（若无归档表则硬删+审计 log）。

- [ ] **Step 1：失败测试**

```java
@Test
void run_keepsRecentNVersions() {
    // 预置：24 个历史版本
    job.run();
    assertThat(sysControlHistoryMapper.count()).isEqualTo(12);
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): SysControlCleanupJob 历史版本清理"
```

---

### Task Q5.2：PerfRunTaskCleanupJob

**Files:**
- Create: `.../job/PerfRunTaskCleanupJob.java`
- Test: `.../job/PerfRunTaskCleanupJobTest.java`

**逻辑：** 清理 90 天前的 SUCCESS 状态 run_task；FAILED/RUNNING 状态保留。

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.2): PerfRunTaskCleanupJob 过期任务清理"
```

---

### Task Q5.3：分布式锁接入（ShedLock）

**Files:**
- Modify: `performance-engine-center/pom.xml`（加 shedlock-spring + shedlock-provider-redis-spring）
- Modify: 2 个 Job 类（加 `@SchedulerLock`）
- Test: `.../job/JobShedLockConcurrentIT.java`

- [ ] **Step 1：失败测试（两节点同时启动，只有一个执行）**

```java
@Test
void twoNodes_onlyOneExecutes() {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger runs = new AtomicInteger();
    runTwoNodesSameTime(() -> { job.run(); runs.incrementAndGet(); latch.countDown(); });
    latch.await(5, SECONDS);
    assertThat(runs.get()).isEqualTo(1);
}
```

- [ ] **Step 2-5：接入 ShedLock + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): 定时任务接入 ShedLock 防重"
```

---

## 阶段 Q6：导出接口

### Task Q6.1：异步导出框架（导出任务表 + Controller 骨架）

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_2_1__perf_export_task.sql`
- Create: `.../entity/PerfExportTask.java`
- Create: `.../mapper/PerfExportTaskMapper.java` + xml
- Create: `.../service/export/PerfExportService.java`
- Create: `.../controller/PerfExportController.java`
- Test: `.../service/export/PerfExportServiceIT.java`

> 设计：导出请求 → 创建 PerfExportTask(PENDING) → 异步执行（Spring @Async）→ 完成写 MinIO → 返回下载 URL
> 表字段：id / export_type / params(JSON) / status / file_key / expire_at / operator_id / created_at

- [ ] **Step 1：失败 IT**

```java
@Test
void exportKpi_createsTask_andReturnsTaskId() {
    String taskId = post("/api/perf/export/kpi",
        Map.of("schemeId","S1","dataDate","2026-04-01"))
        .andReturn().json("$.taskId", String.class);
    PerfExportTask task = mapper.selectById(taskId);
    assertThat(task.getStatus()).isIn("PENDING","RUNNING","SUCCESS");
}

@Test
void queryStatus_returnsDownloadUrl_whenSuccess() {
    String url = get("/api/perf/export/task/" + taskId).andReturn().json("$.downloadUrl");
    assertThat(url).startsWith("http://minio-preview/");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): 异步导出框架 + 导出任务 CRUD"
```

---

### Task Q6.2：KPI 导出 Excel

**Files:**
- Create: `.../service/export/impl/KpiExportStrategy.java`
- Create: `.../service/export/model/KpiExportRow.java`
- Test: `.../service/export/impl/KpiExportStrategyIT.java`

- [ ] **Step 1-5：TDD，用 easyexcel 写 Excel + 上传 MinIO + commit**

```bash
git commit -m "feat(perf-v1.2): KPI 导出 Excel"
```

---

### Task Q6.3：指标/分配/明细 3 个导出策略

**Files:**
- Create: `.../service/export/impl/MetricExportStrategy.java`
- Create: `.../service/export/impl/AllocExportStrategy.java`
- Create: `.../service/export/impl/DetailExportStrategy.java`
- Test: 三个 IT

- [ ] **Step 1-5：并行子任务 TDD（格式同 Q6.2）**

```bash
git commit -m "feat(perf-v1.2): 指标/分配/明细 3 个导出策略"
```

---

### Task Q6.4：4 个导出端点接入 + 资源注册

**Files:**
- Modify: `.../controller/PerfExportController.java`
- Modify: `performance-engine-center/src/main/resources/sql/V1_2_2__perf_v12_resources.sql`
- Test: `.../controller/PerfExportControllerIT.java`

- [ ] **Step 1-5：4 端点 IT + 资源表 PT_RESOURCE 状态从 PENDING 改为 ACTIVE**

```sql
UPDATE pt_resource SET status='ACTIVE' WHERE id IN (
  'P_PERF_EXPORT_KPI','P_PERF_EXPORT_METRIC',
  'P_PERF_EXPORT_ALLOC','P_PERF_EXPORT_DETAIL');
```

```bash
git commit -m "feat(perf-v1.2): 4 个导出端点接入 + 资源启用"
```

---

## 阶段 Q7：数据范围 BizScopeApi 注入

### Task Q7.1：BizScopeApi 注入基础设施

**Files:**
- Modify: `performance-engine-center/pom.xml`（若 V1.1 未加，此处加 auth-permission-center-api 确保 BizScopeApi 可注入）
- Create: `.../service/scope/PerfScopeHelper.java`
- Test: `.../service/scope/PerfScopeHelperTest.java`

**职责：** 封装 `BizScopeApi.getScopeSqlFragment()` 的调用，返回 `(sqlFragment, params)` 供 Mapper 拼接。

- [ ] **Step 1：失败测试**

```java
@Test
void getFragment_forUserU001_includesOrgPath() {
    PerfScopeHelper.Fragment f = helper.getFragment("U001", "P_PERF_ALLOC_QUERY");
    assertThat(f.getSql()).contains("org_path LIKE");
    assertThat(f.getParams()).containsKey("orgPath");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): PerfScopeHelper 封装 BizScopeApi 调用"
```

---

### Task Q7.2：AllocRelationMapper 注入数据范围

**Files:**
- Modify: `.../mapper/CustAllocRelationMapper.xml`（所有查询 SQL 加 `<include refid="scopeFragment"/>`）
- Modify: `.../service/AllocRelationService.java`（传入 scope params）
- Test: `.../service/AllocRelationServiceScopeIT.java`

- [ ] **Step 1：失败测试（不同用户看到的数据受限）**

```java
@Test
void query_as_branchMgr_onlyReturnsBranchScopeRelations() {
    mockUser("U_BRANCH_MGR", orgPath="/HQ/BRANCH_01/");
    List<CustAllocRelation> list = service.listRelations(...);
    assertThat(list).allMatch(r -> r.getOrgId().startsWith("/HQ/BRANCH_01/"));
}

@Test
void query_as_headMgr_returnsAllBranches() {
    mockUser("U_HEAD_MGR", orgPath="/HQ/");
    List<CustAllocRelation> list = service.listRelations(...);
    assertThat(list).extracting(CustAllocRelation::getOrgId).contains("/HQ/BRANCH_01/", "/HQ/BRANCH_02/");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.2): AllocRelation 查询注入数据范围过滤"
```

---

### Task Q7.3：Kpi/Target/Metric 查询接口同样注入

**Files:**
- Modify: 相关 Mapper XML 与 Service
- Test: 对应 IT

- [ ] **Step 1-5：按 Q7.2 模式复制到其他 3 类查询**

```bash
git commit -m "feat(perf-v1.2): Kpi/Target/Metric 查询数据范围注入"
```

---

## 阶段 Q8：种子数据 + 文档收尾

### Task Q8.1：预置业务示例数据

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_2_3__perf_seed_data.sql`
- Test: `.../migration/V1_2_3FlywayIT.java`

> 参考 08 §2-3：注入 5 个示例指标（M_EMP_DEP_AVG_BAL 等）、2 个示例 KPI 方案、1 个示例目标方案。
> **仅 dev 环境生效**：脚本加 `-- Flyway placeholder` 条件，或改用 data.sql + `spring.profiles.active=dev`。

- [ ] **Step 1：失败测试**

```java
@Test
void seedData_inDevProfile_insertsExamples() {
    assertThat(metricDefMapper.selectByCode("M_EMP_DEP_AVG_BAL")).isNotNull();
    assertThat(kpiSchemeMapper.countByStatus("ACTIVE")).isGreaterThanOrEqualTo(2);
}
```

- [ ] **Step 2-5：脚本 + commit**

```bash
git commit -m "feat(perf-v1.2): 预置 5 示例指标 + 2 KPI 方案 + 1 目标方案"
```

---

### Task Q8.2：文档全量同步

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（标记 V1.2 已交付）
- Modify: `CLAUDE.md`（根）：performance-engine-center 状态 → "V1.2 已交付（全量能力）"
- Modify: `docs/modules/performance-engine-center/04-对外API契约.md`（UOE 全部移除）

- [ ] **Step 1-3：修改 + commit**

```bash
git commit -m "docs(perf-v1.2): V1.2 交付后全量文档同步"
```

---

## 验收清单

V1.2 交付后必须全部满足：

- [ ] `mvn clean test` 在 performance-engine-center 全绿
- [ ] `mvn clean verify` 在 bootstrap 全绿
- [ ] 3 个 BPMN 流程在启动时正确部署（`act_re_procdef` 表有记录）
- [ ] 对公分配调整走对公流程、零售走零售流程（通过 IT 验证）
- [ ] 4 类领域事件发布成功率 100%（通过监控事件订阅 log 计数）
- [ ] sys_control 历史版本清理后保留最新 12 个
- [ ] 4 个导出接口均能产生 MinIO 文件并返回下载 URL
- [ ] 分行主管只能看到本分行的 KPI/分配/目标数据
- [ ] `performance-engine-center/CLAUDE.md` 与根 CLAUDE.md 状态一致
- [ ] 所有 V1.2 端点已在 pt_resource 登记且 `status='ACTIVE'`

## 风险与回滚

| 风险 | 严重度 | 应对 |
|---|---|---|
| workflow-center 循环依赖 | 高 | workflow-center 拆出 `workflow-center-api` 子包，performance 只依赖 api | 
| BPMN 流程定义变更导致存量 running 实例异常 | 高 | BPMN 版本用 `_v1` 后缀，升级走 `_v2` 新部署，不覆盖 v1 |
| 事件订阅链路阻塞主事务 | 高 | Task Q4.3 已覆盖，`@Async` + try-catch + log.error |
| 回滚接口被误操作 | 极高 | `reasonRequired=true` + 审计 + 事件通知管理员 + 需 PERF_CONFIG 高级权限 |
| 导出大数据量 OOM | 中 | easyexcel 分批写（默认每 1 万行 flush）+ 导出任务限制并发 2 |
| 数据范围注入破坏既有 SQL | 中 | 所有 Mapper XML 改造必须跑 MapperIT 回归；新测试必须同时包含"限制分支""放开全行"两种 case |

**回滚策略：**
- `spring.profiles.active=v11` 回滚到 V1.1：禁用 job、BPMN 部署、事件发布（通过 `@ConditionalOnProperty`）
- 所有 V1_2_x Flyway 脚本准备 `undo` 反向 SQL
- BPMN 部署配置 `deploymentMode=SINGLE_RESOURCE` 以便重新部署
- 若事件订阅下游异常：可通过 `perf.event.enabled=false` 紧急禁用发布（事件仅用于通知，非强一致依赖）

## 跨模块协作事项

V1.2 依赖下游就绪：
- **workflow-center** 必须提供：
  - `WorkflowApi.startProcess(key, businessKey, variables)`
  - `WorkflowApi.isProcessDefined(key)`
  - `WorkflowApi.queryByBusinessKey(businessKey)`
  - `@EventListener(WorkflowCompletedEvent)` 可被订阅
- **customer-marketing-center** 必须提供：
  - `CustomerQueryApi.getCustomerById(custId)`（含组织归属）
  - `OrgApi.getOrgById(orgId)`（含 orgPath）
- **auth-permission-center** 必须提供：
  - `BizScopeApi.getScopeSqlFragment(userId, resourceId)`
  - `EmpQueryApi.getEmpById(empId)`（含 orgId、grade）

若上述接口缺失，V1.2 无法启动。
