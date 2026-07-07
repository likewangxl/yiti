# 多模块协同开发会话状态记录（2026-04-25）

> **用途**：本次会话经历 performance V1.5 → report V1.0 全量交付 → 3 模块健康检查，context 较大，本文档记录关键状态以便 `/compact` 后无缝续接。

## 会话主线

用户原始指令："**依次进行 1、2、3**"：
1. **方向 1**：启动 `report-analytics-center` 开发 — ✅ 已完成（V1.0 完整交付）
2. **方向 2**：3 个被误标"骨架"模块（customer/bizapp/portal）健康检查 — ✅ 检查完毕，⏳ 修复待执行
3. **方向 3**：跨模块集成测试 / 端到端验证 — ⏳ 未开始

---

## 当前 git 状态（compact 后核验）

- 仓库：`D:\Project\oneplate`
- 分支：`master`
- HEAD：`97b31df`（report V1.0 收口补丁，已 push 到 origin/master）
- 工作树：clean（仅 `.claire/` `.clone/` untracked，已被 .gitignore 忽略）

### 累计交付状态

| 模块 | 状态 | 主要 sha |
|---|---|---|
| performance V1.0-V1.5 | ✅ 已交付（225 commit） | 历史已 push |
| report V1.0 | ✅ 已交付（67 commit） | `de7914f`..`97b31df` |
| 根 CLAUDE.md 状态修正 | ✅ 已交付 | `8f281e0` |

### 平台 9 模块状态总览

```
common ✅ → auth ✅ → governance ✅ → workflow ✅
                                       ↑
portal ✅ + customer ✅ + bizapp ✅ + performance ✅ V1.5
                                       ↑
report ✅ V1.0（本次刚交付）
                                       ↑
bootstrap ✅
```

---

## 3 模块健康检查结果汇总

### bizapp ✅ 健康（168 tests）

- 5 *Api / 21 REST / 2 表 / 21 错误码 / 0 UOE
- **轻债**：错误码缺 5 条（BIZ-40306 / BIZ-42203 / BIZ-42204 / BIZ-42302 / BIZ-50003），实际通过近义码（INVALID_DICT_VALUE / EXPOSURE_EXCEEDS_CREDIT / NOT_DRAFT_STATUS）语义等价覆盖
- TODO 3 处 V2 标记：
  - `LoanService.java:341`（V2 集成 workflow 历史查询补 processMap/approvalLogs）
  - `LoanDetailResp.java:16,69`（V2 通过 ClaimApi 补 ownerEmpId）
- CLAUDE.md 无"V1.0 已知技术债"章节

### customer ⚠️ 需小修（321 tests）

- 5 *Api / 45 REST / 8 表 / **错误码 26 vs 设计 35** / 0 UOE
- **重大偏差 1**：错误码缺 9 条（403 系列 7 条 + 422 系列 8 条 + 部分 500）
- **重大偏差 2**：**零 ArchUnit 守护**（无 `arch/` 子目录，与 perf/report 6 大守护对比明显）
- TODO 6 处散落：
  - `LeadImportPreviewResp.java` ×3 + `LeadImportService.java:122`（行级校验逻辑简化未实现）
  - `CustomerHistoryController.java:33` / `CustomerCrossOrgHistoryVO.java:19` / `CustomerService.java:250`（等 `@AuditLog specialCategory=CROSS_ORG` 支持）
  - `WorkflowCallbackListener.java:70`（未区分 APPROVED/REJECTED）
  - `LeadDeletedListener.java:40`（线索独立标签关联清理预留）
  - `TouchTaskMapper.xml:220`（H2/MySQL 函数方言）
- CLAUDE.md 无"V1.0 已知技术债"章节

### portal ⚠️ 需小修（236 tests）— **含 P1 关键债**

- 5 *Api + 3 Adapter / 8 Controller / 5 表 / 17 错误码 / 0 UOE / 0 TODO
- **🔴 P1 关键债**：`portal/adapter/MetricApi.java` 是 V1 占位（@Deprecated），但 performance 已交付正式 MetricApi。当前 MetricAdapter 注入的是 portal 自家占位 → **工作台指标卡永远走降级分支返回空**
- 涉及文件：
  - `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricApi.java`（占位接口，需删除）
  - `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricAdapter.java`（需切到 `com.bank.branch.platform.performance.api.MetricApi`）
  - `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/MetricCardDTO.java` / `MetricTrendPoint.java` / `PortalTodoItem.java`（3 个 @Deprecated DTO）
