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
| 7 | Phase 2 (b)：lead_approve_v1.bpmn20.xml + lead-e2e profile + LeadWorkflowE2EIT 真 BPMN E2E（APPROVED + REJECTED 双分支） | ✅ 已完成 | `a6b2290` |
| 8 | 合并 master（第一次）：本期前 16 commits + Phase 2 (b) 后续合并 | ✅ 已完成 | merge `3fd9cba` + 后续 `349c563` |
| 9 | Phase 2.5：FU-1/2/6/9 鲁棒性补齐（H2 schema + dead code 删除 + 幂等保护 + 异常兜底）| ✅ 已完成 | `aab052f` `e033d7b` `4efde7e` `6615740` `2195cd8` `075fb9a` `7f14aca` |
| 10 | 合并 master（第三次）：Phase 2.5 7 commits + handover 续接 | ✅ 已完成 | merge `0af1635` |
| 11 | onepl MySQL 幂等导入最新 docs/schema DDL + SEED + 备份 | ✅ 已完成 | 备份 `docs/superpowers/sql/backup/2026-04-27-pre-fu1415-import-backup.sql`（7553 行）|
| 12 | **Phase 2.6 FU-14：孤儿 lead 数据补偿机制（@Scheduled 5min 巡检 + reconcile 抽取 + WorkflowApi.getProcessOutcome）** | ✅ 已完成（compact 后续接交付，reviewer ✅ 通过+5 follow-up） | `5602b52` `28b1bbc` |
| 13 | **Phase 2.6 FU-15 B：用本地 onepl_test_bootstrap MySQL 替代 testcontainers**（本地无 Docker 调整方案） | ✅ 已完成（compact 后续接交付，5 commits，reviewer ✅ 通过+9 follow-up） | `2ec276e` `ccd0eb1` `3b379d9` `884fad2` `a71ef53` |
| 14 | **第四次 master merge：Phase 2.6 FU-15 B 6 commits + V1.6 quartz 整合 21 commits 自动合并** | ✅ 已完成 | merge `cf7b64c` |
| 15 | **FU-25/23/11 三连发清理（FU-15 B 后续整改）+ 第五次 master merge** | ✅ 已完成 | impl `9ced282` + merge `3da0e63` |

---

## 累计 31+ commits + handover 多次续接更新（origin/claude/crazy-joliot-0e2a1f）

