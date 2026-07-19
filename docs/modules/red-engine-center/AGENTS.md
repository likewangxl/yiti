<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-19 -->

# docs/modules/red-engine-center/AGENTS.md

本文件约束 `docs/modules/red-engine-center/` 下的红色引擎（党建管理）文档，统一使用 UTF-8。

## 目录职责

- 本目录记录红色引擎的接口设计与对外契约，当前 2 份文档（`03-接口设计与报文.md`/`04-对外API契约.md`），编号沿用平台惯例但未补齐 01/02/05~09（该模块由独立 `redengine` 系统整体移植合并，文档以「接口/契约」为优先交付项，其余编号文档暂缺，非遗漏计划外补齐）。
- 该模块已于 **2026-07-18** 整体落地交付（6 Controller / 23 REST 端点 / 17 条 `PT_RESOURCE`，50 单元测试用例全绿），文档需以当前实现态为准。

## 维护要求

- 本模块**当前无对外 `*Api`/`*QueryApi`**（`api/` 包下只有 `dto/`），`04-对外API契约.md` 的核心结论是"无对外契约 + 消费上游 `CurrentUserApi`/`FileApi`"——若后续新增跨模块查询能力，必须同步更新该文档，不要留空。
- 本模块**不接 Flowable**，审核流是自管两级状态机（`RE_SUBMIT.status` 字段流转），涉及审核/流程的表述禁止套用 `workflow-center` 的 `businessKey`/`BIZ_PROCESS_MAP`/流程实例等术语。
- PT_RESOURCE 与角色权限矩阵的权威来源是 `docs/superpowers/sql/2026-07-18-redengine-seed.sql`，`03-接口设计与报文.md` 的端点↔资源对照表变更时必须与该脚本保持一致，不要凭记忆改动资源 ID/URL。
- 字段级 DTO 校验注解、错误码语义变更时，优先核实 `red-engine-center/src/main/java/.../api/dto/` 与 `service/` 源码，不要照抄旧简报口径（模块 CLAUDE.md 已记录多处"简报口径 vs 代码实际"的纠偏案例，如红黄牌阈值、逾期规则）。

## 推荐阅读

- `../../../red-engine-center/CLAUDE.md`：模块权威上下文（包结构/数据库表/权限矩阵/技术债/与源系统差异清单），本目录文档的背景信息一律以此为准不重复维护。
- `03-接口设计与报文.md`：REST 接口全量端点、状态机、两级审核流转。
- `04-对外API契约.md`：跨模块依赖边界（消费方/被消费方）。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
