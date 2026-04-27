# Option A + V1.1 整改 + Option B.1 + B.2-a + P0 fix 会话续接锚点（2026-04-25）

> **用途**：本会话从「Option A→B→C 选择」起步，依次交付 Option A 启动级 IT、V1.1 audit 整改、Option B.1 portal workspace IT（含 5 case + 全 reviewer nitpick 整改）、Option B.2-a customer 事件链 IT、P0 生产 bug 修复。会话因 compact 暂停在 Phase 2 (c) 调研阶段，本文档作为下次会话直接续接的锚点。

## 会话主线

| # | 任务 | 状态 | 交付 commit |
|---|---|---|---|
| 0 | 数据库对齐（onepl PT_RESOURCE 122→272→298）+ docs/schema 同步 | ✅ 已完成 | `619243b` `2035fee` `0262349` |
| 1 | Option A 启动级集成测试（BootstrapStartupIT 4 case + RestEndpointInventoryIT 1 case + audit 报告） | ✅ 已完成 | `d9109fa` |
| 2 | V1.1 整改：audit 类型 A 26→0 / 类型 B 11→0 | ✅ 已完成 | `7d8a68c` |
| 3 | Option B.1 portal 工作台聚合 IT 含 5 case（trend UP/FLAT/DOWN/null + shortcut+graceful 4 路）+ reviewer 全 nitpick 整改（UTF-8 全局 / stale jar 文档化 / CurrentUserApi mock 全方法 / 5 路 graceful） | ✅ 已完成 | `c65a290` `c5121fc` `32cf3fd` `b9a8982` |
| 4 | Option B.2-a 最小可信集 IT（LeadApprovedCreatesCustomerMasterIT） | ✅ 已完成 | `532896d` |
| 5 | **P0 生产 bug 修复（WorkflowCallbackListener 链路中断 + 5 listener 审计）** | ✅ 已完成 | `3f1e3c0` `7a90e47` |
| 6 | Phase 2 (c)：WorkflowCallbackListener:87 TODO 修复（区分 APPROVED/REJECTED） | ✅ 已完成 | `1bd5301` `c29046a` |
| 7 | Phase 2 (b)：lead_approve_v1.bpmn20.xml + flowable-lead-e2e profile + LeadWorkflowE2EIT 真 BPMN E2E | ⏳ 待启动 | — |

---

## 累计 14+ commits（origin/claude/crazy-joliot-0e2a1f）

```
c29046a fix(workflow-callback): 区分 APPROVED/REJECTED 分支（Phase 2 (c)）  ← compact 后新增
1bd5301 test(workflow-callback-reject): 红 IT 复现 REJECTED 分支被忽略 bug  ← compact 后新增
6e1a709 docs(session): 本会话 12 commits 状态归档（compact 暂停续接锚点）
7a90e47 fix(workflow-callback): 修复 P0 链路中断 — 去 fallbackExecution + 加 @Transactional REQUIRES_NEW
3f1e3c0 test(workflow-callback-bug): 红 IT 复现 WorkflowCallbackListener 链路中断 P0 bug
532896d feat(option-b2-a): customer 事件链 IT — LeadApproved 触发 cust_master 创建
b9a8982 test(option-b1): 补 WorkspaceService 剩余 5 路并行 IT 覆盖（reviewer §G-1）
32cf3fd test(option-b1): 补 trend 4 边界 case + CurrentUserApi mock 全方法（reviewer §B-1 §D）
c5121fc chore(testing): UTF-8 全局编码 + stale jar 处理文档化（reviewer §E §F nitpick 整改）
c65a290 feat(option-b1): portal 工作台聚合端到端 IT（验证 PerformanceMetricApiBridge 真实链路）
7d8a68c fix(pt-resource): V1.1 整改 audit 类型 A/B 清零（26 新增 + 11 下线）
0262349 fix(seed-v1): R_RM 角色 workflow submit/cancel 绑定恢复设计期 RR_* 命名
d9109fa feat(option-a): 新增启动级集成测试 BootstrapStartupIT + RestEndpointInventoryIT
2035fee docs(schema): 同步 docs/schema/ 与 onepl 实际状态（ddl-report + seed-v1 V1.0 权威）
619243b chore(db-align): 2026-04-25 Option A 预条件数据库对齐脚本归档
```

