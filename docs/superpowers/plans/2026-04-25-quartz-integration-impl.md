# Quartz 整合 sys_job_conf — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **每个 task 一个 implementer + 1 个合并 reviewer**（按子项目 A 末期节奏，避免子代理数量爆炸）。

**Goal:** 将 performance-engine-center 的 3 个 `@Scheduled` 定时任务整合到嵌入式 Quartz 2.3.2 集群模式，统一通过 system-governance-center 的 sys_job_conf 表管理；删除 ShedLock；精简 JobApi 至 1 方法；JobController 5 端点真正可用。

**Architecture:** 嵌入式 Quartz 2.3.2 集群（`isClustered=true` + JDBC JobStore + 11 张 QRTZ_* 同库 onepl）；3 业务 Job 类（保留 run() 方法）+ 3 Quartz 包装 Job 类（implements `org.quartz.Job`）解耦设计；JobListener 自动写 sys_job_run_log；misfire 分级策略（KPI=`FIRE_ONCE_NOW` / 清理 Job=`DO_NOTHING`）；多节点防重由 QRTZ_LOCKS 表行锁保证。

**Tech Stack:** Spring Boot 3.2.3 + JDK 17 + spring-boot-starter-quartz（自带 Quartz 2.3.2）+ MySQL 8.0 + 现有 Druid 连接池

**Spec:** [docs/superpowers/specs/2026-04-25-quartz-integration-design.md](../specs/2026-04-25-quartz-integration-design.md)

**Worktree:** `D:\Project\oneplate\.claude\worktrees\refactor-quartz-job`
**Branch:** `refactor/quartz-job-integration`（基于 master `af66ccd`）

**与 Spec 的命名对齐说明**：spec 中称为 `JobConfService` 的服务，**实际类名为 `JobService`**（已存在 `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/JobService.java`）。本 plan 一律使用真实类名 `JobService`。

---

## File Structure（变更总览）

### 新建文件（11 个 + N 个测试）

| 文件 | 模块 | 责任 |
|------|------|------|
| `system-governance-center/src/main/java/.../config/QuartzConfig.java` | governance | Quartz 调度器 Bean 装配（SchedulerFactoryBean + JobFactory + JobListener 注册） |
| `system-governance-center/src/main/java/.../config/AutowiringSpringBeanJobFactory.java` | governance | Quartz Job 实例化时通过 Spring 完成 @Autowired 字段注入 |
| `system-governance-center/src/main/java/.../listener/JobExecutionLogger.java` | governance | implements JobListener，自动写 sys_job_run_log + 更新 sys_job_conf.last_run_time |
| `performance-engine-center/src/main/java/.../job/quartz/DailyKpiCalcQuartzJob.java` | performance | implements Job，execute() 调 dailyKpiCalcJob.run() |
| `performance-engine-center/src/main/java/.../job/quartz/SysControlCleanupQuartzJob.java` | performance | 同上（业务 run() 返回 int 显式忽略） |
| `performance-engine-center/src/main/java/.../job/quartz/PerfRunTaskCleanupQuartzJob.java` | performance | 同上 |
| `docs/schema/ddl-quartz.sql` | docs | Quartz 2.3.2 官方 11 张 QRTZ_* 表 MySQL DDL |
| `docs/schema/migrations/2026-04-25-quartz-integration.sql` | docs | 完整迁移脚本（sys_job_conf 加字段 + sys_job_run_log 加字段 + INSERT 3 条业务 Job 记录） |

测试文件（≥45 测试）：
- `governance/.../config/AutowiringSpringBeanJobFactoryTest.java`（2 测试）
- `governance/.../config/QuartzConfigIntegrationIT.java`（1 集成测试）
- `governance/.../listener/JobExecutionLoggerTest.java`（5 测试）
- `governance/.../service/JobServiceTest.java`（重写或扩展，6+ 测试）
- `governance/.../service/JobServiceQuartzIntegrationIT.java`（3 集成测试）
- `governance/.../controller/JobControllerTest.java`（重写或扩展，10 测试）
- `performance/.../job/quartz/*QuartzJobTest.java`（3 个文件 × 2 测试 = 6 测试）
- `performance/.../job/*JobTest.java`（已存在，调整以匹配重构后行为）

### 修改文件（核心 ~12 个）

| 文件 | 改动类型 |
|------|---------|
| 根 `pom.xml` | 删除 ShedLock dependencyManagement |
| `performance-engine-center/pom.xml` | 删除 shedlock 2 项 + 新增 spring-boot-starter-quartz |
| `system-governance-center/pom.xml` | 新增 spring-boot-starter-quartz |
| `performance-engine-center/.../job/DailyKpiCalcJob.java` | 删 @Scheduled/@SchedulerLock/@ConditionalOnProperty/scheduled() 包装方法，保留 @Component + run() |
| `performance-engine-center/.../job/SysControlCleanupJob.java` | 同上（保留 @Value keep-count 与 int run()） |
| `performance-engine-center/.../job/PerfRunTaskCleanupJob.java` | 同上（保留 @Value retention-days 与 int run()） |
| `system-governance-center/.../api/JobApi.java` | 删 startJobRun/completeJobRun/failJobRun |
| `system-governance-center/.../facade/JobFacade.java` | 删对应 3 方法 + 相关测试 |
| `system-governance-center/.../service/JobService.java` | 重构：CRUD + Scheduler 联动（scheduleJob/rescheduleJob/pauseJob/resumeJob/triggerJobNow + @PostConstruct syncJobsOnStartup） |
| `system-governance-center/.../controller/JobController.java` | 5 端点 service 调用真正生效（trigger/pause/resume 不再是装饰品） |
| `bootstrap/src/main/resources/application.yml` | 新增 quartz 配置（grep 验证旧 perf.job/shedlock 配置 0 行——经预验证已为 0） |
| `docs/schema/ddl-governance.sql` | sys_job_conf + sys_job_run_log 字段同步注释（标注 V1.6 quartz 引入） |

### 删除文件（1 个）

| 文件 |
|------|
| `performance-engine-center/src/main/java/.../config/ShedLockConfig.java` |

---

## Phase 总览

| Phase | 范围 | Task 数 | 依赖关系 |
|-------|------|---------|---------|
| **P1 基础设施** | pom 依赖 + DDL + 迁移脚本 + JobFactory + JobListener + QuartzConfig | 7 | 内部按顺序，P1 全部完成才进入 P2 |
| **P2 Job 改造** | 3 业务 Job 删旧注解 + 3 Quartz 包装 Job 新增 | 6 | 依赖 P1.1（pom）+ P1.4（JobFactory） |
| **P3 服务层** | JobService 重构 + 5 端点真正实现 + 集成测试 | 7 | 依赖 P1.5（JobListener）+ P1.6（QuartzConfig） |
| **P4 清理与文档** | 删 ShedLock + 精简 JobApi + 文档更新 + 全量验证 | 6 | 依赖前 3 Phase 全部完成 |
| **总计** | | **26 task** | |

**子代理派发节奏**（按用户确认的子项目 A 末期 B 节奏）：
- 每个 task 派发 **1 个 implementer**（opus 1m）
- 完成后派发 **1 个合并 spec+quality reviewer**（opus 1m）
- reviewer 通过则进入下一 task；reviewer 标 issue 则返工

**Phase 推送策略**（按 user memory `feedback_phase_commit_push.md`）：
- P1/P2/P3 末分别 push remote（refactor/quartz-job-integration 分支）
- P4 完成后**不**合并 master（按 user memory：3 子项目（A/B/C）全部完成才合并 master）

---

# Phase 1：基础设施（7 task）

## Task P1.1: Maven 依赖调整

**Files:**
- Modify: `pom.xml`（根，删除 ShedLock dependencyManagement 条目；新增 spring-boot-starter-quartz 不需要——Spring Boot 3.2.3 已管理）
- Modify: `system-governance-center/pom.xml`（新增 spring-boot-starter-quartz）
- Modify: `performance-engine-center/pom.xml`（新增 spring-boot-starter-quartz；**本 task 不删 ShedLock**，留到 P4.1）

- [ ] **Step 1: 检查根 pom.xml 是否有 ShedLock dependencyManagement**

```bash
grep -n "shedlock" pom.xml
```
预期：0 行（前期探索确认根 pom 无 ShedLock 版本声明，仅 performance-engine-center 直接声明 5.13.0）。若有，记录行号。

- [ ] **Step 2: 在 system-governance-center/pom.xml 新增 spring-boot-starter-quartz**

