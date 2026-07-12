<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-14 | Updated: 2026-07-12 -->

# docs/modules/portal-content-center/AGENTS.md

本文件约束 `docs/modules/portal-content-center/` 下的门户模块文档，统一使用 UTF-8。

## 目录职责

- 本目录记录工作台聚合、导航、通讯录、产品资料库等设计，共 9 份文档。
- 门户模块已完整交付（108 Java + 38 测试，0 UOE；V1.13 # 1 已解绑 yiti 开发库），文档需同时区分“当前已实现代码路径”和“目标设计路径”。

## 维护要求

- `workflow-center` 的 `WorkflowQueryApi` 已落地为正式 Bean；portal 文档需描述“正式 QueryApi + `WorkflowQueryAdapter` 降级封装”的当前实现，不要再写成纯占位方案。
- 涉及工作台聚合、待办数量、最近待办的章节，要优先和 `portal-content-center` 现有代码保持一致。
- 性能模块仍属弱依赖，文档中必须保留降级策略说明。

## 推荐阅读

- `01-功能规格.md`：产品行为与展示范围。
- `02-后端架构.md`：当前实现分层和依赖适配方式。
- `09-依赖契约摘要.md`：跨模块依赖的真实现状与降级策略。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
