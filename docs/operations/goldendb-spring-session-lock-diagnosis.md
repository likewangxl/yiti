# GoldenDB `SPRING_SESSION` 锁等待排查 SQL

## 1. 适用范围

本文用于排查以下异常：

```text
org.springframework.dao.CannotAcquireLockException
Lock wait timeout exceeded; try restarting transaction
UPDATE SPRING_SESSION ... WHERE PRIMARY_ID = ?
```

目标生产数据库为 `yiti`，数据库产品为 GoldenDB。

本文所有可执行语句均为只读查询，不包含数据写入、DDL、`KILL`、事务提交回滚或数据库参数修改。由于仓库禁止在可执行 `.sql` 文件中放置独立检查查询，本文以 Markdown 运维文档形式交付。

## 2. 已确认事实与排查边界

- 异常表示应用更新 `yiti.SPRING_SESSION` 时，等待不兼容的数据库锁超过了锁等待超时时间。
- 单凭异常堆栈不能确认生产环境的阻塞者是 Session 清理、同一 Session 并发写入，还是未提交的长事务。
- 锁等待链是瞬时证据。应在异常正在发生、尚未超时的几十秒内执行第 4 节查询；事务结束后，锁等待记录会消失。
- GoldenDB 版本和部署形态不同，可用的 MySQL 兼容锁视图可能不同。第 4.1 和 4.2 节二选一执行。
- 若当前数据库账号只能查看自己的连接，则无法据此确定其他应用连接中的阻塞事务，需要 DBA 补充权限或代为查询。

## 3. 确认环境

```sql
-- current_database 必须为 yiti，不能是 yiti_test。
SELECT
    NOW()           AS capture_time,
    VERSION()       AS database_version,
    DATABASE()      AS current_database,
    CONNECTION_ID() AS current_connection_id;

-- 查看当前锁等待超时时间，用于与应用报错耗时进行对照。
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';
```

检查 GoldenDB 当前支持哪套锁视图：

```sql
-- 若返回 data_locks、data_lock_waits，优先使用第 4.1 节。
SHOW TABLES FROM performance_schema LIKE 'data_lock%';

-- 若存在 INNODB_LOCKS、INNODB_LOCK_WAITS，使用第 4.2 节。
SHOW TABLES FROM information_schema LIKE 'INNODB_LOCK%';
```

如果视图存在但查询仍报权限错误，应保存错误信息并交给 DBA，不能把“无权限”解释成“没有锁等待”。

## 4. 抓取等待—阻塞链

### 4.1 `performance_schema` 新版锁视图

仅在 `performance_schema.data_lock_waits` 和 `performance_schema.data_locks` 均存在时执行：

```sql
SELECT
    NOW() AS capture_time,

    dlw.REQUESTING_ENGINE_TRANSACTION_ID AS waiting_trx_id,
    wt.trx_mysql_thread_id                AS waiting_thread_id,
    wt.trx_started                        AS waiting_trx_started,
    wt.trx_wait_started                   AS lock_wait_started,
    TIMESTAMPDIFF(
        SECOND,
        COALESCE(wt.trx_wait_started, wt.trx_started),
        NOW()
    )                                     AS wait_seconds,
    wp.USER                               AS waiting_user,
    wp.HOST                               AS waiting_host,
    wp.DB                                 AS waiting_database,
    LEFT(COALESCE(wt.trx_query, wp.INFO), 1000)
                                            AS waiting_sql,

    dlw.BLOCKING_ENGINE_TRANSACTION_ID    AS blocking_trx_id,
    bt.trx_mysql_thread_id                AS blocking_thread_id,
    bt.trx_started                        AS blocking_trx_started,
    TIMESTAMPDIFF(SECOND, bt.trx_started, NOW())
                                            AS blocking_seconds,
    bp.USER                               AS blocking_user,
    bp.HOST                               AS blocking_host,
    bp.DB                                 AS blocking_database,
    LEFT(COALESCE(bt.trx_query, bp.INFO), 1000)
                                            AS blocking_sql,

    rl.OBJECT_SCHEMA                      AS locked_schema,
    rl.OBJECT_NAME                        AS locked_table,
    rl.INDEX_NAME                         AS locked_index,
    rl.LOCK_TYPE                          AS requesting_lock_type,
    rl.LOCK_MODE                          AS requesting_lock_mode,
    bl.LOCK_MODE                          AS blocking_lock_mode,
    rl.LOCK_DATA                          AS locked_record
FROM performance_schema.data_lock_waits dlw
JOIN performance_schema.data_locks rl
  ON rl.ENGINE = dlw.ENGINE
 AND rl.ENGINE_LOCK_ID = dlw.REQUESTING_ENGINE_LOCK_ID
JOIN performance_schema.data_locks bl
  ON bl.ENGINE = dlw.ENGINE
 AND bl.ENGINE_LOCK_ID = dlw.BLOCKING_ENGINE_LOCK_ID
LEFT JOIN information_schema.innodb_trx wt
  ON wt.trx_id = dlw.REQUESTING_ENGINE_TRANSACTION_ID
LEFT JOIN information_schema.innodb_trx bt
  ON bt.trx_id = dlw.BLOCKING_ENGINE_TRANSACTION_ID
LEFT JOIN information_schema.PROCESSLIST wp
  ON wp.ID = wt.trx_mysql_thread_id
LEFT JOIN information_schema.PROCESSLIST bp
  ON bp.ID = bt.trx_mysql_thread_id
WHERE rl.OBJECT_SCHEMA = 'yiti'
  AND rl.OBJECT_NAME IN (
      'SPRING_SESSION',
      'SPRING_SESSION_ATTRIBUTES'
  )
ORDER BY wait_seconds DESC;
```

