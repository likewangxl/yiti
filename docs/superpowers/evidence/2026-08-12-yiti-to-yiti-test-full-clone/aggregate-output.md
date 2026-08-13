# 脱敏聚合输出与限定差异

本文件只记录表名、状态、计数、哈希和工具版本；不含业务行、配置值、凭据、会话属性、对象键或外联地址。

## 预检

```text
MYSQL_CLIENT_VERSION       mysql  Ver 8.0.33 for Linux on x86_64
MYSQLDUMP_VERSION          mysqldump  Ver 8.0.33 for Linux on x86_64
SOURCE_PRE_TABLES          187
SOURCE_PRE_EST_ROWS        97305
SOURCE_PRE_BYTES           79347712
TARGET_PRE_TABLES          177
TARGET_PRE_EST_ROWS        94133
TARGET_PRE_BYTES           62390272
SOURCE_ALL_INNODB          187 / 187
TARGET_OTHER_CONNECTIONS_BEFORE_DROP  0
AVAILABLE_BYTES_APPROX     17776320512
CLONE_STARTED_AT           2026-08-12T09:09:43+08:00
CLONE_FINISHED_AT          2026-08-12T09:09:52+08:00
CLONE_PIPELINE             SUCCESS
```

## 导入后、隔离前的快照探测

```text
TARGET_TABLES              187
TABLE_META_SHA256          559f088563198a45daccf90355a8f59dc14b445631477886a8ffeec2c1a5e2af
COLUMN_META_SHA256         e199975e5ac50c5bb6c7f604c5aa0928ee75bd6aa338b33a935f3827c886a77b
INDEX_META_SHA256          26c0ecde07328f8260ca7b918167eac231b540bcd919f723654d3d6e11b821fa
FK_META_SHA256             a05128dc9ad24905ecb7693d9dd2e4a55718e0e9abec13d5d9ab9c79505572ae
TABLE_SET_SHA256           f7e5123170928a3daf8503a0d0df3d47c9120f557cc70d3aed96f2627907ef83
NON_RUNTIME_TABLES         160
NON_RUNTIME_ROW_MISMATCHES 0
NON_RUNTIME_CHECKSUM_MISMATCHES 0
NON_RUNTIME_ROW_SHA256     7454ae6702768371e4a08363762c5a269b1a320c4ee9f33bec07588393ee35e8
NON_RUNTIME_CHECKSUM_SHA256 dec5f00b11b7eb33d4427cbf698650a4861ed10067c107fccc8a696a3237ca1b
```

`NON_RUNTIME_TABLES` 排除了 27 张运行态/净化对象：Spring Session、`PT_LOCK`、`SYS_JOB_CONF`、`SYS_JOB_RUN_LOG`、`SYS_CONFIG_KV`、`FILE_OBJECT`、全部 `QRTZ_*` 状态表、Flowable 工作/外部/事件/批队列表。该限定避免将导入窗口中的调度并发与随后有意的目标净化误报为业务数据差异。

首次探测中的限定差异如下：

```text
ROW_DIFF       QRTZ_FIRED_TRIGGERS  source=0     target=1
ROW_DIFF       SYS_JOB_RUN_LOG      source=2865  target=2864
CHECKSUM_DIFF  QRTZ_FIRED_TRIGGERS
CHECKSUM_DIFF  QRTZ_SCHEDULER_STATE
CHECKSUM_DIFF  QRTZ_TRIGGERS
CHECKSUM_DIFF  SYS_JOB_CONF
CHECKSUM_DIFF  SYS_JOB_RUN_LOG
```

## 最终目标只读断言

```text
TARGET_IDENTITY                         yiti_test / utf8mb4 / utf8mb4_general_ci
TARGET_TABLE_COUNT                      187
TARGET_EVENT_COUNT                      0
TARGET_VIEW_COUNT                       0
TARGET_ROUTINE_COUNT                    0
TARGET_TRIGGER_COUNT                    0
SESSION_LOCK_QUEUE_FILE_ZERO            0
JOB_ACTIVE_OR_MANUAL_OR_NEXT_RUN        0
CONFIG_ACTIVE_OR_VALUE_PRESENT          0
QRTZ_LOCKS_RETAINED                     2
TARGET_OTHER_CONNECTIONS_AFTER_ISOLATION 0
```

## 克隆后的源端并发限制

```text
SOURCE_POST_TABLES                      188
SOURCE_POST_EST_ROWS                    97313
SOURCE_POST_BYTES                       79527936
SOURCE_CHANGE                           CUSTOMER_MARKET_MIGRATION_BAK_20260812: table appeared, aggregated rows=8
SOURCE_CHANGE                           CUST_MASTER: aggregated rows 222 -> 214
SOURCE_CHANGE                           QRTZ_SCHEDULER_STATE checksum changed
SOURCE_CHANGE                           QRTZ_TRIGGERS checksum changed
```

这些都是只读后读发现的源端并发变化，不能归因于本次克隆；本次未对 `yiti` 发出任何写 SQL。

## 凭据与临时文件清理

```text
TEMP_PARENT_MODE                        0700
TEMP_CREDENTIAL_MODE                    0600
TEMP_CREDENTIAL_CONTENT                 never logged or committed
BACKUP_CREATED                          no (user explicitly waived target preservation and rollback)
FUNCTION_SQL_EXECUTED                   no
APPLICATION_STARTED                     no
TEMP_CREDENTIAL_AND_DIRECTORY_REMOVAL   SUCCESS (the 0700 directory and every contained temporary file were removed)
```
