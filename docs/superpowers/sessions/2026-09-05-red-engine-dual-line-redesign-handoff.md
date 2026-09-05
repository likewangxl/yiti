# Handoff：红色引擎（red-engine-center）双线重构

> 交接日期：2026-09-05。仓库：/home/dong/桌面/yiti（分支 master）。
> 本文是 2026-09-05 设计评审会话的交接快照，属时间点档案；后续行为以当时源码与模块文档为准。
> 本文不含设计细节全文——**设计共识已固化为仓库内文档，先读它**：
> `docs/modules/red-engine-center/10-目标设计-材料计分与任务扣分重构.md`（规划态，2026-09-05 共识）

## 下一个会话要做什么

按目标设计文档实施红色引擎双线重构（四大维度材料计分线 + 任务扣分线解耦）。用户尚未下达开发指令，下一步应先与用户确认实施范围与拆分计划，再动工。

## 必读上下文（按序）

1. 仓库根 `AGENTS.md`（红线：TDD、Flyway 禁令、可执行 SQL 白名单、Sol/Luna 协作、playwright-cli 前端验收、数据库门禁）
2. `red-engine-center/AGENTS.md`（现状状态机与已知限制——注意其中"允许改判"兼容行为已被本次产品决策推翻，实施时同步修订）
3. `docs/modules/red-engine-center/10-目标设计-材料计分与任务扣分重构.md`（**本次会话的产出，目标设计唯一权威**）
4. `docs/AGENTS.md` 与 `docs/modules/AGENTS.md`（文档同步要求）

## 设计共识要点速览（细节以设计文档为准）

- 四维材料与任务解耦：材料线无窗口、支部审（不打分）→ 自动流转 → 组织终审计分；任务线不得分、逾期进待扣分列表、组织审核员执行扣分（守卫改为 R_RE_ORGREV + SYS_ADMIN）
- 材料状态机扩展为 1→4→2/3，收紧不可改判；驳回后报送员原记录重报（3→1）
- 新增材料类型分值配置表（组织审核员维护）；计分仅在组织终审；类型上限 ≤ 维度上限（35/50/10/5）
- 红黄牌公式：total = 四维得分 − 扣分，final = total × 0.4，阈值 60/80 不变
- 材料新增"所属年度"字段（默认当年可改）；年度归档死代码删除，替换为按年度的四维材料异步导出 ZIP（复用任务导出体系）
- 待扣分进入条件①②③（未上报/组织驳回未重报/支部驳回未重报），排除"支部已过组织未审"

## 关键现状事实（实施时直接用到）

- 前端组织工作台 `ReviewView.vue` 对四维材料调用材料审核 API，但后端 `ReReviewService`（约 line 347）守卫仅 `R_RE_SECR`/`SYS_ADMIN`——这是要修的核心断点
- 扣分执行：`ReHomeController` `/api/re/home/overdue/execute` → `ReHomeServiceImpl.executeOverdue`（守卫仅 SYS_ADMIN）；`/api/re/cockpit/overdue/execute` 兼容端点资源已停用
- 任务导出异步体系：`ReTaskExportServiceImpl` + `ReTaskExportAsyncExecutor` + `ReTaskExportWorker`，年度材料下载应复用
- 真实 RBAC 验证：bootstrap `RedEngineSmokeIT` + `redengine-smoke` Profile
- 改动 controller/api/DTO/错误码/@BizAuth 后须跑 `scripts/check-contract-docs.sh`

## Suggested skills

- 实施前拆分任务：Skill `mattpocock-skills:tdd`（Red-Green-Refactor 是仓库红线）
- 前端改动验收：按根 AGENTS.md 使用官方 `playwright-cli`（不是 MCP/Playwright Test）
- 如需继续讨论设计细节：Skill `mattpocock-skills:grilling`
- 如需评审实现：Skill `mattpocock-skills:code-review`

## 注意事项

- 项目要求非简单开发任务走 Sol 主代理规划 + Luna 子代理执行 + Sol 验收的协作模式（见根 AGENTS.md）
- 任何 DDL/schema 变更禁止 Flyway 与 DDL 脚本，由 DBA 按审批实施；新增配置表只出设计，不提交 DDL
- 用户沟通语言：中文