### 4.2 `information_schema` 旧版兼容锁视图

如果第 4.1 节的表或字段不存在，且 `INNODB_LOCK_WAITS`、`INNODB_LOCKS`、`INNODB_TRX` 可用，执行：

```sql
SELECT
    NOW()                                  AS capture_time,

    w.requesting_trx_id                    AS waiting_trx_id,
    rt.trx_mysql_thread_id                 AS waiting_thread_id,
    rt.trx_started                         AS waiting_trx_started,
    rt.trx_wait_started                    AS lock_wait_started,
    TIMESTAMPDIFF(
        SECOND,
        COALESCE(rt.trx_wait_started, rt.trx_started),
        NOW()
    )                                      AS wait_seconds,
    rp.USER                                AS waiting_user,
    rp.HOST                                AS waiting_host,
    rp.DB                                  AS waiting_database,
    LEFT(COALESCE(rt.trx_query, rp.INFO), 1000)
                                             AS waiting_sql,

    w.blocking_trx_id                      AS blocking_trx_id,
    bt.trx_mysql_thread_id                 AS blocking_thread_id,
    bt.trx_started                         AS blocking_trx_started,
    TIMESTAMPDIFF(SECOND, bt.trx_started, NOW())
                                             AS blocking_seconds,
    bp.USER                                AS blocking_user,
    bp.HOST                                AS blocking_host,
    bp.DB                                  AS blocking_database,
    LEFT(COALESCE(bt.trx_query, bp.INFO), 1000)
                                             AS blocking_sql,

    rl.lock_table                          AS locked_table,
    rl.lock_index                          AS locked_index,
    rl.lock_type                           AS requesting_lock_type,
    rl.lock_mode                           AS requesting_lock_mode,
    bl.lock_mode                           AS blocking_lock_mode,
    rl.lock_data                           AS locked_record
FROM information_schema.innodb_lock_waits w
JOIN information_schema.innodb_trx rt
  ON rt.trx_id = w.requesting_trx_id
JOIN information_schema.innodb_trx bt
  ON bt.trx_id = w.blocking_trx_id
LEFT JOIN information_schema.innodb_locks rl
  ON rl.lock_id = w.requested_lock_id
LEFT JOIN information_schema.innodb_locks bl
  ON bl.lock_id = w.blocking_lock_id
LEFT JOIN information_schema.PROCESSLIST rp
  ON rp.ID = rt.trx_mysql_thread_id
LEFT JOIN information_schema.PROCESSLIST bp
  ON bp.ID = bt.trx_mysql_thread_id
WHERE rl.lock_table LIKE '%SPRING_SESSION%'
ORDER BY wait_seconds DESC;
```

## 5. 查询活跃事务

即使阻塞连接已经显示为 `Sleep`，事务仍可能没有提交。此时 `blocking_sql` 可能为 `NULL`，但阻塞事务仍会出现在 `INNODB_TRX` 中。

