# D-中模块审计设计规格

> **任务类型**: 基础设施修复 + 代码静态审计 + 文档同步（无业务代码变更）
> **日期**: 2026-04-15
> **触发**: 用户发起业务申请中心开发时发现根目录 CLAUDE.md 与真实代码严重漂移；父 pom 基础设施损坏导致 mvn 无法执行
> **审计深度档位**: D-中（主会话选定）

---

## 1. 背景

### 1.1 矛盾现象

用户提出"开始 business-application-center 模块开发"，但探查发现：

| 矛盾 | 证据 |
|---|---|
| 模块实际已实现 | 3990 行主代码 / 2791 行测试代码 / git commit `a93ba82 feat(bizapp): 实现业务申请中心全部功能` |
| 根 CLAUDE.md 却标记"⏳ 骨架" | `CLAUDE.md` 第 60-64 行、101-106 行两处错误状态 |
| 模块级 CLAUDE.md 自述"114 测试 0 失败" | `business-application-center/CLAUDE.md` 第 141-147 行 |
| 当前 mvn 无法执行 | 父 pom `<dependencyManagement>` 缺 `customer-marketing-center` / `business-application-center` 版本声明 |

经全景扫描确认，此类漂移波及 3 个已实现模块（portal / customer / bizapp），另有 2 个模块（performance / report）文档中有但目录未建。

### 1.2 漂移事故推测

Git 历史显示：

- `20fe585 ???????????????????????` + `2e517e8 ﻿补记本次文档同步的中文提交说明以修正编码丢失` + `7c9dd1b 首次提交本地代码`

最近这批 commit 存在中文编码灾难，推测父 pom 的 `<dependencyManagement>` 在"首次提交本地代码"时被覆盖式回退，导致当前损坏态。根 CLAUDE.md 的模块状态表也同期被回退到更早的版本。

### 1.3 用户决策路径

- 选项 A（验证回归 + 改标签）因 mvn 无法执行而阻塞
- 选项 B/C/D 由用户选择后本 spec 适用 **D-中** 档位

---

## 2. 目标与非目标

### 2.1 目标

1. **恢复基础设施可用性**：父 pom 修复、垃圾日志清理、`mvn test` 可通过
2. **产出代码静态审计报告**：对 3 个漂移模块（portal / customer / bizapp）按统一 checklist 做代码审查，产出 Critical / Important / Minor 分级缺陷清单
3. **同步项目状态文档**：根 CLAUDE.md 模块状态表 / 依赖图 / 包结构三处同步；补全 bootstrap 模块级 CLAUDE.md
4. **为后续 TDD 整改铺路**：缺陷清单的条目结构应足够细致，可直接作为 writing-plans + TDD 子代理的输入

### 2.2 非目标（明确不做）

| 项 | 不做的原因 |
|---|---|
| 修复审计发现的任何业务代码缺陷 | 用户要"只列不改，交他决定" |
| 代码与 `docs/modules/<m>/*.md` 9 份详细文档的契合度审查 | 属 D-深范围，本次不做 |
| SQL 性能分析 / 慢查询排查 | 需真实环境，本次跑不到 |
| 从零规划 performance-engine / report-analytics 模块 | 属 D-超深范围 |
| 重新审计 auth / governance / workflow 三个已对齐的模块 | 这三个模块状态与根 CLAUDE.md 一致，不在漂移范围 |
| 深入安全渗透测试 | 超出静态审计范畴 |

### 2.3 成功标准

1. `mvn -pl portal-content-center,customer-marketing-center,business-application-center -am test` 在本地返回 BUILD SUCCESS
2. `docs/superpowers/audits/2026-04-15-d-med-audit-report.md` 存在并包含 3 个模块各自的缺陷清单，每条含位置、规范依据、问题描述、修复建议
3. 根 `CLAUDE.md` 与真实代码对齐（3 个模块状态从 ⏳ 改为 ✅）
4. `bootstrap/CLAUDE.md` 存在且描述与实际代码匹配
5. 所有变更分步骤提交 git，每个 commit 可独立回退