```
a71ef53 chore(cleanup): 删除 H2 hack 残留（FU-15 B C5 收尾）  ← Phase 2.6 FU-15 B
884fad2 refactor(application-lead-e2e): lead-e2e profile datasource 切到真 MySQL（FU-15 B C4）
3b379d9 refactor(application-flowable-e2e): flowable-e2e profile datasource 切到真 MySQL（FU-15 B C3）
ccd0eb1 refactor(application-test): default profile datasource 切到真 MySQL（FU-15 B C2）
2ec276e chore(test-mysql): 归档 onepl_test_bootstrap 初始化脚本 + data.sql INSERT IGNORE（FU-15 B C1）
28b1bbc fix(lead-compensation): 实现孤儿 lead 补偿机制（FU-14 绿）  ← Phase 2.6 FU-14
5602b52 test(lead-compensation): 红 IT + 编译骨架（FU-14 红）
c63c03a docs(session): handover Phase 2.6 续接锚点（compact 50% 阈值触发存档）
7f14aca fix(workflow-callback): try-catch 兜底（FU-2 绿）  ← Phase 2.5
075fb9a test(workflow-callback-exception): 红 IT 复现异常未兜底（FU-2 红）
2195cd8 fix(workflow-callback): conditionalUpdateStatus 幂等保护（FU-1 绿）
6615740 test(workflow-callback-idempotency): 红 IT 复现重复触发 cust_master 重复创建（FU-1 红）
4efde7e fix(workflow-callback): 删除 dead code handleWorkflowCallback（FU-6 主修）
e033d7b refactor(test): LeadApprovedCreatesCustomerMasterIT 切真事件路径（FU-6 准备）
aab052f fix(test-config): schema.sql TINYINT(1) → TINYINT + 去反引号 is_read（FU-9）
b495c12 docs(session): handover 续接更新 — Phase 2 (b) 完成 + 4 项 reviewer nitpick + 本期最终交付清单
a6b2290 feat(option-b-phase2-b): 补 lead_approve_v1 真 BPMN E2E IT（APPROVED+REJECTED）  ← Phase 2 (b)
293ff04 docs(session): handover 续接更新 — Phase 2 (c) 完成 + 6 项 reviewer follow-up
c29046a fix(workflow-callback): 区分 APPROVED/REJECTED 分支（Phase 2 (c)）
1bd5301 test(workflow-callback-reject): 红 IT 复现 REJECTED 分支被忽略 bug
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

### 决策 11：Phase 2.6 FU-14 — 孤儿 lead 数据补偿机制（commits `5602b52` `28b1bbc`）

**起因**：Phase 2.5 FU-2 给 `WorkflowCallbackListener.onProcessCompleted` 加 try-catch 兜底（避免 Spring `TransactionSynchronizationUtils` ERROR 日志），副作用是 listener 异常被自吞后，`cust_lead.lead_status` 可能停留 `IN_APPROVAL` 但 Flowable 流程已 COMPLETED，形成孤儿数据。reviewer §H-2 列为中等优先级 follow-up（FU-14），本期解决。

**重大调研发现（implementer brief 前）**：
- **`@EnableScheduling` 全仓库未声明** — performance 的 3 个 `@Scheduled` job（DailyKpi / SysControlCleanup / PerfRunTaskCleanup）实际从未跑过，是 dead code！本次必须开启。
- **`biz_process_map` 表无 `outcome` 字段** — outcome 仅活在 `ProcessCompletedEvent` Spring 事件中
- **Flowable history-level=audit 已启用** — `historyService.createHistoricVariableInstanceQuery().variableName("approved")` 可查到 boolean 变量
- **`WorkflowCallbackListener.handleApproved/handleRejected` 是 private** — 跨包补偿 Service 无法直接调用

**采用方案**：方案 A（最小侵入，不动表结构）
- WorkflowApi 加 `getProcessOutcome(processInstanceId): Optional<String>`，用 HistoryService 查 `approved` boolean 变量
- 抽出 `LeadCallbackReconcileService`（public reconcile 方法，listener 与补偿 Service 共用）
- WorkflowCallbackListener 重构为委托 reconciler

**8 项交付**：
1. `WorkflowApi.getProcessOutcome` 接口 + WorkflowFacade 实现（用 HistoryService）
2. `LeadCallbackReconcileService.java` 抽出 reconcileApproved / reconcileRejected 公共方法（@Transactional REQUIRES_NEW，同时支持 listener 主路径 + 补偿路径）
3. `WorkflowCallbackListener` handleApproved/handleRejected 改为简单委托
4. `customer.config.CustomerSchedulingConfig` 新建 (`@EnableScheduling`，全局开启 scheduling)
5. `CustLeadMapper.selectStuckInApproval(cutoff, limit)` + xml
6. `LeadCallbackCompensationService.scanAndCompensate()`（核心方法）+ `scheduledScan()` (`@Scheduled fixedDelayString=300000`，5 分钟一次)
7. 红 IT `LeadCallbackCompensationIT` 3 case：APPROVED 推进 / REJECTED 推进 / RUNNING 跳过
8. TDD 红绿严格分 commit（红 commit 含 IT + 编译骨架 throw UOE，绿 commit 仅业务实现）

**关键设计决策（与 brief 偏离/精化）**：
- **CANCELLED 也视为流程已结束**：`ProcessCompletedListener` 把 `approved=false` 写为 `processStatus=CANCELLED`（不是 COMPLETED！），补偿覆盖 COMPLETED + CANCELLED 双终态
- **嵌套 REQUIRES_NEW**：listener 主路径外层 REQUIRES_NEW + reconcile 内层 REQUIRES_NEW，2 层嵌套 tx，生产 OLTP 频次低（<1k/day）影响可忽略
- **不直接 import `workflow.enums.ProcessStatus`**：跨模块依赖规则，用字符串字面量常量
- **getProcessOutcome 全 try-catch 兜底**：Flowable HistoryService 在测试环境若 ACT_HI_VARINST 表不存在会抛异常，调用方 fail-safe 视为 outcome 未知

**reviewer 综合 review 结论**：✅ 通过（A/B/C/D/E/F/G/H 全过），列 5 项 follow-up（详见末尾 V1.7+ 跟踪段，全不阻塞）

**测试覆盖**：
- 6 模块 surefire 全绿
- bootstrap surefire 42 + failsafe 22（含 LeadCallbackCompensationIT 3 case）全绿
- 注：`@EnableScheduling` 全局开启后，performance 3 job 默认 `enabled=false` 仍 OFF，无副作用

### 决策 12：Phase 2.6 FU-15 B — 用本地 onepl_test_bootstrap MySQL 替代 testcontainers（5 commits `2ec276e` ~ `a71ef53`）

**起因**：FU-15 原方案是 testcontainers 跑真 MySQL 8（消除 H2 LEGACY/MYSQL 与生产 MySQL 的行为差异，FU-9 user_notification 大小写、TINYINT(1) syntax 等长期踩坑）。但本会话开始后探测发现：
- `where docker` 返回找不到文件
- `C:\Program Files\Docker\` 目录不存在
- WSL 未安装
- 无 docker / wsl 进程

**用户选择路径 B**：用本地真 MySQL（独立 onepl_test_bootstrap 库），与 performance/report 模块统一架构（它们已经用 onepl / onepl_test_v103）。

**重大调研发现（implementer brief 前）**：
- **0 个 IT 用 class/method 级 @Transactional + @Rollback**（受限于 @SpringBootTest webEnvironment）
- 11/12 IT 用 @Sql BEFORE_CLASS（含 DELETE + INSERT）每次先清再插，依赖此模式做数据隔离
- bootstrap data.sql 是 IT 专用固定 ID（admin/user001/user002 + 10 PT_RESOURCE 等），**不**等同 seed-v1.sql
- `NON_KEYWORDS=DAY,READE` H2 hack 暗示 schema 含关键字列，docs/schema 已用反引号转义
- testcontainers BOM 1.19.7 已在根 pom，但 docker 不可用导致硬阻塞

**采用方案**：方案 B（用本地 onepl_test_bootstrap 库 + 直连 localhost:3306）
- 不动 IT 代码（@Sql 等保持），仅改 application-*.yml + schema/data 资源
- 三 profile（test / flowable-e2e / lead-e2e）共用同一物理库（DELETE 矩阵防冲突）
- 测试库一次性初始化：8 个 ddl + 15 perf 迁移 + 8 report 迁移 = 59 业务表
- Flowable 81 表（act_/flw_/qrtz_）从 onepl 生产库 mysqldump 复制（绕开 Flowable 7.0.1 schemaUpdate 死循环 bug）
- bootstrap data.sql 改 INSERT IGNORE 兼容多次 ApplicationContext 启动

**5 个 commit 切分**：

| Commit | 内容 |
|---|---|
| C1 `2ec276e` | 归档 init 脚本 + 3 个 data.sql 全部 INSERT INTO → INSERT IGNORE INTO（H2 也兼容）|
| C2 `ccd0eb1` | application-test.yml datasource 切真 MySQL + 修 H2 specific 兼容（CLOB→TEXT × 3 处、CREATE UNIQUE INDEX → 内联 UNIQUE KEY × 8 处、SEED_KS_EMP_* 污染防护）|
| C3 `3b379d9` | application-flowable-e2e.yml 切 + 81 张 Flowable 表从 onepl 复制 + flowable-e2e-data.sql 加 DELETE 兜底 |
| C4 `884fad2` | application-lead-e2e.yml 切 + 加 tinyInt1isBit=false 修 LeadWorkflowE2EIT:155 ClassCastException + lead-e2e-data.sql DELETE 兜底 |
| C5 `a71ef53` | 删除 schema.sql 408 行 + bootstrap/pom.xml 移除 com.h2database:h2 依赖 |

**关键设计决策**：
- **共享库 PT_RESOURCE 冲突 DELETE 矩阵**：三 profile 共用 onepl_test_bootstrap，flowable-e2e 与 lead-e2e 互删对方残留绑定（PT_RESOURCE 100-105 与 LR101-105 URL+METHOD 唯一键冲突）
- **CLOB → TEXT**：H2 CLOB → MySQL TEXT 等价但容量从 1GB 降至 64KB；生产 ddl-customer.sql 实际字段类型待核（FU-24 follow-up）
- **tinyInt1isBit=false**：仅 lead-e2e profile 加，因 LeadWorkflowE2EIT:155 jdbcTemplate.queryForMap 强转 Number；其他 profile 同库不加形成配置漂移（FU-23 follow-up）
- **Flowable 表绕开 schemaUpdate**：Flowable 7.0.1 + 真 MySQL 8 死循环 bug，实测从 onepl 生产库复制 81 表 schema + 13 行 act_ge_property schema.version 元数据可启动；真因待排查（FU-30 follow-up）
- **DDL 偏差发现（FU-29）**：docs/schema/ddl-customer.sql 缺 `touch_task.sla_warning` 列，但 TouchTask.java + TouchTaskMapper.xml 全员使用，是源头 DDL 偏差。本期手工 ALTER TABLE 补齐测试库 + 写到归档脚本步骤 4bis，**未改源 DDL**避免范围扩散

**reviewer 综合 review 结论**：✅ 通过（A/B/G/H/I 全✅ + C/J ✅弱 + D/E/F ⚠️ 可接受），列 9 项 follow-up（FU-22 ~ FU-30，全不阻塞）

**测试覆盖**：
- bootstrap default profile：surefire 42（2 skip）+ failsafe 22（2 skip）= 64 全绿
- flowable-e2e profile（-Dflowable.e2e.enabled=true）：FlowableWorkflowCenterE2ETest 1 case 跑通
- lead-e2e profile（-Dlead.e2e.enabled=true）：LeadWorkflowE2EIT 2 case 跑通
- 双 profile 同时激活：64 case 全绿

**初始化归档**：`docs/superpowers/sql/2026-04-28-onepl-test-bootstrap-init.sql`（149 行，含步骤 1-5 + 4bis）

### 决策 13：第四次 master merge 与 V1.6 quartz 整合并行处理（merge `cf7b64c`，2026-04-28）

**背景**：FU-15 B 5 commits + 1 handover commit 完成后，发现 origin/master 在 acd94db（第三次 merge + FU-14 合并）之后已自演进 21 个 V1.6 quartz 整合 commits（`36ca766` ~ `bc2f74f`）+ 2 个 customer DDL 文档对齐 commits（`6a8f186` `b01ffda`），共 23 commits。

**冲突评估**（merge-tree dry-run）：✅ 自动合并无冲突
- 仅 `bootstrap/src/test/resources/application-test.yml` 双方都改但不同行：master 加 `spring.quartz.enabled=false` + `QuartzAutoConfiguration` exclude，本地切 datasource 到 onepl_test_bootstrap，git ort 策略自动并存
- master 6a8f186 已把 `sla_warning` 加到 `docs/schema/ddl-customer.sql` → **FU-29 已解** ✅

**合并执行**：
1. `git checkout master && git pull --ff-only origin master` → 同步至 b01ffda
2. `git merge --no-ff claude/crazy-joliot-0e2a1f` → 自动合并产生 cf7b64c
3. **测试库 V1.6 quartz schema 同步**：跑 `docs/schema/migrations/2026-04-25-quartz-integration.sql`
   - sys_job_conf 加 `quartz_job_class` + `misfire_policy` 字段
   - sys_job_run_log 加 `scheduled_fire_time` 字段
   - INSERT 3 条 V1.6 业务 Job（JOB_DAILY_KPI_CALC / JOB_SYS_CONTROL_CLEANUP / JOB_PERF_RUN_TASK_CLEANUP）
   - **QRTZ_* 11 表已存在**（FU-15 B C3 mysqldump 时从 onepl 一并复制过来）
   - backup：`docs/superpowers/sql/backup/2026-04-28-pre-v16-quartz-migration-backup.sql`
4. 全量构建 `mvn clean install -DskipTests` → 16 模块 BUILD SUCCESS（48 秒）
5. bootstrap IT 验证 `mvn verify -pl bootstrap` → **surefire 42（2 skip）+ failsafe 22（2 skip）= 64 全绿** ✅
6. push origin master：cf7b64c

**关键日志验证**：
- `JobService.syncJobsOnStartup - Scheduler bean 不可用（测试或禁用 Quartz 场景），跳过启动同步` ← V1.6 Quartz 在测试环境正确禁用
- `RptReadOnlyDataSource initialized url=jdbc:mysql://localhost:3306/onepl_test_bootstrap` ← FU-15 B 真 MySQL 用上了
- `LeadCallbackCompensationService.scanAndCompensate - 无 stuck IN_APPROVAL 线索，跳过` ← FU-14 @Scheduled 5min 巡检正常运行