在 `<!-- MyBatis -->` 块**之后**插入：
```xml
<!--
    Quartz 调度引擎（V1.6 引入）
    Spring Boot 3.2.3 默认管理 Quartz 2.3.2 + JDBC JobStore 集群模式（isClustered=true）
    用途：嵌入式调度引擎，与业务模块同 JVM；通过 sys_job_conf 表统一管理 Job 配置
-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

- [ ] **Step 3: 在 performance-engine-center/pom.xml 新增 spring-boot-starter-quartz**

在 `<!-- MyBatis -->` 块**之后**插入相同内容（注释微调："performance-engine 持有 3 个业务 Job + 3 Quartz 包装类，需要 Quartz API 编译"）。

- [ ] **Step 4: 验证编译通过**

```bash
mvn -pl system-governance-center,performance-engine-center -am compile -DskipTests -q
```
预期：BUILD SUCCESS。验证 quartz-2.3.2.jar 已下载（`mvn -pl performance-engine-center dependency:tree | grep quartz`）。

- [ ] **Step 5: Commit**

```bash
git add pom.xml system-governance-center/pom.xml performance-engine-center/pom.xml
git commit -m "feat(quartz-B): P1.1 add spring-boot-starter-quartz dependency

- system-governance-center: Scheduler/JobListener/QuartzConfig 编译依赖
- performance-engine-center: 3 业务 Job + 3 Quartz 包装类编译依赖
- Spring Boot 3.2.3 自带 quartz 2.3.2，无需显式 version
- ShedLock 依赖暂保留，P4.1 统一删除

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P1.2: 创建 docs/schema/ddl-quartz.sql（11 张 QRTZ_* 表 DDL）

**Files:**
- Create: `docs/schema/ddl-quartz.sql`

**说明**：DDL 来源于 Quartz 2.3.2 官方包内 `org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql`。本 task 直接落盘官方版本（已验证字符集 utf8mb4 兼容；TYPE=InnoDB → 改为 ENGINE=InnoDB；新增表注释）。

- [ ] **Step 1: 创建 ddl-quartz.sql 文件**

完整内容（约 200 行，包含 11 张表 DDL）：

```sql
-- ============================================================
-- Quartz 2.3.2 集群模式 DDL（MySQL 8.0 + InnoDB + utf8mb4）
-- 来源: org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql
-- 适配: TYPE=InnoDB → ENGINE=InnoDB，字符集 utf8mb4_general_ci
-- 用途: 嵌入式 Quartz 调度引擎持久化层（与业务库 onepl 同库）
-- 引入版本: V1.6（2026-04-25 quartz 整合子项目 B）
-- ============================================================

-- 先 DROP 防止脏数据残留（首次部署可注释掉）
-- DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
-- DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
-- DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
-- DROP TABLE IF EXISTS QRTZ_LOCKS;
-- DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
-- DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
-- DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
-- DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
-- DROP TABLE IF EXISTS QRTZ_TRIGGERS;
-- DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
-- DROP TABLE IF EXISTS QRTZ_CALENDARS;

CREATE TABLE QRTZ_JOB_DETAILS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    JOB_NAME VARCHAR(200) NOT NULL,
    JOB_GROUP VARCHAR(200) NOT NULL,
    DESCRIPTION VARCHAR(250) NULL,
    JOB_CLASS_NAME VARCHAR(250) NOT NULL,
    IS_DURABLE VARCHAR(1) NOT NULL,
    IS_NONCONCURRENT VARCHAR(1) NOT NULL,
    IS_UPDATE_DATA VARCHAR(1) NOT NULL,
    REQUESTS_RECOVERY VARCHAR(1) NOT NULL,
    JOB_DATA BLOB NULL,
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz Job 元信息';

CREATE TABLE QRTZ_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    JOB_NAME VARCHAR(200) NOT NULL,
    JOB_GROUP VARCHAR(200) NOT NULL,
    DESCRIPTION VARCHAR(250) NULL,
    NEXT_FIRE_TIME BIGINT(13) NULL,
    PREV_FIRE_TIME BIGINT(13) NULL,
    PRIORITY INTEGER NULL,
    TRIGGER_STATE VARCHAR(16) NOT NULL,
    TRIGGER_TYPE VARCHAR(8) NOT NULL,
    START_TIME BIGINT(13) NOT NULL,
    END_TIME BIGINT(13) NULL,
    CALENDAR_NAME VARCHAR(200) NULL,
    MISFIRE_INSTR SMALLINT(2) NULL,
    JOB_DATA BLOB NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME, JOB_NAME, JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz Trigger 主表';

CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    REPEAT_COUNT BIGINT(7) NOT NULL,
    REPEAT_INTERVAL BIGINT(12) NOT NULL,
    TIMES_TRIGGERED BIGINT(10) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 简单触发器（本项目暂不用）';

CREATE TABLE QRTZ_CRON_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    CRON_EXPRESSION VARCHAR(120) NOT NULL,
    TIME_ZONE_ID VARCHAR(80),
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz Cron 触发器扩展';

CREATE TABLE QRTZ_SIMPROP_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    STR_PROP_1 VARCHAR(512) NULL,
    STR_PROP_2 VARCHAR(512) NULL,
    STR_PROP_3 VARCHAR(512) NULL,
    INT_PROP_1 INT NULL,
    INT_PROP_2 INT NULL,
    LONG_PROP_1 BIGINT NULL,
    LONG_PROP_2 BIGINT NULL,
    DEC_PROP_1 NUMERIC(13,4) NULL,
    DEC_PROP_2 NUMERIC(13,4) NULL,
    BOOL_PROP_1 VARCHAR(1) NULL,
    BOOL_PROP_2 VARCHAR(1) NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 自定义属性触发器（本项目暂不用）';

CREATE TABLE QRTZ_BLOB_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    BLOB_DATA BLOB NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz Blob 触发器（本项目暂不用）';

CREATE TABLE QRTZ_CALENDARS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    CALENDAR_NAME VARCHAR(200) NOT NULL,
    CALENDAR BLOB NOT NULL,
    PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 业务日历';

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 暂停触发器组';

CREATE TABLE QRTZ_FIRED_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    ENTRY_ID VARCHAR(95) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    INSTANCE_NAME VARCHAR(200) NOT NULL,
    FIRED_TIME BIGINT(13) NOT NULL,
    SCHED_TIME BIGINT(13) NOT NULL,
    PRIORITY INTEGER NOT NULL,
    STATE VARCHAR(16) NOT NULL,
    JOB_NAME VARCHAR(200) NULL,
    JOB_GROUP VARCHAR(200) NULL,
    IS_NONCONCURRENT VARCHAR(1) NULL,
    REQUESTS_RECOVERY VARCHAR(1) NULL,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 已触发触发器（执行中状态）';

CREATE TABLE QRTZ_SCHEDULER_STATE (
    SCHED_NAME VARCHAR(120) NOT NULL,
    INSTANCE_NAME VARCHAR(200) NOT NULL,
    LAST_CHECKIN_TIME BIGINT(13) NOT NULL,
    CHECKIN_INTERVAL BIGINT(13) NOT NULL,
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 调度器实例心跳';

CREATE TABLE QRTZ_LOCKS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME VARCHAR(40) NOT NULL,
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz 集群锁（TRIGGER_ACCESS / STATE_ACCESS）';

-- 索引（来自官方 SQL）
CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS(SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS(SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS(SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS(SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS(SCHED_NAME, CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS(SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS(SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, TRIGGER_GROUP);
```

- [ ] **Step 2: 在 onepl 库手动执行（验证 DDL 可用）**

```bash
mysql -uroot -p123456 onepl < docs/schema/ddl-quartz.sql
```
预期：11 个 CREATE TABLE 全部成功。验证：
```sql
SHOW TABLES LIKE 'QRTZ%';   -- 应返回 11 行
```

- [ ] **Step 3: 同步更新 docs/schema/CLAUDE.md（DDL 文件清单）**

在 `ddl-performance.sql` 行后追加：
```markdown
| `ddl-quartz.sql` | system-governance-center (V1.6) | `QRTZ_JOB_DETAILS`, `QRTZ_TRIGGERS`, `QRTZ_CRON_TRIGGERS`, `QRTZ_SIMPLE_TRIGGERS`, `QRTZ_SIMPROP_TRIGGERS`, `QRTZ_BLOB_TRIGGERS`, `QRTZ_CALENDARS`, `QRTZ_FIRED_TRIGGERS`, `QRTZ_PAUSED_TRIGGER_GRPS`, `QRTZ_SCHEDULER_STATE`, `QRTZ_LOCKS`（11 张 Quartz 集群模式标准表） |
```

- [ ] **Step 4: Commit**