---

## 3. 审计范围界定

### 3.1 纳入审计的模块

| 模块 | 代码规模 | 审计依据 |
|---|---|---|
| portal-content-center | 109 主 / 38 测试 | 根 CLAUDE.md + common-dev-guide.md + portal CLAUDE.md |
| customer-marketing-center | 91 主 / 29 测试 | 根 CLAUDE.md + common-dev-guide.md + customer CLAUDE.md |
| business-application-center | 53 主 / 21 测试 | 根 CLAUDE.md + common-dev-guide.md + bizapp CLAUDE.md |

### 3.2 不纳入审计的模块

| 模块 | 原因 |
|---|---|
| common 各子模块 | 非业务模块，已完成对齐 |
| auth-permission-center | 状态一致，不在漂移范围 |
| system-governance-center | 状态一致，不在漂移范围 |
| workflow-center | 状态一致，且有真实 Flowable 联调记录 |
| performance-engine-center | 目录不存在，无代码可审 |
| report-analytics-center | 目录不存在，无代码可审 |
| bootstrap | 仅为启动壳，3 java / 11 test，只补 CLAUDE.md |

---

## 4. 审计 Checklist 定义（4 大类，约 25 条）

完整 checklist 在阶段 1 生成并保存为独立文件。本 spec 只定义大类结构与条目示例。

### 4.1 V1 - 规范合规（约 8 条）

对照 `docs/common-dev-guide.md` + 根 `CLAUDE.md` 的 9 条开发 Checklist：

- V1-01 跨模块调用是否只走 `*Api`（不直连 mapper / entity / serviceImpl）
- V1-02 所有对外接口是否声明 `@BizAuth`
- V1-03 `PT_RESOURCE` 登记（本次通过 `docs/superpowers/sql/*bizapp-pt-resource*.sql` 间接验证）
- V1-04 写操作是否在 Service 层做二次权限校验
- V1-05 高危操作是否独立 URL + 独立授权 + 独立审计
- V1-06 读接口 / 导出接口是否统一应用 DATA_SCOPE
- V1-07 流程类业务是否维护 `business_key` 与 `biz_process_map`
- V1-08 错误码前缀是否符合模块约定（PORTAL / CUST / BIZ）

### 4.2 V2 - 代码质量（约 7 条）

- V2-01 重复代码（跨类 / 跨方法）
- V2-02 过大文件（单文件 > 500 行警示）
- V2-03 死代码 / 未调用方法 / 无用 import
- V2-04 异常处理完整性（是否吞异常 / 是否泄露内部信息 / catch(Exception e) 滥用）
- V2-05 敏感字段日志脱敏（手机号 / 身份证 / 账号 / 金额）
- V2-06 Service 类 + public 方法注释齐全性
- V2-07 是否使用 Object 作为通用参数（CLAUDE.md 明令禁止）

### 4.3 V3 - 测试完备性（约 5 条）

- V3-01 是否存在"事后补测试"痕迹（测试逻辑简陋 / 只验证 happy path / 无边界条件）
- V3-02 单元测试 vs 集成测试边界（单元测试是否误用 SpringBootTest）
- V3-03 核心业务分支覆盖（状态机转换、场景路由、错误码触发路径）
- V3-04 过度 mock 识别（是否 mock 掉了本应 verify 的逻辑）
- V3-05 `@Transactional` 回滚路径测试覆盖

### 4.4 V4 - 文档一致性（约 5 条）

- V4-01 模块级 CLAUDE.md 自述的端点数 vs 实际 Controller 端点数
- V4-02 模块级 CLAUDE.md 自述的 Service / Facade 类清单 vs 实际文件
- V4-03 模块级 CLAUDE.md 自述的测试用例数 vs 实际 `@Test` 计数
- V4-04 错误码 enum 清单 vs CLAUDE.md 列出的错误码前缀
- V4-05 状态机描述 vs 实际 Service 转换代码