**风险点**：无。V1.6 quartz 整合在 master 上已经独立测试通过（用 H2 schema.sql），与 FU-15 B 真 MySQL 切换在 application-test.yml 共存（quartz disabled + datasource MySQL），互不干扰。

### 决策 14：FU-25/23/11 三连发清理（FU-15 B 后续整改，commit `9ced282` + merge `3da0e63`，2026-04-29）

**起因**：FU-15 B 5 commits + 第四次 master merge 完成后，按 reviewer 9 项 follow-up 中挑 3 项 M/nitpick 优先级一并清理，让 FU-15 B 算"真正干净"。

**3 处改动**：

1. **FU-25：抽 it-cleanup.sql 独立前置 cleanup**（M 优先级 → ✅）
   - 现状：flowable-e2e-data.sql 顶部 + lead-e2e-data.sql 顶部各自维护"双向 DELETE 矩阵"（PT_RESOURCE 100-105 互删 + WRR/LRR ID 互删），耦合性高
   - 修复：新建 `bootstrap/src/test/resources/it-cleanup.sql` 统一前置 cleanup（DELETE PT_ROLE_RESOURCE WRR%/LRR% + DELETE PT_RESOURCE 100-105/LR001-LR105）
   - `application-flowable-e2e.yml` + `application-lead-e2e.yml` 在 `spring.sql.init.data-locations` 中放在 `data.sql` 之前（第一个加载）
   - 删除两 *-e2e-data.sql 顶部 DELETE 矩阵（共 14 行）

