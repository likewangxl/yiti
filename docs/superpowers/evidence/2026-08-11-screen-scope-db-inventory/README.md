# yiti_test 大屏范围脚本：严格只读盘点报告（Hard Stop）

## 结论

`yiti_test` 已通过受控提权的 TCP 只读会话完成元数据与聚合盘点，但**不得执行三份 SQL，也不得进行任何后续 DDL/DML、备份、克隆或生产库操作**。

硬停止原因是隔离条件不成立或无法证明成立：`SYS_JOB_CONF` 有 9 条记录且全部 `ACTIVE`，Quartz 有 9 条 `WAITING` 触发器及 1 条调度器状态记录。这与“隔离环境须禁用调度和外发”的前置门禁冲突。另有 1 条 Spring Session、8 条对象存储文件元数据（3 个非空桶）及 5 条均为 `ACTIVE` 的系统配置；未读取任何配置值、凭据、回调地址或业务明细，因此无法证明它们使用了隔离命名空间/凭据/对象存储。

本次只读查询还确认：第三份 seed 脚本当前必然 hard-stop，因为其前置的 auth/report 对齐对象尚未创建。这是脚本执行顺序造成的前置条件未满足，不应被当作可直接修复的“残缺表”而跳过前两份脚本。

盘点完成时间：`2026-08-12T02:02:57+08:00`
目标：`localhost:3306/yiti_test`（TCP，仅此数据库）
最终状态：**BLOCKED — 测试库调度/隔离门禁未满足，外联与对象存储隔离证据不足**

## 只读证明与边界

首次非提权 TCP 尝试在 SQL 送达前失败：`ERROR 2004 (HY000): Can't create TCP/IP socket (1)`。经父代理确认该错误来自沙箱 TCP 限制后，仅对同一目标和同一显式只读会话申请受控提权并获批。

提权重试的数据库侧证明：

| 项目 | 实际值 |
|---|---|
| 当前数据库 | `yiti_test` |
| MySQL 版本 | `8.0.33` |
| `@@session.transaction_read_only` | `1` |
| `@@session.autocommit` | `1` |
| 事务隔离级别 | `REPEATABLE-READ` |
| schema 字符集 / 排序规则 | `utf8mb4` / `utf8mb4_unicode_ci` |

每个成功连接均先执行 `SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY;`，且仅执行 `SELECT`（包括 `information_schema`）和最终 `COMMIT`。未使用 `mysql --force`；未执行 SQL 文件、`SHOW`、DDL、DML、存储过程、动态 SQL、备份、克隆、覆盖、清空或重建；未连接或访问 `yiti`。

所有数据查询均为 `COUNT`、`GROUP BY`、`CASE`、存在性或元数据查询。未读取或输出用户、客户、账户、人员、会话属性、锁值、配置值、凭据、回调地址、文件路径或业务明细。

## 结构盘点

### 已存在的基础对象

| 对象 | 结构结论 |
|---|---|
| `PT_ROLE` | 基线 10 个写入列的类型、长度、空值和默认值与 auth 脚本契约一致；主键为 `ROLE_ID`。`ROLE_CODE` 唯一索引尚不存在，属于 auth 脚本允许补齐的缺口。 |
| `PT_RESOURCE` | 基线 16 个写入列与脚本契约一致；主键为 `RESOURCE_ID`；已有唯一键 `(RESOURCE_URL, RESOURCE_METHOD, SYS_CODE)`，列序正确。 |
| `PT_ROLE_RESOURCE` | 5 个脚本写入列与契约一致；主键为 `ID`。 |
| `AUDIT_LOG` | 16 个基础审计列与 auth 脚本契约一致；6 个本期结构化审计列当前均不存在，属脚本允许新增的状态。 |
| `RPT_SCREEN` | 既有画布基线列存在，`screen_code varchar(64) NOT NULL`、`deleted tinyint NOT NULL DEFAULT 0` 契约匹配；本期 5 个对齐字段均尚不存在。 |
| `RPT_SCREEN_DATASOURCE` | 既有 7 个对齐基线列存在；本期 `biz_line`、`updated_by` 均尚不存在。 |