工作树状态：clean（所有改动已 commit + push）。

---

## 重大技术决策与发现

### 决策 1：bootstrap 测试 H2 schema 扩展（commit `c65a290`）

`bootstrap/src/test/resources/schema.sql` 追加 7 张表 H2 兼容版：
- `sys_control` / `perf_metric_def` / `perf_kpi_scheme` / `perf_kpi_item` / `perf_target_value` / `emp_index_result`（val_1..val_5 裁剪 200→5）/ `portal_shortcut`

DDL 来源：`docs/schema/ddl-performance.sql` + `docs/schema/ddl-portal.sql` 适配 H2（去反引号 / tinyint(1) → int / 索引省略）。

### 决策 2：UTF-8 全局编码（commit `c5121fc`）

pom.xml `surefire/failsafe` argLine 加 `-Dfile.encoding=UTF-8`，全局根治 Windows JVM file.encoding=GBK 导致 @Sql 加载中文 fake data 乱码。不再需要 @SqlConfig(encoding="UTF-8") 局部 hack。

### 决策 3：CLAUDE.md 加"测试 IT 注意事项"章节（commit `c5121fc`）

文档化 stale jar 处理（跨模块 java 改动后必跑 `mvn clean install -DskipTests`）+ surefire/failsafe 分工（*Test.java vs *IT.java）+ UTF-8 全局已配置。

### 决策 4：seed-v1.sql §3 重写为 mysqldump dump 风格（commit `2035fee`）

PT_RESOURCE 章节改为按 RESOURCE_ID 字典序 mysqldump 输出（每行一个 INSERT IGNORE INTO），与当前 onepl 实际 272 条对齐。后续 commit `7d8a68c` 在此基础上扩到 298 条 + 11 条 STATUS=1 下线。

### 决策 5：WorkflowCallbackListener fallbackExecution 修复方案（commit `7a90e47`）

**问题链路**（reviewer 确认）：
```
ProcessCompletedListener.notify() [Flowable，事务内]
  ↓ publishEvent(ProcessCompletedEvent) [事务内]
WorkflowCallbackListener.onProcessCompleted
  @TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)
  ← 在事务 commit 后触发（事务已结束）
  ↓ publishEvent(LeadApprovedEvent) [事务外]
LeadApprovedListener.handle
  @TransactionalEventListener(phase=AFTER_COMMIT)  ← 无 fallbackExecution
  ← 事件发布时无活跃事务 → Spring 静默 skip
✗ cust_master 永不创建
```

**修复 pattern**（应用于 3 个 listener）：
- 去 `fallbackExecution = true`
- 加 `@Transactional(propagation=REQUIRES_NEW, rollbackFor=Exception.class)`

### 决策 6：5 个 listener 审计结果

| Listener | 状态 | 处理 |
|---|---|---|
| customer.WorkflowCallbackListener | bug（生产链路断裂） | ✅ 修复 |
| bizapp.LoanWorkflowListener | 同 pattern（下游 0 消费者） | ✅ 防御性修复 |
| bizapp.SupportWorkflowListener | 同 pattern（下游 0 消费者） | ✅ 防御性修复 |
| performance.AllocAdjustCompletedListener | 已有 fallbackExecution + REQUIRES_NEW 共存正确 pattern | 不动 |
| performance.TargetAdjustCompletedListener | 同上 | 不动 |

### 决策 7：portal RESOURCE_ID 长度 ≤ 20（commit `619243b`）

PT_RESOURCE.RESOURCE_ID 是 varchar(20)，原 superpowers/sql/2026-04-11-portal-resources-align.sql 设计期 ID 超长（`RES_PRODUCT_SUPPORT_AVAILABLE` 29 字符）会被截断。修复后用：
- `RES_PORTAL_WORKSPACE`（20）
- `RES_PROD_SUP_AVL`（16）
- `RES_SHORTCUT_PUT`（16）