- **次要债**：
  - `WorkflowQueryAdapter:82-83` overdueInfo/bizDetailUrl 硬编码 null
  - `portal-content-center/CLAUDE.md` "当前实现说明" 描述与实际不符（说"workflow 仍走 Adapter 降级"，实际已接入正式 WorkflowQueryApi）

---

## 用户决策：选项 A（compact 后第一件事）

**单 patch 集中修复（1-2 commit / ~1 小时）**：

### A.1 修复 portal P1 关键债（必做）

1. **Read** `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricAdapter.java`
2. **替换 import**：`com.bank.branch.platform.portal.adapter.MetricApi` → `com.bank.branch.platform.performance.api.MetricApi`
3. **DTO 类型替换**：评估 `portal.adapter.dto.MetricCardDTO` 与 `performance.api.dto.MetricCardDTO` 字段差异
   - 如字段一致：直接 import 切换 + 删除 portal 占位 DTO
   - 如字段有差异：保留 portal 占位 DTO 作为视图模型 + 新增 Converter（performance MetricCardDTO → portal MetricCardDTO）
4. **Grep 找所有 portal.adapter.MetricApi 引用**：MetricAdapter / PortalServiceImpl / convert/PortalAssembler 等
5. **portal pom.xml 是否依赖 performance**：如否需追加（注意是否引入循环依赖风险）
6. **删除占位**：
   - `portal/adapter/MetricApi.java`
   - `portal/adapter/dto/MetricCardDTO.java`（如可被 performance DTO 替代）
   - `portal/adapter/dto/MetricTrendPoint.java`
   - `portal/adapter/dto/PortalTodoItem.java`（如确实未被使用）
7. **跑测试**：`mvn -pl portal-content-center test` 应 ≥ 230 tests 全绿（删除占位 DTO 测试可能 -2~5）
8. **跨模块编译**：`mvn -pl portal-content-center,bootstrap verify -am` BUILD SUCCESS

### A.2 portal CLAUDE.md 描述更新（必做）

- 修改 `portal-content-center/CLAUDE.md` "当前实现说明"段
- 删除"workflow 仍走 Adapter 降级"过时描述
- 删除"V1 占位 MetricApi"段
- 追加"V1.0 已知技术债"章节，登记 P2 次要债（WorkflowQueryAdapter overdueInfo/bizDetailUrl 等）

### A.3 customer CLAUDE.md 添加技术债章节（必做，仅登记不修复）

新增"V1.0 已知技术债"章节，登记 7 项：

| # | 标题 | 优先级 | 来源 |
|---|---|---|---|
| 1 | 错误码 26 vs 设计 35，缺 403 系列 7 条 + 422 系列 8 条 + 部分 500 | 中 | 健康检查 |
| 2 | 零 ArchUnit 守护（无 arch/ 子目录） | 中 | 健康检查 |
| 3 | 线索导入行级校验简化未实现（4 处 TODO） | 低 | 代码 TODO |
| 4 | CROSS_ORG 审计待 @AuditLog 升级（3 处 TODO） | 低 | 代码 TODO |
| 5 | WorkflowCallbackListener 未区分 APPROVED/REJECTED | 低 | 代码 TODO |
| 6 | LeadDeletedListener 线索独立标签清理预留 | 低 | 代码 TODO |
| 7 | TouchTaskMapper.xml H2/MySQL 函数方言 TODO | 低 | 代码 TODO |

### A.4 bizapp CLAUDE.md 添加技术债章节（必做，仅登记不修复）

新增"V1.0 已知技术债"章节，登记 4 项：

| # | 标题 | 优先级 | 来源 |
|---|---|---|---|
| 1 | 错误码缺 5 条（BIZ-40306 / 42203 / 42204 / 42302 / 50003），近义码语义等价覆盖 | 低 | 健康检查 |
| 2 | LoanService.java:341 V2 集成 workflow 历史查询补 processMap/approvalLogs | 低 | 代码 TODO |
| 3 | LoanDetailResp.java:16 V2 通过 ClaimApi 补 ownerEmpId | 低 | 代码 TODO |
| 4 | LoanDetailResp.java:69 V2 通过 ClaimApi 补 claimedTime | 低 | 代码 TODO |

### A.5 commit + push

预期 commit message（HEREDOC 格式 + Co-Authored-By 尾行）：

