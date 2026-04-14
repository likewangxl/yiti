# docs/modules/portal-content-center/AGENTS.md

本文件约束 `docs/modules/portal-content-center/` 下的门户模块文档，统一使用 UTF-8。

## 目录职责

- 本目录记录工作台聚合、导航、通讯录、产品资料库等设计，共 9 份文档。
- 门户模块当前代码已存在实现切片，文档需同时区分“当前已实现代码路径”和“目标设计路径”。

## 维护要求

- 不能把 `workflow-center` 的 `WorkflowQueryApi` 当成已落地 Bean。当前 portal 代码实际使用本地 `WorkflowQueryAdapter` 占位降级，待办查询仍依赖 workflow-center 后续补齐查询 Java 契约或统一 REST 适配层。
- 涉及工作台聚合、待办数量、最近待办的章节，要优先和 `portal-content-center` 现有代码保持一致。
- 性能模块仍属弱依赖，文档中必须保留降级策略说明。

## 推荐阅读

- `01-功能规格.md`：产品行为与展示范围。
- `02-后端架构.md`：当前实现分层和依赖适配方式。
- `09-依赖契约摘要.md`：跨模块依赖的真实现状与降级策略。

## 覆盖关系

- 继承 `../AGENTS.md` 与 `../../AGENTS.md`。