```sql
SELECT
    NOW() AS capture_time,
    trx_id,
    trx_state,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS trx_age_seconds,
    trx_wait_started,
    TIMESTAMPDIFF(SECOND, trx_wait_started, NOW()) AS wait_seconds,
    trx_mysql_thread_id,
    trx_tables_locked,
    trx_rows_locked,
    trx_rows_modified,
    LEFT(trx_query, 1000) AS current_sql
FROM information_schema.innodb_trx
ORDER BY trx_started;
```

重点关注：

- `trx_age_seconds` 明显偏大；
- `trx_rows_locked` 大于零；
- `current_sql` 为 `NULL`，但事务长期存在；
- `trx_mysql_thread_id` 与第 4 节查出的 `blocking_thread_id` 相同。

## 6. 查询相关连接

```sql
SELECT
    NOW() AS capture_time,
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    LEFT(INFO, 1000) AS current_sql
FROM information_schema.PROCESSLIST
WHERE DB = 'yiti'
  AND (
      INFO LIKE '%SPRING_SESSION%'
      OR STATE LIKE '%lock%'
  )
ORDER BY TIME DESC;
```

同时保存完整进程快照：

```sql
SHOW FULL PROCESSLIST;
```

如果只能看到自己的连接，说明当前账号通常缺少查看其他会话所需的权限，本次结果不足以确定阻塞者。

## 7. 查询 InnoDB 状态

```sql
SHOW ENGINE INNODB STATUS;
```

重点搜索：

- `TRANSACTIONS`
- `LOCK WAIT`
- `WAITING FOR THIS LOCK TO BE GRANTED`
- `LATEST DETECTED DEADLOCK`

锁等待超时不一定形成死锁，因此没有 `LATEST DETECTED DEADLOCK` 不代表没有锁竞争。

## 8. 核对 Session 表索引

```sql
SHOW INDEX FROM yiti.SPRING_SESSION;
```

正常应重点核对：

- `PRIMARY_ID` 主键；
- `SESSION_ID` 唯一索引；
- `EXPIRY_TIME` 索引；
- `PRINCIPAL_NAME` 索引。

`EXPIRY_TIME` 索引缺失可能导致过期 Session 清理扫描范围扩大，但不能仅凭索引情况断言它就是本次阻塞者。

## 9. 根因判定口径

| 查询结果 | 可支持的判断 |
|---|---|
| 等待 SQL 是 `UPDATE SPRING_SESSION ... WHERE PRIMARY_ID = ...`，阻塞 SQL 是 `DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ...` | 基本确认 Spring Session 过期清理与会话保存发生锁竞争 |
| 等待和阻塞 SQL 都是 `UPDATE SPRING_SESSION`，且来自不同应用主机 | 倾向于多实例或同一 Session 并发更新 |
| `blocking_sql` 为 `NULL`，阻塞事务持续很久，对应进程为 `Sleep` | 倾向于连接空闲但事务没有提交或回滚 |
| 报错发生时没有抓到等待链 | 只能说明当前查询没有取得证据；可能是执行太晚、权限不足，或代理/DN 可见性限制，不能判定“没有锁” |

当前异常截图只能确认 `SPRING_SESSION` 更新发生锁等待超时。生产环境中的具体阻塞者必须以第 4 节抓到的实时等待—阻塞链为准。

## 10. GoldenDB 特有边界与 DBA 升级信息

GoldenDB 可能区分单 DN 锁问题和全局分布式锁问题。若报错正在发生，但上述兼容视图仍没有返回等待链，应向 DBA 提供：

- 精确报错时间及持续时间；
- 应用 traceId；
- `waiting_trx_id`、`waiting_thread_id`；
- 应用连接的 `HOST`；
- `SHOW FULL PROCESSLIST` 和 `SHOW ENGINE INNODB STATUS` 原始结果；
- GoldenDB 版本及当前查询账号的可见权限范围。

由 DBA 继续在 GoldenDB Insight 或实际受影响 DN 上核对单 DN/全局分布式锁信息。不要在没有真实阻塞链的情况下直接清空 `SPRING_SESSION`、盲目增加 `innodb_lock_wait_timeout`，或终止数据库连接。

## 11. 参考资料

- [GoldenDB 官方技术资料](https://www.zte.com.cn/content/dam/zte-site/res-www-zte-com-cn/mediares/golden_db/pdf/report2024010303.pdf)
- [MySQL 8 Performance Schema Data Lock Waits](https://dev.mysql.com/doc/refman/8.0/en/performance-schema-data-lock-waits-table.html)
- [MySQL 5.7 InnoDB Transaction and Locking Information](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/innodb-information-schema-transactions.html)