实际列定义、索引、外键、触发器、视图和同名过程检查见 [02-structure-metadata.md](02-structure-metadata.md)。目标范围内未返回外键、触发器、同名视图或三份脚本的同名过程。

### 尚不存在的本期对象

`PT_ORG_PROFILE`、`PT_ORG_GROUP`、`PT_ORG_GROUP_MEMBER`、`PT_ROLE_ORG_GROUP`、`RPT_SCREEN_ACCESS_ROLE` 在 `yiti_test` 均不存在（对象、目标列和目标索引计数均为 0）。

这对第一份 auth 脚本和第二份 align 脚本分别是“待创建”的正常初始状态；但第三份 seed 脚本要求其中的 `PT_ORG_GROUP`、`RPT_SCREEN_ACCESS_ROLE` 已存在，故当前不能执行 seed。

## 身份、重复与长度风险（仅聚合）

| 等价 hard-stop 项 | 聚合结果 | 结论 |
|---|---:|---|
| `PT_ROLE.ROLE_CODE` 重复组 | 0 | 未发现 auth 角色业务键重复。 |
| 既有 `R_SCREEN_*` 非数字 `ROLE_ID` | 0 | 未发现该类冲突。 |
| 数字角色 ID 序列耗尽风险 | 0 | 未发现上界风险。 |
| `PT_RESOURCE` URL/METHOD/SYS_CODE 重复组 | 0 | 未发现资源业务身份重复。 |
| auth 目标资源的 ID→身份冲突 / 身份→ID 冲突 | 0 / 0 | 可安全地视作未占用，未输出资源明细。 |
| report 目标资源的 ID→身份冲突 / 身份→ID 冲突 | 0 / 0 | 可安全地视作未占用，未输出资源明细。 |
| 废弃超长资源 `R_RPT_SCR_CFG_META_SAVE` | 0 | 未发现。 |
| `SYS_ADMIN` 记录数 / 禁用或非数字 ID 数 | 1 / 0 | align 所需的精确身份条件满足。 |
| report 目标 `PT_ROLE_RESOURCE` 重复绑定组 | 0 | 未发现。 |
| 既有屏读取/配置资源计数 | 2 | align 所需前置资源齐全。 |
| 活跃 `RPT_SCREEN.screen_code` 重复组 | 0 | 未发现。 |
| seed 两个目标查看角色计数 | 0 | auth 前置角色尚未创建，seed 必须停止。 |
| seed 目标活跃 `screen_code` 重复组 | 0 | 未发现。 |

没有读取角色 ID、用户、角色成员、资源名称之外的业务数据；上述固定资源/角色标识均来自待执行脚本而非数据库明细输出。

## 三份脚本的首个 DDL 前 hard-stop 等价复核

| 脚本 | 等价只读结果 | 是否可进入非只读段 |
|---|---|---|
| `2026-08-11-auth-org-profile-group.sql` | 基础表、精确写入列、主键、资源唯一业务键、重复/双向身份与数字 ID 检查均未发现阻断项；新 AUTH 表、结构化审计列和角色码唯一键尚不存在，均是此脚本允许创建/补齐的目标。 | **否**。全局隔离门禁失败，且未获 DDL/DML 执行授权。 |
| `2026-08-11-screen-scope-map-align.sql` | 既有 report/auth 基线列、主键、资源唯一身份、`SYS_ADMIN`、存量配置资源、活跃屏编码与目标资源身份未发现阻断项；5 个 `RPT_SCREEN` 字段、2 个数据源字段、屏级白名单表及索引均尚未应用，属该脚本的待创建目标。 | **否**。执行顺序要求先完成 auth 脚本；同时隔离门禁失败。 |
| `2026-08-11-screen-scope-map-seed.sql` | 前置对象/字段缺失：`PT_ORG_GROUP=0`、`RPT_SCREEN_ACCESS_ROLE=0`、两个查看角色计数=0、seed 要求的 `RPT_SCREEN` 列仅 11/16。 | **否**。这是预期的顺序性 hard-stop，必须在获授权且隔离验证通过后由前两份脚本建立前置条件。 |

