# yiti → yiti_test 全量克隆与离线隔离证据

## 最终结论

已按用户的明确无回滚授权，将 `localhost:3306/yiti` 在 `2026-08-12T09:09:43+08:00` 至 `2026-08-12T09:09:52+08:00` 的一致性快照流式导入新建的 `yiti_test`。目标库现有 187 张基础表，且数据库内调度、会话、锁、动态配置值、对象存储元数据和 Flowable 工作队列均已净化或停用；没有启动后端、前端或任何外联服务。

这不是当前时刻 `yiti` 的持续镜像：克隆完成后，源库被其他进程并发变更。该限制已在“限定差异”中记录；本次不重克隆、不写入 `yiti`。

## 授权与边界

用户先明确表示：`yiti_test` 中的数据不需要保留，直接用 `yiti` 全量覆盖；随后对以下精确授权回复“确认”：

> 我授权现在使用 `localhost:3306` 的 `yiti` 数据，无回滚地 `DROP/CREATE` 并覆盖 `yiti_test`；允许临时使用本地 root 凭据；我接受原 `yiti_test` 不可恢复。

因此本次：

- 仅破坏性重建了 `yiti_test`；没有执行 `DROP`、DDL 或 DML 到 `yiti`。
- 用户明确放弃原 `yiti_test` 的备份与回滚；未创建目标备份，也没有恢复尝试。
- 使用本地 MySQL 客户端与 `mysqldump` 8.0.33；凭据仅存在于非仓库的临时 `0700` 目录内、`0600` 客户端文件中，未进入命令输出、仓库或本证据。
- 未执行 `2026-08-11-auth-org-profile-group.sql`、`2026-08-11-screen-scope-map-align.sql`、`2026-08-11-screen-scope-map-seed.sql` 或任何其它功能 SQL；未启动应用。

## 执行方式

破坏前的只读预检确认：`yiti` 为 187 张 InnoDB 基础表、`yiti_test` 没有其它连接；MySQL 数据目录所在文件系统尚有约 17.36 GB 可用空间。

仅在目标连接数为零时执行了下列脱敏等价命令：

```text
DROP DATABASE IF EXISTS yiti_test;
CREATE DATABASE yiti_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
mysqldump --single-transaction --quick --skip-lock-tables --skip-add-locks \
  --routines --triggers --skip-events --set-gtid-purged=OFF --no-tablespaces yiti \
  | mysql --database=yiti_test
```

导出始终以 `--single-transaction` 一致性快照读取源库；SQL 未落地为明文转储文件。`--skip-events` 明确排除了 MySQL Event，最终目标库 Event 计数也为零。

## 快照核验

导入后的结构指纹在后读探测时完全一致：187 张表的表、列、索引和外键 SHA-256 指纹均相同；表集合 SHA-256 为 `f7e5123170928a3daf8503a0d0df3d47c9120f557cc70d3aed96f2627907ef83`。

按 27 张运行态/隔离对象排除后，160 张非运行态表（含业务数据）的逐表精确行数和 MySQL `CHECKSUM TABLE` 摘要均为 0 差异。完整聚合值与限定差异见 [aggregate-output.md](aggregate-output.md)。未读取、导出或记录任何业务行、配置值、凭据、会话属性或对象键。

## 已提交的目标隔离

以下 DML 全部在一个 `yiti_test` 事务内提交，且提交前再次确认目标身份和无其它目标连接。

| 类别 | 操作 | 实际影响 |
| --- | --- | ---: |
| 会话 / 平台锁 | 清空 `SPRING_SESSION_ATTRIBUTES`、`SPRING_SESSION`、`PT_LOCK` | 0 / 0 / 0 |
| 应用调度 | 所有 `SYS_JOB_CONF` 置 `PAUSED`、`allow_manual_trigger=0`、`next_run_time=NULL` | 9 |
| Quartz | 清空已确认的运行、触发、作业、日历、暂停组、调度器状态表 | 29（其中 fired 1、cron 9、triggers 9、job details 9、scheduler state 1） |
| Quartz 静态锁 | 保留 `QRTZ_LOCKS` | 2 行 |
| Flowable / 批工作队列 | 清空 `ACT_RU_*_JOB`、外部作业、事件订阅、`FLW_RU_BATCH*` | 全部 0 |
| 动态配置 | 所有 `SYS_CONFIG_KV` 置 `DISABLED` 并把 `config_value` 设为 `NULL`（fail-closed） | 5 |
| 对象存储元数据 | 清空 `FILE_OBJECT`，不对外部对象存储发任何请求 | 8 |

最终只读断言：上述会话/锁/队列/文件元数据合计 0；活跃或可手工触发/有下一次运行时间的任务为 0；活跃或仍有值的动态配置为 0；目标 MySQL Event 为 0。

## 限定差异与限制

导入完成后的首次源/目标后读探测发现 2 张表的精确行数、5 张表的 `CHECKSUM TABLE` 不同，全部属于运行态调度范围：`QRTZ_FIRED_TRIGGERS`、`QRTZ_SCHEDULER_STATE`、`QRTZ_TRIGGERS`、`SYS_JOB_CONF`、`SYS_JOB_RUN_LOG`。这些表不在上述 160 张非运行态表的匹配集合内；其中目标运行态行已按隔离策略清除或停用。

更重要的是，随后只读复核发现源库已经被其它进程改动：表数由 187 变为 188，新增表 `CUSTOMER_MARKET_MIGRATION_BAK_20260812`（聚合行数 8），并观察到 `CUST_MASTER` 聚合行数从 222 变为 214；Quartz 状态校验和也变更。由此只能证明 `yiti_test` 是上述 09:09:43–09:09:52 的已隔离快照，不能宣称其等于后读时或当前的源库。没有因这一并发变化重克隆，也没有向 `yiti` 写入任何内容。

数据库侧隔离适用于当前未启动的离线快照。`application-remerge.yml` 仅切换数据源，仍会继承默认 Quartz、SOAP 与对象存储配置；在明确提供独立实例标识、禁用 Quartz/Flowable 异步/SOAP、独立对象存储与外联凭据的启动覆盖前，不得启动应用访问该库。本次没有启动应用，故未触发外联。

## 清理与停止条件

临时客户端凭据与其临时目录已在本证据写入后删除；该目录只包含短生命周期凭据和脱敏的聚合中间文件，不保留备份、业务数据或明文 SQL 转储。凭据删除状态记录在 [aggregate-output.md](aggregate-output.md)。

本任务至此停止：不再执行功能 SQL、不启动服务、不重克隆，也不对 `yiti` 做任何写入。