2. **FU-23：取消 tinyInt1isBit=false 配置漂移**（M 优先级 → ✅）
   - 现状：仅 lead-e2e profile url 加 `tinyInt1isBit=false`（因 `LeadWorkflowE2EIT:155` 用 `(Number) created.get("deleted")` cast 强转，MySQL 默认 tinyInt1isBit=true 把 TINYINT(1) 解析为 Boolean → ClassCastException）。default + flowable-e2e 没加，造成 3 profile url 配置漂移
   - 方案 B 采用：改 IT 用 RowMapper 显式 `rs.getInt("deleted")` 替代 cast；`application-lead-e2e.yml` url 移除 `tinyInt1isBit=false`，与其他 profile 一致

3. **FU-11：删 javadoc 已删除方法引用**（nitpick → ✅）
   - 现状：`WorkflowCallbackEventChainBugIT.java:57` javadoc 引用 `handleWorkflowCallback`（FU-6 commit `4efde7e` 早已删除）
   - 修复：改为补充说明"LeadApprovedCreatesCustomerMasterIT 已切到 TxPublisher 真事件路径（与本 IT 对齐）"，保留历史背景

**测试覆盖**：
- default profile：surefire 42（2 skip）+ failsafe 22（2 skip）= **64 case 全绿**
- lead-e2e profile：LeadWorkflowE2EIT 2 case PASS（验证 FU-23 RowMapper 替代 cast 工作正常）
- flowable-e2e profile：FlowableWorkflowCenterE2ETest 1 case PASS（验证 FU-25 it-cleanup.sql 工作正常）

**第五次 master merge**：
- 1 个 commit `9ced282` 合并到 master
- 期间 origin/master 又领先 4 个 commits（customer-v1 偏离度 P0/P1/P1a），与本期 bootstrap test 资源改动**完全不重叠**，merge-tree 自动合并无冲突
- merge commit `3da0e63` push origin/master 成功

**副作用观察（V1.7+ follow-up）**：
- default profile 启动日志含 V1.6 `JobService.syncJobsOnStartup` 异常：`jobKey=DAILY_REPORT/MONTHLY_PERF 同步失败，跳过继续`
- 真因：onepl_test_bootstrap 的 sys_job_conf 历史测试数据 J001/J002 经 V1.6 ALTER 后 `quartz_job_class` 字段填默认空字符串（`NOT NULL DEFAULT ''`），而 V1.6 syncJobsOnStartup 期望非空类全限定名做 Class.forName
- 影响：仅启动日志 ERROR，不阻塞 IT 通过；建议 V1.7+ 跟踪（清掉 J001/J002 或在 it-cleanup.sql 加 DELETE，或给 default profile 跳过 startup sync）

### 决策 11：Phase 2.6 FU-14 — 孤儿 lead 数据补偿机制（commits `5602b52` `28b1bbc`）

（详见上文）

### 决策 10：Phase 2.5 FU-1/2/6/9 鲁棒性补齐（7 commits `aab052f` ~ `7f14aca`）

**起因**：Phase 2 (b) reviewer 列出 4 项中等优先级 nitpick + Phase 2 (c) reviewer 列出 6 项 nitpick，本期一并清理 4 项核心：

**FU-9（commit `aab052f`）— H2 LEGACY schema 修复**：
- 真因诊断（implementer 调研发现）：`schema.sql` 中 `TINYINT(1)` 在 LEGACY 模式 syntax error，三张表（user_notification / sys_job_conf / touch_task）建表失败被 `continue-on-error` 吞掉，后续 `INSERT INTO user_notification` 报 `Table not found` 又被 NotificationService.try-catch 吞掉
- 方案 A 否决（统一 MODE=MYSQL）：MYSQL 模式下 H2 不识别 Flowable 官方 IDENTITY 数据类型，启动挂掉
- 方案 B 采用：`TINYINT(1) → TINYINT`（MySQL 8 内部存储等价）+ 去反引号 `is_read`（is_read 不是保留字，去掉无副作用）
- 修改最小化：6 行 diff，仅动 `bootstrap/src/test/resources/schema.sql`

**FU-6（commits `e033d7b` + `4efde7e`）— 删 dead code handleWorkflowCallback**：
- 调研结论：grep 全仓库无任何 Controller / Service 调用 `handleWorkflowCallback`，仅 `LeadApprovedCreatesCustomerMasterIT` 在用（绕开真实事件路径假绿覆盖）
- 修复方案：先 commit e033d7b 把 IT 切到 TxPublisher 真事件路径（与 EventChainBugIT 同 pattern），再 commit 4efde7e 删除 line 181-208 整个 dead 方法
- WorkflowCallbackListener 精简到只剩 onProcessCompleted + handleApproved + handleRejected

**FU-1（commits `6615740` 红 + `2195cd8` 绿）— conditionalUpdateStatus 幂等保护**：
- 红 IT `WorkflowCallbackIdempotencyBugIT`：双 publishEvent(ProcessCompletedEvent) 模拟重复触发，断言 cust_master 行数=1（cust_master.lead_id 无 UK，旧代码下会得 2 行重复）
- 绿 fix：新增 `CustLeadMapper.conditionalUpdateStatus` 方法（与 `LoanApplyMapper.conditionalUpdateStatus` 完全对齐 @Param 名 + SQL pattern），handleApproved/handleRejected 第一行用 `conditionalUpdateStatus(IN_APPROVAL → APPROVED/REJECTED)`，rowsAffected=0 时 log.warn 早返回不发事件