该复核只镜像门禁条件，不执行脚本中的 `PREPARE`、`DROP/CREATE PROCEDURE`、`CALL`、DDL 或 DML。

## yiti_test 隔离只读核验

| 范畴 | 存在性 / 聚合结果 | 判定 |
|---|---|---|
| Spring Session | `SPRING_SESSION=1`、`SPRING_SESSION_ATTRIBUTES=1` | 存在会话数据；未读取会话属性，无法证明与其他环境隔离。 |
| 平台锁 | `PT_LOCK=0` | 当前无锁记录，但这不替代锁表/命名空间隔离证明。 |
| 应用调度 | `SYS_JOB_CONF=9`，其中 `ACTIVE=9` | **硬停止：调度未禁用。** |
| Quartz | `QRTZ_JOB_DETAILS=9`、`QRTZ_TRIGGERS=9`，状态 `WAITING=9`；`QRTZ_SCHEDULER_STATE=1`；执行中 `QRTZ_FIRED_TRIGGERS=0` | **硬停止：调度器/等待触发器仍启用。** |
| 消息/缓存配置 | `SYS_CONFIG_KV` 共 5 条且均 `ACTIVE`；按消息、缓存、命名空间键名模式命中的行均为 0 | 不读取值时不能证明应用配置、环境变量或其他表未启用外部消息/缓存；证据不足。 |
| 对象存储 | `FILE_OBJECT=8`，非空桶计数 3；配置键名模式命中对象存储项为 0 | 不读取桶名、对象键或配置值，无法证明使用独立对象存储/命名空间。 |
| 回调 / 凭据 | 配置键名模式命中回调、凭据项均为 0 | 仅说明 `SYS_CONFIG_KV.config_key` 未命中预设模式；不读取值，不能证明无外联、无环境凭据或无其他配置表。 |

消息、缓存、对象存储、外联回调和凭据相关表的元数据检索未发现除 `FILE_OBJECT`、`SYS_CONFIG_KV` 外的模式命中表；该结果不是完整运行环境隔离证明。完整原始聚合输出见 [03-hard-stop-and-isolation-output.md](03-hard-stop-and-isolation-output.md)。

## 输入留痕

- 根 `AGENTS.md`、`docs/AGENTS.md` → `docs/CLAUDE.md`、`report-analytics-center/AGENTS.md` → `report-analytics-center/CLAUDE.md`、`auth-permission-center/AGENTS.md` → `auth-permission-center/CLAUDE.md` 已完整阅读。
- `2026-08-11-auth-org-profile-group.sql`：`08cc198dbbbefa4b45863486381b6c231d84f36557ecc297d3539c234ada8843`。
- `2026-08-11-screen-scope-map-align.sql`：`450ad1475553305fcce99ef97e766d05aa0ea167a15f3bef316cd70a78dc9fda`。
- `2026-08-11-screen-scope-map-seed.sql`：`111599bd4178584f7b53dd968498dc9a7875667a2f30ab02a3de28820be35309`。
- `2026-08-11-screen-scope-map-decoupling-design.md`：`e1b90d6351cf22b8d43cb7c300012692a9f876af5a9d545b63d61d5045921126`。

## 后续停止条件

在隔离证据完整、调度和外发实际禁用、对象存储与凭据命名空间得到独立证明之前：

- 不得执行三份 SQL；
- 不得克隆、覆盖、清空、重建或备份任何数据库；
- 不得访问或改动 `yiti`；
- 不得把本次“auth/align 基线可通过”解释为执行授权；
- 若恢复验证，必须先重新执行完整只读盘点，并在报告后重新取得对任何非只读操作的明确授权。