```bash
git add docs/schema/ddl-quartz.sql docs/schema/CLAUDE.md
git commit -m "feat(quartz-B): P1.2 add Quartz 2.3.2 DDL (11 QRTZ_* tables)

- 来源: org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql
- 适配: ENGINE=InnoDB + utf8mb4_general_ci
- 同库 onepl（spec 决策 #3=A）
- 同步 docs/schema/CLAUDE.md 文件清单

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P1.3: sys_job_conf / sys_job_run_log 字段扩展 + 3 条业务 Job INSERT

**Files:**
- Create: `docs/schema/migrations/2026-04-25-quartz-integration.sql`
- Modify: `docs/schema/ddl-governance.sql`（行 71-107，添加新字段 + 注释 V1.6）

- [ ] **Step 1: 创建 migrations/2026-04-25-quartz-integration.sql**

```sql
-- ============================================================
-- 子项目 B（Quartz 整合）数据库迁移脚本
-- 引入版本: V1.6（2026-04-25）
-- 前置: docs/schema/ddl-quartz.sql 已先执行（11 张 QRTZ_* 表已创建）
-- ============================================================

-- Step 1: sys_job_conf 表字段扩展
ALTER TABLE sys_job_conf
    ADD COLUMN quartz_job_class VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
    ADD COLUMN misfire_policy   VARCHAR(32)  NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略：FIRE_ONCE_NOW/DO_NOTHING/IGNORE_MISFIRE_POLICY（V1.6 新增）';

-- Step 2: sys_job_run_log 表字段扩展
ALTER TABLE sys_job_run_log
    ADD COLUMN scheduled_fire_time DATETIME(3) NULL COMMENT 'Quartz 计划触发时间（V1.6 新增，用于 misfire 排查）';

-- Step 3: 写入 3 条业务 Job 记录（performance-engine-center 现有 3 个 Job）
INSERT INTO sys_job_conf (
    id, job_key, job_name, cron_expr, status, allow_manual_trigger,
    quartz_job_class, misfire_policy,
    remark, created_by, created_time, updated_by, updated_time
) VALUES
('JOB_DAILY_KPI_CALC',
 'DAILY_KPI_CALC',
 '日常 KPI 计算',
 '0 30 1 * * ?',
 'ACTIVE',
 1,
 'com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob',
 'FIRE_ONCE_NOW',
 'V1.6 quartz 整合引入；KPI 计算（T-1）错过补跑一次',
 'SYSTEM', NOW(), 'SYSTEM', NOW()),
('JOB_SYS_CONTROL_CLEANUP',
 'SYS_CONTROL_CLEANUP',
 '系统控制历史清理',
 '0 0 3 * * ?',
 'ACTIVE',
 1,
 'com.bank.branch.platform.performance.job.quartz.SysControlCleanupQuartzJob',
 'DO_NOTHING',
 'V1.6 quartz 整合引入；按 scope_dim 分组保留最新 12 条历史，错过即跳过',
 'SYSTEM', NOW(), 'SYSTEM', NOW()),
('JOB_PERF_RUN_TASK_CLEANUP',
 'PERF_RUN_TASK_CLEANUP',
 '绩效执行任务清理',
 '0 30 3 * * ?',
 'ACTIVE',
 1,
 'com.bank.branch.platform.performance.job.quartz.PerfRunTaskCleanupQuartzJob',
 'DO_NOTHING',
 'V1.6 quartz 整合引入；删除 SUCCESS + end_time<now-90d 任务，错过即跳过',
 'SYSTEM', NOW(), 'SYSTEM', NOW());
```

- [ ] **Step 2: 同步更新 ddl-governance.sql 中 sys_job_conf 与 sys_job_run_log DDL**

在 sys_job_conf 表 DDL 中（行 75 cron_expr 之后），添加：
```sql
  `quartz_job_class` varchar(255) NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名（V1.6 新增）',
  `misfire_policy` varchar(32) NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略（V1.6 新增）',
```

在 sys_job_run_log 表 DDL 中（行 99 end_time 之后），添加：
```sql
  `scheduled_fire_time` datetime(3) DEFAULT NULL COMMENT 'Quartz 计划触发时间（V1.6 新增）',
```

- [ ] **Step 3: 在 onepl 库执行迁移**

```bash
mysql -uroot -p123456 onepl < docs/schema/migrations/2026-04-25-quartz-integration.sql
```
预期：3 个 ALTER 成功 + 3 条 INSERT 成功。验证：
```sql
DESCRIBE sys_job_conf;        -- 应有 quartz_job_class + misfire_policy 字段
DESCRIBE sys_job_run_log;     -- 应有 scheduled_fire_time 字段
SELECT job_key, status, quartz_job_class, misfire_policy FROM sys_job_conf WHERE id LIKE 'JOB_%';
-- 应返回 3 行
```

- [ ] **Step 4: Commit**

```bash
git add docs/schema/migrations/2026-04-25-quartz-integration.sql docs/schema/ddl-governance.sql
git commit -m "feat(quartz-B): P1.3 sys_job_conf/sys_job_run_log 字段扩展 + 3 业务 Job INSERT