**FU-2（commits `075fb9a` 红 + `7f14aca` 绿）— 整体 try-catch 异常兜底**：
- **设计创新**：implementer 用 logback ListAppender 抓 Spring `TransactionSynchronizationUtils.invokeAfterCompletion` 的 ERROR 日志做差异断言。原理：AFTER_COMMIT listener 异常 Spring 不让冒泡到调用方，但 Spring 框架会内部 catch + log.error("TransactionSynchronization.afterCompletion threw exception", e)。红 IT 验证 fix 前抓到此 ERROR，fix 后业务自吞 → 此 ERROR 不再出现
- 红 IT `WorkflowCallbackExceptionSwallowBugIT`：`@SpyBean CustLeadMapper.selectById` 抛 RuntimeException 注入故障
- 绿 fix：onProcessCompleted 整体 try { 业务 } catch (Exception e) { log.error 不重抛 }，与 `LoanWorkflowListener.onProcessCompleted` line 73-131 完全对齐

**reviewer 综合 review 结论**：✅ 通过（A/B/C/D/E/F/G/H 全过），列 6 项 nitpick（详见 V1.7+ 跟踪段，全不阻塞）

**测试覆盖**：
- 6 模块 surefire 1502 case 全绿（无回归）
- bootstrap failsafe 默认 profile 19 IT PASS（含新增 Idempotency + ExceptionSwallow 2 个）
- bootstrap failsafe lead-e2e profile：LeadWorkflowE2EIT 2 case PASS，user_notification 不再报错

### 决策 9：Phase 2 (b) 真 BPMN E2E IT（commit `a6b2290`）

**目标**：补真 BPMN 端到端 IT 防回归 lead 审批 APPROVED + REJECTED 双分支，CI 默认跳过 + 本地 `-Dlead.e2e.enabled=true` 触发。

**5 个新建文件**：
1. `workflow-center/src/main/resources/bpmn/lead_approve_v1.bpmn20.xml`（41 行）— 2 节点 BPMN：startEvent → branch_manager_approve userTask → exclusiveGateway → 2 endEvent；processDefinitionKey 与 LeadService.PROCESS_DEFINITION_KEY 严格对齐
2. `bootstrap/src/test/resources/application-lead-e2e.yml`（56 行）— lead-e2e profile，独立 H2 内存库 + Flowable embedded
3. `bootstrap/src/test/resources/lead-e2e-data.sql`（65 行）— PT_USER + 角色 + wf_node_candidate_conf + EXT_ORG_INFO，候选组键格式严格匹配 ROLE:R_LEAD_BRANCH_MGR
4. `bootstrap/src/test/java/com/bank/branch/platform/it/config/LeadE2ETestConfig.java`（70 行）— 独立 @Profile("lead-e2e") + MapperScan 加 customer.mapper（不能复用 FlowableE2ETestConfig：profile 不同 + customer 模块 mapper 缺失）
5. `bootstrap/src/test/java/com/bank/branch/platform/it/LeadWorkflowE2EIT.java`（287 行）— 2 case 端到端覆盖 createDraft → submit → claim → approve/reject → 断言 cust_lead 状态 + cust_master count

**关键链路验证**：
- APPROVED：approve task → AFTER_COMMIT 链路同步执行（Phase 2 (c) 修复后 WorkflowCallbackListener 用 REQUIRES_NEW 显式新事务）→ LeadApprovedListener → CustMasterAssemblerService → cust_master 创建
- REJECTED：reject task → outcome=REJECTED → WorkflowCallbackListener.handleRejected（Phase 2 (c) 引入）→ 仅更新 lead.status + publish LeadRejectedEvent，不创 cust_master

**reviewer 综合 review 结论**：✅ 通过（A/B/C/D/E/F/G/H/I/J 全过），列 4 个 nitpick follow-up（详见 V1.7+ 跟踪段）。

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
| **LeadWorkflowE2EIT**（CI 默认跳过） | **2** | 真 BPMN E2E：APPROVED 分支 cust_master 创建 / REJECTED 分支 cust_master 不创建（Phase 2 (b)）|
| **WorkflowCallbackIdempotencyBugIT** | **1** | 红 IT 防回归 listener 缺幂等保护致 cust_master 重复创建（Phase 2.5 FU-1）|
| **WorkflowCallbackExceptionSwallowBugIT** | **1** | 红 IT 防回归 listener 异常未自吞致 Spring 框架 ERROR 日志（Phase 2.5 FU-2）|
| **LeadCallbackCompensationIT** | **3** | 补偿 Service 巡检 stuck IN_APPROVAL：APPROVED 推进 / REJECTED 推进 / RUNNING 跳过（Phase 2.6 FU-14）|
| FlowableWorkflowCenterE2ETest | ? | Flowable E2E（@EnabledIfSystemProperty 保护） |
| FlowableWorkflowCenterRealEnvTest | ? | 真 MySQL Flowable E2E |
| PerformanceMetricApiBridgeTest | 10 | bridge 单元测试 |

bootstrap 全模块（含 surefire + failsafe）：**42 + 11 = 53 case PASS / 3 SKIP**。

6 模块全回归：**1502 case 全绿**（auth 154 + portal 236 + customer 322 + bizapp 168 + performance 580 + bootstrap 42）。

---

## 待办（Phase 2 续接锚点）

### Phase 2 (c) — ✅ 已完成（compact 后续接交付）

commits `1bd5301` `c29046a` 已 push。详见上文「决策 8」。预估 30-45 分钟，实际约 35 分钟（含 reviewer review）。reviewer ⚠️ 通过 + 6 个 follow-up（不阻塞，详见末尾 V1.7+ 跟踪段）。

### Phase 2 (b) — ✅ 已完成（compact 后续接交付，commit `a6b2290`）

详见上文「决策 9」。预估 1.5-2h，实际约 50 分钟（implementer + reviewer）。reviewer ✅ 通过 + 4 个 nitpick（不阻塞，详见末尾 V1.7+ 跟踪段）。

### Phase 2.6 FU-14 — ✅ 已完成（compact 后续接交付，commits `5602b52` `28b1bbc`）

详见上文「决策 11」。预估 2-3h，实际约 1.5h（含 2 次轻调研 + implementer + reviewer）。reviewer ✅ 通过 + 5 项 follow-up（不阻塞，详见末尾 V1.7+ 跟踪段）。

