# 原始命令清单与输出索引（脱敏）

## 记录规则

- 所有数据库凭据均替换为 `***`；未输出任何配置值、会话属性、锁值、用户/客户/业务明细或对象键。
- 所有 MySQL 命令均显式指定 `--protocol=TCP --host=localhost --port=3306 --database=yiti_test`；没有任何 `yiti` 目标。
- 成功命令的 SQL 均以 `SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY;` 开头，并以 `COMMIT` 结束；其间仅有 `SELECT`。
- 未使用 `mysql --force`，未执行 SQL 文件、DDL、DML、`CALL`、动态 SQL、备份、克隆、覆盖、清空或重建。

## 命令时间线

| ID | 执行环境 | 命令/查询范围 | 结果 |
|---|---|---|---|
| Q0 | 沙箱 | 显式只读会话证明 + schema 元数据 | TCP socket 创建失败，未送达数据库。 |
| Q1 | 受控提权获批 | 与 Q0 完全相同的显式只读会话证明 + schema 元数据 | 成功，证明当前库/版本/只读状态。 |
| Q2 | 受控提权获批 | 11 个目标表的 tables/columns/statistics/外键/触发器/视图/例程元数据 | 成功，见 [02-structure-metadata.md](02-structure-metadata.md)。 |
| Q3 | 受控提权获批 | auth、align、seed 首个 DDL 前 hard-stop 的重复、身份、长度、目标对象和索引聚合镜像 | 成功，原始聚合输出见 [03-hard-stop-and-isolation-output.md](03-hard-stop-and-isolation-output.md)。 |
| Q4 | 受控提权获批 | 会话、锁、Quartz、任务、消息/缓存/对象存储/外联配置相关表与列元数据 | 成功，原始输出见 [03-hard-stop-and-isolation-output.md](03-hard-stop-and-isolation-output.md)。 |
| Q5 | 受控提权获批 | 上述隔离对象的行数、状态和配置键类别聚合 | 成功，原始聚合输出见 [03-hard-stop-and-isolation-output.md](03-hard-stop-and-isolation-output.md)。 |

## Q0 / Q1：完全相同的数据库命令

```bash
MYSQL_PWD=*** mysql --protocol=TCP --host=localhost --port=3306 --user=root --database=yiti_test --batch --raw --skip-column-names --execute "SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY; SELECT 'session_proof' AS section, DATABASE() AS target_database, VERSION() AS mysql_version, @@session.transaction_read_only AS session_transaction_read_only, @@session.autocommit AS session_autocommit, @@session.transaction_isolation AS transaction_isolation; SELECT 'schema_metadata' AS section, SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE(); COMMIT;"
```

Q0 原始 stderr：

```text
ERROR 2004 (HY000): Can't create TCP/IP socket (1)
```

Q0 退出状态：`1`。该错误经父代理确认属于沙箱 TCP 限制；未将其误判为数据库结论。

Q1 在受控提权获批后使用完全相同命令，原始 stdout：

```text
session_proof	yiti_test	8.0.33	1	1	REPEATABLE-READ
schema_metadata	yiti_test	utf8mb4	utf8mb4_unicode_ci
```

## Q2：结构元数据查询范围

Q2 使用与 Q1 相同的 MySQL 外层参数和显式只读事务，依次执行以下只读 `SELECT`：

1. `information_schema.tables`：`PT_ROLE`、`PT_RESOURCE`、`PT_ROLE_RESOURCE`、`AUDIT_LOG`、四张 auth 新表、`RPT_SCREEN`、`RPT_SCREEN_DATASOURCE`、`RPT_SCREEN_ACCESS_ROLE` 的表类型、引擎、排序规则；
2. `information_schema.columns`：上述对象的列序、类型、字符长度、数值精度/小数位、NULL、默认值、extra、生成表达式；
3. `information_schema.statistics`：上述对象的索引名称、唯一性、列序、前缀长度；
4. `information_schema.key_column_usage`：上述对象的外键；
5. `information_schema.triggers`：上述对象的触发器；
6. `information_schema.views`：上述对象的同名视图冲突；
7. `information_schema.routines`：三份脚本过程名的同名冲突。

原始查询输出已逐字段转录于 [02-structure-metadata.md](02-structure-metadata.md)。没有输出行级表数据。

## Q3：hard-stop 聚合查询范围

Q3 在同一只读模式中执行如下等价镜像，而非执行脚本的 `PREPARE` 或过程：

- `PT_ROLE` 的 `ROLE_CODE` 重复、现有 `R_SCREEN_*` 非数字 ID、数值 ID 上界风险；
- `PT_RESOURCE` 的 URL/METHOD/SYS_CODE 重复，以及 auth/report 四/七个固定目标资源的 ID→身份和身份→ID 双向冲突计数；
- `SYS_ADMIN` 精确计数和启用/数字 ID 条件、目标 `PT_ROLE_RESOURCE` 重复绑定、既有屏配置资源计数；
- 活跃 `RPT_SCREEN.screen_code` 重复组、目标 seed 角色/屏重复组；
- 五张待创建表的对象/列/索引计数、本期扩展列计数和关键索引摘要。

完整原始聚合输出见 [03-hard-stop-and-isolation-output.md](03-hard-stop-and-isolation-output.md)。

## Q4 / Q5：隔离元数据及聚合查询范围

Q4 只从 `information_schema` 检索 `SPRING_SESSION`、`PT_LOCK`、`SYS_JOB_CONF`、`QRTZ_*`、`FILE_OBJECT`、`SYS_CONFIG_KV` 及名称模式匹配的消息、缓存、对象存储、回调、凭据相关表/状态列；Q5 只对这些已确认对象进行 `COUNT(*)`、状态分组和 `config_key` 模式计数，未选择 `config_value` 或任一实际键名。

完整原始输出见 [03-hard-stop-and-isolation-output.md](03-hard-stop-and-isolation-output.md)。

## 非数据库输入留痕

```bash
date -Is
sha256sum docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql docs/superpowers/specs/2026-08-11-screen-scope-map-decoupling-design.md
```

```text
2026-08-12T01:53:29+08:00
08cc198dbbbefa4b45863486381b6c231d84f36557ecc297d3539c234ada8843  docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql
450ad1475553305fcce99ef97e766d05aa0ea167a15f3bef316cd70a78dc9fda  docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql
111599bd4178584f7b53dd968498dc9a7875667a2f30ab02a3de28820be35309  docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql
e1b90d6351cf22b8d43cb7c300012692a9f876af5a9d545b63d61d5045921126  docs/superpowers/specs/2026-08-11-screen-scope-map-decoupling-design.md
```