- sys_job_conf 新增 quartz_job_class + misfire_policy 字段（V1.6）
- sys_job_run_log 新增 scheduled_fire_time 字段（V1.6）
- 3 条业务 Job 记录初始化（DAILY_KPI_CALC / SYS_CONTROL_CLEANUP / PERF_RUN_TASK_CLEANUP）
- ddl-governance.sql 同步标注 V1.6 字段

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P1.4: AutowiringSpringBeanJobFactory + 单元测试

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/config/AutowiringSpringBeanJobFactory.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/config/AutowiringSpringBeanJobFactoryTest.java`

- [ ] **Step 1: 写失败的单元测试（Red）**

```java
package com.bank.branch.platform.governance.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutowiringSpringBeanJobFactoryTest {

    @Mock private ApplicationContext applicationContext;
    @Mock private AutowireCapableBeanFactory autowireFactory;
    @Mock private TriggerFiredBundle bundle;
    @Mock private Scheduler scheduler;
    @Mock private JobDetail jobDetail;

    @Test
    void createJobInstance_invokesAutowireBean() throws Exception {
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(applicationContext);
        when(applicationContext.getAutowireCapableBeanFactory()).thenReturn(autowireFactory);

        // 准备一个普通的 Job class
        when(bundle.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobClass()).thenReturn((Class) TestJob.class);

        Object job = factory.createJobInstance(bundle);

        assertThat(job).isInstanceOf(TestJob.class);
        verify(autowireFactory).autowireBean(job);
    }

    @Test
    void createJobInstance_withoutApplicationContext_throwsIllegalStateException() {
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        // 不调 setApplicationContext

        when(bundle.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobClass()).thenReturn((Class) TestJob.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.createJobInstance(bundle))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ApplicationContext");
    }

    public static class TestJob implements org.quartz.Job {
        @Override public void execute(org.quartz.JobExecutionContext context) {}
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -pl system-governance-center test -Dtest=AutowiringSpringBeanJobFactoryTest -q
```
预期：编译失败（AutowiringSpringBeanJobFactory class 不存在）。

- [ ] **Step 3: 实现 AutowiringSpringBeanJobFactory（Green）**

```java
package com.bank.branch.platform.governance.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * 支持 @Autowired 的 Quartz JobFactory.
 *
 * <p>Quartz 通过反射 newInstance() 创建 Job 对象，不经过 Spring，导致 @Autowired 字段无法注入。
 * 本类继承 Spring 自带 SpringBeanJobFactory（仅注入 JobDataMap 字段），扩展为：
 * 反射创建 Job 后调用 applicationContext.getAutowireCapableBeanFactory().autowireBean(job)
 * 完成 Spring Bean 字段注入。
 *
 * <p>用法：在 QuartzConfig 中将本类注册为 SchedulerFactoryBean.setJobFactory()。
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory
        implements ApplicationContextAware {

    private transient AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        if (beanFactory == null) {
            throw new IllegalStateException(
                "ApplicationContext not set; AutowiringSpringBeanJobFactory cannot autowire job");
        }
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);
        return job;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn -pl system-governance-center test -Dtest=AutowiringSpringBeanJobFactoryTest -q
```
预期：2 测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/config/AutowiringSpringBeanJobFactory.java \
        system-governance-center/src/test/java/com/bank/branch/platform/governance/config/AutowiringSpringBeanJobFactoryTest.java
git commit -m "feat(quartz-B): P1.4 AutowiringSpringBeanJobFactory + 2 单元测试

- 继承 Spring SpringBeanJobFactory，扩展为通过 ApplicationContext.autowireBean
  让 Quartz Job 实例化时自动完成 @Autowired 字段注入
- TDD: Red → Green 闭环

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P1.5: JobExecutionLogger（JobListener）+ 单元测试

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/listener/JobExecutionLogger.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/listener/JobExecutionLoggerTest.java`

**注**：本 task 内 JobListener 调用 `SysJobConfMapper` / `SysJobRunLogMapper`（已存在），但具体方法 `selectByJobKey` / `insert` / `updateSuccess` / `updateFailed` 可能需要 Mapper 同步扩展。**先 Mock 调用，等 P3 阶段对齐 Mapper XML**。

- [ ] **Step 1: 写 5 个失败的单元测试（Red）**

```java
package com.bank.branch.platform.governance.listener;

import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.entity.SysJobRunLog;
import com.bank.branch.platform.governance.mapper.SysJobConfMapper;
import com.bank.branch.platform.governance.mapper.SysJobRunLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutionLoggerTest {

    @Mock private SysJobConfMapper jobConfMapper;
    @Mock private SysJobRunLogMapper runLogMapper;
    @Mock private JobExecutionContext context;
    @Mock private JobDetail jobDetail;

    @InjectMocks private JobExecutionLogger listener;

    @Test
    void getName_returnsClassName() {
        assertThat(listener.getName()).isEqualTo("JobExecutionLogger");
    }

    @Test
    void jobToBeExecuted_scheduledTrigger_writesRunningLog() {
        SysJobConf jobConf = new SysJobConf();
        jobConf.setId("JOB_DAILY_KPI_CALC");
        jobConf.setJobKey("DAILY_KPI_CALC");

        JobDataMap mergedData = new JobDataMap();    // 不含 triggerType → 默认 SCHEDULED
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));
        when(context.getMergedJobDataMap()).thenReturn(mergedData);
        when(context.getScheduledFireTime()).thenReturn(new Date());
        when(jobConfMapper.selectByJobKey("DAILY_KPI_CALC")).thenReturn(jobConf);

        listener.jobToBeExecuted(context);

        ArgumentCaptor<SysJobRunLog> captor = ArgumentCaptor.forClass(SysJobRunLog.class);
        verify(runLogMapper).insert(captor.capture());
        SysJobRunLog inserted = captor.getValue();
        assertThat(inserted.getJobId()).isEqualTo("JOB_DAILY_KPI_CALC");
        assertThat(inserted.getTriggerType()).isEqualTo("SCHEDULED");
        assertThat(inserted.getStatus()).isEqualTo("RUNNING");
        assertThat(inserted.getId()).isNotBlank();
        verify(context).put(eq("runLogId"), eq(inserted.getId()));
    }

    @Test
    void jobToBeExecuted_manualTrigger_writesManualLog() {
        SysJobConf jobConf = new SysJobConf();
        jobConf.setId("JOB_DAILY_KPI_CALC");
        jobConf.setJobKey("DAILY_KPI_CALC");

        JobDataMap mergedData = new JobDataMap();
        mergedData.put("triggerType", "MANUAL");
        mergedData.put("operatorEmpId", "EMP_001");

        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));
        when(context.getMergedJobDataMap()).thenReturn(mergedData);
        when(context.getScheduledFireTime()).thenReturn(new Date());
        when(jobConfMapper.selectByJobKey("DAILY_KPI_CALC")).thenReturn(jobConf);

        listener.jobToBeExecuted(context);

        ArgumentCaptor<SysJobRunLog> captor = ArgumentCaptor.forClass(SysJobRunLog.class);
        verify(runLogMapper).insert(captor.capture());
        SysJobRunLog inserted = captor.getValue();
        assertThat(inserted.getTriggerType()).isEqualTo("MANUAL");
        assertThat(inserted.getCreatedBy()).isEqualTo("EMP_001");
    }

    @Test
    void jobWasExecuted_success_writesSuccessLog() {
        when(context.get("runLogId")).thenReturn("RUNLOG_123");
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));

        listener.jobWasExecuted(context, null);

        verify(runLogMapper).updateSuccess(eq("RUNLOG_123"), any());
        verify(jobConfMapper).updateLastRunTime(eq("DAILY_KPI_CALC"), any());
    }

    @Test
    void jobWasExecuted_failure_writesFailedLogWithTruncatedErrorMsg() {
        StringBuilder bigStack = new StringBuilder();
        for (int i = 0; i < 5000; i++) bigStack.append("x");
        JobExecutionException jobEx = new JobExecutionException(new RuntimeException(bigStack.toString()));

        when(context.get("runLogId")).thenReturn("RUNLOG_123");
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));

        listener.jobWasExecuted(context, jobEx);

        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(runLogMapper).updateFailed(eq("RUNLOG_123"), any(), errorMsgCaptor.capture());
        assertThat(errorMsgCaptor.getValue().length()).isLessThanOrEqualTo(4000);
    }

    @Test
    void jobToBeExecuted_mapperThrows_doesNotPropagateToQuartz() {
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("UNKNOWN", "DEFAULT"));
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        when(jobConfMapper.selectByJobKey("UNKNOWN")).thenThrow(new RuntimeException("DB down"));

        // 不应抛出
        listener.jobToBeExecuted(context);
        // 验证 runLogMapper.insert 未被调用（因为前置 selectByJobKey 已挂掉）
        verifyNoInteractions(runLogMapper);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -pl system-governance-center test -Dtest=JobExecutionLoggerTest -q
```
预期：编译失败（JobExecutionLogger 不存在 + Mapper 方法不存在）。

- [ ] **Step 3: 在 SysJobConfMapper / SysJobRunLogMapper 接口加方法（如果不存在）**

`SysJobConfMapper.java` 增加：
```java
SysJobConf selectByJobKey(@Param("jobKey") String jobKey);
int updateLastRunTime(@Param("jobKey") String jobKey, @Param("lastRunTime") LocalDateTime lastRunTime);
```

`SysJobRunLogMapper.java` 增加：
```java
int updateSuccess(@Param("id") String id, @Param("endTime") LocalDateTime endTime);
int updateFailed(@Param("id") String id, @Param("endTime") LocalDateTime endTime, @Param("errorMsg") String errorMsg);
```

对应 XML 添加 SQL（参考现有 SysJobConfMapper.xml / SysJobRunLogMapper.xml 的 select/update 模式）。

- [ ] **Step 4: 实现 JobExecutionLogger（Green）**

```java
package com.bank.branch.platform.governance.listener;

import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.entity.SysJobRunLog;
import com.bank.branch.platform.governance.mapper.SysJobConfMapper;
import com.bank.branch.platform.governance.mapper.SysJobRunLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Quartz Job 执行日志监听器（V1.6 quartz 整合引入）.
 *
 * <p>注册为全局 JobListener（QuartzConfig.setGlobalJobListeners）.
 *
 * <p><strong>异常隔离</strong>：所有内部异常 try-catch + log，不向 Quartz 抛出，
 * 防止治理日志失败影响业务调度（与 GovAuditLogHandler "审计写入失败不阻塞主业务" 模式一致）。
 *
 * <p><strong>触发类型识别</strong>：
 * <ul>
 *   <li>JobDataMap 含 "triggerType"="MANUAL" → MANUAL（手动触发，operatorEmpId 取自 dataMap）</li>
 *   <li>否则 → SCHEDULED（自动调度，created_by="SYSTEM"）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutionLogger implements JobListener {

    private static final int ERROR_MSG_MAX_LEN = 4000;
    private static final String SYSTEM_TRIGGER = "SYSTEM";

    private final SysJobConfMapper jobConfMapper;
    private final SysJobRunLogMapper runLogMapper;

    @Override
    public String getName() {
        return "JobExecutionLogger";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        try {
            String jobKey = context.getJobDetail().getKey().getName();
            SysJobConf jobConf = jobConfMapper.selectByJobKey(jobKey);
            if (jobConf == null) {
                log.warn("[JobExecutionLogger] jobKey={} 在 sys_job_conf 表中不存在，跳过日志记录", jobKey);
                return;
            }

            String triggerType = (String) context.getMergedJobDataMap()
                .getOrDefault("triggerType", "SCHEDULED");
            String operatorEmpId = (String) context.getMergedJobDataMap()
                .getOrDefault("operatorEmpId", SYSTEM_TRIGGER);

            SysJobRunLog runLog = new SysJobRunLog();
            runLog.setId(UUID.randomUUID().toString().replace("-", ""));
            runLog.setJobId(jobConf.getId());
            runLog.setTriggerType(triggerType);
            runLog.setStatus("RUNNING");
            runLog.setStartTime(LocalDateTime.now());
            runLog.setCreatedBy(operatorEmpId);
            // scheduled_fire_time 由 Quartz context 提供
            Date scheduledFireTime = context.getScheduledFireTime();
            if (scheduledFireTime != null) {
                runLog.setScheduledFireTime(
                    LocalDateTime.ofInstant(scheduledFireTime.toInstant(), ZoneId.systemDefault()));
            }

            runLogMapper.insert(runLog);
            context.put("runLogId", runLog.getId());

        } catch (Exception e) {
            log.error("[JobExecutionLogger] 写入 RUNNING 日志失败，jobKey={}, 业务执行不受影响",
                context.getJobDetail().getKey(), e);
        }
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        try {
            String runLogId = (String) context.get("runLogId");
            if (runLogId == null) {
                log.warn("[JobExecutionLogger] context 中无 runLogId，跳过结束日志更新");
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (jobException == null) {
                runLogMapper.updateSuccess(runLogId, now);
            } else {
                String errorMsg = ExceptionUtils.getStackTrace(jobException);
                if (errorMsg.length() > ERROR_MSG_MAX_LEN) {
                    errorMsg = errorMsg.substring(0, ERROR_MSG_MAX_LEN);
                }
                runLogMapper.updateFailed(runLogId, now, errorMsg);
            }
            // 同步更新 sys_job_conf.last_run_time
            String jobKey = context.getJobDetail().getKey().getName();
            jobConfMapper.updateLastRunTime(jobKey, now);

        } catch (Exception e) {
            log.error("[JobExecutionLogger] 更新结果日志失败，runLogId={}, 业务执行结果未持久化",
                context.get("runLogId"), e);
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // no-op：本项目不使用 TriggerListener veto
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

```bash
mvn -pl system-governance-center test -Dtest=JobExecutionLoggerTest -q
```
预期：5 测试 PASS。

- [ ] **Step 6: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/listener/JobExecutionLogger.java \
        system-governance-center/src/test/java/com/bank/branch/platform/governance/listener/JobExecutionLoggerTest.java \
        system-governance-center/src/main/java/com/bank/branch/platform/governance/mapper/SysJobConfMapper.java \
        system-governance-center/src/main/java/com/bank/branch/platform/governance/mapper/SysJobRunLogMapper.java \
        system-governance-center/src/main/resources/mapper/SysJobConfMapper.xml \
        system-governance-center/src/main/resources/mapper/SysJobRunLogMapper.xml
git commit -m "feat(quartz-B): P1.5 JobExecutionLogger + Mapper 扩展 + 5 单元测试

- JobListener 全局监听 jobToBeExecuted/jobWasExecuted
- 自动写 sys_job_run_log（SCHEDULED/MANUAL 区分 + errorMsg 截断 4000）
- 同步更新 sys_job_conf.last_run_time
- 异常隔离：内部 try-catch，不影响业务调度
- Mapper 新增 4 方法（selectByJobKey/updateLastRunTime/updateSuccess/updateFailed）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P1.6: QuartzConfig + application.yml + 集成测试

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/config/QuartzConfig.java`
- Create: `system-governance-center/src/test/java/com/bank/branch/platform/governance/config/QuartzConfigIntegrationIT.java`
- Modify: `bootstrap/src/main/resources/application.yml`（新增 spring.quartz 配置块）

- [ ] **Step 1: 写失败的集成测试（Red）**

```java
package com.bank.branch.platform.governance.config;

import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {QuartzConfig.class, AutowiringSpringBeanJobFactory.class,
    com.bank.branch.platform.governance.listener.JobExecutionLogger.class})
@TestPropertySource(properties = {
    "spring.quartz.job-store-type=memory",                                  // 测试用 RAMJobStore，不依赖 QRTZ_* 表
    "spring.quartz.properties.org.quartz.jobStore.isClustered=false"
})
class QuartzConfigIntegrationIT {

    @Autowired private Scheduler scheduler;

    @Test
    void scheduler_isStartedAndConfigured() throws SchedulerException {
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.isStarted()).isTrue();
        assertThat(scheduler.getMetaData().getJobStoreClass().getSimpleName())
            .isEqualTo("RAMJobStore");   // 测试上下文用内存
    }
}
```

注：测试需要 mock SysJobConfMapper / SysJobRunLogMapper bean（用 `@MockBean`）。完整测试代码：
```java
@org.springframework.boot.test.mock.mockito.MockBean
private com.bank.branch.platform.governance.mapper.SysJobConfMapper jobConfMapper;
@org.springframework.boot.test.mock.mockito.MockBean
private com.bank.branch.platform.governance.mapper.SysJobRunLogMapper runLogMapper;
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -pl system-governance-center test -Dtest=QuartzConfigIntegrationIT -q
```
预期：QuartzConfig class 不存在编译失败，或 Scheduler bean 缺失。

- [ ] **Step 3: 实现 QuartzConfig（Green）**

```java
package com.bank.branch.platform.governance.config;

import com.bank.branch.platform.governance.listener.JobExecutionLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Quartz 调度引擎装配（V1.6 quartz 整合引入）.
 *
 * <p>共享主 DataSource（与 sys_job_conf 同库 onepl，spec 决策 #3=A）.
 * <p>JobFactory 使用 AutowiringSpringBeanJobFactory，让 Quartz Job 内部可 @Autowired Spring Bean.
 * <p>全局 JobListener 注册 JobExecutionLogger，自动写 sys_job_run_log.
 *
 * <p>开关: spring.quartz.enabled=true（默认 true，缺省即启用，与 spring-boot-starter-quartz 一致）.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.quartz", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class QuartzConfig {

    @Bean
    public AutowiringSpringBeanJobFactory springBeanJobFactory(ApplicationContext ctx) {
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(ctx);
        return factory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            DataSource dataSource,
            AutowiringSpringBeanJobFactory jobFactory,
            JobExecutionLogger jobListener,
            QuartzProperties quartzProperties) {

        SchedulerFactoryBean bean = new SchedulerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setJobFactory(jobFactory);
        bean.setOverwriteExistingJobs(true);
        bean.setAutoStartup(true);
        bean.setStartupDelay(10);                          // 应用启动 10s 后再启动调度
        bean.setWaitForJobsToCompleteOnShutdown(true);

        Properties props = new Properties();
        props.putAll(quartzProperties.getProperties());
        bean.setQuartzProperties(props);

        bean.setGlobalJobListeners(jobListener);
        return bean;
    }
}
```

- [ ] **Step 4: 在 application.yml 新增 spring.quartz 配置**

定位 `bootstrap/src/main/resources/application.yml`，在 `spring:` 顶级 key 下添加：
```yaml
spring:
  # ... 现有 datasource / redis / etc.
  quartz:
    enabled: true
    job-store-type: jdbc
    jdbc:
      initialize-schema: never                              # DDL 由 docs/schema/ddl-quartz.sql 手动初始化
    properties:
      org.quartz.scheduler.instanceName: BranchPlatformScheduler
      org.quartz.scheduler.instanceId: AUTO                 # 自动生成节点 ID
      org.quartz.threadPool.threadCount: 5
      org.quartz.threadPool.threadPriority: 5
      org.quartz.jobStore.class: org.quartz.impl.jdbcjobstore.JobStoreTX
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
      org.quartz.jobStore.tablePrefix: QRTZ_
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.clusterCheckinInterval: 20000
      org.quartz.jobStore.misfireThreshold: 60000
```

- [ ] **Step 5: 运行测试验证通过**

```bash
mvn -pl system-governance-center test -Dtest=QuartzConfigIntegrationIT -q
```
预期：1 集成测试 PASS。

- [ ] **Step 6: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/config/QuartzConfig.java \
        system-governance-center/src/test/java/com/bank/branch/platform/governance/config/QuartzConfigIntegrationIT.java \
        bootstrap/src/main/resources/application.yml
git commit -m "feat(quartz-B): P1.6 QuartzConfig + application.yml + 集成测试

- SchedulerFactoryBean: shared DataSource, AutowireFactory, GlobalListener=JobExecutionLogger
- application.yml: spring.quartz.* 完整配置（jdbc + isClustered=true + misfireThreshold=60s）
- 集成测试: SpringBootTest @ memory job-store-type 验证 Scheduler bean 可用
- @ConditionalOnProperty(matchIfMissing=true) 默认启用

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P1.7: Phase 1 全模块构建 + Push 远程

- [ ] **Step 1: 全模块构建验证**

```bash
mvn clean install -DskipTests=false -q
```
预期：全部 BUILD SUCCESS。如有失败，定位到 task（P1.1-P1.6）回退修复，**不**进入 P2。

- [ ] **Step 2: 推送 P1 到远程**

```bash
git push -u origin refactor/quartz-job-integration
```

---

# Phase 2：Job 改造（6 task）

## Task P2.1: DailyKpiCalcJob 重构（删 @Scheduled / @SchedulerLock / @ConditionalOnProperty）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/DailyKpiCalcJob.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/DailyKpiCalcJobTest.java`（已存在，调整测试以匹配重构后行为）

- [ ] **Step 1: 修改 DailyKpiCalcJobTest，移除针对 @Scheduled/scheduled() 的旧测试**

读现有测试，删除涉及 `@Scheduled` 注解断言、`scheduled()` 方法调用的测试。保留 `run()` 方法的业务行为测试（按 spec §7.1.C 模板补齐 3 个：listActiveSchemes/empty/singleFails）。

- [ ] **Step 2: 修改 DailyKpiCalcJob 主代码**

按 spec §4.3 重构：
- 删除 `@ConditionalOnProperty(prefix="perf.job.daily-kpi", ...)`（类级注解）
- 删除 `@Scheduled(cron="${perf.job.daily-kpi.cron:...}")` + `@SchedulerLock(...)` + `public void scheduled() { run(); }`
- 删除 ShedLock import: `net.javacrumbs.shedlock.spring.annotation.SchedulerLock`
- 删除 Spring Scheduling import: `org.springframework.scheduling.annotation.Scheduled`
- 删除 ConditionalOnProperty import
- 保留 `@Component`、`@RequiredArgsConstructor`、`@Slf4j`、`run()` 方法体不变

- [ ] **Step 3: 运行测试验证通过**

```bash
mvn -pl performance-engine-center test -Dtest=DailyKpiCalcJobTest -q
```
预期：业务测试 PASS。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/DailyKpiCalcJob.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/DailyKpiCalcJobTest.java
git commit -m "refactor(quartz-B): P2.1 DailyKpiCalcJob 删 @Scheduled/@SchedulerLock/scheduled()

- 删除 @ConditionalOnProperty / @Scheduled / @SchedulerLock / scheduled() 包装方法
- 删除 ShedLock 与 Spring Scheduling import
- 保留 @Component + run() void 业务方法不变（KpiSchemeService.listActiveSchemes 调用链不变）
- 测试调整：移除 @Scheduled 验证，保留 run() 业务行为测试

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P2.2: SysControlCleanupJob 重构

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/SysControlCleanupJob.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/SysControlCleanupJobTest.java`

- [ ] **Step 1: 调整测试**（移除 scheduled() 测试，保留 `int run()` 业务测试）
- [ ] **Step 2: 重构主代码**（同 P2.1，但保留 `@Value("${perf.job.sys-control-cleanup.keep-count:12}") private int keepCount;` 与 `int run()` 返回值）
- [ ] **Step 3: 运行测试**
- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(quartz-B): P2.2 SysControlCleanupJob 删 @Scheduled/@SchedulerLock

- 同 P2.1 模式
- 保留 @Value keep-count（业务参数，与调度无关）
- 保留 int run() 返回值（删除行数总和）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P2.3: PerfRunTaskCleanupJob 重构

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/PerfRunTaskCleanupJob.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/PerfRunTaskCleanupJobTest.java`

- [ ] **Step 1: 调整测试**
- [ ] **Step 2: 重构主代码**（同 P2.2，保留 `@Value retention-days` 与 `int run()`）
- [ ] **Step 3: 运行测试**
- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(quartz-B): P2.3 PerfRunTaskCleanupJob 删 @Scheduled/@SchedulerLock

- 同 P2.2 模式
- 保留 @Value retention-days
- 保留 int run() 返回值

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P2.4: DailyKpiCalcQuartzJob（Quartz 包装类）+ 单元测试

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/quartz/DailyKpiCalcQuartzJob.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/quartz/DailyKpiCalcQuartzJobTest.java`

- [ ] **Step 1: 写失败的单元测试（Red）**

按 spec §7.1.D 模板：
```java
package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.DailyKpiCalcJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyKpiCalcQuartzJobTest {

    @Mock private DailyKpiCalcJob dailyKpiCalcJob;
    @Mock private JobExecutionContext context;

    @InjectMocks private DailyKpiCalcQuartzJob quartzJob;

    @Test
    void execute_delegatesToBusinessJob() {
        assertDoesNotThrow(() -> quartzJob.execute(context));
        verify(dailyKpiCalcJob).run();
    }

    @Test
    void execute_businessException_throwsJobExecutionException_refireFalse() {
        doThrow(new RuntimeException("业务异常")).when(dailyKpiCalcJob).run();

        assertThatThrownBy(() -> quartzJob.execute(context))
            .isInstanceOf(JobExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isFalse());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**
- [ ] **Step 3: 实现 DailyKpiCalcQuartzJob（Green）**

```java
package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.DailyKpiCalcJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 日常 KPI 计算 Quartz 包装类（V1.6 quartz 整合引入）.
 *
 * <p>不加 @Component！Quartz 通过反射 newInstance() 创建本对象 →
 * AutowiringSpringBeanJobFactory 完成 @Autowired 注入（详见 system-governance QuartzConfig）.
 *
 * <p>execute() 仅作业务方法委托，不持有任何业务逻辑.
 */
@Slf4j
public class DailyKpiCalcQuartzJob implements Job {

    @Autowired
    private DailyKpiCalcJob dailyKpiCalcJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            dailyKpiCalcJob.run();   // void 返回
        } catch (Exception e) {
            log.error("[DailyKpiCalcQuartzJob] 执行异常，jobKey={}",
                context.getJobDetail().getKey(), e);
            throw new JobExecutionException(e, false);   // false = 不立即重试
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(quartz-B): P2.4 DailyKpiCalcQuartzJob + 2 单元测试

- implements org.quartz.Job，execute() 委托 dailyKpiCalcJob.run()
- 不加 @Component（Quartz 反射创建，AutowiringSpringBeanJobFactory 注入）
- 异常路径：JobExecutionException(refire=false)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P2.5: SysControlCleanupQuartzJob（Quartz 包装类）+ 单元测试

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/quartz/SysControlCleanupQuartzJob.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/quartz/SysControlCleanupQuartzJobTest.java`

模式同 P2.4，但调用 `sysControlCleanupJob.run()` 返回 int **显式忽略**（无赋值）。
2 测试：execute_delegatesToBusinessJob_ignoresReturnValue + execute_businessException_throwsJobExecutionException。

```java
@Override
public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
        sysControlCleanupJob.run();   // 返回 int，调度链路不消费 → 显式丢弃
    } catch (Exception e) {
        log.error("[SysControlCleanupQuartzJob] 执行异常", e);
        throw new JobExecutionException(e, false);
    }
}
```

Commit：
```bash
git commit -m "feat(quartz-B): P2.5 SysControlCleanupQuartzJob + 2 单元测试

- 同 P2.4 模式，但显式忽略 run() 的 int 返回值
- 调度链路不消费返回值（仅业务/单测断言时使用）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P2.6: PerfRunTaskCleanupQuartzJob（Quartz 包装类）+ 单元测试

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/quartz/PerfRunTaskCleanupQuartzJob.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/quartz/PerfRunTaskCleanupQuartzJobTest.java`

模式同 P2.5。

Commit：
```bash
git commit -m "feat(quartz-B): P2.6 PerfRunTaskCleanupQuartzJob + 2 单元测试

- 同 P2.5 模式

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Phase 2 末尾**：执行 `mvn clean install -DskipTests=false -q` 验证全模块通过；`git push origin refactor/quartz-job-integration`。

---

# Phase 3：服务层（7 task）

## Task P3.1: JobService.@PostConstruct.syncJobsOnStartup() 实现

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/JobService.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/JobServiceSyncOnStartupTest.java`（新增）

**职责**：JobService 注入 Scheduler；@PostConstruct 启动同步：遍历 sys_job_conf WHERE status='ACTIVE' → 对每条记录构造 JobDetail + CronTrigger → scheduler.scheduleJob（overwriteExistingJobs=true 自动覆盖 cron 变更）。

- [ ] **Step 1: 写 3 个失败测试（Red）**：sync_3JobsActive_schedulesAll / sync_oneJobConfigError_continuesOthers / sync_zeroJobs_doesNothing
- [ ] **Step 2: 在 JobService 新增 Scheduler 注入 + @PostConstruct 方法**

```java
private final Scheduler scheduler;     // 新增依赖

@PostConstruct
public void syncJobsOnStartup() {
    List<SysJobConf> activeJobs = jobConfMapper.selectByStatus("ACTIVE");
    int success = 0, failed = 0;
    for (SysJobConf job : activeJobs) {
        try {
            scheduleQuartzJob(job);
            success++;
        } catch (Exception e) {
            log.error("[JobService.syncJobsOnStartup] jobKey={} 同步失败，跳过继续",
                job.getJobKey(), e);
            failed++;
        }
    }
    log.info("[JobService.syncJobsOnStartup] 完成：成功={}, 失败={}", success, failed);
}

private void scheduleQuartzJob(SysJobConf job) throws SchedulerException, ClassNotFoundException {
    @SuppressWarnings("unchecked")
    Class<? extends Job> clazz = (Class<? extends Job>) Class.forName(job.getQuartzJobClass());
    JobDetail detail = JobBuilder.newJob(clazz)
        .withIdentity(job.getJobKey(), "DEFAULT")
        .storeDurably()
        .build();
    CronScheduleBuilder cron = applyMisfirePolicy(
        cronSchedule(job.getCronExpr()), job.getMisfirePolicy());
    CronTrigger trigger = TriggerBuilder.newTrigger()
        .withIdentity(job.getJobKey() + "_TRIGGER", "DEFAULT")
        .withSchedule(cron)
        .forJob(detail)
        .build();
    scheduler.scheduleJob(detail, trigger);
}

private CronScheduleBuilder applyMisfirePolicy(CronScheduleBuilder builder, String policy) {
    return switch (policy) {
        case "FIRE_ONCE_NOW" -> builder.withMisfireHandlingInstructionFireAndProceed();
        case "DO_NOTHING" -> builder.withMisfireHandlingInstructionDoNothing();
        case "IGNORE_MISFIRE_POLICY" -> builder.withMisfireHandlingInstructionIgnoreMisfires();
        default -> throw new IllegalArgumentException("未知 misfire_policy: " + policy);
    };
}
```

- [ ] **Step 3: SysJobConfMapper 新增 selectByStatus**

```java
List<SysJobConf> selectByStatus(@Param("status") String status);
```
对应 XML 添加 SQL。

- [ ] **Step 4: 运行测试通过 + Commit**

---

## Task P3.2: JobService.triggerJobNow + JobController.triggerJob 联调 + MockMvc 测试

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/JobService.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/JobController.java`
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/JobServiceTest.java`
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/JobControllerTest.java`

- [ ] **Step 1: 写测试**：JobService.triggerJobNow_existingJob_callsScheduler + JobController.trigger_returnsOk_passesEmpId
- [ ] **Step 2: 实现 JobService.triggerJobNow**

```java
public void triggerJobNow(String jobId, String operatorEmpId) {
    SysJobConf job = jobConfMapper.selectById(jobId);
    if (job == null) {
        throw new BizException(GovErrorCode.JOB_NOT_FOUND, "Job 不存在: " + jobId);
    }
    JobDataMap data = new JobDataMap();
    data.put("triggerType", "MANUAL");
    data.put("operatorEmpId", operatorEmpId);
    try {
        scheduler.triggerJob(JobKey.jobKey(job.getJobKey(), "DEFAULT"), data);
    } catch (SchedulerException e) {
        throw new BizException(GovErrorCode.JOB_TRIGGER_FAILED, "触发失败", e);
    }
}
```

- [ ] **Step 3: JobController.triggerJob 改为传 currentEmpId**（从 SecurityContext 取）
- [ ] **Step 4: 运行测试通过 + Commit**

---

## Task P3.3: JobService.pauseJob / resumeJob + JobController 联调 + 测试

**Files:**
- Modify: JobService.java
- Modify: JobController.java（pause/resume 端点）
- Modify: 对应测试

- [ ] **Step 1-4 同 P3.2 模式**

```java
@Transactional
public void pauseJob(String jobId) {
    SysJobConf job = jobConfMapper.selectById(jobId);
    if (job == null) throw new BizException(GovErrorCode.JOB_NOT_FOUND);
    jobConfMapper.updateStatus(jobId, "PAUSED");
    try {
        scheduler.pauseJob(JobKey.jobKey(job.getJobKey(), "DEFAULT"));
    } catch (SchedulerException e) {
        throw new BizException(GovErrorCode.JOB_PAUSE_FAILED, e);
    }
}

@Transactional
public void resumeJob(String jobId) {
    SysJobConf job = jobConfMapper.selectById(jobId);
    if (job == null) throw new BizException(GovErrorCode.JOB_NOT_FOUND);
    jobConfMapper.updateStatus(jobId, "ACTIVE");
    try {
        scheduler.resumeJob(JobKey.jobKey(job.getJobKey(), "DEFAULT"));
    } catch (SchedulerException e) {
        throw new BizException(GovErrorCode.JOB_RESUME_FAILED, e);
    }
}
```

`SysJobConfMapper` 新增 `updateStatus(@Param("id") String id, @Param("status") String status)`。

GovErrorCode 新增 3 个错误码（JOB_NOT_FOUND / JOB_TRIGGER_FAILED / JOB_PAUSE_FAILED / JOB_RESUME_FAILED）。

---

## Task P3.4: JobService 集成测试（与真实 Scheduler 联动 schedule/reschedule/unschedule）

**Files:**
- Create: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/JobServiceQuartzIntegrationIT.java`

```java
@SpringBootTest(classes = {QuartzConfig.class, AutowiringSpringBeanJobFactory.class,
    JobExecutionLogger.class, JobService.class})
@TestPropertySource(properties = {"spring.quartz.job-store-type=memory"})
class JobServiceQuartzIntegrationIT {

    @Autowired private JobService jobService;
    @Autowired private Scheduler scheduler;
    @MockBean private SysJobConfMapper jobConfMapper;
    @MockBean private SysJobRunLogMapper runLogMapper;

    @Test
    void scheduleJob_writesToScheduler() throws SchedulerException { ... }

    @Test
    void rescheduleJob_updatesCron() throws SchedulerException { ... }

    @Test
    void triggerJobNow_invokesQuartzTrigger() throws SchedulerException { ... }
}
```

- [ ] 3 测试 + Commit

---

## Task P3.5: misfire policy 应用 + 单元测试

**Files:**
- Modify: JobService.java（applyMisfirePolicy 方法已在 P3.1 引入，本 task 完善 + 补测试）
- Test: JobServiceTest.java 新增 4 case

测试 4 case：FIRE_ONCE_NOW / DO_NOTHING / IGNORE_MISFIRE_POLICY / 未知策略抛 IllegalArgumentException

---

## Task P3.6: JobController 5 端点 MockMvc 测试

**Files:**
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/JobControllerTest.java`

5 端点 × 2 测试（成功 + 鉴权失败）= 10 测试。

---

## Task P3.7: Phase 3 全模块构建 + Push 远程

```bash
mvn clean install -DskipTests=false -q
git push origin refactor/quartz-job-integration
```

---

# Phase 4：清理与文档（6 task）

## Task P4.1: 删除 ShedLock 全部痕迹

**Files:**
- Modify: `performance-engine-center/pom.xml`（删除 shedlock-spring + shedlock-provider-redis-spring 2 项）
- Delete: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/ShedLockConfig.java`
- Modify: `bootstrap/src/main/resources/application.yml`（删除 shedlock 段，如果有）

- [ ] **Step 1: grep 确认全库 ShedLock 引用面**

```bash
grep -rn "shedlock\|@SchedulerLock\|ShedLockConfig" --include="*.java" --include="*.xml" --include="*.yml"
```
预期：仅 P2.1-P2.3 已删的 @SchedulerLock 残留 import（如果有）+ ShedLockConfig 文件 + pom.xml 2 项

- [ ] **Step 2: 删除 ShedLockConfig.java**

```bash
rm performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/ShedLockConfig.java
```

- [ ] **Step 3: pom.xml 删除 2 个依赖**

```xml
<!-- 删除以下 2 项 -->
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.13.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-spring</artifactId>
    <version>5.13.0</version>
</dependency>
```

- [ ] **Step 4: application.yml 检查并删除 shedlock 配置**

```bash
grep -n "shedlock" bootstrap/src/main/resources/*.yml
```
有则删除。

- [ ] **Step 5: 终极 grep 验证**

```bash
grep -rn "shedlock\|@SchedulerLock\|ShedLockConfig" --include="*.java" --include="*.xml" --include="*.yml"
```
预期：**0 行**

- [ ] **Step 6: mvn clean install 验证**

预期：BUILD SUCCESS。

- [ ] **Step 7: Commit**

```bash
git commit -m "refactor(quartz-B): P4.1 删除 ShedLock 全部痕迹

- 删除 performance-engine-center/pom.xml 中 shedlock-spring + shedlock-provider-redis-spring
- 删除 performance-engine-center/.../config/ShedLockConfig.java
- 验证全库 grep shedlock|@SchedulerLock|ShedLockConfig = 0 行
- 防重能力由 Quartz Cluster QRTZ_LOCKS 行锁接管（spec 决策 #2=A）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P4.2: JobApi 精简（删 startJobRun/completeJobRun/failJobRun）

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/JobApi.java`（删 3 方法）
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/facade/JobFacade.java`（删对应 3 实现）
- Modify: 相关测试（删除 facade 测试中 3 方法的所有 case）

- [ ] **Step 1: grep 全库扫描 JobApi 3 方法的调用方**

```bash
grep -rn "JobApi\.\(startJobRun\|completeJobRun\|failJobRun\)\|jobApi\.\(startJobRun\|completeJobRun\|failJobRun\)" --include="*.java"
```
预期：0 行（前期探索确认）。若有外部模块调用，**必须先升级调用方**才能删 API。

- [ ] **Step 2: 删 JobApi.java 中 3 方法**

剩余只有 `Optional<JobConfDTO> getJobConf(String jobKey);`

- [ ] **Step 3: 删 JobFacade.java 对应实现**

- [ ] **Step 4: 删 JobFacadeTest 中 3 方法的所有 case**

- [ ] **Step 5: mvn clean install 验证**

- [ ] **Step 6: Commit**

```bash
git commit -m "refactor(quartz-B): P4.2 JobApi 精简到 1 方法（删 startJobRun/completeJobRun/failJobRun）

- JobApi 仅保留 getJobConf（spec 决策 #5=B）
- JobFacade 删除对应 3 个 facade 方法 + 测试
- 全库 grep 验证 0 行外部调用方
- 写日志由 JobExecutionLogger 内部直接调用 SysJobRunLogService（不走 *Api）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P4.3: application.yml 配置项清理验证

**Files:**
- Verify: `bootstrap/src/main/resources/application.yml` / `application-dev.yml`

- [ ] **Step 1: 验证旧 perf.job.* cron/enabled 配置 0 行（应已为 0，前期已验证）**

```bash
grep -n "perf\.job\." bootstrap/src/main/resources/*.yml
```
预期：仅保留 keep-count / retention-days；不应有 daily-kpi.cron / daily-kpi.enabled 等

- [ ] **Step 2: 验证 quartz 配置已就位（P1.6 已添加）**

```bash
grep -n "quartz" bootstrap/src/main/resources/*.yml
```
预期：spring.quartz.* 完整配置块存在

- [ ] **Step 3: 如有遗漏配置项需清理，本 task 内修复 + Commit；否则跳过**

```bash
git commit -m "chore(quartz-B): P4.3 application.yml 配置清理验证（已就位）

- 旧 perf.job.{daily-kpi,sys-control-cleanup,run-task-cleanup}.{cron,enabled} 验证 0 行
- 业务参数 perf.job.sys-control-cleanup.keep-count + perf.job.run-task-cleanup.retention-days 保留
- spring.quartz.* 配置块（P1.6 已添加）验证完整

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

如无变更则跳过 Commit。

---

## Task P4.4: 文档更新

**Files:**
- Modify: 根 `CLAUDE.md`（performance-engine-center 模块状态加 V1.6 标记）
- Modify: `system-governance-center/CLAUDE.md`（JobApi 4→1 方法、新增 QuartzConfig + JobExecutionLogger 说明）
- Modify: `performance-engine-center/CLAUDE.md`（删 ShedLock 相关、新增 Quartz 包装 Job 说明）
- Modify: `docs/CLAUDE.md`（新增 ddl-quartz.sql 说明 — P1.2 已部分更新）

- [ ] **Step 1: 根 CLAUDE.md performance 模块标记 V1.6**

`performance-engine-center` 行：
```markdown
| `performance-engine-center` | com.bank.branch.platform.performance | V1.6 已交付（quartz 整合 + ShedLock 删除） | 绩效计算中心 (V1.0-V1.5 累积能力 + V1.6 quartz 整合) |
```

- [ ] **Step 2: system-governance-center/CLAUDE.md 更新**

JobApi 表（行 99-105）改为：
```markdown
### JobApi (V1.6 精简)

| 方法 | 用途 |
|------|------|
| `getJobConf(jobKey)` | 获取定时任务配置（其他模块只读用途） |
```

新增章节"Quartz 调度引擎（V1.6）"：
- QuartzConfig：SchedulerFactoryBean + AutowiringSpringBeanJobFactory + JobExecutionLogger
- 集群模式 isClustered=true，11 张 QRTZ_* 同库 onepl
- JobListener 自动写 sys_job_run_log

- [ ] **Step 3: performance-engine-center/CLAUDE.md 更新**

删除 V1.2 Q5 ShedLock 相关章节；新增 Quartz 包装 Job 章节：
- 3 业务 Job + 3 Quartz 包装类（`job/quartz/` 子包）
- 业务方法独立可测，包装类仅作 execute() 委托

- [ ] **Step 4: 验证 + Commit**

```bash
git commit -m "docs(quartz-B): P4.4 4 份 CLAUDE.md 同步 V1.6 quartz 整合

- 根 CLAUDE.md: performance 模块状态标 V1.6
- system-governance/CLAUDE.md: JobApi 表 4→1 方法 + 新增 Quartz 章节
- performance-engine/CLAUDE.md: 删 ShedLock 章节 + 新增 Quartz 包装 Job 章节
- docs/CLAUDE.md: ddl-quartz.sql 说明（P1.2 已部分）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task P4.5: 全量构建 + 测试验证

- [ ] **Step 1: 全模块 clean install + 单元测试**

```bash
mvn clean install -DskipTests=false -q
```
预期：BUILD SUCCESS。

- [ ] **Step 2: surefire 测试统计**

```bash
mvn -pl system-governance-center,performance-engine-center test -q | grep "Tests run"
```
预期：surefire 单元测试 ≥45（按 spec §10.2 验收标准）

- [ ] **Step 3: failsafe 集成测试**

```bash
mvn -pl system-governance-center verify -q | grep "Tests run"
```
预期：failsafe 集成测试 ≥5

- [ ] **Step 4: 启动应用并验证 sys_job_conf 同步到 QRTZ_TRIGGERS**

```bash
cd bootstrap && mvn spring-boot:run &
# 等 30s 启动完成
mysql -uroot -p123456 onepl -e "SELECT TRIGGER_NAME, JOB_NAME, NEXT_FIRE_TIME FROM QRTZ_TRIGGERS;"
```
预期：3 行（DAILY_KPI_CALC_TRIGGER / SYS_CONTROL_CLEANUP_TRIGGER / PERF_RUN_TASK_CLEANUP_TRIGGER）

- [ ] **Step 5: §10.2 验收 grep 检查**

```bash
# 全部应返回 0 行
grep -rn "shedlock\|@SchedulerLock\|ShedLockConfig" --include="*.java" --include="*.xml" --include="*.yml"
grep -rn "@Scheduled" performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/
grep -rn "JobApi\.\(startJobRun\|completeJobRun\|failJobRun\)" --include="*.java"
```

- [ ] **Step 6: Commit（如有验证类附加修复）**

如全部通过则跳过 Commit；如有最后微调则 commit。

---

## Task P4.6: Phase 4 收尾推送 + 回报用户

- [ ] **Step 1: 推送 P4 到远程**

```bash
git push origin refactor/quartz-job-integration
```

- [ ] **Step 2: 回报用户**：子项目 B 全部 26 task 完成；测试统计 + grep 验证清单；**不**合并 master，等子项目 C（MyBatis-Plus）完成后统一合并。

- [ ] **Step 3: 更新 CLAUDE.md 模块依赖图**（如有）确认 V1.6 标记，准备进入子项目 C。

---

# 执行总结模板（供 implementer/reviewer 参考）

每个 task 完成后，implementer 子代理向主代理回报：

```
## Task PX.Y 完成报告

**Status:** DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED

**Files changed:**
- 新建: <文件清单>
- 修改: <文件清单>

**Tests added/modified:**
- 单元: N 个（surefire），全部 PASS
- 集成: M 个（failsafe），全部 PASS（如适用）

**Commits:**
- <SHA>: <commit message>

**TDD verification:**
- Red 步骤验证: 测试先失败 ✅
- Green 步骤验证: 测试后通过 ✅
- 重构步骤: 是否做（描述）

**Self-review findings:**
- <自查发现的问题，如已修复说明，未修复说明原因>

**Concerns (if DONE_WITH_CONCERNS):**
- <顾虑或观察点>
```

---

# 风险应对（沿用 spec §11）

| 风险 | 应对 |
|------|------|
| QRTZ_* DDL 未先执行导致 P1.6 集成测试 / P4.5 启动失败 | 先在 onepl 库手动执行 ddl-quartz.sql + migration 脚本（P1.2 / P1.3 已包含） |
| @Autowired 注入失败 | P1.4 测试已覆盖；P4.5 启动验证补跑 syncJobsOnStartup |
| misfire 配置错误 | P3.5 测试覆盖；启用 fail-fast（未知 policy 抛 IllegalArgumentException） |
| JobListener 写入失败影响业务 | P1.5 测试已覆盖异常隔离 |
| 集群行锁性能 | 当前单实例不会触发，未来扩容时观察 |
| Phase 间依赖混乱 | Phase 总览表已明确依赖；Phase 末必须全模块 clean install 通过才进入下一 Phase |

---

**END OF PLAN**