### Phase 2.6 FU-15 B — ✅ 已完成（compact 后续接交付，commits `2ec276e` ~ `a71ef53`）

详见上文「决策 12」。预估 4-6h，实际约 3.5h（含 2 次轻调研 + Docker 阻塞决策切 B 方案 + opus implementer 5 commits + opus reviewer ✅）。reviewer ✅ 通过 + 9 项 follow-up（FU-22 ~ FU-30，全不阻塞，详见末尾 V1.7+ 跟踪段）。

**为什么从 testcontainers 改成 B 方案**：本会话开始后探测发现 Windows 本地无 Docker（where docker 找不到 + Docker Desktop 未装 + WSL 未装），用户选 B 方案（用本地 onepl_test_bootstrap 库 + 直连 localhost:3306）。与 performance/report 模块统一架构，无需 Docker 即可达到 FU-15 根本目标"测试与生产 DDL 完全一致"。

#### 已废弃的原 Phase 2 (b) 待办说明（保留作历史参考）

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

### Phase 2 (b) reviewer 4 个 nitpick（V1.7+ 跟踪，不阻塞合并）

| # | 项 | 优先级 | 说明 |
|---|---|---|---|
| FU-7 | `FlowableE2ETestConfig` 与 `LeadE2ETestConfig` 90% 重复 | 低 | 仅 MapperScan 多 1 项 + Profile 名不一样；可抽公共 abstract base class |
| FU-8 | `LeadWorkflowE2EIT` REJECTED case 没像 APPROVED 那样校验 preCount=0 | 低 | 对称性微优化，1 行可补 |
| ~~FU-9~~ | **H2 LEGACY mode 修复** | ~~中~~ | **✅ Phase 2.5 已修（commit `aab052f`）**：真因不是大小写而是 `TINYINT(1)` syntax，已改 TINYINT + 去反引号 |
| FU-10 | `lead_approve_v1.bpmn` 长期不存在但 `LeadService.PROCESS_DEFINITION_KEY` 早就引用 | 已修 | Phase 2 (b) commit `a6b2290` 顺手补上，记录作为历史技术债已清零 |

### Phase 2.5 reviewer 6 个 nitpick（V1.7+ 跟踪，不阻塞合并）

| # | 项 | 优先级 | 来源 |
|---|---|---|---|
| ~~FU-11~~ | **`WorkflowCallbackEventChainBugIT.java:57` javadoc 仍引用已删除的 `handleWorkflowCallback` 方法** | ~~nitpick~~ | **✅ 2026-04-29 已修（commit `9ced282`）**：改为补充说明 LeadApprovedCreatesCustomerMasterIT 已切到 TxPublisher 真事件路径 |
| FU-12 | `conditionalUpdateStatus` 的 `updatedBy` 硬编码 `"SYSTEM"`（与 LoanWorkflowListener 一致），未来可通过 ProcessCompletedEvent 携带 approverEmpId 提升审计精度 | 低 | Phase 2.5 reviewer §C.nit-1 |
| FU-13 | `WorkflowCallbackListener.handleApproved/handleRejected` 状态机假设（fromStatus=IN_APPROVAL）应在 javadoc 中显式说明 | 低 | Phase 2.5 reviewer §H-1 |
| ~~FU-14~~ | **listener 异常兜底后 lead.status 可能停留在 IN_APPROVAL 形成孤儿数据，需要补偿机制（定时巡检 + 重发 ProcessCompletedEvent）** | ~~中~~ | **✅ Phase 2.6 已完成（commits `5602b52` `28b1bbc`）** |
| **FU-15** | **测试 schema 与生产 DDL 长期不一致**（本次仅治 H2 LEGACY 标症），长期方向：testcontainers 真 MySQL | **中** | Phase 2.5 reviewer §H-3，Phase 2.6 待续接 |
| FU-16 | `audit-report.md` 自动生成时间戳每跑一次 IT 就变，建议改为不带时间戳或用 `.gitignore` 忽略 | nitpick | 工作树多次出现 unstaged 状态 |

**Phase 2.5 reviewer 总评**：implementer 的 FU-9 真因诊断（TINYINT(1) syntax）+ FU-2 logback ListAppender 抓 Spring 框架 ERROR 日志的设计巧妙，是 Spring AFTER_COMMIT listener 异常断言的标准做法之一。TDD 红绿严格分离（红 commit 仅 IT、绿 commit 仅业务），与 LoanWorkflowListener pattern 100% 对齐。可合并。

### Phase 2.6 FU-14 reviewer 5 项 follow-up（V1.7+ 跟踪，不阻塞合并）

| # | 项 | 优先级 | 来源 |
|---|---|---|---|
| FU-17 | `ProcessCompletedEvent` record 应从 `workflow.listener` 提到 `workflow.api.event` 子包，让 customer 模块只依赖 api 包。当前 `WorkflowCallbackListener.java:9` 引用 listener 内部 record，跨模块边界泄漏（历史遗留，非 Phase 2.6 引入） | M | Phase 2.6 reviewer §D |
| ~~FU-18~~ | ~~testcontainers 接入后（FU-15），新增 1 条 IT 验证 H2/MySQL 下 CANCELLED outcome 语义一致性~~ | ~~M~~ | **改写为 FU-31**：FU-15 B 实际不用 testcontainers，但 default profile 已切真 MySQL，CANCELLED IT 仍可补 |
| FU-19 | listener 主路径嵌套 REQUIRES_NEW（外层 tx + 内层 reconcile tx）压测下连接池水位监控，必要时把 listener 改成无 tx + reconcile 单层 tx | L | Phase 2.6 reviewer §E/§H |
| FU-20 | `CompensationService.COMPENSATION_REJECT_REASON = "由补偿任务推进，原因不明"` 字面量考虑挪到 i18n 或 enum | L | Phase 2.6 reviewer §A |
| FU-21 | handover doc 推送后回写 FU-14 完成节点 + commit hash + 测试数据 | L | Phase 2.6 reviewer §G |

