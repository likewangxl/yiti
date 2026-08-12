# Screen-scope SQL：`yiti_test` 首轮执行停止证据

执行时间窗口：2026-08-12（Asia/Shanghai）；证据归档时间：2026-08-12T12:03:02+08:00。

## 结论

首份 SQL 已在 `localhost:3306` 的 `yiti_test` 执行，退出码为 `1`。MySQL 在
`2026-08-11-auth-org-profile-group.sql` 的 `CALL sp_auth_org_profile_group_20260811()`
处报告 `ERROR 1267`（`utf8mb4_general_ci` 与 `utf8mb4_0900_ai_ci` 的排序规则冲突）。

按照“任一失败立即停止”的约束，未执行第二份 align SQL、第三份 seed SQL，也未修改任一
SQL 文件、未启动应用或前端、未连接或写入 `yiti`。

## 已执行范围

三份批准执行顺序中的脚本为：

1. `docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql`：已执行，失败，退出码 `1`。
2. `docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql`：未执行。
3. `docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql`：未执行。

所有实际 MySQL 调用均固定使用：

```text
--protocol=TCP --host=localhost --port=3306 --database=yiti_test
```

未使用 `--force`、自动重连、吞错包装器或任何 `yiti` 数据库参数。

## 执行前最小只读身份确认

在显式只读事务中读取到的脱敏身份字段如下：

```text
DATABASE()       yiti_test
server_uuid      d3a209c4-42bd-11f1-bb1f-000c299f5629
hostname         ubuntu
port             3306
version          8.0.33
read_only        0
super_read_only  0
```

实际写会话在 `SOURCE` 前设置了脚本要求的六个会话变量。其中目标
`server_uuid`、`hostname`、`port`、`schema` 均取自上述同一目标的实际值；工单标识为
`USER_DIRECT_AUTH_20260812`，哈希变量使用直接授权文本的 SHA-256。该哈希满足脚本的
64 位十六进制格式检查，但不被表述为外部签名 manifest。

## 首个失败原始输出（已脱敏）

```text
ERROR 1267 (HY000) at line 1398 in file: 'docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql':
Illegal mix of collations (utf8mb4_general_ci,IMPLICIT) and (utf8mb4_0900_ai_ci,IMPLICIT) for operation '='
AUTH deploy zero-DDL preflight passed
AUTH deploy zero-DDL preflight passed
EXIT_CODE 1
```

文件第 1398 行只是过程调用：

```sql
CALL sp_auth_org_profile_group_20260811();
```

因此服务器输出没有给出过程内的精确 SQL 语句位置。

## 失败后只读盘点（仅聚合/元数据）

失败后使用新的显式只读事务确认，目标仍为同一 `yiti_test` 身份；结果如下：

```text
PT_ORG_PROFILE                 exists
PT_ORG_GROUP                   exists
PT_ORG_GROUP_MEMBER            exists
PT_ROLE_ORG_GROUP              exists
AUDIT_LOG structured columns   6
AUDIT_LOG target index columns 2
PT_ROLE role-code unique index 1
expected screen roles           2
expected auth resources         0
auth procedure remains          1
```

这证明第一份脚本在出错前已产生不可由普通事务回滚的 DDL 和两个角色写入；它尚未插入
7 个本期 auth 资源，且结尾的 `DROP PROCEDURE` 未执行。

为分析冲突而做的最后一次只读元数据检查结果：

```text
session charset/collation       utf8mb4 / utf8mb4_0900_ai_ci
database charset/collation      utf8mb4 / utf8mb4_general_ci
PT_RESOURCE key columns         utf8mb4_general_ci
PT_ROLE key columns             utf8mb4_general_ci
```

没有读取任何用户、客户、机构成员、配置值、会话属性、对象键、凭据或业务行明细。

## 事实、推测与风险

### 事实

- 第一份 SQL 的零 DDL guard 已通过，随后在过程内失败；退出码为 `1`。
- auth 脚本已部分生效，具体范围见上方失败后盘点。
- align 和 seed 均未执行；`yiti` 未被连接或写入。

### 推测

- 错误文本与脚本中的临时资源表/既有 `PT_RESOURCE` 连接比较相符：脚本以
  `DEFAULT CHARSET=utf8mb4` 创建临时表，而目标库及既有资源列为
  `utf8mb4_general_ci`；在当前会话 `utf8mb4_0900_ai_ci` 下，二者的隐式比较很可能在
  最后的资源补齐步骤触发 `ERROR 1267`。
- MySQL 未返回过程内部语句号，因此上述是基于排序规则元数据、部分执行位置和资源数为零的
  推断，不是已由服务器逐句诊断确认的结论。

### 风险与后续停止条件

- 由于 DDL 隐式提交，不能把本次失败当作已自动回滚；当前 `yiti_test` 是部分应用状态。
- 原样重跑第一份 SQL 很可能再次触发同一排序规则冲突；在修复获授权前，不应重跑。
- align 依赖 auth 完成，尤其依赖 auth 资源/角色基线；seed 又依赖前两份，因此不得跳过顺序。
- 后续只能在明确授权的恢复或 SQL 修订方案下处理；本次任务不修改 SQL，也不尝试补偿、反向
  DDL/DML 或继续执行。

## 输入哈希

```text
adc6485f27b7889947c67c58f0aa49a6701e73fb114fbb08155f3b4f004f4ed8  2026-08-11-auth-org-profile-group.sql
81310dbb0232f23e5374207c93d427512429dad7012ce08ea39d6e4a1dc33349  2026-08-11-screen-scope-map-align.sql
5c1b22ecdd8c74480e0607237d58316a71e762383cab1dead4c7cfe20798d500  2026-08-11-screen-scope-map-seed.sql
```