---

## 5. 执行策略

### 5.1 总策略：清单驱动 + 3 模块并行子代理

- **清单驱动优于经验式**：可复现、可追溯、可派单
- **子代理类型选 Explore**：只读不写，工具集足够，token 开销低于 general-purpose
- **3 模块并行**：各模块独立无顺序依赖；主会话接收结构化报告，过滤过程噪音

### 5.2 主会话与子代理分工

| 职责 | 执行方 |
|---|---|
| 基础设施修复（pom / 垃圾清理 / 验证 mvn） | 主会话 |
| Checklist 生成 | 主会话 |
| 模块代码静态审查 | 3 × Explore 子代理（并行） |
| 3 份报告汇总 | 主会话 |
| 根 CLAUDE.md 同步 | 主会话 |
| bootstrap CLAUDE.md 新建 | 主会话 |
| git commit 分步提交 | 主会话 |

---

## 6. 执行阶段（五阶段）

### 阶段 0：基础设施修复

**0.1 修父 pom.xml**

在 `<dependencyManagement>` 末尾（当前 portal-content-center 声明之后）追加 2 条：

```xml
<!-- customer-marketing-center -->
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>customer-marketing-center</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- business-application-center -->
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>business-application-center</artifactId>
    <version>${project.version}</version>
</dependency>
```

**0.2 清理 JVM 崩溃日志垃圾**

从仓库根目录和子目录删除：
- `hs_err_pid*.log`
- `replay_pid*.log`

不在此阶段处理的相关问题（记录下来供后续整改参考）：
- `.gitignore` 是否已包含这两个模式（如未包含，作为 V2 类缺陷放入审计清单，**不在此阶段修改**）

**0.3 验证 mvn 可通过**

执行：
```bash
mvn -pl portal-content-center,customer-marketing-center,business-application-center -am test
```

**中断条件**：若任一模块测试失败，立即停止并向用户上报，转入 D-深讨论（因为测试失败意味着代码 vs 功能规范有实质偏离）。

### 阶段 1：生成审计 Checklist

主会话基于 common-dev-guide.md 9 章 + 根 CLAUDE.md 开发 Checklist 段落 + 4 大类定义，生成：

`docs/superpowers/audits/2026-04-15-module-audit-checklist.md`

内容结构：
- 每条 checklist 含 ID / 所属大类 / 标题 / 规范依据（文档 + 章节）/ 检查方法 / 期望输出格式

此 checklist 作为阶段 2 所有子代理的共同输入。

### 阶段 2：并行审查

同时派发 3 个 Explore 子代理，每个子代理的任务书包含：

- 目标模块根路径
- `docs/superpowers/audits/2026-04-15-module-audit-checklist.md`（checklist）
- `docs/common-dev-guide.md`（规范依据）
- 根 `CLAUDE.md`（规范依据）
- 目标模块的 `CLAUDE.md`（自述依据，用于 V4 文档一致性比对）
- 产出格式要求（下文 §7 定义）

子代理产出：
- `docs/superpowers/audits/2026-04-15-portal-audit.md`
- `docs/superpowers/audits/2026-04-15-customer-audit.md`
- `docs/superpowers/audits/2026-04-15-bizapp-audit.md`

### 阶段 3：汇总

主会话合并 3 份模块报告为总报告：

`docs/superpowers/audits/2026-04-15-d-med-audit-report.md`

内容结构：
1. 执行摘要（各模块 Critical / Important / Minor 数量矩阵）
2. 跨模块共性问题提炼（如"3 个模块都缺少 @Transactional 回滚测试"这类模式）
3. 每模块详细清单引用（链接到独立 audit 文件）
4. 建议整改优先级

### 阶段 4：文档同步

**4.1 更新根 `CLAUDE.md`**