### 决策 8：Phase 2 (c) WorkflowCallbackListener APPROVED/REJECTED 分发（commits `1bd5301` `c29046a`）

**bug 描述**：`WorkflowCallbackListener.onProcessCompleted:87` TODO 长期忽略 `event.outcome()`，无论流程审批结果如何都走 `handleApproved` → 驳回流程被错误标 APPROVED + 错误创建 cust_master / 错误失效（DELETE 路径）。语义层 bug，与 P0 fallbackExecution bug 正交。

**修复方案**（参考 `bizapp.LoanWorkflowListener` 已有 pattern）：
- 按 `"REJECTED".equals(event.outcome())` 分发（字面量在前防 NPE）
- 保留 `handleApproved` 不动
- 新增 `handleRejected`：updateStatus(REJECTED) + publish LeadRejectedEvent（携带 reason）
- 新增 `LeadRejectedEvent`（与 LoanRejectedEvent 设计对齐，V1 暂无下游消费保留扩展点）

**TDD 红-绿 严格分 commit**：
- `1bd5301`：纯红 IT（仅 +207 行 IT 文件，零业务代码改动），WorkflowCallbackRejectedBranchBugIT 用 TxPublisher @Transactional 模式真实模拟 Flowable 路径
- `c29046a`：fix（+41 LeadRejectedEvent + +66/-13 WorkflowCallbackListener），红 IT 转绿，6 模块 1502 case 全回归 PASS

**reviewer 综合 review 结论**：⚠️ 通过（A/B/C/D/E/F/G 全过），列 6 个 follow-up（详见末尾 V1.7+ 跟踪段）。

---

## 当前 IT 测试覆盖（bootstrap 模块）

| IT 类 | case 数 | 覆盖 |
|---|---:|---|
| BranchPlatformApplicationTest | 1 | 启动 |
| SmokeTest | 1 | H2 + JdbcTemplate |
| CrossModuleApiTest | ? | 跨模块 *Api Bean 装配 |
| BusinessApplicationCenterIT | ? | bizapp 业务申请 |
| CustomerMarketingCenterIT | ? | customer 标签/线索/认领/触达 |
| FullAuthChainTest | 6 | 完整认证链路 |
| BootstrapStartupIT | 4 | bean 装配 + Bridge 注入 + 端点 ≥141 |
| RestEndpointInventoryIT | 1 | 端点 vs PT_RESOURCE 对账（A 0 / B 0）|
| **PortalWorkspaceMetricIT** | **5** | trend UP/FLAT/DOWN/null + shortcut 真实链路 + 4 路 graceful |
| **LeadApprovedCreatesCustomerMasterIT** | **1** | LeadApproved → CustMasterAssemblerService → cust_master |
| **WorkflowCallbackEventChainBugIT** | **1** | 红 IT 防回归 fallbackExecution P0 bug |
| **WorkflowCallbackRejectedBranchBugIT** | **1** | 红 IT 防回归 REJECTED 分支被忽略语义 bug（Phase 2 (c)）|
| FlowableWorkflowCenterE2ETest | ? | Flowable E2E（@EnabledIfSystemProperty 保护） |
| FlowableWorkflowCenterRealEnvTest | ? | 真 MySQL Flowable E2E |
| PerformanceMetricApiBridgeTest | 10 | bridge 单元测试 |

bootstrap 全模块（含 surefire + failsafe）：**42 + 11 = 53 case PASS / 3 SKIP**。

6 模块全回归：**1502 case 全绿**（auth 154 + portal 236 + customer 322 + bizapp 168 + performance 580 + bootstrap 42）。

---

## 待办（Phase 2 续接锚点）

### Phase 2 (c) — ✅ 已完成（compact 后续接交付）

