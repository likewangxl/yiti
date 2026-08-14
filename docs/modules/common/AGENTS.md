<!-- Parent: ../AGENTS.md -->

# common 文档维护指导

本文件约束 `docs/modules/common/`，继承上级文档规则。

## 目录职责

- 记录 `common-web`、`common-trace`、`common-security`、`common-aop`、`common-db` 的公共组件边界，包括 `LockManager`/`PT_LOCK` 分布式锁基础设施。
- 这里不定义业务流程或业务模块接口；与 `docs/common-dev-guide.md` 重复时，后者是跨模块规范源。

## 阅读顺序

1. `01-功能规格.md`：公共基础能力和非目标。
2. `02-后端架构.md`：子模块依赖、包结构和装配。
3. `03-关键组件设计.md`：响应、安全、追踪、AOP 和数据库组件。

## 模块边界与同步

- common 不依赖业务模块，不得为单一业务场景反向引入业务依赖。
- 响应模型、异常、鉴权、审计、数据权限、分布式锁、Trace 或日志变化时，先同步 `common-dev-guide.md`，再更新本目录设计。
- 公共 API/注解变化必须检查所有消费者和对应测试；不要在此复制业务模块端点或 DTO 清单。
- 数据库内容只描述公共设施模型；结构实施服从 `docs/schema/AGENTS.md`。
