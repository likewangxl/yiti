<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-14 | Updated: 2026-07-12 -->

# docs/modules/auth-permission-center/AGENTS.md

本文件约束 `docs/modules/auth-permission-center/` 下的认证授权文档，统一使用 UTF-8。

## 目录职责

- 本目录记录认证、用户、组织、RBAC、BizType 数据范围等设计，共 8 份文档。
- `auth-permission-center` 是全平台底座支撑域，可被所有模块依赖，但不依赖业务模块。

## 阅读顺序

- `01-功能规格.md`：理解认证授权职责与边界。
- `02-后端架构.md`：查看认证链路、鉴权组件、组织与角色模型。
- `04-对外API契约.md`：跨模块调用 auth 能力时的首选文档。
- `05-表结构DDL.md` 与 `08-初始化数据清单.md`：查看 RBAC 与 PT_* 数据基线。

## 维护要求

- `WORKFLOW_PARTICIPANT` 数据范围已由 performance-engine-center V1.4 落地真实查询（不再是占位实现），`workflow-center` 也已公开正式 Bean `WorkflowQueryApi`；文档不要再默认写成“尚未补齐”，如遇后续新 BizType 接入 `WORKFLOW_PARTICIPANT`，仍需注明其查询链路依赖哪个模块的哪个接口。
- 用户、角色、机构字段命名变化时，要同步检查依赖这些字段的治理、工作流、门户文档。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