commits `1bd5301` `c29046a` 已 push。详见上文「决策 8」。预估 30-45 分钟，实际约 35 分钟（含 reviewer review）。reviewer ⚠️ 通过 + 6 个 follow-up（不阻塞，详见末尾 V1.7+ 跟踪段）。

### Phase 2 (b) — 真 BPMN E2E IT（防回归终极屏障）

**目标**：补 `lead_approve_v1.bpmn20.xml` + `flowable-lead-e2e` profile + `LeadWorkflowE2EIT`，端到端验证：
- createDraft → submitForApproval → claim task → approve → cust_master 创建 → createLoanDraft → submitLoan → loan_apply IN_APPROVAL（通过分支）
- createDraft → submitForApproval → reject → cust_lead REJECTED（驳回分支，依赖 (c) 已完成）

**实施步骤**：
1. 新建 `workflow-center/src/main/resources/bpmn/lead_approve_v1.bpmn20.xml`（参考 `loan_approve_v1.bpmn20.xml`，简化为 2 节点：branch_manager_approve → 通过/驳回）
2. 新建 `bootstrap/src/test/resources/application-lead-e2e.yml`（参考 `application-flowable-e2e.yml` 复用 H2 + 真 Flowable）
3. 新建 `bootstrap/src/test/resources/lead-e2e-data.sql`（参考 `flowable-e2e-data.sql` 含 PT_USER + 角色 + wf_node_candidate_conf）
4. 新建 `bootstrap/src/test/java/com/bank/branch/platform/it/LeadWorkflowE2EIT.java`（@ActiveProfiles("lead-e2e") + @EnabledIfSystemProperty 保护）
5. CI 默认跳过（与 FlowableWorkflowCenterE2ETest 一致），本地 `-Dlead.e2e.enabled=true` 触发
6. 1-2 commits push origin

**关键文件路径**：
- BPMN 参考：`workflow-center/src/main/resources/bpmn/loan_approve_v1.bpmn20.xml`
- profile 参考：`bootstrap/src/test/resources/application-flowable-e2e.yml`
- IT 参考：`bootstrap/src/test/java/com/bank/branch/platform/it/FlowableWorkflowCenterE2ETest.java`
- 数据参考：`bootstrap/src/test/resources/flowable-e2e-data.sql`

**预计工时**：1.5-2 小时，2-3 commits。

### §G-3 Bridge 模式扫描 follow-up（reviewer 之前提到，未做）

类似 `PerformanceMetricApiBridge` 的 Bridge 模式扫描 IT 化：
- `WorkflowQueryAdapter`（portal 内已有 graceful 降级，需端到端 IT 防回归）
- 潜在 `governance.FileApi → portal` 等

留作 V1.7+ 跟踪项。

### Phase 2 (c) reviewer 6 个 follow-up（V1.7+ 跟踪，不阻塞合并）

| # | 项 | 优先级 | 说明 |
|---|---|---|---|
| FU-1 | `WorkflowCallbackListener.handleApproved/handleRejected` 缺幂等保护 | 中 | vs LoanWorkflowListener 的 `conditionalUpdateStatus(IN_APPROVAL → target)` + `rowsAffected==0 早返回`；需补 `CustLeadMapper.conditionalUpdateStatus` |
| FU-2 | `WorkflowCallbackListener.onProcessCompleted` 缺整体 try-catch 兜底 | 低 | vs LoanWorkflowListener line 73/127 的 `try { 全部业务 } catch { log.error 不重抛 }` |
| FU-3 | `operatorEmpId` 硬编码 `"SYSTEM"` | 低 | 应从 Flowable 历史拿 lastApproverEmpId 注入；当前 ProcessCompletedEvent 载荷无 operator 信息 |
| FU-4 | 红 IT 仅覆盖 `LeadOp.CREATE`，缺 UPDATE/DELETE | 低 | 建议补 DELETE 验证 cust_master 仍 ACTIVE / UPDATE 验证 cust_master 字段不更新 |
| FU-5 | 红 IT 未直接断言 `LeadRejectedEvent` 被 publish | 低 | 建议加 `@SpyBean ApplicationEventPublisher` 验证 publish 调用次数 |
| FU-6 | **`handleWorkflowCallback`（line 181-208 外部入口）REJECTED 路径不发布 LeadRejectedEvent** | **中** | 与 onProcessCompleted REJECTED 路径不一致；将来某天有人通过外部 REST 入口走该方法驳回，会得到「lead.status==REJECTED 但事件未发布」的不一致状态 |