修改位置：
- 第 50-58 行"当前已实现的模块"表：增加 portal / customer / bizapp 三行
- 第 60-64 行"尚未实现的模块"：删除 portal / customer / bizapp 三行，保留 performance / report
- 第 67-79 行"当前模块依赖图"：扩展依赖图包含新增模块
- 第 94-107 行"包结构规范"：三个模块状态从 ⏳ 骨架 改为 ✅ 已完成
- 第 207-211 行"模块级 CLAUDE.md"：增加 portal / customer / bizapp 的链接

**4.2 新建 `bootstrap/CLAUDE.md`**

内容大纲（仿照其他模块 CLAUDE.md）：
- 模块概述（Spring Boot 启动入口，非业务模块）
- 主类：`BranchPlatformApplication`
- 依赖关系：依赖所有已实现业务模块
- 启动命令：`cd bootstrap && mvn spring-boot:run`
- 配置文件：`src/main/resources/application.yml`
- 运行期要求：MySQL / Redis / 可选 MinIO
- 特殊说明：commons-compress 1.25.0 版本覆盖原因（POM 中已有注释）
- 测试：集成测试 AbstractIntegrationTest 基类

### 阶段 5：停止与交接

**本 spec 不执行阶段 5 的实际修复动作**。主会话在完成阶段 4 后向用户交接：

> "D-中审计已完成，产出报告位于 `docs/superpowers/audits/2026-04-15-d-med-audit-report.md`。请您审阅报告，决定：
>
> - 选项甲：启动 TDD 整改（走 writing-plans → 子代理 TDD 修复每个 Critical / Important 项）
> - 选项乙：仅保留报告，暂不修复，下次开发新功能时附带整改
> - 选项丙：发现报告内容严重偏离，需要升级为 D-深审计"

---

## 7. 子代理产出格式（强制）

每份模块 audit 报告必须遵守以下结构，便于主会话机械汇总：

```markdown
# <module> 审计报告

> Date: 2026-04-15
> Auditor: Explore subagent
> Checklist version: 2026-04-15-module-audit-checklist.md

## 执行摘要

| 大类 | Critical | Important | Minor | 合计 |
|---|---|---|---|---|
| V1 规范合规 | n | n | n | n |
| V2 代码质量 | n | n | n | n |
| V3 测试完备性 | n | n | n | n |
| V4 文档一致性 | n | n | n | n |
| **合计** | n | n | n | n |

## Checklist 通过情况

| ID | 标题 | 结论 | 说明 |
|---|---|---|---|
| V1-01 | 跨模块调用是否只走 *Api | ✅ | — |
| V1-02 | @BizAuth 齐全 | ❌ | 见 D-001 |
| ... | | | |

## 缺陷详单

### D-001 [Critical] V1-02 | <简短标题>

- **位置**: `module/src/.../FileName.java:142`
- **规范依据**: `common-dev-guide.md §4.3` / 根 `CLAUDE.md` 第 190 行
- **问题描述**: ...
- **建议修复**: ...
- **证据**: 3-5 行关键代码片段

### D-002 [Important] V2-01 | ...
...
```

**严重程度定义**：

| 级别 | 定义 |
|---|---|
| **Critical** | 违反 CLAUDE.md 明文禁止条款 / 可能导致运行时错误 / 存在安全隐患 / 权限绕过 |
| **Important** | 违反最佳实践 / 影响可维护性 / 测试覆盖重大缺口 / 文档与代码不一致 |
| **Minor** | 代码风格 / 注释缺失 / 轻微重复 / 命名不一致 |

---

## 8. Git 提交策略

分步提交，每步独立可 revert：

