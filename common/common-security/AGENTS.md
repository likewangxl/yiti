<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-04-26 -->

# common-security

## Purpose
数据权限范围管理、`@BizAuth` 注解模型、数据脱敏、API 签名校验。是平台安全体系的基础组件。

**基础包名**: `com.bank.branch.platform.common.security`
**自动配置**: `SecurityAutoConfiguration`（通过 `AutoConfiguration.imports` 激活）

## Key Files

| File | Description |
|------|-------------|
| `annotation/BizAuth.java` | 方法级鉴权注解 (bizType + action)，标注在 Controller 上 |
| `context/CurrentUserContext.java` | 当前用户上下文 Java record (empId, mainOrgCode, roleIds, systemAdmin 等), 可序列化存入 Redis Session |
| `context/DataScopeContext.java` | 数据权限范围 ThreadLocal 上下文 (bizType, action, scope, empId, orgCode, orgSubtreeCodes) |
| `enums/BizType.java` | 18 种业务类型常量 (LEAD, LOAN, SUPPORT, REPORT, PERF_CONFIG, SYS_CONFIG 等) |
| `enums/BizAction.java` | 15 种操作类型常量 (READ, WRITE, DELETE, EXPORT, EXECUTE, CONFIG 等) |
| `enums/DataScopeType.java` | 7 种数据范围级别 (SELF_CREATED, SELF, SELF_ASSIGNED, ORG, ORG_SUBTREE, ALL, WORKFLOW_PARTICIPANT) |
| `masker/SensitiveDataMasker.java` | 数据脱敏工具 (手机号保留前3后4 / 身份证保留前3后4 / 银行账号仅后4 / 金额固定遮罩) |
| `meta/ObjectMeta.java` | 业务对象元数据 Java record (objectKey, tableName, ownerOrgCol, createdByCol, supportedScopes 等) |
| `meta/ObjectMetaRegistry.java` | 业务对象元数据注册表 (ConcurrentHashMap)，线程安全 |
| `sign/SignatureUtils.java` | HmacSHA256 API 签名/验签工具 |
| `config/SecurityAutoConfiguration.java` | 自动配置 (注册 ObjectMetaRegistry + DataScopeCleanupFilter) |

## For AI Agents

### Working In This Directory
- **ThreadLocal 清理**: `DataScopeCleanupFilter` 在每个请求结束时调用 `DataScopeContext.clear()`，防止内存泄漏
- 扩展新 BizType 时需评估是否真的需要独立业务类型，优先复用现有枚举
- 新增业务表时需在 `ObjectMetaRegistry` 注册 `ObjectMeta`
- `CurrentUserContext` 是序列化对象，新增字段注意兼容 Redis Session 序列化

## Dependencies

### Internal
- `common-web`（BizException / ResponseWrapper）

### External
- `spring-boot-starter-web` — Servlet Filter
- `jackson-databind` — JSON 序列化

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