```
fix(portal-v1): MetricAdapter 切到 performance 正式 MetricApi（P1 关键债清零）

- 替换 portal/adapter/MetricApi.java 占位为 com.bank.branch.platform.performance.api.MetricApi
- 删除 3 个 @Deprecated DTO 占位（MetricCardDTO/MetricTrendPoint/PortalTodoItem）
- MetricAdapter 注入正式 API，工作台指标卡不再永远走降级分支
- portal CLAUDE.md "当前实现说明" 同步更新

```

```
docs(modules): customer/bizapp CLAUDE.md 登记 V1.0 已知技术债

- customer-marketing-center 登记 7 项（错误码缺 9 / 0 ArchUnit / 6 TODO）
- business-application-center 登记 4 项（错误码缺 5 近义码覆盖 / 3 V2 TODO）
- 治理类问题，待 V1.1 整改

```

---

## 风险点（compact 后注意）

1. **portal P1 修复的 DTO 字段差异**：performance.MetricCardDTO 包含 V1.4/V1.5 引入的 mom/yoy/previousValue 字段；portal.adapter.dto.MetricCardDTO 字段集可能更小。需对比后决定：
   - 直接复用 performance DTO（最简，但 portal 视图层暴露多余字段）
   - 保留 portal DTO + 新增 Converter（更纯净）
2. **portal pom.xml 循环依赖检查**：portal 是通用域，performance 依赖 customer，customer 不依赖 portal，所以 portal 依赖 performance 不构成循环
3. **PortalTodoItem.java 是否删除**：需 Grep 确认是否仍被 PortalService 使用（plan 调研报告中"3 个 @Deprecated DTO"包含此项，但实际可能已废）
4. **测试基线变化**：删除占位测试可能让 portal surefire 从 236 略降，预期不超 ±5 tests

---

## compact 后续接执行步骤

1. **第一步**：Read 本文档
2. **第二步**：派发 polish patch agent（高配模型），任务清单按 A.1-A.5 执行
3. **第三步**：派发 polish review agent（高配模型），核验：
   - portal 工作台指标卡真实调用 performance MetricApi
   - portal/customer/bizapp CLAUDE.md 技术债章节完整登记
   - 测试全绿（≥ 700 tests 跨 4 模块）
4. **第四步**：push 到 origin/master
5. **第五步**：进入方向 3 跨模块集成测试规划

---

## 关键文件路径（compact 后参考）

### Plan / 设计文档

- 报表 plan：`D:\Project\oneplate\docs\superpowers\plans\2026-04-25-report-analytics-center-v1.0-plan.md`（3941 行）
- performance V1.5 plan：`docs/superpowers/plans/2026-04-24-performance-v1.5-iteration-plan.md`（1662 行）
- 9 份 report 设计文档：`docs/modules/report-analytics-center/01-09*.md`

### 模块 CLAUDE.md（A 方案待修改）

- `D:\Project\oneplate\portal-content-center\CLAUDE.md`（A.2 描述更新）
- `D:\Project\oneplate\customer-marketing-center\CLAUDE.md`（A.3 添加技术债章节）
- `D:\Project\oneplate\business-application-center\CLAUDE.md`（A.4 添加技术债章节）

### portal 关键债涉及文件（A.1 待清零）

- `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricApi.java`
- `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricAdapter.java`
- `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/MetricCardDTO.java`
- `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/MetricTrendPoint.java`
- `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/dto/PortalTodoItem.java`
- `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/WorkflowQueryAdapter.java`（次要债 overdueInfo/bizDetailUrl）

### performance 正式 API（A.1 切换目标）

- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/MetricApi.java`（V1.1 P2.6 已交付）
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/dto/MetricCardDTO.java`（含 V1.4 mom/yoy/previousValue 字段）

---

## 用户/Agent 协作约定

- 子代理统一使用 高配模型（用户指令优先于 MEMORY.md 标准 默认）
- `git add` 显式列文件，不用 `git add -A`
- TDD 严格 Red-Green 分离（A 方案因为是文档/重构，不强制 Red+Green，但生产代码改动需测试守护）
- 每个 Phase 完成后 push，全部完成合并 master（A 方案在 master 上直接做，无需合并）

---

**文档生成时间**：2026-04-25
**对应 git HEAD**：`97b31df`
**会话累计 commit**：237（performance V1.0-V1.5 共 225 + report V1.0 共 67 + 根架构规约 修正 1 = 实际 git log 计数从迭代起点起算）
