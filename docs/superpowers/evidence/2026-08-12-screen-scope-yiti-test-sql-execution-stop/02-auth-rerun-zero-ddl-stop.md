# Auth 修复后重跑：零 DDL guard 停止证据

执行时间：2026-08-12；归档时间：2026-08-12T12:13:35+08:00。

## 结论

在 Terra 提供排序规则最小修复后，已在 `localhost:3306/yiti_test` 重新从 auth 脚本开始执行。
本次仍以退出码 `1` 停止，但失败发生在 auth 脚本的零 DDL guard；因此本轮没有进入过程内
DDL/DML，align 与 seed 均未执行。

## 执行前只读身份与部分状态

```text
DATABASE()       yiti_test
server_uuid      d3a209c4-42bd-11f1-bb1f-000c299f5629
hostname         ubuntu
port             3306
transaction RO    1
partial tables    4
screen roles      2
auth resources    0
auth procedure    1
```

写会话在 `SOURCE` 前设置了同一实际目标的四项身份变量，以及脚本必填的非空工单/哈希变量；
未使用 `--force` 或自动重连。

## 原始失败输出（脱敏）

```text
ERROR 1146 (42S02) at line 529 in file: 'docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql':
Table 'yiti_test.__auth_preflight_stop_0e9d8a11960411f1937e000c295dbb8c__' doesn't exist
AUTH deploy preflight: AUDIT_LOG 结构化字段完整列定义异常
EXIT_CODE 1
```

`ERROR 1146` 是脚本 fail-close guard 为已发现的 preflight 违规而刻意查询不存在对象所产生的
停止信号；首个业务阻塞是其上一行的 `AUDIT_LOG` 结构化字段检查失败。

## 只读元数据核验

失败后只读查询显示六个结构化字段和目标索引已存在，且类型、可空性和默认值与脚本实际
`ALTER TABLE` 定义相符：

```text
target_type       column_type=varchar(100), YES, default NULL, extra empty, utf8mb4_general_ci
target_id         column_type=varchar(128), YES, default NULL, extra empty, utf8mb4_general_ci
before_snapshot   column_type=text, YES, default NULL, extra empty, utf8mb4_general_ci, character_maximum_length 65535
after_snapshot    column_type=text, YES, default NULL, extra empty, utf8mb4_general_ci, character_maximum_length 65535
added_items       column_type=text, YES, default NULL, extra empty, utf8mb4_general_ci, character_maximum_length 65535
removed_items     column_type=text, YES, default NULL, extra empty, utf8mb4_general_ci, character_maximum_length 65535
IDX_AUDIT_LOG_TARGET  non-unique (target_type, target_id)
```

没有读取 `AUDIT_LOG` 的任意行、快照内容、用户、客户、配置或凭据。

## 已确认的脚本缺陷

auth 零 DDL guard 的 `expected_audit_column`（约第 487–492 行）把四个 `TEXT` 字段的
`character_maximum_length` 期望值写为 `NULL`，随后用空安全等于比较实际元数据。当前 MySQL
8.0.33 对 `TEXT` 返回 `character_maximum_length=65535`，所以已由同一脚本创建的正确字段会被
错误地判为异常。

这是由脚本文本和上述只读元数据直接确认的原因，不是排序规则问题。过程内部对同样 `TEXT`
字段的逐项检查没有比较 `character_maximum_length`，因此需要修复的是零 DDL guard 的该段判定，
而非当前 `AUDIT_LOG` 结构。

## 停止边界

- 本轮 auth 未完成；auth 资源仍为 `0`，故不具备进入 align 的条件。
- 第二、第三份 SQL 未执行；未访问或改动 `yiti`；未启动应用或前端。
- 不修改 SQL、不手工补写资源、不做回滚或补偿。只有 SQL 修复获得确认后，才可从 auth 重新开始。

## 当前脚本哈希

```text
646848db808755ee1b03e46a58f6f083690213fca1494ce2529e6a08c6d81005  2026-08-11-auth-org-profile-group.sql
81310dbb0232f23e5374207c93d427512429dad7012ce08ea39d6e4a1dc33349  2026-08-11-screen-scope-map-align.sql
5c1b22ecdd8c74480e0607237d58316a71e762383cab1dead4c7cfe20798d500  2026-08-11-screen-scope-map-seed.sql
```