| 序号 | Commit message | 涉及文件 |
|---|---|---|
| 1 | `chore(pom): 补齐父 pom dependencyManagement 缺失的两个模块版本声明` | `pom.xml` |
| 2 | `chore: 清理 JVM 崩溃日志垃圾 (hs_err_* / replay_*)` | 仓库根 + 各模块目录 |
| 3 | `docs(audit): 生成 D-中模块审计 checklist 与 3 份模块报告` | `docs/superpowers/audits/2026-04-15-*` |
| 4 | `docs(audit): 汇总 D-中模块审计总报告` | `docs/superpowers/audits/2026-04-15-d-med-audit-report.md` |
| 5 | `docs: 同步根 CLAUDE.md 模块状态与依赖图（portal/customer/bizapp → ✅）` | `CLAUDE.md` |
| 6 | `docs: 补全 bootstrap 模块级 CLAUDE.md` | `bootstrap/CLAUDE.md` |

审计 spec 本身在本 spec 落盘后立即单独提交：

| 序号 | Commit message | 涉及文件 |
|---|---|---|
| 0 | `docs(spec): D-中模块审计设计规格` | `docs/superpowers/specs/2026-04-15-d-med-module-audit-design.md` |

---

## 9. 风险与缓解

| 风险 | 可能性 | 影响 | 缓解 |
|---|---|---|---|
| pom 修复后仍有其他构建问题 | 中 | 阻塞阶段 0.3 | 阶段 0.3 中断后立即向用户上报，不擅自扩大修复范围 |
| 3 模块测试出现红色 | 中 | 打破 D-中前提 | 立即上报，转 D-深讨论 |
| 并行子代理产出格式偏离 §7 | 中 | 汇总困难 | 子代理任务书中强制 §7 格式，主会话汇总前做结构校验 |
| 审计清单条目过多（> 50 条） | 低 | 报告冗长 | 本 spec 限制大类 4 个、条目 ~25 条，子代理按 ID 逐条输出，不自由发挥 |
| 中文编码事故再发 | 低 | 产出文件乱码 | 所有新文件明确 UTF-8，本轮不复用被编码污染过的文件 |
| 阶段 0 修 pom 动到 git 历史 | 低 | 用户担心 | 只修 `pom.xml` 文件内容，不做 rebase / reset / 改 history |

---

## 10. 回退方案

每个 commit 独立可 revert。具体：

| 回退场景 | 操作 |
|---|---|
| 审计过程意识到 D-中不够 | `git revert` commit 3/4/5/6，保留 pom 修复 + 垃圾清理 |
| pom 修复引入新问题 | `git revert` commit 1 |
| 文档同步误判（如某模块其实不完整） | `git revert` commit 5 |
| 全部放弃本次审计 | 按倒序 `git revert` commit 6 → 1 |

---

## 11. 与 brainstorming / writing-plans 的衔接

本 spec 是 brainstorming 阶段的产出。预期后续流程：

```
本 spec (落盘 + 评审)
   ↓
spec-document-reviewer 子代理评审（最多 3 轮）
   ↓
用户审阅批准
   ↓
writing-plans 技能生成实施计划（docs/superpowers/plans/2026-04-15-d-med-audit-plan.md）
   ↓
按实施计划执行（主会话 + 并行子代理）
   ↓
阶段 5 停止点，交接用户决策
```

**本 spec 不直接进入实施**。writing-plans 会把本 spec 细化为可逐步打勾的任务列表。

---

## 12. 附录：决策路径摘要

| 时点 | 用户决策 | 结果 |
|---|---|---|
| 会话起始 | 要求开始 bizapp 模块开发 + 用 TDD + 用子代理 | 探查发现模块已实现 |
| Q1 澄清 | A / B / C / D 选 A（验证回归） | 继续 |
| 阶段 0 | 发现 pom 损坏无法跑测试 | 上报阻塞 |
| Q2 决策 | A-1 / A-2 / A-3 / 转 D，选"转 D" | 升级审计路线 |
| Q3 澄清 | D-浅 / D-中 / D-深 / D-超深，选 D-中 | 本 spec 适用档位 |
| 方案呈现 | 4 Section 分段，全部批准 | 进入 spec 落盘 |

此路径显示：从"直接开发"到"承认项目处于漂移态并做审计"的转向是用户主动决策的结果，本 spec 的范围边界与该决策严格一致。
