<!-- Parent: ../AGENTS.md -->

# auth-permission-center 文档维护指导

本文件约束 `docs/modules/auth-permission-center/`，继承上级文档规则。

## 目录职责

- 记录认证、Session、用户、组织、RBAC、资源、BizType 和数据范围设计。
- auth 是平台底座，可被其他模块依赖，但不得依赖业务模块；权限失败必须 fail-close。

## 阅读顺序

1. `01-功能规格.md`：认证授权职责。
2. `02-后端架构.md`：认证链路、组织、角色和范围模型。
3. `03-接口设计与报文.md`、`04-对外API契约.md`：REST 与跨模块能力。
4. `06-并发与事务策略.md`、`07-审计要求.md`：权限变更一致性和审计。

## 契约同步与边界

- `CurrentUserApi`、`BizScopeApi`、`OrgApi`、`UserApi`、`RoleApi` 等公开契约以当前 `api/` 源码为准；变化时同步 `04` 及所有消费者的 `09`。
- 资源匹配、`@BizAuth`、角色资源和数据范围规则变化时，同步 `03`、`06`、`07` 与 `common-dev-guide.md`。
- 新 BizType/Action 必须先核对现有枚举、资源和范围解析链，不能用前端隐藏替代后端权限。
- `05`、`08` 只描述 RBAC/PT_* 数据模型和初始化要求，不是数据库执行入口。