**Phase 2.6 FU-14 reviewer 总评**：✅ 通过。红绿分离严格、跨模块依赖合规、边界条件覆盖到位（CANCELLED + RUNNING + outcome empty + Flowable 异常兜底全有），IT 断言强度足够（不只 status 还查 cust_master 全链路），幂等保护双层（conditionalUpdate + REQUIRES_NEW）。无阻塞问题。

### Phase 2.6 FU-15 B reviewer 9 项 follow-up（V1.7+ 跟踪，不阻塞合并）

| # | 项 | 优先级 | 来源 |
|---|---|---|---|
| FU-22 | CI 加"启动后断言关键种子行数"健康检查（防 INSERT IGNORE 静默吞错）| L | reviewer 维度 B |
| ~~FU-23~~ | **tinyInt1isBit 配置漂移：3 profile 一致化或改 IT 用 `getObject(..)` 而非 `(Number)` cast** | ~~M~~ | **✅ 2026-04-29 已修（commit `9ced282`）**：LeadWorkflowE2EIT:155 改用 RowMapper Lambda 显式 rs.getInt + application-lead-e2e.yml 移除 url tinyInt1isBit=false 一致化 |
| FU-24 | 核实生产 DDL 中 `support_request.other_demand` / `cust_lead.customer_desc` / `cust_master.customer_desc` 是否 TEXT/MEDIUMTEXT/LONGTEXT，对齐测试库容量上限（H2 CLOB 默认 1GB → MySQL TEXT 64KB）| M | reviewer 维度 D |
| ~~FU-25~~ | **抽 `it-cleanup.sql` 独立前置 cleanup，三 profile 引用，去除互删耦合（共享库 PT_RESOURCE 100-105 vs LR101-105 双向 DELETE 矩阵难维护）** | ~~M~~ | **✅ 2026-04-29 已修（commit `9ced282`）**：新建 it-cleanup.sql + application-flowable-e2e.yml/application-lead-e2e.yml data-locations 第一个加载 + 删两 *-e2e-data.sql 顶部双向 DELETE 矩阵 |
| FU-26 | 归档脚本 §5：(a) bash 加 PowerShell 等价命令 (b) 显式声明禁止 dump act_hi_*/act_ru_* 实例数据 | L | reviewer 维度 F |
| FU-27 | ~~合并 master 前跑全量 mvn verify 确认 6 模块 1502 case 不受影响~~ ✅ 已执行（exit 0）| - | reviewer 维度 H |
| FU-28 | ~~handover doc 更新：FU-15 ⏸️→✅，回写步骤 4bis/5 关键发现~~ ✅ 已执行 | - | reviewer 维度 J |
| ~~FU-29~~ | **sla_warning 修源 DDL：`docs/schema/ddl-customer.sql` 补字段 + 提供 V1_x 迁移脚本** | ~~M~~ | **✅ 2026-04-28 master `6a8f186` commit 自动消解**（与本期并行，customer-v1 P0 偏离度修复带做）|
| FU-30 | 排查 Flowable 7.0.1 schemaUpdate 死循环真因（已 exclude Druid 仍报，疑似非 Druid 问题） | L | reviewer 维度 F |
| FU-31 | 补 1 条真 MySQL CANCELLED outcome IT（替代原 FU-18，因 default profile 已切真 MySQL 不再需要等 testcontainers）| L | FU-18 改写 |
| FU-32 | bootstrap default profile 启动日志含 V1.6 `JobService.syncJobsOnStartup` ERROR：jobKey=DAILY_REPORT/MONTHLY_PERF 同步失败。真因：sys_job_conf 历史测试数据 J001/J002 的 quartz_job_class 字段经 V1.6 ALTER TABLE 后填默认空字符串。建议在 it-cleanup.sql 加 DELETE 或给 default profile 跳过 startup sync（不阻塞 IT，仅日志 ERROR）| L | 2026-04-29 FU-25/23/11 验证发现 |

**Phase 2.6 FU-15 B reviewer 总评**：✅ 通过。5 commit 切片清晰（INSERT IGNORE 准备 → 3 profile 序列切换 → 清理），每个 commit 末附完整测试证据；归档脚本写得详细（含 4bis sla_warning 补齐 + 步骤 5 Flowable 81 表 mysqldump 复制），具备可重复执行性。CLOB→TEXT、CREATE UNIQUE INDEX 内联化、tinyInt1isBit=false 是必要的 MySQL 兼容修复。FU-15 B 完整达到 FU-15 根本目标"测试与生产 DDL 一致"，与 performance/report 模块统一架构。无阻塞，可合并 master。

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

### 快速入口（compact 后续接专用）

**当前状态（2026-04-28）**：FU-14 已完成（commits `5602b52` `28b1bbc`），handover doc 第二次 compact 阈值触发归档。下次会话目标是 **Phase 2.6 FU-15（testcontainers 真 MySQL）+ 第四次 master merge**。

compact 前已完成的全部前置工作：
- ✅ 新偏好已记录到 memory：`feedback_compact_threshold.md`（上下文 50% 阈值自动 compact）
- ✅ 本机 onepl MySQL 已幂等重跑 docs/schema 最新 DDL + SEED（PT_RESOURCE 298 / 286 active / 12 deleted；PT_USER 15；PT_ROLE 12；PT_ROLE_RESOURCE 567）
- ✅ 数据库备份归档：`docs/superpowers/sql/backup/2026-04-27-pre-fu1415-import-backup.sql`（7553 行）
- ✅ 本地 Redis 服务已启动，MySQL 已启动（root/123456 @ localhost:3306/onepl）
- ✅ Phase 2.6 FU-14 已交付（@Scheduled 5min 补偿巡检 + reconcile 抽取 + WorkflowApi.getProcessOutcome）

**Phase 2.6 续接清单（已全部完成）**：

#### ~~FU-14（孤儿数据补偿机制）~~ — ✅ 已完成

详见上文「决策 11」。commits `5602b52` `28b1bbc` 已 push。reviewer ✅ 通过 + 5 项 follow-up（详见末尾 V1.7+ 跟踪段）。

#### ~~FU-15 B（用本地 onepl_test_bootstrap MySQL 替代 testcontainers）~~ — ✅ 已完成