**reviewer 特别提醒**：commit message 里 "与 LoanWorkflowListener pattern 对齐" 略有夸大 — 实际只对齐了 outcome 分发，没对齐幂等（conditionalUpdateStatus）和整体异常兜底（try-catch）。建议 V1.7+ 把 FU-1 / FU-2 / FU-6 合并成一个「WorkflowCallbackListener 鲁棒性补齐」任务统一跟踪。

---

## 用户偏好沿用

- 子代理模型分层：**Explore→sonnet，其他（implementer/reviewer）→opus**（详见 `~/.claude/projects/D--Project-oneplate/memory/feedback_subagent_model.md`）
- 每 task = implementer + 1 综合 reviewer（不分 spec/code 两阶段）
- Phase 完成即 push origin，全部完成合并 master（A 方案直接在 master 上做）
- 中文 commit message + HEREDOC 格式 + `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` 尾行
- `git add` 显式列文件，禁用 `git add -A`
- TDD 严格 Red-Green 分离（红 IT commit 与 fix commit 分离）
- 禁用 `--no-verify` / `--no-gpg-sign`
- UTF-8 编码 + 中文注释规范

---

## 下次会话续接指引

### 快速入口

读本文档了解全貌，然后选择以下方向之一（Phase 2 (c) 已完成，余下选项按当前优先级排序）：

1. **继续 Phase 2 (b)**：派 implementer 写真 BPMN E2E IT（清单见上文 §"Phase 2 (b)"；REJECTED 分支验证现已可用，因 (c) 已修复）
2. **跳过 (b)，进入 Option B.3 / B.4**：report SqlProbe 异步导出 / perf→report 跨模块只读
3. **进入 Option C**：100+60 条 curl 回归（需 docker-compose 完整环境）
4. **本会话收尾合并 master**：将 14+ commits 合并到 master 分支
5. **跑 Phase 2 (c) reviewer follow-up FU-1/FU-2/FU-6**：「WorkflowCallbackListener 鲁棒性补齐」一次性收尾

### 验证当前状态命令

```bash
# 确认 git 状态
git status
git log --oneline origin/master..HEAD | head -15

# 跑全模块回归确认 1502 case 仍绿
mvn clean install -DskipTests
mvn test -pl auth-permission-center,customer-marketing-center,portal-content-center,business-application-center,performance-engine-center,bootstrap

# 跑 bootstrap IT（含本会话新增 IT）
mvn verify -pl bootstrap
```

### 关键文档速查

- 本文档：`docs/superpowers/sessions/2026-04-25-b-option-a-bc-handover.md`
- 上一会话存档：`docs/superpowers/sessions/2026-04-25-a-fix-completion-and-dir3-handover.md`
- audit 报告：`docs/superpowers/reports/2026-04-25-endpoint-resource-audit.md`（A 0 / B 0）
- 数据库对齐脚本：`docs/superpowers/sql/2026-04-25-option-a-precondition-align.sql` + `2026-04-25-v1-1-pt-resource-cleanup.sql`
- mysqldump 备份：`docs/superpowers/sql/backup/2026-04-25-pre-option-a-backup.sql`

---

**文档生成时间**：2026-04-25（compact 后续接更新于 2026-04-27）
**对应 git HEAD**：`c29046a`（compact 前 `7a90e47`，compact 归档 `6e1a709`，后 Phase 2 (c) 红 `1bd5301` + 绿 `c29046a`）
**会话累计 commit（本期分量）**：14 commits + 本 handover 续接更新
**平台累计 commit（含历史）**：241（上期累计） + 14（本期） = **255 commits**