详见上文「决策 12」。commits `2ec276e` `ccd0eb1` `3b379d9` `884fad2` `a71ef53` 已 push。reviewer ✅ 通过 + 9 项 follow-up（详见末尾 V1.7+ 跟踪段）。

**关键调整**：原 FU-15 计划用 testcontainers 真 MySQL，本会话开始后探测发现本地无 Docker（where docker 找不到 + Docker Desktop 未装 + WSL 未装），用户选 B 方案（用本地 onepl_test_bootstrap 库 + 直连 localhost:3306）。与 performance/report 模块统一架构，无需 Docker 即可达到 FU-15 根本目标"测试与生产 DDL 完全一致"。

#### Phase 2.6 完成 → 第四次 master merge

Phase 2.6 = FU-14 + FU-15 B 共 7 commits 已全部交付，等待第四次 master merge（与第三次 0af1635 之后累计 7 commits 上 master）。

mvn 全量回归验证（FU-27）：`mvn clean install -DskipTests + mvn verify -pl bootstrap` 已跑过 exit code 0（含 LeadCallbackCompensationIT 全 3 case 跑通），bootstrap 64 case 全绿。

### 续接命令模板（如需再次启动会话）

如果第四次 master merge 因 compact 阈值触发未在本会话完成，下次会话用户预期发出：
> "读 docs/superpowers/sessions/2026-04-25-b-option-a-bc-handover.md，执行第四次 master merge"

主代理应：
1. Read 本 handover doc（确认 FU-14 + FU-15 B 全部 ✅）
2. 验证 git status clean + 7 commits ahead of origin/master
3. `git checkout master && git merge --no-ff claude/crazy-joliot-0e2a1f -m "..."` 第四次合并
4. push master + push branch
5. handover doc 末尾元数据更新（HEAD / commit count）

如想继续 V1.7+ follow-up，可挑：FU-7/8/11/12/13/16/17/19/20/22/23/24/25/26/29/30/31，或 Option B.3 / B.4。

### 历史选项（仍可选）

1. **Option B.3 / B.4**：report SqlProbe 异步导出 / perf→report 跨模块只读
2. **Option C**：100+60 条 curl 回归（需 docker-compose 完整环境）
3. **nitpick 清理**：FU-7/FU-8/FU-11/FU-12/FU-13/FU-16

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

**文档生成时间**：2026-04-25（compact 后续接更新 2026-04-27 多次 + 2026-04-28 Phase 2.6 FU-14/FU-15 B 完成 + 第四次 master merge + 2026-04-29 FU-25/23/11 三连发 + 第五次 master merge）
**对应 git HEAD**：`3da0e63`（master 第五次合并节点，含 FU-25/23/11 三连发清理 commit `9ced282`）→ origin/master 已推送
**会话累计 commit（本期分量）**：31 commits + handover 多次续接更新 + 4/5 次 master merge commits（共约 35 commits）
**平台累计 commit（含历史）**：241（上期累计） + 32（本期，含 FU-25/23/11） = **273 commits**（不计 master merge commits 与 V1.6 quartz 21 个 master 自演进 commits）

**本期最终交付清单**（已合并/即将合并 master）：
- Option A 启动级 IT（5 case）
- V1.1 PT_RESOURCE 整改（清零 audit 类型 A 26 + 类型 B 11）
- Option B.1 portal workspace IT（5 case）
- Option B.2-a customer 事件链 IT（1 case）
- P0 fallbackExecution 修复（5 listener 审计，3 个修复）
- Phase 2 (c) WorkflowCallbackListener APPROVED/REJECTED 分发（含 LeadRejectedEvent 新增）
- Phase 2 (b) lead_approve_v1.bpmn20.xml + LeadWorkflowE2EIT 真 BPMN E2E（CI 默认跳过，本地 -D 触发）
- **Phase 2.5 鲁棒性补齐**：H2 LEGACY schema TINYINT(1) 修复 + handleWorkflowCallback dead code 删除 + conditionalUpdateStatus 幂等保护 + 整体 try-catch 异常兜底
- **Phase 2.6 FU-14 孤儿数据补偿**：@EnableScheduling 全局开启 + LeadCallbackCompensationService 5min 巡检 + LeadCallbackReconcileService 抽取 + WorkflowApi.getProcessOutcome 新增 + 3 case 红 IT
- **Phase 2.6 FU-15 B 测试库切真 MySQL**：onepl_test_bootstrap 独立测试库（59 表）+ bootstrap 三 profile datasource 切换 + H2 hack 清零（CLOB→TEXT × 3 / CREATE UNIQUE INDEX 内联化 × 8 / schema.sql 408 行删除 / pom.xml 移除 h2 依赖）+ Flowable 81 表 mysqldump 复制绕开 schemaUpdate 死循环
- 测试基础设施：UTF-8 全局编码 + stale jar 文档化 + LeadE2ETestConfig + onepl_test_bootstrap 库初始化归档（149 行 sql 脚本，含 4bis sla_warning 补齐 + 步骤 5 Flowable 表 mysqldump 命令）
- 数据库对齐：onepl PT_RESOURCE 122→272→298 + docs/schema mysqldump 同步 + 新增 onepl_test_bootstrap 测试库

**WorkflowCallbackListener 演进总结**（本期重点路径）：
1. 原始：fallbackExecution=true（P0 链路 bug，cust_master 永不创建）
2. P0 修复（commit `7a90e47`）：去 fallbackExecution + 加 @Transactional REQUIRES_NEW
3. Phase 2 (c)（`c29046a`）：按 outcome 分发 APPROVED/REJECTED
4. Phase 2.5 FU-1（`2195cd8`）：conditionalUpdateStatus 幂等保护
5. Phase 2.5 FU-2（`7f14aca`）：整体 try-catch 异常兜底
6. Phase 2.5 FU-6（`4efde7e`）：删除 dead code handleWorkflowCallback
7. Phase 2.6 FU-14（`28b1bbc`）：handleApproved/handleRejected 抽到 LeadCallbackReconcileService + 补偿 Service 共享 reconcile 逻辑
8. **当前状态**：与 bizapp.LoanWorkflowListener pattern 完全对齐 ✅，并形成补偿兜底闭环
