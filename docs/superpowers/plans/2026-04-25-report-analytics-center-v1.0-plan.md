# Report-Analytics-Center V1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实施 report-analytics-center 模块 V1.0，落地 25 个 REST 接口 + 3 张自有表 + 跨模块只读聚合。

**Architecture:** 纯只读支撑域；不暴露 *Api 接口（仅 REST 给前端）；通过依赖 4 个已交付模块的 *Api（auth/governance/customer/performance）做跨模块查询；自有表仅 3 张配置表（rpt_saved_query / sql_probe_history / rpt_snapshot_task V1 仅建表）；异步导出复刻 perf_export_task 模式新增 rpt_export_task。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis 3.0.3 + JDK 17 + Flyway + JSqlParser 4.9 + Caffeine 缓存 + EasyExcel 3.3.4（导出）+ TDD Red-Green-Refactor

**依赖文档（权威需求来源）:**
- `docs/modules/report-analytics-center/01-功能规格.md`（330 行：业务范围 + 7 大子系统）
- `docs/modules/report-analytics-center/02-后端架构.md`（740 行：包结构 + 25 错误码 + Mapper 伪代码）
- `docs/modules/report-analytics-center/03-接口设计与报文.md`（1260 行：25 接口字段级 + 17 PT_RESOURCE）
- `docs/modules/report-analytics-center/04-对外API契约.md`（V1 不暴露 *Api，仅作 V2 预留）
- `docs/modules/report-analytics-center/05-表结构DDL.md`（415 行：3 张自有表 DDL）
- `docs/modules/report-analytics-center/06-并发与事务策略.md`（655 行）
- `docs/modules/report-analytics-center/07-审计要求.md`（610 行）
- `docs/modules/report-analytics-center/08-初始化数据清单.md`（572 行：sys_config_kv + 25 PT_RESOURCE + 角色绑定）
- `docs/modules/report-analytics-center/09-依赖契约摘要.md`（800 行：4 模块 Api 摘录）
- `docs/schema/ddl-report.sql`（rpt_saved_query / sql_probe_history / rpt_snapshot_task 三张表 DDL）
- `docs/common-dev-guide.md`（统一响应、错误码、分页、鉴权链路、DATA_SCOPE、审计、事件、日志九大共享规范）

**前置调研结论（撰写者已核对）:**
- `report-analytics-center/` 目录尚未创建（无 pom.xml、无 Java 文件、无 CLAUDE.md），M0 需从零搭建。
- 根 `pom.xml` 当前 `<modules>` 不含 `report-analytics-center`，M0.1 需新增。
- `auth-permission-center` 实际仅暴露 `CurrentUserApi / BizScopeApi / OrgApi / ResourceApi / AuthApi`，**不存在** `EmpQueryApi / UserQueryApi / OrgQueryApi / DataScopeApi / PermissionQueryApi`。设计文档 09 中提到的 Api 名以现状为准（`OrgApi.getOrg / getOrgSubtree / getUserMainOrg`，员工查询走 `CurrentUserApi.getCurrentUserContext` 或本地 Mapper）。
- `system-governance-center` 实际暴露 `DictApi / ConfigApi / CalendarApi / AuditApi / NotifyApi / FileApi / JobApi`，无 `AlertPublishApi / NotificationApi`（设计文档 02 中所提 NotificationApi 应映射为 `NotifyApi.sendNotification`）。
- `system-governance-center` 已有自己的 `SqlProbeController`（路径 `/api/admin/sql-probe/*`，BizType=SYS_CONFIG），与本模块设计的 `/api/reports/sql-probe/*` **路径不同、功能并行存在**。本模块在 SQL 探查上的差异点：(a) 路径前缀 `/api/reports/`，(b) 通过 JSqlParser 4.9 做 AST 级安全校验（governance 端是更朴素的关键字校验），(c) 维护本模块自有的 `sql_probe_history` 而非复用 governance 的 audit_log（双写关系：sql_probe_history + audit_log）。
- `customer-marketing-center` 实际暴露 `TagApi / LeadApi / CustomerQueryApi / ClaimApi / TouchTaskQueryApi`，**不存在** `LeadQueryApi`。设计文档 09 中 LeadApi 的方法以现状为准。
- `customer-marketing-center.TouchTaskQueryApi.getOrgTouchSummary` 已存在，可作为 C.2 触达汇总报表的数据源。
- `performance-engine-center.MetricApi.getEmpMetricValues / getOrgMetricValues / getCustMetricValues` 在 V1.1 已交付实现（V1.0 占位 UOE 已清零）；`MetricApi.batchGet*MetricValues / getMetricHistory / estimateRowCount` 09 文档中描述但**当前代码不存在**（属规划态），M2/M3 实施时按"规划态"处理：先用循环单条查询拼装，性能优化作为后续观察项；后续 V1.1+ 若 MetricApi 增补，再切换。
- `performance-engine-center.KpiApi.batchGetKpiTotal / getKpiRanking / getKpiDetail` 同样属规划态，09 文档已说明。当前 KpiApi 仅提供 `getKpiScheme / getKpiSchemeById` 与 V1.1 UOE 清零后的 `getCurrentKpiTotal / getCurrentKpiResult / getKpiHistory` 三个查询。M3 perf-summary 报表需做"仅按 EMP/ORG 维度循环调 getCurrentKpiTotal / getCurrentKpiResult"的简化处理。
- `performance-engine-center.SysControlApi` 当前不存在（V1 计划文档提及但未真实暴露 *Api）。M2 仪表盘的"数据版本"语义采用"performance 端在响应里给定 latestDataDate"或直接调 `MetricApi.getMetricDef` 配套读取 `sys_control` 表的妥协（本模块**禁止**直连 sys_control）。
- `performance-engine-center` 已有完整异步导出参考骨架：
  - `service/export/PerfExportService.java`（接口 5 方法）+ `impl/PerfExportServiceImpl.java`（已读 119 行确认骨架）
  - `entity/PerfExportTask.java`（PerfExportTask 实体，主键 id varchar(32)，字段：export_type/params_json/status/file_key/file_size/row_count/expire_at/operator_id/error_msg）
  - `mapper/PerfExportTaskMapper.java` + 对应 XML
  - `service/export/ExportStrategy.java`（策略接口）+ 4 个策略实现（KpiExportStrategy / MetricExportStrategy / AllocExportStrategy / DetailExportStrategy）
  - `controller/PerfExportController.java` + 3 个导出 ReqDTO + 1 个 RespDTO
  - DDL 脚本 `V1_2_1__perf_export_task.sql`（35 行，表名 perf_export_task）
  - V1.2 初期同步执行（createTask 内串行调 strategy.execute），V1.3+ 才计划切异步。**本模块直接对齐 V1.2 同步执行模型**（M5 落地，复用相同状态机），不引入异步线程池。
- `performance-engine-center` Flyway 风格参考 `V1_4_0__perf_target_owner_cols.sql`（含完整 runbook 注释 + 历史回填 + 索引）。本模块 M0 的 `V1_0_0__rpt_init.sql` 借鉴此风格。
- `governance.AuditApi.log(AuditLogCmd)` 是 REQUIRES_NEW 同步事务，与 `logAsync` 不存在（09 文档提到 logAsync 属规划态）。M2 仪表盘使用同步 `log()` 加上 `@Async + try-catch` 包装，避免影响响应时间。
- `common-security` 的 `BizType` 枚举已含 `REPORT` 与 `SYS_CONFIG`，`BizAction` 枚举已含 `READ / LIST / CREATE / UPDATE / DELETE / EXPORT / EXECUTE_SQL`，无需扩展。

**模块定位（来源 01 §1.1, 02 §2.2）:**
- 类型：支撑域（纯只读）
- 依赖方向：report → {auth, governance, customer, performance}（单向）
- 反向依赖：禁止任何业务模块在 pom.xml 引入 report-analytics-center
- 对外 Api：V1 不暴露（04 §1.1 明示），仅 REST 给前端

**预估规模:**
- 6 个 Milestone，预估 60-80 commit
- 25 个 REST 接口（17 条 PT_RESOURCE 注册按 03 §H 清单 + 8 条覆盖 08 §5.2 扩展资源 = 25 条总数）
- 3 张自有表（rpt_saved_query / sql_probe_history / rpt_snapshot_task）+ 1 张异步导出表（rpt_export_task）
- 25 条 RPT-* 基线错误码（02 §6.5：业务 10 + 权限 3 + SQL 探查 9 + 系统 3）+ 5 条 J 章扩展错误码（03 §J.4 RPT-42207~RPT-42211；J.4 中的 RPT-50002 与基线 §6.4 同码合并）
- 6 个 Maven 模块依赖（common-web/common-security/common-trace/common-aop/common-db + auth/governance/customer/performance + jsqlparser + easyexcel）

---

## 阶段概览

| Milestone | 范围 | 预估 commit | Phase 数 |
|---|---|---|---|
| **M0** | Maven 模块脚手架 + DDL Flyway + Entity/Mapper 雏形 + 25 错误码（02 §6.5 完整基线）+ Service/Controller 骨架 + 6 架构守护 + Controller IT 测试基础设施（F11） | 8-13 | 6 |
| **M1** | 动态查询：query-dimensions + dynamic-query + meta + export（4 接口）+ 查询方案保存（4 接口）= 8 接口 | 12-15 | 6 |
| **M2** | 仪表盘：president + org + emp（3 接口）+ Caffeine 缓存策略 + DashboardPresidentMetrics 预置指标 | 8-10 | 4 |
| **M3** | 汇总报表：touch / perf / cust 各 view + export = 6 接口 | 10-12 | 5 |
| **M4** | SQL 探查（高危）：execute + history + history-detail + whitelist + SqlSafeValidator + 双写审计 | 10-12 | 5 |
| **M5** | 异步导出（task）：rpt_export_task DDL + RptExportService + status/cancel/download 3 接口 | 8-10 | 4 |
| **M6** | 收尾：全量回归 + 6 架构守护 + 25 PT_RESOURCE 注册 + 文档三份同步 + 技术债清算 + V1.1 跨模块 Javadoc 顺手修补（F2/F4 副产物） | 6-10 | 4 |
| 合计 | 25 接口 + 4 张表 + 跨模块只读 | **62-83 commit** | **34 Phase** |

每个 Phase 内部按 1-3 Task 拆分；每 Task 严格 TDD Red+Green 双 commit（必要时 Refactor 单独 commit）。

---

## Milestone M0：模块脚手架与基础设施

### Phase M0.1：Maven 模块创建 + 父 pom.xml 注册

**Files:**
- Create: `D:\Project\oneplate\report-analytics-center\pom.xml`
- Modify: `D:\Project\oneplate\pom.xml`（在 `<modules>` 末尾追加 `<module>report-analytics-center</module>`）
- Create: `D:\Project\oneplate\report-analytics-center\src\main\java\com\bank\branch\platform\report\.gitkeep`（占位）

#### Task M0.1.1：父 pom.xml 注册子模块 + 子模块 pom.xml 雏形

- [ ] **Step 1：写 Red 测试 — `RptModuleStructureArchTest`**

```java
// report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/RptModuleStructureArchTest.java
package com.bank.branch.platform.report.arch;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.assertj.core.api.Assertions.assertThat;

class RptModuleStructureArchTest {

    @Test
    void shouldHaveModulePomXml() {
        File pom = new File("pom.xml");
        assertThat(pom).exists().isFile();
    }

    @Test
    void shouldDeclareJsqlParserDependency() throws Exception {
        String pom = java.nio.file.Files.readString(new java.io.File("pom.xml").toPath());
        assertThat(pom).contains("jsqlparser").contains("4.9");
    }

    @Test
    void shouldDeclareEasyExcelDependency() throws Exception {
        String pom = java.nio.file.Files.readString(new java.io.File("pom.xml").toPath());
        assertThat(pom).contains("easyexcel");
    }
}
```

- [ ] **Step 2：运行测试，确认失败（Red）**

```bash
cd D:/Project/oneplate/report-analytics-center
mvn -q -pl report-analytics-center test
```

预期：`pom.xml` 不存在或父模块未注册，测试编译/执行失败。

- [ ] **Step 3：Green — 创建父模块注册 + 子模块 pom.xml**

修改根 `pom.xml` 在 `<modules>` 列表追加：
```xml
<module>report-analytics-center</module>
```

创建 `report-analytics-center/pom.xml`：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>branch-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>report-analytics-center</artifactId>
    <name>Report Analytics Center</name>
    <description>报表分析中心：动态指标查询、固定管理报表、SQL 探查、异步导出（V1.0 25 REST + 3 自有表 + 1 异步导出表）</description>

    <dependencies>
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Common 模块 -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-trace</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-db</artifactId>
        </dependency>

        <!-- 跨模块依赖（4 个上游） -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>auth-permission-center</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>system-governance-center</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>customer-marketing-center</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>performance-engine-center</artifactId>
        </dependency>

        <!-- Spring Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- MyBatis -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis-starter.version}</version>
        </dependency>

        <!-- Flyway -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>

        <!-- JSqlParser 4.9（M4 SQL 探查 AST 校验） -->
        <dependency>
            <groupId>com.github.jsqlparser</groupId>
            <artifactId>jsqlparser</artifactId>
            <version>4.9</version>
        </dependency>

        <!-- EasyExcel 3.3.4（M5 导出） -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>easyexcel</artifactId>
            <version>${easyexcel.version}</version>
        </dependency>

        <!-- Caffeine（仪表盘 + 配置缓存） -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4：运行测试确认通过（Green）**

```bash
mvn -q -pl report-analytics-center test
```

- [ ] **Step 5：Commit**

```bash
git add pom.xml report-analytics-center/pom.xml report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/RptModuleStructureArchTest.java
git commit -m "$(cat <<'EOF'
chore(report-v1): report-analytics-center Maven 模块脚手架（Task M0.1.1）

- 父 pom.xml <modules> 追加 report-analytics-center
- 子 pom.xml 声明 6 个内部依赖 + jsqlparser 4.9 + easyexcel 3.3.4 + caffeine
- RptModuleStructureArchTest 守护模块声明（pom + jsqlparser + easyexcel）

EOF
)"
```

---

### Phase M0.2：Flyway 基线脚本 + 4 张表 DDL

**Files:**
- Create: `report-analytics-center/src/main/resources/sql/V1_0_0__rpt_init.sql`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/sql/V1_0_0FlywayIT.java`

#### Task M0.2.1：3 张自有表 DDL Flyway 初始化

> 包含 `rpt_saved_query` / `sql_probe_history` / `rpt_snapshot_task` 三张自有表，rpt_export_task 留待 M5。

- [ ] **Step 1：Red — V1_0_0FlywayIT**

```java
// src/test/java/com/bank/branch/platform/report/sql/V1_0_0FlywayIT.java
package com.bank.branch.platform.report.sql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.bank.branch.platform.report.ReportTestApplication.class)
class V1_0_0FlywayIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rptSavedQueryShouldExistWithRequiredColumns() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name='rpt_saved_query' " +
            "AND column_name IN ('id','emp_id','name','dim','subject_ids','metric_codes','version','created_time','updated_time')",
            Integer.class);
        assertThat(count).isEqualTo(9);
    }

    @Test
    void sqlProbeHistoryShouldExistWithRequiredColumns() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name='sql_probe_history' " +
            "AND column_name IN ('id','emp_id','sql_text','remark','row_count','execution_time_ms','status','error_msg','created_time')",
            Integer.class);
        assertThat(count).isEqualTo(9);
    }

    @Test
    void rptSnapshotTaskShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_name='rpt_snapshot_task'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

并配套创建最小启动类 `ReportTestApplication`：
```java
// src/test/java/com/bank/branch/platform/report/ReportTestApplication.java
package com.bank.branch.platform.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReportTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportTestApplication.class, args);
    }
}
```

`src/test/resources/application-test.yml` 配 H2 + Flyway 自动迁移：
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:rpt_test;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  flyway:
    locations: classpath:sql
    baseline-on-migrate: true
```

- [ ] **Step 2：Run & 确认失败**

```bash
mvn -q -pl report-analytics-center -Dtest=V1_0_0FlywayIT verify
```

预期：`sql/` 目录不存在 → 表不存在 → 三个测试断言失败。

- [ ] **Step 3：Green — 创建 V1_0_0__rpt_init.sql**

```sql
-- =====================================================================
-- report-analytics-center V1.0 基线 DDL
-- Version: V1_0_0
-- Date: 2026-04-25
-- Task: M0.2.1
--
-- 来源：docs/modules/report-analytics-center/05-表结构DDL.md
-- 三张自有表：rpt_saved_query / sql_probe_history / rpt_snapshot_task
-- rpt_export_task 留待 M5（V1_0_2__rpt_export_task.sql）
-- =====================================================================

-- 2.1 rpt_saved_query 动态查询保存方案
CREATE TABLE IF NOT EXISTS `rpt_saved_query` (
  `id`            varchar(32) NOT NULL COMMENT '方案ID（UUID）',
  `emp_id`        varchar(32) NOT NULL COMMENT '员工工号',
  `name`          varchar(200) NOT NULL COMMENT '方案名称',
  `dim`           varchar(20) NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids`   text NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes`  text NOT NULL COMMENT '指标编码列表(JSON数组)',
  `version`       int(11) DEFAULT 0 COMMENT '乐观锁版本号',
  `created_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`, `created_time`),
  KEY `idx_emp_id_name` (`emp_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';

-- 2.2 sql_probe_history SQL 探查历史
CREATE TABLE IF NOT EXISTS `sql_probe_history` (
  `id`                varchar(32) NOT NULL COMMENT '历史ID（UUID）',
  `emp_id`            varchar(32) NOT NULL COMMENT '执行人工号',
  `sql_text`          text NOT NULL COMMENT 'SQL语句',
  `remark`            varchar(500) DEFAULT NULL COMMENT '备注(reason)',
  `row_count`         int(11) DEFAULT NULL COMMENT '影响行数',
  `execution_time_ms` int(11) DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `status`            varchar(20) DEFAULT NULL COMMENT '状态：SUCCESS/FAILED/TIMEOUT/RUNNING',
  `error_msg`         text COMMENT '错误信息',
  `created_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_emp_time` (`emp_id`, `created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SQL探查历史';

-- 2.3 rpt_snapshot_task 快照任务配置（V1 仅建表，不启用）
CREATE TABLE IF NOT EXISTS `rpt_snapshot_task` (
  `id`             varchar(32) NOT NULL COMMENT '任务ID',
  `task_name`      varchar(200) NOT NULL COMMENT '任务名称',
  `snapshot_type`  varchar(50) NOT NULL COMMENT '快照类型',
  `cron_expr`      varchar(100) NOT NULL COMMENT 'Cron表达式',
  `status`         varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
  `last_run_time`  datetime DEFAULT NULL COMMENT '最近执行时间',
  `next_run_time`  datetime DEFAULT NULL COMMENT '下次执行时间',
  `created_time`   datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_next_run_time` (`next_run_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='快照任务配置（V1预留）';
```

- [ ] **Step 4：Run & 确认通过**

```bash
mvn -q -pl report-analytics-center -Dtest=V1_0_0FlywayIT verify
```

预期：3 个测试全绿。

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/resources/sql/V1_0_0__rpt_init.sql \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/sql/V1_0_0FlywayIT.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/ReportTestApplication.java \
        report-analytics-center/src/test/resources/application-test.yml
git commit -m "$(cat <<'EOF'
feat(report-v1): V1_0_0 Flyway 基线 DDL 落地（Green，Task M0.2.1）

- rpt_saved_query：方案归属 emp_id + 乐观锁 version + 双索引
- sql_probe_history：SQL 探查历史 + 5 索引（emp/time/emp_time/status）
- rpt_snapshot_task：V1 仅建表预留
- V1_0_0FlywayIT 守护 3 张表 + 关键字段存在

EOF
)"
```

---

### Phase M0.3：25 错误码枚举 + RptErrorCodeTest

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java`

#### Task M0.3.1：RptErrorCode 25 条 + 守护测试

> **范围：** 02 §6.5 完整基线 25 条（业务 10 + 权限 3 + SQL 探查 9 + 系统 3）。M5.4 再扩展 5 条 J 章导出限制码（RPT-42207~42211），最终合计 30 条。

- [ ] **Step 1：Red — RptErrorCodeTest**

```java
// src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java
package com.bank.branch.platform.report.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RptErrorCodeTest {

    @Test
    void shouldHaveExactly25ErrorCodes_BaseSet() {
        // 02 §6.5 基线 25 条（业务 10 + 权限 3 + SQL 探查 9 + 系统 3）
        assertThat(RptErrorCode.values()).hasSize(25);
    }

    @Test
    void allCodesShouldStartWithRptPrefix() {
        for (RptErrorCode c : RptErrorCode.values()) {
            assertThat(c.getCode()).startsWith("RPT-");
        }
    }

    @Test
    void shouldContainExpectedBusinessCodes() {
        // 业务码 10 条抽样
        assertThat(RptErrorCode.SAVED_QUERY_NOT_FOUND.getCode()).isEqualTo("RPT-40001");
        assertThat(RptErrorCode.DATA_VERSION_UNAVAILABLE.getCode()).isEqualTo("RPT-40004");
        assertThat(RptErrorCode.EXPORT_TASK_NOT_FOUND.getCode()).isEqualTo("RPT-40009");
        assertThat(RptErrorCode.EXPORT_TASK_NOT_READY.getCode()).isEqualTo("RPT-40010");
        // 权限码 3 条抽样
        assertThat(RptErrorCode.DASHBOARD_NO_ACCESS.getCode()).isEqualTo("RPT-40301");
        assertThat(RptErrorCode.DATA_SCOPE_INSUFFICIENT.getCode()).isEqualTo("RPT-40303");
        // SQL 探查 9 条抽样（含原 plan 漏掉的 42004 / 42006 / 42009）
        assertThat(RptErrorCode.SQL_PARSE_FAILED.getCode()).isEqualTo("RPT-42001");
        assertThat(RptErrorCode.SQL_ROW_LIMIT_EXCEEDED.getCode()).isEqualTo("RPT-42004");
        assertThat(RptErrorCode.SQL_EXECUTION_TIMEOUT.getCode()).isEqualTo("RPT-42005");
        assertThat(RptErrorCode.SQL_CONCURRENT_LIMIT.getCode()).isEqualTo("RPT-42006");
        assertThat(RptErrorCode.SQL_LENGTH_EXCEEDED.getCode()).isEqualTo("RPT-42008");
        assertThat(RptErrorCode.SQL_EXECUTION_FAILED.getCode()).isEqualTo("RPT-42009");
        // 系统码 3 条全断言（含原 plan 漏掉的 50002 / 50003）
        assertThat(RptErrorCode.CROSS_MODULE_CALL_FAILED.getCode()).isEqualTo("RPT-50001");
        assertThat(RptErrorCode.CACHE_READ_FAILED.getCode()).isEqualTo("RPT-50002");
        assertThat(RptErrorCode.EXPORT_START_FAILED.getCode()).isEqualTo("RPT-50003");
    }

    @Test
    void allMessagesShouldBeChinese() {
        for (RptErrorCode c : RptErrorCode.values()) {
            assertThat(c.getMsg()).matches(".*[\\u4e00-\\u9fa5]+.*");
        }
    }

    @Test
    void codesShouldBeUnique() {
        long distinct = java.util.Arrays.stream(RptErrorCode.values())
            .map(RptErrorCode::getCode).distinct().count();
        assertThat(distinct).isEqualTo(25);
    }
}
```

- [ ] **Step 2：Run & 确认失败**

```bash
mvn -q -pl report-analytics-center -Dtest=RptErrorCodeTest test
```

预期：编译失败（枚举类不存在）。

- [ ] **Step 3：Green — 实现 RptErrorCode**

```java
// src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java
package com.bank.branch.platform.report.enums;

import com.bank.branch.platform.common.web.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报表分析中心错误码（02-后端架构.md §6.5）.
 *
 * <p>编码规则：RPT-{HTTP_STATUS}{SEQ}
 * <ul>
 *   <li>400xx 业务错误（资源不存在、参数非法）</li>
 *   <li>403xx 权限/数据范围</li>
 *   <li>420xx SQL 探查相关</li>
 *   <li>500xx 系统错误</li>
 * </ul>
 *
 * <p>本枚举为 V1 基线 25 条（02 §6.5 完整列表）；M5.4 异步导出再扩展 5 条 J 章导出限制码（RPT-42207~42211）。
 */
@Getter
@AllArgsConstructor
public enum RptErrorCode implements ErrorCode {

    // 400xx 业务错误（10 条）
    SAVED_QUERY_NOT_FOUND("RPT-40001", "查询方案不存在"),
    SAVED_QUERY_NO_ACCESS("RPT-40002", "无权访问查询方案"),
    SAVED_QUERY_LIMIT_EXCEEDED("RPT-40003", "最多保存 10 个查询方案"),
    DATA_VERSION_UNAVAILABLE("RPT-40004", "数据日期对应的数据版本不可用"),
    SUBJECT_OUT_OF_SCOPE("RPT-40005", "查询对象不在数据范围内"),
    METRIC_DIM_MISMATCH("RPT-40006", "指标不属于该维度"),
    SUBJECT_SIZE_EXCEEDED("RPT-40007", "查询对象数超出限制（最多 100 个）"),
    METRIC_SIZE_EXCEEDED("RPT-40008", "查询指标数超出限制（最多 20 个）"),
    EXPORT_TASK_NOT_FOUND("RPT-40009", "导出任务不存在"),
    EXPORT_TASK_NOT_READY("RPT-40010", "导出任务尚未完成"),

    // 403xx 权限（3 条）
    DASHBOARD_NO_ACCESS("RPT-40301", "无权访问仪表盘"),
    SQL_PROBE_NO_ACCESS("RPT-40302", "无权使用 SQL 探查"),
    DATA_SCOPE_INSUFFICIENT("RPT-40303", "数据范围不足"),

    // 420xx SQL 探查（9 条）
    SQL_PARSE_FAILED("RPT-42001", "SQL 语法校验失败"),
    SQL_TABLE_NOT_WHITELISTED("RPT-42002", "SQL 访问了白名单外的表"),
    SQL_FORBIDDEN_KEYWORD("RPT-42003", "SQL 包含禁用关键字"),
    SQL_ROW_LIMIT_EXCEEDED("RPT-42004", "SQL 结果超出行数限制"),
    SQL_EXECUTION_TIMEOUT("RPT-42005", "SQL 执行超时"),
    SQL_CONCURRENT_LIMIT("RPT-42006", "SQL 并发数超限"),
    SQL_ONLY_SELECT_ALLOWED("RPT-42007", "SQL 仅允许 SELECT 语句"),
    SQL_LENGTH_EXCEEDED("RPT-42008", "SQL 长度超出限制"),
    SQL_EXECUTION_FAILED("RPT-42009", "SQL 执行失败"),

    // 500xx 系统错误（3 条）
    CROSS_MODULE_CALL_FAILED("RPT-50001", "跨模块调用失败"),
    CACHE_READ_FAILED("RPT-50002", "缓存读取失败"),
    EXPORT_START_FAILED("RPT-50003", "异步导出任务启动失败");

    private final String code;
    private final String msg;
}
```

- [ ] **Step 4：Run & 确认通过**

```bash
mvn -q -pl report-analytics-center -Dtest=RptErrorCodeTest test
```

预期：5 个测试全绿。

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): RptErrorCode 25 条基线错误码（Green，Task M0.3.1，修复 F1 漏项）

- 400xx 业务错误 10 条（saved-query / data-version / subject / metric / export-task）
- 403xx 权限 3 条（DASHBOARD / SQL_PROBE / DATA_SCOPE）
- 420xx SQL 探查 9 条（含原 plan 漏掉的 42004 / 42006 / 42009）
- 500xx 系统 3 条（含原 plan 漏掉的 50002 / 50003）
- RptErrorCodeTest 守护数量 25 + 前缀 + 中文消息 + 唯一性 + 关键码值（含 EXPORT_START_FAILED）
- 修复 F1：原 plan 错把 25 写为 18（漏 42004/42006/42009/50002/50003）

EOF
)"
```

---

### Phase M0.4：6 架构守护（BizAuth / NoEntityInController / NoUOE 等）

**Files:**
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/BizAuthArchTest.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/NoEntityInControllerArchTest.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/NoMapperInControllerArchTest.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/PackageStructureArchTest.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/CrossModuleApiOnlyArchTest.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/NoUoeInFacadeTestsArchTest.java`

#### Task M0.4.1：6 个 ArchUnit 守护测试

- [ ] **Step 1：Red — 6 个 ArchUnit 测试同时创建**

每个测试文件（详细类体见 02-后端架构.md §4 + 借鉴 performance-engine-center 的同名测试）：

```java
// 1) BizAuthArchTest：所有 RestController 公共方法必须有 @BizAuth
package com.bank.branch.platform.report.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.bank.branch.platform.report")
class BizAuthArchTest {

    @ArchTest
    static final ArchRule controllers_methods_must_have_BizAuth =
        methods()
            .that().areDeclaredInClassesThat()
                   .resideInAPackage("..controller..")
                   .and().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .and().arePublic()
            .and().areNotAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .should().beAnnotatedWith(com.bank.branch.platform.common.security.annotation.BizAuth.class)
            .orShould().beAnnotatedWith(com.bank.branch.platform.common.security.annotation.OpenApi.class);
}

// 2) NoEntityInControllerArchTest：Controller 方法签名禁止出现 entity 类型
// 3) NoMapperInControllerArchTest：Controller 不允许直接注入 Mapper
// 4) PackageStructureArchTest：包结构必须按 02 §3 layout（controller/facade/service/mapper/entity/dto/enums/config/listener/support）
// 5) CrossModuleApiOnlyArchTest：跨模块只允许通过 com.bank.branch.platform.{auth,governance,customer,performance}.api.* 引用，禁止 entity / mapper / serviceImpl
// 6) NoUoeInFacadeTestsArchTest：facade 测试不允许 assertThrows(UnsupportedOperationException.class, ...)
```

完整 6 个测试类的代码见 02 §4 + 借鉴 performance-engine-center/src/test/java/.../arch/* 同名实现。

- [ ] **Step 2：Run & 确认失败**

预期：6 个测试均报"目标包不存在"或"规则未满足"（因 controller/ 子包尚未创建，部分规则会因没有目标类而 vacuously 通过；至少有 PackageStructureArchTest 显式失败）。

- [ ] **Step 3：Green — 创建最小包结构 + 6 个守护落地**

创建空目录骨架：
```bash
mkdir -p report-analytics-center/src/main/java/com/bank/branch/platform/report/{controller,facade,service,mapper,entity,dto/req,dto/resp,enums,config,listener,support,convert}
```

在每个目录添加 `package-info.java` 占位（说明本目录用途，让 ArchUnit 找到目标包）。

- [ ] **Step 4：Run & 确认 6 守护绿**

```bash
mvn -q -pl report-analytics-center -Dtest='*ArchTest' test
```

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/test/java/com/bank/branch/platform/report/arch/ \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/
git commit -m "$(cat <<'EOF'
test(report-v1): 6 架构守护 + 包结构骨架（Green，Task M0.4.1）

- BizAuthArchTest：Controller 公共方法必须有 @BizAuth
- NoEntityInControllerArchTest：Controller 签名禁出现 Entity
- NoMapperInControllerArchTest：Controller 禁止注入 Mapper
- PackageStructureArchTest：包结构对齐 02 §3
- CrossModuleApiOnlyArchTest：跨模块只走 *Api
- NoUoeInFacadeTestsArchTest：facade 测试禁 assertThrows(UOE)

EOF
)"
```

---

### Phase M0.5：3 张表 Entity + Mapper 雏形 + 1 张表的最小 IT

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptSavedQuery.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/SqlProbeHistory.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptSnapshotTask.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptSavedQueryMapper.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/SqlProbeHistoryMapper.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptSnapshotTaskMapper.java`
- Create: `report-analytics-center/src/main/resources/mapper/RptSavedQueryMapper.xml`
- Create: `report-analytics-center/src/main/resources/mapper/SqlProbeHistoryMapper.xml`
- Create: `report-analytics-center/src/main/resources/mapper/RptSnapshotTaskMapper.xml`

#### Task M0.5.1：3 张表 Entity + Mapper（仅 insert + selectById + countByEmpId 雏形）

- [ ] **Step 1：Red — RptSavedQueryMapperIT**

```java
// src/test/java/com/bank/branch/platform/report/mapper/RptSavedQueryMapperIT.java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.entity.RptSavedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ReportTestApplication.class)
@Transactional
class RptSavedQueryMapperIT {

    @Autowired RptSavedQueryMapper mapper;

    @Test
    void insertAndSelectById_shouldRoundTrip() {
        RptSavedQuery e = new RptSavedQuery();
        e.setId("TEST_SQ_001");
        e.setEmpId("E10001");
        e.setName("我的方案 1");
        e.setDim("EMP");
        e.setSubjectIds("[\"E10001\"]");
        e.setMetricCodes("[\"M_DEPOSIT_BAL\"]");
        e.setVersion(0);
        e.setCreatedTime(LocalDateTime.now());

        mapper.insert(e);

        RptSavedQuery loaded = mapper.selectById("TEST_SQ_001");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getEmpId()).isEqualTo("E10001");
        assertThat(loaded.getName()).isEqualTo("我的方案 1");
    }

    @Test
    void countByEmpId_shouldReturnZero_whenNoData() {
        assertThat(mapper.countByEmpId("E_NOT_EXIST")).isZero();
    }
}
```

- [ ] **Step 2：Run & 确认失败**

预期：Mapper 接口/XML 不存在 → 应用启动失败。

- [ ] **Step 3：Green — 创建 3 个 Entity + 3 对 Mapper（接口 + XML）**

```java
// RptSavedQuery.java
@Data
public class RptSavedQuery {
    private String id;
    private String empId;
    private String name;
    private String dim;
    private String subjectIds;
    private String metricCodes;
    private Integer version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

// RptSavedQueryMapper.java
public interface RptSavedQueryMapper {
    int insert(RptSavedQuery e);
    RptSavedQuery selectById(@Param("id") String id);
    int countByEmpId(@Param("empId") String empId);
    int updateByIdSelective(RptSavedQuery e);
    int deleteById(@Param("id") String id);
}
```

XML：
```xml
<!-- RptSavedQueryMapper.xml -->
<mapper namespace="com.bank.branch.platform.report.mapper.RptSavedQueryMapper">
  <resultMap id="BaseMap" type="com.bank.branch.platform.report.entity.RptSavedQuery">
    <id     column="id"            property="id"/>
    <result column="emp_id"        property="empId"/>
    <result column="name"          property="name"/>
    <result column="dim"           property="dim"/>
    <result column="subject_ids"   property="subjectIds"/>
    <result column="metric_codes"  property="metricCodes"/>
    <result column="version"       property="version"/>
    <result column="created_time"  property="createdTime"/>
    <result column="updated_time"  property="updatedTime"/>
  </resultMap>

  <sql id="BASE_COLUMNS">
    id, emp_id, name, dim, subject_ids, metric_codes, version, created_time, updated_time
  </sql>

  <insert id="insert">
    INSERT INTO rpt_saved_query (<include refid="BASE_COLUMNS"/>)
    VALUES (#{id}, #{empId}, #{name}, #{dim}, #{subjectIds}, #{metricCodes}, #{version}, #{createdTime}, #{updatedTime})
  </insert>

  <select id="selectById" resultMap="BaseMap">
    SELECT <include refid="BASE_COLUMNS"/> FROM rpt_saved_query WHERE id = #{id}
  </select>

  <select id="countByEmpId" resultType="int">
    SELECT COUNT(*) FROM rpt_saved_query WHERE emp_id = #{empId}
  </select>
</mapper>
```

`SqlProbeHistory` / `RptSnapshotTask` Entity + Mapper 同样模式（Mapper 仅声明 insert + selectById + selectByEmpIdPaged），代码体省略形式重复，按 02 §3 + 05 §2.x 落地。

- [ ] **Step 4：Run & 确认通过**

```bash
mvn -q -pl report-analytics-center -Dtest='RptSavedQueryMapperIT' verify
```

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/ \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/ \
        report-analytics-center/src/main/resources/mapper/ \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/mapper/RptSavedQueryMapperIT.java
git commit -m "$(cat <<'EOF'
feat(report-v1): 3 张自有表 Entity + Mapper 雏形（Green，Task M0.5.1）

- RptSavedQuery / SqlProbeHistory / RptSnapshotTask Entity（贫血模型）
- 3 对 Mapper 接口 + XML（insert + selectById + count + update + delete 基础方法）
- RptSavedQueryMapperIT 守护 round-trip + countByEmpId

EOF
)"
```

---

### Phase M0.6：测试基础设施约定（F11 修补：PT_RESOURCE 时序前置）

**问题（reviewer F11）:** M5.4.2 / M1.6 / M2.4 / M3.4 / M4.4 才集中注册 PT_RESOURCE，但每个 Controller 阶段（M1.x ~ M5.x）的端到端 IT 在 PT_RESOURCE 行尚未写入时跑，会被 BizAuthInterceptor 拦截器以"resource_id not found"拒绝。

**约定（写在本 Phase，作为后续所有 Controller IT 必须遵守的测试规范）:**

#### Task M0.6.1：Controller IT 测试规范 — @MockBean BizAuthInterceptor

**所有 `*ControllerIT` 必须满足以下约定之一：**

**约定 A（首选）：** Controller IT 用 `@MockBean` 拦截 BizAuthInterceptor，不依赖 PT_RESOURCE 表已写入：

```java
@SpringBootTest(classes = ReportTestApplication.class)
@AutoConfigureMockMvc
class XxxControllerIT {

    @MockBean private BizAuthInterceptor bizAuthInterceptor;  // 关键：跳过 PT_RESOURCE 表查询

    @BeforeEach
    void mockBizAuth() throws Exception {
        // 默认全放行；具体 IT 需要 deny 时单测覆盖
        when(bizAuthInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }
    // ... 测试 case
}
```

**约定 B（备选）：** 测试基类 `@Sql` 提前 INSERT 测试用 PT_RESOURCE 行：

```java
@Sql(scripts = "/sql/test-pt-resources-rpt.sql", executionPhase = BEFORE_TEST_CLASS)
class XxxControllerIT { ... }
```

测试用 PT_RESOURCE 种子集中放在 `report-analytics-center/src/test/resources/sql/test-pt-resources-rpt.sql`（M0.6.1 创建空骨架，每个 Controller IT 阶段按需追加）。

**约定 C（生产环境）:** M1.6 / M2.4 / M3.4 / M4.4 / M5.4 集中注册的 V1_0_X__rpt_*_pt_resources.sql 仍保留作为生产 Flyway，仅在 IT 中走 mock/seed 旁路。

#### Task M0.6.2：BizAuthInterceptor mock 助手 + 测试 PT_RESOURCE 种子骨架

- [ ] **Step 1：Red — 写一个 ControllerIT 范例验证 mock 工作**

```java
// MetaControllerSmokeIT.java（仅作为约定验证）
@Test
void smokeTest_withMockedBizAuth() throws Exception {
    when(bizAuthInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    mvc.perform(get("/api/reports/query-dimensions"))
        .andExpect(status().isOk());
}
```

- [ ] **Step 2-3：Green — 创建 `BaseControllerIT` 抽象类**

```java
// src/test/java/com/bank/branch/platform/report/BaseControllerIT.java
@SpringBootTest(classes = ReportTestApplication.class)
@AutoConfigureMockMvc
public abstract class BaseControllerIT {
    @Autowired protected MockMvc mvc;
    @MockBean protected BizAuthInterceptor bizAuthInterceptor;

    @BeforeEach
    void setUpBizAuth() throws Exception {
        when(bizAuthInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }
}
```

后续所有 ControllerIT 继承此 BaseControllerIT 即可跳过 PT_RESOURCE 检查。

- [ ] **Step 4-5：Run + Commit**

```bash
git commit -m "$(cat <<'EOF'
chore(report-v1): F11 修补 — Controller IT 测试基础设施（BaseControllerIT + @MockBean BizAuthInterceptor）

- 解决：PT_RESOURCE 在 M5.4.2 / M1.6 集中注册前，Controller IT 走 mock 拦截器
- 约定 A（首选）：所有 *ControllerIT 继承 BaseControllerIT
- 约定 B（备选）：@Sql 提前 INSERT 测试 PT_RESOURCE 行
- 生产 V1_0_X__rpt_*_pt_resources.sql 保留集中注册（不分散到每个接口 commit）

EOF
)"
```

---

## Milestone M1：动态查询 + 查询方案保存（8 接口）

### Phase M1.1：维度+指标树元数据（A.1 GET /query-dimensions）

**Files:**
- Create: `dto/resp/QueryDimensionRespDTO.java` + `MetricTreeNodeDTO.java`
- Create: `service/MetaService.java` + `MetaServiceImpl.java`
- Create: `controller/MetaController.java`

#### Task M1.1.1：A.1 GET /api/reports/query-dimensions（按 dim 返回指标树）

**业务规则（来源 03 §A.1）:**
- 入参：dim ∈ {EMP / ORG / CUST}
- 出参：QueryDimensionRespDTO { dim, dimName, metrics: List<MetricTreeNodeDTO> }
- 数据来源：`MetricApi.listMetrics(baseDim, null)`（performance V1.0 已实现）+ 按 metric_def 自带的 `category` 字段做分组

- [ ] **Step 1：Red — MetaServiceTest**

```java
// src/test/java/com/bank/branch/platform/report/service/MetaServiceTest.java
package com.bank.branch.platform.report.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.report.dto.resp.QueryDimensionRespDTO;
import com.bank.branch.platform.report.service.impl.MetaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock MetricApi metricApi;
    @Mock DictApi dictApi;
    @InjectMocks MetaServiceImpl service;

    @Test
    void getQueryDimensions_EMP_returnsGroupedTree() {
        MetricDefDTO m1 = mockMetric("M_DEPOSIT_BAL", "存款余额", "EMP", "DEPOSIT");
        MetricDefDTO m2 = mockMetric("M_LOAN_BAL", "贷款余额", "EMP", "LOAN");
        when(metricApi.listMetrics("EMP", null)).thenReturn(List.of(m1, m2));
        when(dictApi.getDictLabel("REPORT_DIM", "EMP")).thenReturn("人员");

        QueryDimensionRespDTO resp = service.getQueryDimensions("EMP");

        assertThat(resp.getDim()).isEqualTo("EMP");
        assertThat(resp.getDimName()).isEqualTo("人员");
        assertThat(resp.getMetrics()).hasSize(2);
        assertThat(resp.getMetrics().get(0).getGroupCode()).isEqualTo("DEPOSIT");
        assertThat(resp.getMetrics().get(0).getChildren()).hasSize(1);
        assertThat(resp.getMetrics().get(0).getChildren().get(0).getMetricCode()).isEqualTo("M_DEPOSIT_BAL");
    }

    @Test
    void getQueryDimensions_invalidDim_throws() {
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> service.getQueryDimensions("INVALID"))
            .hasMessageContaining("RPT-40006");
    }

    private MetricDefDTO mockMetric(String code, String name, String dim, String category) {
        MetricDefDTO m = new MetricDefDTO();
        m.setMetricCode(code);
        m.setMetricName(name);
        m.setBaseDim(dim);
        m.setCategory(category);
        return m;
    }
}
```

- [ ] **Step 2：Run & 确认失败**（service 类不存在 → 编译失败）

- [ ] **Step 3：Green — 实现 MetaService + DTO**

```java
// dto/resp/QueryDimensionRespDTO.java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class QueryDimensionRespDTO {
    private String dim;
    private String dimName;
    private List<MetricTreeNodeDTO> metrics;
}

// dto/resp/MetricTreeNodeDTO.java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class MetricTreeNodeDTO {
    private String groupCode;
    private String groupName;
    private String metricCode;
    private String metricName;
    private String unit;
    private String description;
    private List<MetricTreeNodeDTO> children;
}

// service/MetaService.java
public interface MetaService {
    QueryDimensionRespDTO getQueryDimensions(String dim);
}

// service/impl/MetaServiceImpl.java
@Service
@RequiredArgsConstructor
public class MetaServiceImpl implements MetaService {

    private static final Set<String> VALID_DIMS = Set.of("EMP", "ORG", "CUST");

    private final MetricApi metricApi;
    private final DictApi dictApi;

    @Override
    public QueryDimensionRespDTO getQueryDimensions(String dim) {
        if (!VALID_DIMS.contains(dim)) {
            throw new BizException(RptErrorCode.METRIC_DIM_MISMATCH);
        }
        List<MetricDefDTO> metrics = metricApi.listMetrics(dim, null);
        // 按 category 分组
        Map<String, List<MetricTreeNodeDTO>> grouped = metrics.stream()
            .map(m -> MetricTreeNodeDTO.builder()
                .metricCode(m.getMetricCode())
                .metricName(m.getMetricName())
                .unit(m.getUnit())
                .description(m.getDescription())
                .build())
            .collect(Collectors.groupingBy(
                m -> Optional.ofNullable(getCategoryByCode(m.getMetricCode(), metrics)).orElse("DEFAULT")));

        List<MetricTreeNodeDTO> tree = grouped.entrySet().stream()
            .map(e -> MetricTreeNodeDTO.builder()
                .groupCode(e.getKey())
                .groupName(dictApi.getDictLabel("METRIC_CATEGORY", e.getKey()))
                .children(e.getValue())
                .build())
            .sorted(Comparator.comparing(MetricTreeNodeDTO::getGroupCode))
            .collect(Collectors.toList());

        return QueryDimensionRespDTO.builder()
            .dim(dim)
            .dimName(dictApi.getDictLabel("REPORT_DIM", dim))
            .metrics(tree)
            .build();
    }

    private String getCategoryByCode(String code, List<MetricDefDTO> all) {
        return all.stream().filter(m -> m.getMetricCode().equals(code))
            .findFirst().map(MetricDefDTO::getCategory).orElse(null);
    }
}

// controller/MetaController.java
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "报表-元数据")
public class MetaController {
    private final MetaService metaService;

    @GetMapping("/query-dimensions")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "获取查询维度+指标树")
    public CommonResult<QueryDimensionRespDTO> getQueryDimensions(@RequestParam String dim) {
        return CommonResult.success(metaService.getQueryDimensions(dim));
    }
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/ \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/MetaService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/impl/MetaServiceImpl.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/MetaController.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/MetaServiceTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): A.1 GET /query-dimensions 维度+指标树（Green，Task M1.1.1）

- 入参 dim 校验 EMP/ORG/CUST，非法返回 RPT-40006
- 数据源：MetricApi.listMetrics(baseDim, null)，按 category 分组
- DictApi 翻译 REPORT_DIM / METRIC_CATEGORY 中文名
- MetaServiceTest 覆盖正常 + 非法入参分支

EOF
)"
```

---

### Phase M1.2：动态查询执行（A.2 POST /dynamic-query）

**业务规则（03 §A.2 + 09 §X.3）:**
- 入参 DynamicQueryReqDTO：dim / subjectIds (≤100) / metricCodes (≤20) / dataDate
- DataScope 双层过滤：外层 BizScopeApi.buildScopeContext(REPORT, LIST) + 内层 MetricApi 自带 scope
- 跨模块批量调用：performance MetricApi 单条循环（V1.0 简化，batchGet* 留观察项）
- 错误码：40007 / 40008 / 40005 / 40004

#### Task M1.2.1：DynamicQueryService + DynamicQueryFacade + Controller

- [ ] **Step 1：Red — DynamicQueryFacadeTest**

完整测试代码（150 行 mock 5 个 Api 联动覆盖：subjectIds 超限 / metricCodes 超限 / 越权 subject / 数据版本不可用 / 正常 happy path）。略，按 03 §A.2 字段级精度落地。

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — 实现 DynamicQueryService 三层**

```java
// service/DynamicQueryService.java
public interface DynamicQueryService {
    DynamicQueryRespDTO execute(DynamicQueryReqDTO req);
}

// service/impl/DynamicQueryServiceImpl.java
@Service
@RequiredArgsConstructor
public class DynamicQueryServiceImpl implements DynamicQueryService {
    private final BizScopeApi bizScopeApi;
    private final CurrentUserApi currentUserApi;
    private final MetricApi metricApi;
    private final OrgApi orgApi;

    @Override
    public DynamicQueryRespDTO execute(DynamicQueryReqDTO req) {
        // 1) 入参校验
        if (req.getSubjectIds().size() > 100) {
            throw new BizException(RptErrorCode.SUBJECT_SIZE_EXCEEDED);
        }
        if (req.getMetricCodes().size() > 20) {
            throw new BizException(RptErrorCode.METRIC_SIZE_EXCEEDED);
        }
        // 2) DataScope 过滤（外层）
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);
        List<String> allowed = filterBySubjectScope(req.getDim(), req.getSubjectIds(), scope);
        if (allowed.size() < req.getSubjectIds().size()) {
            throw new BizException(RptErrorCode.SUBJECT_OUT_OF_SCOPE);
        }
        // 3) 批量取值（V1.0 单条循环，batch 优化留观察项）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String sid : allowed) {
            Map<String, BigDecimal> values = fetchByDim(req.getDim(), sid, req.getDataDate(), req.getMetricCodes());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectId", sid);
            row.put("subjectName", resolveSubjectName(req.getDim(), sid));
            row.putAll(values);
            rows.add(row);
        }
        // 4) 列定义
        List<MetricColumnDTO> columns = req.getMetricCodes().stream()
            .map(code -> metricApi.getMetricDef(code).orElse(null))
            .filter(Objects::nonNull)
            .map(this::toColumnDTO)
            .collect(Collectors.toList());

        return DynamicQueryRespDTO.builder()
            .dim(req.getDim())
            .dataDate(req.getDataDate())
            .columns(columns)
            .rows(rows)
            .rowCount(rows.size())
            .build();
    }
    // 辅助方法略（filterBySubjectScope / fetchByDim / resolveSubjectName / toColumnDTO）
}
```

完整 Facade + Controller 体见 02 §3 + 03 §A.2。

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/DynamicQueryReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/DynamicQueryRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/MetricColumnDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/DynamicQueryService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/impl/DynamicQueryServiceImpl.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/facade/DynamicQueryFacade.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/DynamicQueryController.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/DynamicQueryServiceTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): A.2 POST /dynamic-query 动态查询执行（Green，Task M1.2.1）

- 三层过滤：subjectSize/metricSize 入参 + DataScope 外层 + MetricApi 内层
- 跨模块：MetricApi.getEmpMetricValues / getOrgMetricValues / getCustMetricValues
- 错误码：40007/40008/40005/40004
- DynamicQueryServiceTest 覆盖 5 个分支

EOF
)"
```

---

### Phase M1.3：动态查询导出占位（A.3 POST /dynamic-query/export）

**说明：** A.3 强制异步导出，但具体导出执行逻辑 + Worker 落地在 M5。M1.3 仅落地 Controller + 校验 + 创建任务的 PENDING 状态，taskId 返回。M5 完成异步执行链路。

#### Task M1.3.1：DynamicQueryExportController（占位）

- [ ] Step 1-5：略，按 M1.2 同样模式落地。1 commit。

---

### Phase M1.4：查询方案保存（B.1 GET /saved-queries 列表）

**业务规则（03 §B.1 + 06 §2）:**
- 仅查本人的方案（DataScope SELF）
- 可按 dim 筛选

#### Task M1.4.1：B.1 列表接口

- [ ] Step 1-5：略。1 commit（Red+Green）。

---

### Phase M1.5：查询方案保存（B.2 POST + B.3 PUT + B.4 DELETE）

#### Task M1.5.1：B.2 POST 保存（含 10 条上限 + 自动删除最旧）

业务核心（06 §2.2）：单事务内先 count → 超 10 删最旧 → INSERT。

- [ ] **Step 1：Red — SavedQueryServiceTest 覆盖"超 10 条自动删最旧"**

```java
// src/test/java/com/bank/branch/platform/report/service/SavedQueryServiceTest.java
@ExtendWith(MockitoExtension.class)
class SavedQueryServiceTest {

    @Mock private RptSavedQueryMapper mapper;
    @Mock private CurrentUserApi currentUserApi;
    @InjectMocks private SavedQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    @Test
    void saveQuery_whenUnder10_insertOnly() {
        when(mapper.countByEmp("E001")).thenReturn(5);
        SavedQuerySaveReqDTO req = buildReq("dim_emp_q1");
        String id = service.saveQuery(req);
        verify(mapper, never()).deleteOldest(anyString());
        verify(mapper, times(1)).insert(any(RptSavedQuery.class));
        assertThat(id).isNotBlank();
    }

    @Test
    void saveQuery_when10_shouldDeleteOldestThenInsert() {
        when(mapper.countByEmp("E001")).thenReturn(10);
        when(mapper.findOldestId("E001")).thenReturn("Q-OLDEST");
        SavedQuerySaveReqDTO req = buildReq("dim_emp_q11");
        service.saveQuery(req);
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteOldest("Q-OLDEST");
        order.verify(mapper).insert(any(RptSavedQuery.class));
    }

    @Test
    void saveQuery_when11OrMore_alsoDeletesOldest() {
        // 防御性：异常状态（已超 10），仍删最旧再插
        when(mapper.countByEmp("E001")).thenReturn(11);
        when(mapper.findOldestId("E001")).thenReturn("Q-OLDEST");
        service.saveQuery(buildReq("any"));
        verify(mapper).deleteOldest("Q-OLDEST");
        verify(mapper).insert(any());
    }

    private SavedQuerySaveReqDTO buildReq(String name) {
        SavedQuerySaveReqDTO r = new SavedQuerySaveReqDTO();
        r.setName(name);
        r.setDim("EMP");
        r.setSubjectIdsJson("[\"E001\"]");
        r.setMetricCodesJson("[\"DEP_BAL_EMP_DAILY\"]");
        return r;
    }
}
```

- [ ] **Step 2：Run & 确认失败**

```bash
mvn -q -pl report-analytics-center -Dtest=SavedQueryServiceTest test
```

- [ ] **Step 3：Green — 实现 SavedQueryServiceImpl.saveQuery**

```java
// service/impl/SavedQueryServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedQueryServiceImpl implements SavedQueryService {

    private static final int MAX_PER_EMP = 10;

    private final RptSavedQueryMapper mapper;
    private final CurrentUserApi currentUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveQuery(SavedQuerySaveReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        // 1) 计数 → 超 10 删最旧（06 §2.2 单事务）
        int cnt = mapper.countByEmp(empId);
        if (cnt >= MAX_PER_EMP) {
            String oldestId = mapper.findOldestId(empId);
            if (oldestId != null) {
                mapper.deleteOldest(oldestId);
                log.info("[SavedQuery] 用户 {} 已达 {} 条上限，自动删最旧 {}", empId, MAX_PER_EMP, oldestId);
            }
        }
        // 2) 插新记录
        RptSavedQuery entity = new RptSavedQuery();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setEmpId(empId);
        entity.setName(req.getName());
        entity.setDim(req.getDim());
        entity.setSubjectIdsJson(req.getSubjectIdsJson());
        entity.setMetricCodesJson(req.getMetricCodesJson());
        entity.setVersion(1);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        mapper.insert(entity);
        return entity.getId();
    }
}

// mapper/RptSavedQueryMapper.java 新增方法
int countByEmp(@Param("empId") String empId);
String findOldestId(@Param("empId") String empId);
int deleteOldest(@Param("id") String id);

// mapper/RptSavedQueryMapper.xml 新增 SQL
<select id="countByEmp" resultType="int">
  SELECT COUNT(*) FROM rpt_saved_query WHERE emp_id = #{empId} AND status='ACTIVE'
</select>
<select id="findOldestId" resultType="string">
  SELECT id FROM rpt_saved_query
  WHERE emp_id = #{empId} AND status='ACTIVE'
  ORDER BY created_time ASC LIMIT 1
</select>
<update id="deleteOldest">
  UPDATE rpt_saved_query SET status='INACTIVE', updated_time=NOW()
  WHERE id = #{id}
</update>
```

- [ ] **Step 4：Run & 确认 3 个测试全绿**

- [ ] **Step 5：Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(report-v1): B.2 POST /saved-queries 保存（10 条上限 + 自动删最旧，Green，Task M1.5.1）

- 单事务内 count → 超 10 软删最旧 → INSERT
- SavedQueryServiceTest 覆盖 < 10 / = 10 / > 10 三分支
- 错误码：无（业务上不返错，超限静默删旧）

EOF
)"
```

#### Task M1.5.2：B.3 PUT 更新（乐观锁 version + 仅本人）

- [ ] Step 1-5：略。

#### Task M1.5.3：B.4 DELETE（仅本人）

- [ ] Step 1-5：略。

---

### Phase M1.6：M1 阶段回归 + 8 接口 PT_RESOURCE 注册

**Files:**
- Create: `report-analytics-center/src/main/resources/sql/V1_0_1__rpt_meta_pt_resources.sql`（8 条）

#### Task M1.6.1：M1 阶段全量回归

- [ ] Step 1：`mvn -q -pl report-analytics-center clean verify` 全绿
- [ ] Step 2：检查 8 接口 PT_RESOURCE 在 V1_0_1 脚本中已声明
- [ ] Step 3：Commit

```bash
git commit -m "chore(report-v1): M1 全量回归 + 8 接口 PT_RESOURCE（Task M1.6.1）..."
```

---

## Milestone M2：固定管理报表 — 仪表盘（3 接口）

### Phase M2.1：DashboardPresidentMetrics 预置指标 + Caffeine 缓存配置

**Files:**
- Create: `config/DashboardPresidentMetrics.java`
- Create: `config/ReportCacheConfig.java`
- Create: `service/DashboardCacheKeys.java`

#### Task M2.1.1：硬编码 5 类预置指标常量类

**业务规则（08 §6.1）:** 分行行长仪表盘的 5 类预置指标（CORE / ACHIEVEMENT / TREND / RANKING / CUST_CONTRIBUTION）硬编码于代码，避免首次部署需配置元数据。

- [ ] **Step 1：Red — DashboardPresidentMetricsTest**

```java
// src/test/java/com/bank/branch/platform/report/config/DashboardPresidentMetricsTest.java
package com.bank.branch.platform.report.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardPresidentMetricsTest {

    @Test
    void coreMetrics_shouldHave4Items() {
        assertThat(DashboardPresidentMetrics.CORE_METRICS)
            .containsExactly("DEP_BAL_ORG_DAILY", "LOAN_BAL_ORG_DAILY",
                             "INT_INCOME_ORG_MONTH", "FEE_INCOME_ORG_MONTH");
    }

    @Test
    void achievementMetrics_shouldHave4Items() {
        assertThat(DashboardPresidentMetrics.ACHIEVEMENT_METRICS).hasSize(4);
    }

    @Test
    void trendMetrics_shouldHave4Items() {
        assertThat(DashboardPresidentMetrics.TREND_METRICS).hasSize(4);
    }

    @Test
    void rankingMetrics_shouldHave2Items() {
        assertThat(DashboardPresidentMetrics.RANKING_METRICS).hasSize(2);
    }

    @Test
    void custContributionMetrics_shouldHave2Items() {
        assertThat(DashboardPresidentMetrics.CUST_CONTRIBUTION_METRICS).hasSize(2);
    }

    @Test
    void allMetricsShouldBeImmutable() {
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> DashboardPresidentMetrics.CORE_METRICS.add("X"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2：Run & 确认失败**（类不存在）

- [ ] **Step 3：Green — 创建常量类**

```java
// config/DashboardPresidentMetrics.java
package com.bank.branch.platform.report.config;

import java.util.List;

/**
 * 分行行长仪表盘 V1 预置指标（硬编码，08 §6.1）.
 *
 * <p>V2 计划迁移到 rpt_dashboard_def 表，支持不同分行/角色定制。
 */
public final class DashboardPresidentMetrics {

    private DashboardPresidentMetrics() {}

    /** 核心指标（顶部 KPI 区） */
    public static final List<String> CORE_METRICS = List.of(
        "DEP_BAL_ORG_DAILY",
        "LOAN_BAL_ORG_DAILY",
        "INT_INCOME_ORG_MONTH",
        "FEE_INCOME_ORG_MONTH"
    );

    /** 达成率指标（进度条区） */
    public static final List<String> ACHIEVEMENT_METRICS = List.of(
        "DEP_ACHIEVE_RATE_ORG",
        "LOAN_ACHIEVE_RATE_ORG",
        "NEW_CUST_ACHIEVE_ORG",
        "KPI_ACHIEVE_RATE_ORG"
    );

    /** 同比/环比 */
    public static final List<String> TREND_METRICS = List.of(
        "DEP_BAL_YOY_RATE",
        "LOAN_BAL_YOY_RATE",
        "DEP_BAL_MOM_RATE",
        "LOAN_BAL_MOM_RATE"
    );

    /** 机构排行榜 */
    public static final List<String> RANKING_METRICS = List.of(
        "KPI_TOTAL_SCORE_ORG",
        "DEP_BAL_ORG_DAILY"
    );

    /** Top 客户贡献 */
    public static final List<String> CUST_CONTRIBUTION_METRICS = List.of(
        "AUM_TOTAL_CUST",
        "PROFIT_CONTRIB_CUST"
    );
}
```

并落地 `ReportCacheConfig`：
```java
// config/ReportCacheConfig.java
@Configuration
@EnableCaching
public class ReportCacheConfig {

    @Bean("rptCacheManager")
    public CacheManager rptCacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager(
            "rpt:dashboard:president",
            "rpt:metric:tree",
            "rpt:summary:touch",
            "rpt:summary:perf",
            "rpt:summary:cust");
        cm.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(500));
        return cm;
    }
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/config/DashboardPresidentMetrics.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/config/ReportCacheConfig.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/config/DashboardPresidentMetricsTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): 仪表盘预置指标常量 + Caffeine 缓存（Green，Task M2.1.1）

- DashboardPresidentMetrics：5 类预置指标硬编码（CORE/ACHIEVEMENT/TREND/RANKING/CUST_CONTRIB）
- 全部 List.of(...) 不可变，DashboardPresidentMetricsTest 守护数量与不可变性
- ReportCacheConfig：5 个 Caffeine 缓存（dashboard / metric-tree / 3 类 summary），TTL 5min

EOF
)"
```

---

### Phase M2.2：分行行长仪表盘（C.1 GET /dashboard/president）

**业务规则（03 §C.1 + 01 §4.1）:**
- 权限：BizAuth(REPORT, READ) + 角色白名单"分行行长"（R_PRESIDENT）→ 不通过返回 RPT-40301
- 数据装配：
  - depositTrend / loanTrend：MetricApi.getOrgMetricValues 循环 12 个月（V1.0 简化，趋势查询用单条循环）
  - orgRanking：OrgApi.getOrgChildren / getOrgSubtreeCodes 取下属机构，再循环 batch get 指标
  - topCustomers：CustomerQueryApi.searchCustomers 默认 + 按 AUM 排序（V1.0 取前 10）
  - summaryMetrics：CORE_METRICS + getOrgMetricValues 单次查询
- 缓存：`rpt:dashboard:president`，TTL 5 分钟，按 dataDate 与 orgCode 区分
- 审计：异步包装的 AuditApi.log（避免影响响应时间）

#### Task M2.2.1：DashboardServiceTest（最重要 mock 链路）

- [ ] **Step 1：Red — DashboardServiceTest**

```java
// src/test/java/com/bank/branch/platform/report/service/DashboardServiceTest.java
package com.bank.branch.platform.report.service;

// 完整测试代码：mock CurrentUserApi(R_PRESIDENT 角色) + OrgApi + MetricApi + CustomerQueryApi 联动
// 覆盖：(1) R_PRESIDENT 角色通过 (2) 非 R_PRESIDENT 抛 RPT-40301 (3) dataDate 默认值兜底

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock CurrentUserApi currentUserApi;
    @Mock OrgApi orgApi;
    @Mock MetricApi metricApi;
    @Mock CustomerQueryApi customerQueryApi;
    @Mock AuditApi auditApi;
    @InjectMocks DashboardServiceImpl service;

    @Test
    void getPresidentDashboard_withRoleR_PRESIDENT_returnsData() {
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES_001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_001");
        when(orgApi.getOrgSubtreeCodes("ORG_001")).thenReturn(Set.of("ORG_001", "ORG_002"));
        when(metricApi.getOrgMetricValues(eq("ORG_001"), any(), eq(DashboardPresidentMetrics.CORE_METRICS)))
            .thenReturn(Map.of(
                "DEP_BAL_ORG_DAILY", new BigDecimal("1520.00"),
                "LOAN_BAL_ORG_DAILY", new BigDecimal("980.50"),
                "INT_INCOME_ORG_MONTH", new BigDecimal("35.20"),
                "FEE_INCOME_ORG_MONTH", new BigDecimal("12.80")));
        // ... 其他 mock 装配

        PresidentDashboardRespDTO resp = service.getPresidentDashboard(LocalDate.parse("2026-04-09"));

        assertThat(resp.getDataDate()).isEqualTo(LocalDate.parse("2026-04-09"));
        assertThat(resp.getSummaryMetrics())
            .containsKeys("DEP_BAL_ORG_DAILY", "LOAN_BAL_ORG_DAILY",
                          "INT_INCOME_ORG_MONTH", "FEE_INCOME_ORG_MONTH");
    }

    @Test
    void getPresidentDashboard_withoutRole_throws40301() {
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RM"));

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> service.getPresidentDashboard(LocalDate.now()))
            .hasMessageContaining("RPT-40301");
    }
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — 实现 DashboardService + Facade + Controller**

完整代码（300+ 行）按 02 §3 + 03 §C.1 落地。Facade 层做角色校验 + 跨模块编排，Service 层做数据装配 + Caffeine 缓存。

```java
// service/DashboardService.java
public interface DashboardService {
    PresidentDashboardRespDTO getPresidentDashboard(LocalDate dataDate);
}

// service/impl/DashboardServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private static final String ROLE_PRESIDENT = "R_PRESIDENT";

    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;
    private final MetricApi metricApi;
    private final CustomerQueryApi customerQueryApi;
    private final AuditApi auditApi;

    @Override
    @Cacheable(value = "rpt:dashboard:president",
               key = "T(java.lang.String).format('%s:%s', #dataDate, @currentUserApi.getCurrentOrgCode())",
               unless = "#result == null")
    public PresidentDashboardRespDTO getPresidentDashboard(LocalDate dataDate) {
        // F8 修补说明：SpEL `@currentUserApi` 通过 BeanFactoryResolver 解析当前请求的 CurrentUserApi bean
        // 同款表达式适用于 M2.3 机构 / 员工仪表盘（cache name 改为 rpt:dashboard:org / rpt:dashboard:emp）
        // 1) 角色校验
        if (!currentUserApi.getCurrentRoleCodes().contains(ROLE_PRESIDENT)) {
            throw new BizException(RptErrorCode.DASHBOARD_NO_ACCESS);
        }
        String orgCode = currentUserApi.getCurrentOrgCode();
        // 2) summary metrics
        Map<String, BigDecimal> summary = metricApi.getOrgMetricValues(orgCode, dataDate,
            DashboardPresidentMetrics.CORE_METRICS);
        // 3) 趋势图（12 月循环）
        ChartDataDTO depositTrend = buildTrend("DEP_BAL_ORG_DAILY", "全行存款趋势", orgCode, dataDate);
        ChartDataDTO loanTrend = buildTrend("LOAN_BAL_ORG_DAILY", "全行贷款趋势", orgCode, dataDate);
        // 4) 机构排名
        List<OrgRankingItemDTO> ranking = buildOrgRanking(orgCode, dataDate);
        // 5) Top 客户
        List<TopCustomerDTO> topCusts = buildTopCustomers(dataDate);
        // 6) 异步审计
        try {
            auditApi.log(buildAuditCmd(orgCode, dataDate, "SUCCESS"));
        } catch (Exception e) {
            log.warn("[DashboardService] audit log failed", e);
        }
        return PresidentDashboardRespDTO.builder()
            .dataDate(dataDate)
            .summaryMetrics(summary)
            .depositTrend(depositTrend)
            .loanTrend(loanTrend)
            .orgRanking(ranking)
            .topCustomers(topCusts)
            .build();
    }
    // 辅助方法略
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/PresidentDashboardRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ChartDataDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/OrgRankingItemDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/TopCustomerDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/DashboardService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/impl/DashboardServiceImpl.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/facade/DashboardFacade.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/DashboardController.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/DashboardServiceTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): C.1 GET /dashboard/president 分行行长仪表盘（Green，Task M2.2.1）

- 角色白名单：R_PRESIDENT，非角色返回 RPT-40301
- 装配 5 个区块：summary / depositTrend / loanTrend / orgRanking / topCustomers
- 跨模块：MetricApi / OrgApi / CustomerQueryApi
- Caffeine 缓存 5min，按 (dataDate, orgCode) 区分
- DashboardServiceTest 覆盖角色通过/拒绝两分支

EOF
)"
```

---

### Phase M2.3：机构仪表盘 + 员工仪表盘（C-bis: GET /dashboard/org/{orgCode} + /dashboard/emp/{empId}）

**说明：** 03 文档原始只显式展开 C.1 总裁仪表盘，08 §5.1 资源清单含 `RPT_DASH_ORG` / `RPT_DASH_EMP` 两条。机构仪表盘按 ORG_SUBTREE 维度展示子机构数据；员工仪表盘按 SELF 维度展示个人 KPI。

#### Task M2.3.1：C.2 机构仪表盘 + Task M2.3.2：C.3 员工仪表盘

每 Task 一对 Red+Green commit，模式同 M2.2，参考 03 §C.1 字段集做 ORG/EMP 维度变体。

---

### Phase M2.4：M2 阶段全量回归 + 3 接口 PT_RESOURCE 注册

#### Task M2.4.1：补充 V1_0_2__rpt_dashboard_pt_resources.sql + 全量回归

- [ ] Step 1-5：略，单 commit。

---

## Milestone M3：汇总报表（6 接口）

### Phase M3.1：触达任务监控报表（C.2 GET /touch-task-summary + 导出）

**业务规则（03 §C.2 + 09 §3.3）:**
- 数据源：`customer-marketing-center.TouchTaskQueryApi.getOrgTouchSummary` + 按 dateRange 过滤
- DataScope：BizScope REPORT + ORG_SUBTREE，机构子树过滤
- 入参 startDate / endDate / orgId（可选），范围 ≤ 1 年（08 §2.1 max.date.range.days = 366）

#### Task M3.1.1：C.2 view 接口

- [ ] **Step 1：Red — TouchSummaryServiceTest**

```java
@ExtendWith(MockitoExtension.class)
class TouchSummaryServiceTest {

    @Mock private TouchTaskQueryApi touchTaskQueryApi;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private OrgApi orgApi;
    @InjectMocks private TouchSummaryServiceImpl service;

    @Test
    void getOrgTouchSummary_shouldReturnAggregated() {
        // 准备上游 mock 数据（customer.TouchTaskQueryApi.getOrgTouchSummary 返回 TouchOrgSummaryDTO）
        TouchOrgSummaryDTO upstream = new TouchOrgSummaryDTO();
        upstream.setOrgCode("BR001");
        upstream.setTotalTask(120);
        upstream.setSuccessCount(80);
        upstream.setFailedCount(40);
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR001");
        when(touchTaskQueryApi.getOrgTouchSummary(eq("BR001"),
                any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(upstream));

        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2026, 4, 1));
        req.setEndDate(LocalDate.of(2026, 4, 25));
        req.setOrgId("BR001");
        PageResult<ReportTouchOrgVO> result = service.getOrgTouchSummary(req, new PageRequest(1, 20));

        assertThat(result.getTotal()).isEqualTo(1);
        ReportTouchOrgVO vo = result.getList().get(0);
        assertThat(vo.getOrgCode()).isEqualTo("BR001");
        assertThat(vo.getTotalTask()).isEqualTo(120);
        assertThat(vo.getSuccessRate()).isEqualByComparingTo(new BigDecimal("0.6667"));
    }

    @Test
    void getOrgTouchSummary_dateRangeOver1Year_throws40006() {
        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2025, 1, 1));
        req.setEndDate(LocalDate.of(2026, 4, 1));  // 455 days
        assertThatThrownBy(() -> service.getOrgTouchSummary(req, new PageRequest(1, 20)))
            .hasMessageContaining("RPT-40006");
    }

    @Test
    void getOrgTouchSummary_upstreamFailure_wrapsAs50001() {
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR001");
        when(touchTaskQueryApi.getOrgTouchSummary(any(), any(), any()))
            .thenThrow(new RuntimeException("upstream timeout"));
        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2026, 4, 1));
        req.setEndDate(LocalDate.of(2026, 4, 25));
        assertThatThrownBy(() -> service.getOrgTouchSummary(req, new PageRequest(1, 20)))
            .hasMessageContaining("RPT-50001");
    }
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — TouchSummaryServiceImpl + ReportTouchOrgVO 转换**

```java
// dto/resp/ReportTouchOrgVO.java
@Data
@Builder
public class ReportTouchOrgVO {
    private String orgCode;
    private String orgName;
    private Integer totalTask;
    private Integer successCount;
    private Integer failedCount;
    private BigDecimal successRate;  // 4 位小数
}

// service/impl/TouchSummaryServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class TouchSummaryServiceImpl implements TouchSummaryService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int SCALE = 4;

    private final TouchTaskQueryApi touchTaskQueryApi;
    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;

    @Override
    public PageResult<ReportTouchOrgVO> getOrgTouchSummary(
            TouchSummaryReqDTO req, PageRequest page) {
        // 1) 日期范围校验（08 §2.1 max.date.range.days = 366）
        long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate());
        if (days > MAX_RANGE_DAYS) {
            throw new BizException(RptErrorCode.METRIC_DIM_MISMATCH,
                "日期范围 " + days + " 天超过上限 " + MAX_RANGE_DAYS);
        }
        // 2) DataScope：未传 orgId 时按当前用户 orgCode 兜底
        String orgCode = StringUtils.hasText(req.getOrgId())
            ? req.getOrgId()
            : currentUserApi.getCurrentOrgCode();

        // 3) 调上游 + try-catch 转 50001（R4 fail-close）
        List<TouchOrgSummaryDTO> upstream;
        try {
            upstream = touchTaskQueryApi.getOrgTouchSummary(
                orgCode, req.getStartDate(), req.getEndDate());
        } catch (RuntimeException ex) {
            log.warn("[TouchSummary] upstream failed", ex);
            throw new BizException(RptErrorCode.CROSS_MODULE_CALL_FAILED,
                "TouchTaskQueryApi.getOrgTouchSummary: " + ex.getMessage());
        }
        // 4) 装配 VO（含 OrgApi 反查 orgName + 计算 successRate）
        List<ReportTouchOrgVO> voList = upstream.stream()
            .map(this::toVO).collect(Collectors.toList());

        // 5) 内存分页（V1 简化，规模 ≤ 10000 行可接受）
        return PageResult.of(voList, page);
    }

    private ReportTouchOrgVO toVO(TouchOrgSummaryDTO dto) {
        BigDecimal rate = dto.getTotalTask() == 0
            ? BigDecimal.ZERO
            : new BigDecimal(dto.getSuccessCount())
                .divide(new BigDecimal(dto.getTotalTask()), SCALE, RoundingMode.HALF_UP);
        return ReportTouchOrgVO.builder()
            .orgCode(dto.getOrgCode())
            .orgName(orgApi.getOrg(dto.getOrgCode()).getOrgName())
            .totalTask(dto.getTotalTask())
            .successCount(dto.getSuccessCount())
            .failedCount(dto.getFailedCount())
            .successRate(rate)
            .build();
    }
}
```

- [ ] **Step 4：Run & 确认 3 个测试全绿**

- [ ] **Step 5：Commit（一对 Red+Green commit）**

#### Task M3.1.2：触达汇总导出（异步占位，Worker 落地在 M5）

- [ ] Step 1-5：略。一对 Red+Green commit。

---

### Phase M3.2：绩效汇总报表（C.3 GET /perf-summary + 导出）

**业务规则（03 §C.3 + 09 §4.2）:**
- 数据源：performance.KpiApi.getCurrentKpiTotal / getCurrentKpiResult 真实方法（V1.1 P2.6 已交付实现）
- V1.0 报表 perf-summary 按 EMP 维度循环单条调用（subjectIds.size ≤ 100，单次接口耗时可接受）
- 维度：dim ∈ {ORG / EMP}；ORG 维度先按子树展开 EMP 列表，再循环
- 数据日期：performance.SysControlApi 当前不存在，临时按"performance 端在 KpiResultDTO 中给出 calcDate"或前端传 dataDate 兜底

#### Task M3.2.1：C.3 view 接口（KpiApi 单条循环兜底）

- [ ] **Step 1：Red — PerfSummaryServiceTest**

```java
@ExtendWith(MockitoExtension.class)
class PerfSummaryServiceTest {

    @Mock private KpiApi kpiApi;
    @Mock private CurrentUserApi currentUserApi;
    @InjectMocks private PerfSummaryServiceImpl service;

    @Test
    void getEmpPerfSummary_singleEmp_returnsKpiTotal() {
        // V1.1 P2.6 真调：getCurrentKpiTotal(empId, cycleType)
        KpiTotalDTO total = new KpiTotalDTO();
        total.setEmpId("E001");
        total.setCycleType("MONTHLY");
        total.setTotalScore(new BigDecimal("85.5"));
        when(kpiApi.getCurrentKpiTotal("E001", "MONTHLY")).thenReturn(total);

        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E001"));
        req.setCycleType("MONTHLY");
        PageResult<PerfSummaryRowVO> result = service.getPerfSummary(req, new PageRequest(1, 20));

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getTotalScore()).isEqualByComparingTo("85.5");
    }

    @Test
    void getEmpPerfSummary_subjectIdsOver100_throws40007() {
        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(IntStream.range(0, 101)
            .mapToObj(i -> "E" + i).collect(Collectors.toList()));
        assertThatThrownBy(() -> service.getPerfSummary(req, new PageRequest(1, 20)))
            .hasMessageContaining("RPT-40007");
    }

    @Test
    void getEmpPerfSummary_kpiApiThrows_wrapsAs50001() {
        when(kpiApi.getCurrentKpiTotal(anyString(), anyString()))
            .thenThrow(new RuntimeException("upstream"));
        PerfSummaryReqDTO req = new PerfSummaryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E001"));
        req.setCycleType("MONTHLY");
        assertThatThrownBy(() -> service.getPerfSummary(req, new PageRequest(1, 20)))
            .hasMessageContaining("RPT-50001");
    }
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — PerfSummaryServiceImpl**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PerfSummaryServiceImpl implements PerfSummaryService {

    private static final int MAX_SUBJECTS = 100;

    private final KpiApi kpiApi;
    private final CurrentUserApi currentUserApi;

    @Override
    public PageResult<PerfSummaryRowVO> getPerfSummary(
            PerfSummaryReqDTO req, PageRequest page) {
        // 1) 大小校验
        if (req.getSubjectIds().size() > MAX_SUBJECTS) {
            throw new BizException(RptErrorCode.SUBJECT_SIZE_EXCEEDED);
        }
        // 2) 单条循环（V1.0 简化，performance V1.1 P2.6 已交付真实接口；
        //    后续 V1.1+ 若 KpiApi.batchGetKpiTotal 落地再切批量）
        List<PerfSummaryRowVO> rows = new ArrayList<>();
        for (String empId : req.getSubjectIds()) {
            try {
                KpiTotalDTO total = kpiApi.getCurrentKpiTotal(empId, req.getCycleType());
                if (total == null) continue;  // 该员工无 KPI 数据则跳过
                rows.add(PerfSummaryRowVO.builder()
                    .empId(empId)
                    .cycleType(total.getCycleType())
                    .totalScore(total.getTotalScore())
                    .build());
            } catch (RuntimeException ex) {
                log.warn("[PerfSummary] kpiApi failed for empId={}", empId, ex);
                throw new BizException(RptErrorCode.CROSS_MODULE_CALL_FAILED,
                    "KpiApi.getCurrentKpiTotal: " + ex.getMessage());
            }
        }
        return PageResult.of(rows, page);
    }
}
```

- [ ] **Step 4：Run & 确认 3 个测试全绿**

- [ ] **Step 5：Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(report-v1): C.3 GET /perf-summary 绩效汇总（KpiApi V1.1 真调，Green，Task M3.2.1）

- 调 performance.KpiApi.getCurrentKpiTotal（V1.1 P2.6 已交付实现）
- subjectIds 上限 100；单条循环（V1.1+ 计划切 batchGet 切换登记 M6.3 技术债）
- try-catch 包装上游异常 → RPT-50001（R4 fail-close）

EOF
)"
```

#### Task M3.2.2：C.3 导出（异步任务占位，Worker 落地 M5）

- [ ] Step 1-5：按 M3.1.2 同款模板落地（异步任务 createTask + 类型 PERF_SUMMARY）。

---

### Phase M3.3：客户池统计报表（C.4 GET /customer-pool-summary + 导出）

**业务规则（03 §C.4）:**
- 数据源：`CustomerQueryApi.countCustomers` + 按客户等级分组（VIP / 普通 / 潜在）
- DATA_SCOPE：受 BizType=REPORT + ORG_SUBTREE 约束

#### Task M3.3.1：C.4 view + Task M3.3.2：导出

每 Task 一对 commit。

---

### Phase M3.4：M3 阶段全量回归 + 6 接口 PT_RESOURCE 注册

#### Task M3.4.1：V1_0_3__rpt_summary_pt_resources.sql + 全量回归

- [ ] Step 1-5：略，单 commit。

---

### Phase M3.5：M3 collateral — 跨模块 Api 真实可用性回归

**说明：** M3 的关键风险是 customer.TouchTaskQueryApi.getOrgTouchSummary / performance.KpiApi.getCurrentKpiTotal 实际签名是否与设计文档 09 一致。本 Phase 做一次 Mapper IT + 真 SpringContext + 真 Mock Bean 的端到端校验，避免 M6 才暴露问题。

#### Task M3.5.1：跨模块 Api 集成测试

- [ ] Step 1：写 ReportCrossModuleAvailabilityIT 启动 SpringContext + Mock 4 个上游 API + 调用 M2/M3 三接口。
- [ ] Step 2：Run，预期成功（M2/M3 已落地）。如果某 Api 签名与文档不符，本 commit 用 fix 对齐文档/CLAUDE.md。
- [ ] Step 3：Commit。

---

## Milestone M4：SQL 探查（高危，5 接口含 SqlSafeValidator）

### Phase M4.1：SqlSafeValidator 核心校验器（≥15 边界用例）

**业务规则（02 §5 + BR-1 决策点）:**
- JSqlParser 4.9 解析 → AST
- 子查询深度 ≤ 3 层
- 禁 UNION / EXCEPT / INTERSECT / 触发器 / 存储过程
- 强制 LIMIT ≤ 1000，无 LIMIT 自动追加
- 白名单 12 张表（08 §1.1：cust_master / cust_lead / cust_tag / touch_record / emp_index_result / org_index_result / cust_index_result / kpi_result / metric_def / sys_dict / sys_dict_item / EXT_ORG_INFO / EXT_USER_ORG）
- 禁用关键字（08 §1.1 30 个）

**Files:**
- Create: `support/SqlSafeValidator.java` + `SqlSafeResult.java`
- Create: `support/SqlSafeValidatorTest.java`（≥15 case）

#### Task M4.1.1：SqlSafeValidator 主校验器

- [ ] **Step 1：Red — SqlSafeValidatorTest（≥15 边界用例）**

```java
// src/test/java/com/bank/branch/platform/report/support/SqlSafeValidatorTest.java
package com.bank.branch.platform.report.support;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlSafeValidatorTest {

    private final SqlSafeValidator v = new SqlSafeValidator(
        java.util.List.of("cust_master", "kpi_result", "metric_def"),
        java.util.List.of("DROP", "DELETE", "UPDATE", "INSERT"),
        1000, 5000, 3);

    // ===== 1) 通过用例 =====
    @Test void validate_simpleSelect_passes() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM cust_master WHERE id=1");
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getNormalizedSql()).contains("LIMIT 1000");
    }

    @Test void validate_selectWithWhitelistJoin_passes() {
        SqlSafeResult r = v.validateAndNormalize(
            "SELECT k.score FROM kpi_result k JOIN cust_master c ON k.emp_id = c.id LIMIT 100");
        assertThat(r.isAllowed()).isTrue();
    }

    @Test void validate_selectWithSubquery_passesIfDepthLE3() {
        SqlSafeResult r = v.validateAndNormalize(
            "SELECT * FROM cust_master WHERE id IN (SELECT emp_id FROM kpi_result) LIMIT 100");
        assertThat(r.isAllowed()).isTrue();
    }

    // ===== 2) 拒绝用例（白名单）=====
    @Test void validate_tableNotInWhitelist_rejects42002() {
        assertThatThrownBy(() -> v.validateAndNormalize("SELECT * FROM secret_table"))
            .hasMessageContaining("RPT-42002");
    }

    // ===== 3) 拒绝用例（关键字）=====
    @Test void validate_dropKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("DROP TABLE cust_master"))
            .hasMessageContaining("RPT-42003");
    }
    @Test void validate_deleteKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("DELETE FROM cust_master"))
            .hasMessageContaining("RPT-42003");
    }
    @Test void validate_updateKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("UPDATE cust_master SET name='x'"))
            .hasMessageContaining("RPT-42003");
    }
    @Test void validate_insertKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("INSERT INTO cust_master VALUES (1)"))
            .hasMessageContaining("RPT-42003");
    }

    // ===== 4) 仅 SELECT 限制 =====
    @Test void validate_nonSelectStatement_rejects42007() {
        assertThatThrownBy(() -> v.validateAndNormalize("SHOW TABLES"))
            .hasMessageContaining("RPT-42007");
    }

    // ===== 5) UNION/EXCEPT/INTERSECT 拒绝 =====
    @Test void validate_unionAll_rejects42001() {
        assertThatThrownBy(() ->
            v.validateAndNormalize("SELECT * FROM cust_master UNION ALL SELECT * FROM kpi_result"))
            .hasMessageContaining("RPT-42001");
    }

    // ===== 6) 子查询深度 =====
    @Test void validate_subqueryDepth4_rejects42001() {
        String sql = "SELECT * FROM cust_master WHERE id IN " +
                     "(SELECT id FROM cust_master WHERE id IN " +
                     "(SELECT id FROM cust_master WHERE id IN " +
                     "(SELECT id FROM cust_master WHERE id IN " +
                     "(SELECT id FROM cust_master))))";
        assertThatThrownBy(() -> v.validateAndNormalize(sql))
            .hasMessageContaining("RPT-42001");
    }

    // ===== 7) LIMIT 自动追加 =====
    @Test void validate_noLimit_autoAppends1000() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM cust_master");
        assertThat(r.getNormalizedSql()).endsWith("LIMIT 1000");
    }
    @Test void validate_limitOver1000_clampsTo1000() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM cust_master LIMIT 5000");
        assertThat(r.getNormalizedSql()).endsWith("LIMIT 1000");
    }
    @Test void validate_limitUnder1000_keeps() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM cust_master LIMIT 50");
        assertThat(r.getNormalizedSql()).endsWith("LIMIT 50");
    }

    // ===== 8) 长度上限 =====
    @Test void validate_sqlLengthOver5000_rejects42008() {
        String longSql = "SELECT * FROM cust_master WHERE id=" + "1".repeat(5100);
        assertThatThrownBy(() -> v.validateAndNormalize(longSql))
            .hasMessageContaining("RPT-42008");
    }

    // ===== 9) 解析失败 =====
    @Test void validate_invalidSyntax_rejects42001() {
        assertThatThrownBy(() -> v.validateAndNormalize("SELECT FROM ;;"))
            .hasMessageContaining("RPT-42001");
    }

    // ===== 10) 大小写不敏感关键字 =====
    @Test void validate_lowercaseKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("drop table cust_master"))
            .hasMessageContaining("RPT-42003");
    }
}
```

- [ ] **Step 2：Run & 确认失败**（SqlSafeValidator 不存在）

- [ ] **Step 3：Green — 实现 SqlSafeValidator**

```java
// support/SqlSafeValidator.java
package com.bank.branch.platform.report.support;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.enums.RptErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 探查 AST 安全校验器（02 §5 + BR-1 决策点）.
 *
 * <p>校验链：
 * <ol>
 *   <li>长度上限</li>
 *   <li>JSqlParser 解析（拿 AST）</li>
 *   <li>仅 Select（SELECT 语句）</li>
 *   <li>禁 UNION/EXCEPT/INTERSECT</li>
 *   <li>子查询深度 ≤ 3</li>
 *   <li>白名单表</li>
 *   <li>禁用关键字（保留正则兜底，AST 解析不到的 case 由正则覆盖）</li>
 *   <li>LIMIT 追加 / clamp</li>
 * </ol>
 */
@Component
@Slf4j
public class SqlSafeValidator {

    private final List<String> whitelistTables;
    private final List<String> forbiddenKeywords;
    private final int maxRows;
    private final int maxSqlLength;
    private final int maxSubqueryDepth;
    private final Pattern keywordPattern;
    private final Pattern limitPattern;

    public SqlSafeValidator(List<String> whitelistTables,
                            List<String> forbiddenKeywords,
                            int maxRows,
                            int maxSqlLength,
                            int maxSubqueryDepth) {
        this.whitelistTables = whitelistTables.stream()
            .map(s -> s.toLowerCase(Locale.ROOT)).toList();
        this.forbiddenKeywords = forbiddenKeywords;
        this.maxRows = maxRows;
        this.maxSqlLength = maxSqlLength;
        this.maxSubqueryDepth = maxSubqueryDepth;
        this.keywordPattern = Pattern.compile(
            "(?i)\\b(" + String.join("|", forbiddenKeywords) + ")\\b");
        this.limitPattern = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\s*(,\\s*\\d+)?\\s*$");
    }

    public SqlSafeResult validateAndNormalize(String sql) {
        // 1) 长度
        if (sql.length() > maxSqlLength) {
            throw new BizException(RptErrorCode.SQL_PARSE_FAILED,
                "SQL 长度 " + sql.length() + " 超过上限 " + maxSqlLength);
        }
        // 2) 关键字（正则兜底，先于解析以提供清晰错误码）
        if (keywordPattern.matcher(sql).find()) {
            throw new BizException(RptErrorCode.SQL_FORBIDDEN_KEYWORD);
        }
        // 3) JSqlParser 解析
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            throw new BizException(RptErrorCode.SQL_PARSE_FAILED, ex.getMessage());
        }
        // 4) 仅 Select
        if (!(stmt instanceof Select)) {
            throw new BizException(RptErrorCode.SQL_ONLY_SELECT_ALLOWED);
        }
        Select select = (Select) stmt;
        // 5) UNION/EXCEPT/INTERSECT 禁用
        if (select.getSelectBody() instanceof SetOperationList) {
            throw new BizException(RptErrorCode.SQL_PARSE_FAILED,
                "禁止 UNION/EXCEPT/INTERSECT");
        }
        // 6) 子查询深度
        int depth = computeDepth(select.getSelectBody(), 1);
        if (depth > maxSubqueryDepth) {
            throw new BizException(RptErrorCode.SQL_PARSE_FAILED,
                "子查询嵌套深度 " + depth + " > " + maxSubqueryDepth);
        }
        // 7) 白名单表校验
        TablesNamesFinder finder = new TablesNamesFinder();
        List<String> tables = finder.getTableList(stmt);
        for (String t : tables) {
            if (!whitelistTables.contains(t.toLowerCase(Locale.ROOT))) {
                throw new BizException(RptErrorCode.SQL_TABLE_NOT_WHITELISTED,
                    "表 " + t + " 不在白名单");
            }
        }
        // 8) LIMIT 追加 / clamp
        String normalized = normalizeLimit(sql);
        return SqlSafeResult.allowed(normalized, tables);
    }

    /**
     * 递归计算子查询嵌套深度（F10 修补：原 plan 仅占位伪代码，此处给出 6 处递归路径完整实现）.
     *
     * <p>覆盖路径（JSqlParser 4.9 visitor 替代方案，使用 instanceof 显式递归）：
     * <ol>
     *   <li>FromItem：Table（无子查询） / SubSelect / SubJoin（递归 left + right + on 表达式）</li>
     *   <li>每个 Join 的 right item</li>
     *   <li>Where 表达式（InExpression.getRightItemsList / ExistsExpression / NotExpression / AndExpression / OrExpression）</li>
     *   <li>Having 表达式（同 Where）</li>
     *   <li>SelectItem 列表（SelectExpressionItem 中的 SubSelect）</li>
     * </ol>
     */
    private int computeDepth(net.sf.jsqlparser.statement.select.SelectBody body, int currentDepth) {
        if (!(body instanceof PlainSelect)) return currentDepth;
        PlainSelect ps = (PlainSelect) body;
        int max = currentDepth;

        // 1) FROM item（可能是 SubSelect / SubJoin）
        max = Math.max(max, depthOfFromItem(ps.getFromItem(), currentDepth));

        // 2) JOINs：每个 join 的 right item
        if (ps.getJoins() != null) {
            for (net.sf.jsqlparser.statement.select.Join j : ps.getJoins()) {
                max = Math.max(max, depthOfFromItem(j.getRightItem(), currentDepth));
                if (j.getOnExpression() != null) {
                    max = Math.max(max, depthOfExpression(j.getOnExpression(), currentDepth));
                }
            }
        }

        // 3) WHERE 表达式
        if (ps.getWhere() != null) {
            max = Math.max(max, depthOfExpression(ps.getWhere(), currentDepth));
        }

        // 4) HAVING 表达式
        if (ps.getHaving() != null) {
            max = Math.max(max, depthOfExpression(ps.getHaving(), currentDepth));
        }

        // 5) SELECT 项中的 SubSelect（如 SELECT (SELECT MAX(x) FROM t2) FROM t1）
        if (ps.getSelectItems() != null) {
            for (net.sf.jsqlparser.statement.select.SelectItem item : ps.getSelectItems()) {
                if (item instanceof net.sf.jsqlparser.statement.select.SelectExpressionItem) {
                    net.sf.jsqlparser.expression.Expression expr =
                        ((net.sf.jsqlparser.statement.select.SelectExpressionItem) item).getExpression();
                    max = Math.max(max, depthOfExpression(expr, currentDepth));
                }
            }
        }

        return max;
    }

    /** 处理 FromItem 的子查询深度（Table 无子查询，SubSelect/SubJoin 需递归）. */
    private int depthOfFromItem(net.sf.jsqlparser.statement.select.FromItem item, int currentDepth) {
        if (item == null) return currentDepth;
        if (item instanceof net.sf.jsqlparser.statement.select.SubSelect) {
            net.sf.jsqlparser.statement.select.SubSelect sub =
                (net.sf.jsqlparser.statement.select.SubSelect) item;
            return computeDepth(sub.getSelectBody(), currentDepth + 1);
        }
        if (item instanceof net.sf.jsqlparser.statement.select.SubJoin) {
            net.sf.jsqlparser.statement.select.SubJoin sj =
                (net.sf.jsqlparser.statement.select.SubJoin) item;
            int max = depthOfFromItem(sj.getLeft(), currentDepth);
            if (sj.getJoinList() != null) {
                for (net.sf.jsqlparser.statement.select.Join j : sj.getJoinList()) {
                    max = Math.max(max, depthOfFromItem(j.getRightItem(), currentDepth));
                }
            }
            return max;
        }
        // Table 等其他类型无子查询
        return currentDepth;
    }

    /** 递归扫描表达式中的 SubSelect（IN / EXISTS / NOT / AND / OR / 比较运算等都可能藏 SubSelect）. */
    private int depthOfExpression(net.sf.jsqlparser.expression.Expression expr, int currentDepth) {
        if (expr == null) return currentDepth;
        // SubSelect 直接出现（如 col = (SELECT ...)）
        if (expr instanceof SubSelect) {
            return computeDepth(((SubSelect) expr).getSelectBody(), currentDepth + 1);
        }
        // IN (SELECT ...)
        if (expr instanceof net.sf.jsqlparser.expression.operators.relational.InExpression) {
            net.sf.jsqlparser.expression.operators.relational.InExpression in =
                (net.sf.jsqlparser.expression.operators.relational.InExpression) expr;
            int max = currentDepth;
            if (in.getLeftExpression() != null) {
                max = Math.max(max, depthOfExpression(in.getLeftExpression(), currentDepth));
            }
            // rightItemsList 在 4.9 是 ItemsList，可能是 SubSelect / ExpressionList
            net.sf.jsqlparser.expression.operators.relational.ItemsList items = in.getRightItemsList();
            if (items instanceof SubSelect) {
                max = Math.max(max, computeDepth(((SubSelect) items).getSelectBody(), currentDepth + 1));
            }
            return max;
        }
        // EXISTS (SELECT ...)
        if (expr instanceof net.sf.jsqlparser.expression.operators.relational.ExistsExpression) {
            net.sf.jsqlparser.expression.operators.relational.ExistsExpression ex =
                (net.sf.jsqlparser.expression.operators.relational.ExistsExpression) expr;
            return depthOfExpression(ex.getRightExpression(), currentDepth);
        }
        // NOT
        if (expr instanceof net.sf.jsqlparser.expression.NotExpression) {
            return depthOfExpression(
                ((net.sf.jsqlparser.expression.NotExpression) expr).getExpression(), currentDepth);
        }
        // AND / OR 二元
        if (expr instanceof net.sf.jsqlparser.expression.operators.conditional.AndExpression) {
            net.sf.jsqlparser.expression.operators.conditional.AndExpression and =
                (net.sf.jsqlparser.expression.operators.conditional.AndExpression) expr;
            return Math.max(
                depthOfExpression(and.getLeftExpression(), currentDepth),
                depthOfExpression(and.getRightExpression(), currentDepth));
        }
        if (expr instanceof net.sf.jsqlparser.expression.operators.conditional.OrExpression) {
            net.sf.jsqlparser.expression.operators.conditional.OrExpression or =
                (net.sf.jsqlparser.expression.operators.conditional.OrExpression) expr;
            return Math.max(
                depthOfExpression(or.getLeftExpression(), currentDepth),
                depthOfExpression(or.getRightExpression(), currentDepth));
        }
        // 比较运算（=, >, <, !=, LIKE 等都继承自 BinaryExpression）
        if (expr instanceof net.sf.jsqlparser.expression.BinaryExpression) {
            net.sf.jsqlparser.expression.BinaryExpression bin =
                (net.sf.jsqlparser.expression.BinaryExpression) expr;
            return Math.max(
                depthOfExpression(bin.getLeftExpression(), currentDepth),
                depthOfExpression(bin.getRightExpression(), currentDepth));
        }
        // 其他叶子表达式（Column / LongValue / StringValue 等）无子查询
        return currentDepth;
    }

    private String normalizeLimit(String sql) {
        java.util.regex.Matcher m = limitPattern.matcher(sql.trim());
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > maxRows) {
                return limitPattern.matcher(sql.trim()).replaceFirst("LIMIT " + maxRows);
            }
            return sql;
        }
        // 无 LIMIT 自动追加
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed + " LIMIT " + maxRows;
    }
}

// support/SqlSafeResult.java
@Data
@AllArgsConstructor
public class SqlSafeResult {
    private boolean allowed;
    private String normalizedSql;
    private List<String> referencedTables;

    public static SqlSafeResult allowed(String sql, List<String> tables) {
        return new SqlSafeResult(true, sql, tables);
    }
}
```

- [ ] **Step 4：Run & 确认 ≥15 case 全绿**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/support/SqlSafeValidator.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/support/SqlSafeResult.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/support/SqlSafeValidatorTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): SqlSafeValidator JSqlParser 4.9 AST 校验（Green，Task M4.1.1）

- 校验链 8 步：长度/关键字/解析/SELECT/SET 操作/子查询深度/白名单/LIMIT
- SqlSafeValidatorTest 覆盖 ≥15 边界用例（通过+拒绝+LIMIT 三类）
- 错误码：42001 / 42002 / 42003 / 42007 / 42008

EOF
)"
```

---

### Phase M4.2：SQL 探查执行（D.1 POST /sql-probe/execute）

**业务规则（03 §D.1 + 07 §1 + BR-1）:**
- 权限：BizAuth(SYS_CONFIG, EXECUTE_SQL) + 角色白名单 R_BACK_TECH
- 双写审计：sql_probe_history INSERT（status=RUNNING → SUCCESS/FAILED）+ AuditApi.log（同步，必填 reason）
- 独立 readOnlyDataSource 数据源（M4.2.2 单独 commit 落地）
- 并发上限：Semaphore(10)
- 超时：30 秒（statement.setQueryTimeout）
- 行数上限：1000（statement.setMaxRows）

#### Task M4.2.1：SqlProbeService + SqlProbeController（核心执行链）

- [ ] **Step 1：Red — SqlProbeServiceTest**

完整测试覆盖：(1) 角色 R_BACK_TECH 校验 (2) reason 必填 (3) 校验通过执行成功 (4) 校验失败拒绝 (5) 超时 (6) 并发超限 (7) 双写审计验证。

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — 实现 SqlProbeService**

```java
// service/SqlProbeService.java
public interface SqlProbeService {
    SqlProbeExecuteRespDTO execute(SqlProbeExecuteReqDTO req);
    PageResult<SqlProbeHistoryRespDTO> queryHistory(SqlProbeHistoryQueryReqDTO query, PageRequest page);
    SqlProbeHistoryRespDTO getHistoryDetail(String id);
    SchemaWhitelistRespDTO getSchemaWhitelist();
}

// service/impl/SqlProbeServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class SqlProbeServiceImpl implements SqlProbeService {

    private static final String ROLE_BACK_TECH = "R_BACK_TECH";

    private final SqlSafeValidator validator;
    private final SqlProbeHistoryMapper historyMapper;
    private final CurrentUserApi currentUserApi;
    private final AuditApi auditApi;
    private final ConfigApi configApi;
    @Qualifier("rptReadOnlyDataSource")
    private final DataSource readOnlyDataSource;

    private final Semaphore semaphore = new Semaphore(10);

    @Override
    public SqlProbeExecuteRespDTO execute(SqlProbeExecuteReqDTO req) {
        // 1) 角色校验
        if (!currentUserApi.getCurrentRoleCodes().contains(ROLE_BACK_TECH)) {
            throw new BizException(RptErrorCode.SQL_PROBE_NO_ACCESS);
        }
        String empId = currentUserApi.getCurrentEmpId();

        // 2) 校验 + 标准化 SQL
        SqlSafeResult safe = validator.validateAndNormalize(req.getSql());

        // 3) 占位 RUNNING 历史（先 INSERT，便于失联场景能查）
        String historyId = UUID.randomUUID().toString().replace("-", "");
        SqlProbeHistory history = new SqlProbeHistory();
        history.setId(historyId);
        history.setEmpId(empId);
        history.setSqlText(safe.getNormalizedSql());
        history.setRemark(req.getRemark());
        history.setStatus("RUNNING");
        historyMapper.insert(history);

        // 4) 并发控制 + 执行
        if (!semaphore.tryAcquire()) {
            historyMapper.updateStatus(historyId, "FAILED", "并发超限");
            auditApi.log(buildAuditCmd(empId, historyId, "FAILED", req.getRemark()));
            throw new BizException(RptErrorCode.SQL_EXECUTION_TIMEOUT, "并发数超限");
        }
        long startMs = System.currentTimeMillis();
        try (Connection conn = readOnlyDataSource.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(30);
                stmt.setMaxRows(1000);
                ResultSet rs = stmt.executeQuery(safe.getNormalizedSql());
                List<String> columns = readColumns(rs);
                List<Map<String, Object>> rows = readRows(rs, columns);
                int elapsed = (int) (System.currentTimeMillis() - startMs);

                historyMapper.updateSuccess(historyId, rows.size(), elapsed);
                auditApi.log(buildAuditCmd(empId, historyId, "SUCCESS", req.getRemark()));

                return SqlProbeExecuteRespDTO.builder()
                    .historyId(historyId)
                    .columns(columns)
                    .rows(rows)
                    .rowCount(rows.size())
                    .executionTimeMs(elapsed)
                    .build();
            }
        } catch (SQLTimeoutException e) {
            historyMapper.updateStatus(historyId, "TIMEOUT", e.getMessage());
            auditApi.log(buildAuditCmd(empId, historyId, "FAILED", req.getRemark()));
            throw new BizException(RptErrorCode.SQL_EXECUTION_TIMEOUT, e.getMessage());
        } catch (SQLException e) {
            historyMapper.updateStatus(historyId, "FAILED", e.getMessage());
            auditApi.log(buildAuditCmd(empId, historyId, "FAILED", req.getRemark()));
            throw new BizException(RptErrorCode.SQL_PARSE_FAILED, e.getMessage());
        } finally {
            semaphore.release();
        }
    }
    // 辅助方法略
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/SqlProbeExecuteReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/SqlProbeExecuteRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/SqlProbeService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/impl/SqlProbeServiceImpl.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/SqlProbeController.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/SqlProbeServiceTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): D.1 POST /sql-probe/execute SQL 探查执行（Green，Task M4.2.1）

- 角色 R_BACK_TECH 校验 + reason 必填
- SqlSafeValidator + 双写审计（sql_probe_history INSERT(RUNNING) → UPDATE(SUCCESS/FAILED) + AuditApi.log）
- 并发 Semaphore(10) + 超时 30s + maxRows 1000
- 错误码：40302/42001/42002/42003/42005/42007/42008

EOF
)"
```

#### Task M4.2.2：rptReadOnlyDataSource 配置 Bean

- [ ] **Step 1：Red — RptReadOnlyDataSourceConfigTest**

```java
@SpringBootTest(classes = ReportTestApplication.class)
class RptReadOnlyDataSourceConfigTest {

    @Autowired
    @Qualifier("rptReadOnlyDataSource")
    private DataSource dataSource;

    @Test
    void rptReadOnlyDataSourceShouldExist() {
        assertThat(dataSource).isNotNull();
        assertThat(dataSource).isInstanceOf(DruidDataSource.class);
    }

    @Test
    void connectionShouldBeReadOnly() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isReadOnly()).isTrue();
        }
    }

    @Test
    void shouldRejectWriteSql() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() ->
                stmt.executeUpdate("UPDATE rpt_saved_query SET name='x' WHERE id='nonexistent'"))
                .isInstanceOf(SQLException.class);  // read-only 拒绝写
        }
    }
}
```

- [ ] **Step 2：Run & 确认失败**（bean 不存在）

- [ ] **Step 3：Green — RptReadOnlyDataSourceConfig**

```java
// config/RptReadOnlyDataSourceConfig.java
package com.bank.branch.platform.report.config;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQL 探查独立只读数据源（02 §5 + BR-1）.
 *
 * <p>用途：与主 DataSource 物理隔离，使用 sql_probe_readonly 数据库账号
 * （仅授予 SELECT 权限 + 仅白名单表）。配置项：rpt.datasource.read-only.*
 *
 * <p>所有从此 DataSource 取的连接强制 setReadOnly(true)，
 * MySQL 端会拒绝写 SQL（即使数据库账号意外有写权限也防御性兜底）。
 */
@Configuration
@Slf4j
public class RptReadOnlyDataSourceConfig {

    @Bean(name = "rptReadOnlyDataSource", destroyMethod = "close")
    @Qualifier("rptReadOnlyDataSource")
    @ConfigurationProperties(prefix = "rpt.datasource.read-only")
    public DataSource rptReadOnlyDataSource() {
        DruidDataSource ds = new DruidDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection conn = super.getConnection();
                conn.setReadOnly(true);
                return conn;
            }
        };
        ds.setDefaultReadOnly(true);
        ds.setMaxActive(10);
        ds.setMinIdle(2);
        ds.setQueryTimeout(30);
        log.info("[RptReadOnlyDataSource] initialized with readOnly=true, maxActive=10");
        return ds;
    }
}
```

```yaml
# application.yml 追加
rpt:
  datasource:
    read-only:
      url: jdbc:mysql://localhost:3306/onepl?useSSL=false&serverTimezone=Asia/Shanghai
      username: sql_probe_readonly  # 数据库管理员手工创建，仅 SELECT
      password: "${RPT_SQL_PROBE_PASSWORD:probe_pwd}"
      driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(report-v1): rptReadOnlyDataSource 独立只读数据源（Green，Task M4.2.2）

- 独立 DruidDataSource bean，物理账号 sql_probe_readonly（仅 SELECT 权限）
- defaultReadOnly=true + getConnection() 强制 setReadOnly(true) 双层防御
- maxActive=10 + queryTimeout=30s 与 SqlProbeService Semaphore 对齐
- 配置 rpt.datasource.read-only.*（密码走环境变量 RPT_SQL_PROBE_PASSWORD）

EOF
)"
```

---

### Phase M4.3：SQL 探查历史（D.2 + D.3 + D.4）

#### Task M4.3.1：D.2 GET /sql-probe/history（分页查本人）

- [ ] Step 1-5：略。

#### Task M4.3.2：D.3 GET /sql-probe/history/{id}（详情）

- [ ] Step 1-5：略。

#### Task M4.3.3：D.4 GET /sql-probe/schema-whitelist（白名单展示）

- [ ] Step 1-5：略。

---

### Phase M4.4：M4 阶段全量回归 + 5 接口 PT_RESOURCE 注册

#### Task M4.4.1：V1_0_4__rpt_sql_probe_pt_resources.sql + 全量回归

- [ ] Step 1-5：略，单 commit。

---

## Milestone M5：异步导出（rpt_export_task + 3 接口）

### Phase M5.1：rpt_export_task DDL Flyway

**Files:**
- Create: `report-analytics-center/src/main/resources/sql/V1_0_5__rpt_export_task.sql`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/sql/V1_0_5FlywayIT.java`

#### Task M5.1.1：rpt_export_task 表 DDL

> **来源：** 复刻 `performance-engine-center` 的 `V1_2_1__perf_export_task.sql`，表名 perf_export_task → rpt_export_task；字段集完全一致。

- [ ] **Step 1：Red — V1_0_5FlywayIT**

```java
@SpringBootTest(classes = ReportTestApplication.class)
class V1_0_5FlywayIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void rptExportTaskShouldExist() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name='rpt_export_task' " +
            "AND column_name IN ('id','export_type','params_json','status','file_key','file_size','row_count','expire_at','operator_id','error_msg','created_time','updated_time')",
            Integer.class);
        assertThat(cnt).isEqualTo(12);
    }
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — V1_0_5__rpt_export_task.sql**

```sql
-- =====================================================================
-- report-analytics-center V1.0 异步导出任务表
-- Version: V1_0_5
-- Date: 2026-04-25
-- Task: M5.1.1
--
-- 来源：复刻 performance-engine-center V1_2_1__perf_export_task.sql
-- 路径：仅本模块独占，不复用 perf_export_task（避免跨模块表共享）
-- =====================================================================

CREATE TABLE IF NOT EXISTS `rpt_export_task` (
  `id`           varchar(32) NOT NULL COMMENT '导出任务ID',
  `export_type`  varchar(32) NOT NULL COMMENT '类型：DYNAMIC_QUERY/TOUCH_SUMMARY/PERF_SUMMARY/CUSTPOOL_SUMMARY',
  `params_json`  text DEFAULT NULL COMMENT '导出参数 JSON',
  `status`       varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  `file_key`     varchar(200) DEFAULT NULL COMMENT 'MinIO object key',
  `file_size`    bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `row_count`    int DEFAULT NULL COMMENT '导出行数',
  `expire_at`    datetime DEFAULT NULL COMMENT '文件过期时间',
  `operator_id`  varchar(32) NOT NULL COMMENT '操作人员工号',
  `error_msg`    text DEFAULT NULL COMMENT '失败原因',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_export_type` (`export_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表异步导出任务';
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

---

### Phase M5.2：RptExportService + 4 个 ExportStrategy

**业务规则（02 §9 + BR-2 决策点）:**
- 接口骨架对齐 `PerfExportService`：createTask / getTask / getTaskForOwner / createTaskDto / getTaskDto / cancelTask
- 状态机：PENDING → RUNNING → SUCCESS/FAILED/CANCELLED
- V1 同步执行（在 createTask 内串行调 strategy.execute），M6 文档同步说明 V1.1+ 计划切异步
- 4 个策略：DYNAMIC_QUERY / TOUCH_SUMMARY / PERF_SUMMARY / CUSTPOOL_SUMMARY
- 文件上传走 governance.FileApi.upload（V1 用同步上传，无独立 MinIO 客户端）

**Files:**
- Create: `entity/RptExportTask.java` + `mapper/RptExportTaskMapper.java` + XML
- Create: `service/export/RptExportService.java` + `impl/RptExportServiceImpl.java`
- Create: `service/export/ExportStrategy.java` + 4 个 strategy 实现
- Create: `service/export/model/{DynamicQueryExportRow,TouchSummaryExportRow,PerfSummaryExportRow,CustPoolExportRow}.java`

#### Task M5.2.1：RptExportTask Entity + Mapper

- [ ] **Step 1：Red — RptExportTaskMapperIT**

```java
@SpringBootTest(classes = ReportTestApplication.class)
class RptExportTaskMapperIT {

    @Autowired private RptExportTaskMapper mapper;

    @Test
    void insertAndQuery_shouldRoundtrip() {
        RptExportTask task = new RptExportTask();
        task.setId("TEST_RPT_EXP_001");
        task.setExportType("DYNAMIC_QUERY");
        task.setStatus("PENDING");
        task.setOperatorId("E001");
        task.setParamsJson("{}");
        mapper.insert(task);

        RptExportTask got = mapper.selectById("TEST_RPT_EXP_001");
        assertThat(got).isNotNull();
        assertThat(got.getStatus()).isEqualTo("PENDING");
        assertThat(got.getOperatorId()).isEqualTo("E001");
    }

    @Test
    void updateStatus_RUNNING_shouldPersist() {
        // 假定 setUp 已 INSERT
        mapper.updateStatus("TEST_RPT_EXP_001", "RUNNING");
        assertThat(mapper.selectById("TEST_RPT_EXP_001").getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void updateSuccess_shouldSetFileKeyAndExpireAt() {
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);
        mapper.updateSuccess("TEST_RPT_EXP_001", "minio/key.xlsx", 100, 102400L, expireAt);
        RptExportTask got = mapper.selectById("TEST_RPT_EXP_001");
        assertThat(got.getStatus()).isEqualTo("SUCCESS");
        assertThat(got.getFileKey()).isEqualTo("minio/key.xlsx");
        assertThat(got.getRowCount()).isEqualTo(100);
    }
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — Entity + Mapper + XML**

```java
// entity/RptExportTask.java
@Data
public class RptExportTask {
    private String id;
    private String exportType;
    private String paramsJson;
    private String status;
    private String fileKey;
    private Long fileSize;
    private Integer rowCount;
    private LocalDateTime expireAt;
    private String operatorId;
    private String errorMsg;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

// mapper/RptExportTaskMapper.java
@Mapper
public interface RptExportTaskMapper {
    int insert(RptExportTask task);
    RptExportTask selectById(@Param("id") String id);
    int updateStatus(@Param("id") String id, @Param("status") String status);
    int updateSuccess(@Param("id") String id,
                      @Param("fileKey") String fileKey,
                      @Param("rowCount") Integer rowCount,
                      @Param("fileSize") Long fileSize,
                      @Param("expireAt") LocalDateTime expireAt);
    int updateFailed(@Param("id") String id, @Param("errorMsg") String errorMsg);
    List<RptExportTask> selectByOperator(@Param("operatorId") String operatorId,
                                         @Param("status") String status);
}
```

```xml
<!-- mapper/RptExportTaskMapper.xml -->
<insert id="insert" parameterType="RptExportTask">
  INSERT INTO rpt_export_task (id, export_type, params_json, status,
    operator_id, created_time, updated_time)
  VALUES (#{id}, #{exportType}, #{paramsJson}, #{status},
    #{operatorId}, NOW(), NOW())
</insert>
<select id="selectById" resultType="RptExportTask">
  SELECT * FROM rpt_export_task WHERE id = #{id}
</select>
<update id="updateStatus">
  UPDATE rpt_export_task SET status = #{status}, updated_time = NOW()
  WHERE id = #{id}
</update>
<update id="updateSuccess">
  UPDATE rpt_export_task
  SET status='SUCCESS', file_key=#{fileKey}, row_count=#{rowCount},
      file_size=#{fileSize}, expire_at=#{expireAt}, updated_time=NOW()
  WHERE id = #{id}
</update>
<update id="updateFailed">
  UPDATE rpt_export_task
  SET status='FAILED', error_msg=#{errorMsg}, updated_time=NOW()
  WHERE id = #{id}
</update>
```

- [ ] **Step 4：Run & 确认 3 个 IT 全绿**

- [ ] **Step 5：Commit**

#### Task M5.2.2：ExportStrategy 接口 + RptExportServiceImpl 主入口

- [ ] **Step 1：Red — RptExportServiceTest**

完整测试覆盖：(1) createTask 成功落 PENDING (2) 未知 exportType 抛 BIZ_KIND_INVALID (3) operatorId 必填 (4) getTaskForOwner 校验归属。

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — 实现 RptExportServiceImpl（参考 PerfExportServiceImpl 119 行骨架）**

```java
// service/export/RptExportService.java
public interface RptExportService {
    String createTask(String exportType, Map<String, Object> params, String operatorId);
    RptExportTask getTask(String taskId);
    RptExportTask getTaskForOwner(String taskId, String operatorId);
    void cancelTask(String taskId, String operatorId);
}

// service/export/ExportStrategy.java
public interface ExportStrategy {
    String exportType();
    int execute(RptExportTask task);
}

// service/export/impl/RptExportServiceImpl.java（核心，骨架对齐 PerfExportServiceImpl）
@Service
@Slf4j
public class RptExportServiceImpl implements RptExportService {

    private static final int FILE_EXPIRE_DAYS = 7;

    private final RptExportTaskMapper taskMapper;
    private final Map<String, ExportStrategy> strategyMap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RptExportServiceImpl(RptExportTaskMapper taskMapper, List<ExportStrategy> strategies) {
        this.taskMapper = taskMapper;
        this.strategyMap = new HashMap<>();
        for (ExportStrategy s : strategies) {
            String type = s.exportType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("ExportStrategy " + s.getClass().getName() + " 返回空 exportType");
            }
            if (this.strategyMap.putIfAbsent(type, s) != null) {
                throw new IllegalStateException("ExportStrategy exportType 冲突: " + type);
            }
        }
        log.info("[RptExportService] 已装配 {} 个导出策略: {}", strategyMap.size(), strategyMap.keySet());
    }

    @Override
    public String createTask(String exportType, Map<String, Object> params, String operatorId) {
        if (exportType == null || exportType.isBlank()) {
            throw new BizException(RptErrorCode.EXPORT_START_FAILED, "exportType 必填");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new BizException(RptErrorCode.EXPORT_START_FAILED, "operatorId 必填");
        }
        ExportStrategy strategy = strategyMap.get(exportType);
        if (strategy == null) {
            throw new BizException(RptErrorCode.EXPORT_START_FAILED, "未知 exportType: " + exportType);
        }
        RptExportTask task = new RptExportTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setExportType(exportType);
        task.setParamsJson(serializeParams(params));
        task.setStatus("PENDING");
        task.setOperatorId(operatorId);
        taskMapper.insert(task);

        taskMapper.updateStatus(task.getId(), "RUNNING");
        try {
            int rowCount = strategy.execute(task);
            LocalDateTime expireAt = LocalDateTime.now().plusDays(FILE_EXPIRE_DAYS);
            taskMapper.updateSuccess(task.getId(), task.getFileKey(), rowCount,
                task.getFileSize(), expireAt);
            return task.getId();
        } catch (RuntimeException ex) {
            taskMapper.updateFailed(task.getId(), truncate(ex.getMessage()));
            throw ex;
        }
    }
    // 其他方法略
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/service/export/ \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptExportTask.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptExportTaskMapper.java \
        report-analytics-center/src/main/resources/mapper/RptExportTaskMapper.xml \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/export/RptExportServiceTest.java
git commit -m "$(cat <<'EOF'
feat(report-v1): RptExportService 骨架（Green，Task M5.2.2）

- 接口对齐 PerfExportService（createTask/getTask/getTaskForOwner/cancelTask）
- 状态机：PENDING → RUNNING → SUCCESS/FAILED/CANCELLED
- 4 个 ExportStrategy（DYNAMIC_QUERY/TOUCH_SUMMARY/PERF_SUMMARY/CUSTPOOL_SUMMARY）依赖装配
- V1.0 同步执行（与 PerfExport V1.2 同模型，V1.1+ 计划切异步）

EOF
)"
```

#### Task M5.2.3：4 个 ExportStrategy 实现（DYNAMIC_QUERY 优先）

- [ ] Step 1-5：略，每个 strategy 一对 commit（共 4 commit），按 03 §I.4 列定义 + EasyExcel 落地。

---

### Phase M5.3：导出任务 3 接口（E.1 status / E.2 cancel / E.3 download）

#### Task M5.3.1：E.1 GET /export-tasks/{taskId}（状态查询）

- [ ] **Step 1：Red — RptExportControllerStatusIT**

```java
@SpringBootTest(classes = ReportTestApplication.class, webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
class RptExportControllerStatusIT {

    @Autowired private MockMvc mvc;
    @MockBean private RptExportService exportService;
    @MockBean private CurrentUserApi currentUserApi;
    @MockBean private BizAuthInterceptor bizAuth;  // F11 修补：mock 拦截器

    @Test
    void getStatus_existingTask_shouldReturn200() throws Exception {
        RptExportTask task = new RptExportTask();
        task.setId("EXP001");
        task.setStatus("RUNNING");
        task.setOperatorId("E001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(exportService.getTaskForOwner("EXP001", "E001")).thenReturn(task);

        mvc.perform(get("/api/reports/export-tasks/EXP001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void getStatus_nonOwner_shouldReturn403() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E002");
        when(exportService.getTaskForOwner("EXP001", "E002"))
            .thenThrow(new BizException(RptErrorCode.EXPORT_DOWNLOAD_FORBIDDEN));

        mvc.perform(get("/api/reports/export-tasks/EXP001"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("RPT-42209"));
    }

    @Test
    void getStatus_notFound_shouldReturn404() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(exportService.getTaskForOwner("NOEXIST", "E001"))
            .thenThrow(new BizException(RptErrorCode.EXPORT_TASK_NOT_FOUND));

        mvc.perform(get("/api/reports/export-tasks/NOEXIST"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RPT-40009"));
    }
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — Controller + Facade + Service**

```java
// controller/RptExportController.java
@RestController
@RequestMapping("/api/reports/export-tasks")
@RequiredArgsConstructor
@Slf4j
public class RptExportController {

    private final RptExportFacade facade;

    @GetMapping("/{taskId}")
    @BizAuth(bizType = BizType.REPORT, bizAction = BizAction.READ, resourceId = "RPT_EXPORT_STATUS")
    public ResponseWrapper<RptExportTaskRespDTO> getStatus(@PathVariable String taskId) {
        return ResponseWrapper.ok(facade.getStatus(taskId));
    }
    // E.2/E.3 见后续 Task
}

// facade/RptExportFacade.java
@Component
@RequiredArgsConstructor
public class RptExportFacade {
    private final RptExportService exportService;
    private final CurrentUserApi currentUserApi;

    public RptExportTaskRespDTO getStatus(String taskId) {
        String empId = currentUserApi.getCurrentEmpId();
        RptExportTask task = exportService.getTaskForOwner(taskId, empId);
        return toDto(task);
    }

    private RptExportTaskRespDTO toDto(RptExportTask t) {
        return RptExportTaskRespDTO.builder()
            .taskId(t.getId())
            .status(t.getStatus())
            .exportType(t.getExportType())
            .rowCount(t.getRowCount())
            .errorMsg(t.getErrorMsg())
            .createdTime(t.getCreatedTime())
            .build();
    }
}

// service/export/impl/RptExportServiceImpl.java 补 getTaskForOwner
@Override
public RptExportTask getTaskForOwner(String taskId, String operatorId) {
    RptExportTask t = taskMapper.selectById(taskId);
    if (t == null) {
        throw new BizException(RptErrorCode.EXPORT_TASK_NOT_FOUND);
    }
    if (!Objects.equals(t.getOperatorId(), operatorId)) {
        throw new BizException(RptErrorCode.EXPORT_DOWNLOAD_FORBIDDEN);
    }
    return t;
}
```

- [ ] **Step 4：Run & 确认 3 IT 全绿**

- [ ] **Step 5：Commit**

#### Task M5.3.2：E.2 DELETE /export-tasks/{taskId}（取消）

- [ ] **Step 1：Red — RptExportControllerCancelIT**

```java
@Test
void cancel_pending_shouldReturn200AndUpdateStatus() throws Exception {
    when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    doNothing().when(exportService).cancelTask("EXP001", "E001");
    mvc.perform(delete("/api/reports/export-tasks/EXP001"))
        .andExpect(status().isOk());
    verify(exportService).cancelTask("EXP001", "E001");
}

@Test
void cancel_alreadySuccess_shouldReturn400() throws Exception {
    when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    doThrow(new BizException(RptErrorCode.EXPORT_TASK_NOT_READY))
        .when(exportService).cancelTask("EXP001", "E001");
    mvc.perform(delete("/api/reports/export-tasks/EXP001"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RPT-40010"));
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — cancelTask 实现**

```java
// RptExportController
@DeleteMapping("/{taskId}")
@BizAuth(bizType = BizType.REPORT, bizAction = BizAction.UPDATE, resourceId = "RPT_EXPORT_CANCEL")
public ResponseWrapper<Void> cancel(@PathVariable String taskId) {
    facade.cancel(taskId);
    return ResponseWrapper.ok();
}

// RptExportFacade
public void cancel(String taskId) {
    String empId = currentUserApi.getCurrentEmpId();
    exportService.cancelTask(taskId, empId);
}

// RptExportServiceImpl
@Override
public void cancelTask(String taskId, String operatorId) {
    RptExportTask t = getTaskForOwner(taskId, operatorId);
    if ("SUCCESS".equals(t.getStatus()) || "FAILED".equals(t.getStatus())) {
        // 状态机：仅 PENDING/RUNNING 可取消
        throw new BizException(RptErrorCode.EXPORT_TASK_NOT_READY,
            "状态 " + t.getStatus() + " 不可取消");
    }
    taskMapper.updateStatus(taskId, "CANCELLED");
    log.info("[RptExport] task {} cancelled by {}", taskId, operatorId);
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

#### Task M5.3.3：E.3 GET /export-tasks/{taskId}/download

- [ ] **Step 1：Red — RptExportControllerDownloadIT**

```java
@MockBean private FileApi fileApi;

@Test
void download_successTask_shouldRedirectToPresignedUrl() throws Exception {
    RptExportTask t = new RptExportTask();
    t.setId("EXP001"); t.setStatus("SUCCESS"); t.setFileKey("minio/key.xlsx");
    t.setOperatorId("E001");
    when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    when(exportService.getTaskForOwner("EXP001", "E001")).thenReturn(t);
    when(fileApi.getDownloadUrl("minio/key.xlsx"))
        .thenReturn("https://minio.example/sig?token=xxx");

    mvc.perform(get("/api/reports/export-tasks/EXP001/download"))
        .andExpect(status().isFound())  // 302
        .andExpect(header().string("Location", "https://minio.example/sig?token=xxx"));
}

@Test
void download_pendingTask_shouldReturn400() throws Exception {
    RptExportTask t = new RptExportTask();
    t.setId("EXP001"); t.setStatus("RUNNING"); t.setOperatorId("E001");
    when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    when(exportService.getTaskForOwner("EXP001", "E001")).thenReturn(t);

    mvc.perform(get("/api/reports/export-tasks/EXP001/download"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RPT-40010"));
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — download 实现**

```java
// RptExportController
@GetMapping("/{taskId}/download")
@BizAuth(bizType = BizType.REPORT, bizAction = BizAction.EXPORT, resourceId = "RPT_EXPORT_DOWNLOAD")
public ResponseEntity<Void> download(@PathVariable String taskId) {
    String url = facade.getDownloadUrl(taskId);
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, url).build();
}

// RptExportFacade
public String getDownloadUrl(String taskId) {
    String empId = currentUserApi.getCurrentEmpId();
    RptExportTask t = exportService.getTaskForOwner(taskId, empId);
    if (!"SUCCESS".equals(t.getStatus())) {
        throw new BizException(RptErrorCode.EXPORT_TASK_NOT_READY,
            "状态 " + t.getStatus() + " 文件未生成");
    }
    return fileApi.getDownloadUrl(t.getFileKey());
}
```

- [ ] **Step 4：Run & 确认通过**

- [ ] **Step 5：Commit**

---

### Phase M5.4：M5 阶段补充错误码 + 全量回归

**Files:**
- Modify: `enums/RptErrorCode.java`（新增 5 条 J 章扩展错误码：RPT-42207 / RPT-42208 / RPT-42209 / RPT-42210 / RPT-42211；J.4 中的 RPT-50002 与基线 §6.4 同码合并，不再重复）
- Modify: `RptErrorCodeTest.java`（断言数量从 25 → 30，并补 5 条扩展抽样）

> **F1 修复说明：** 基线 25 条已含 RPT-50003 EXPORT_START_FAILED（M0.3 引入）+ RPT-50002 CACHE_READ_FAILED（M0.3 引入）。M5.4 仅扩展 J 章导出业务限制 5 条（42207~42211）。最终 25 + 5 = 30 条。

#### Task M5.4.1：扩展 5 条 J 章导出错误码 + RptErrorCodeTest 25→30

- [ ] **Step 1：Red — 修改 RptErrorCodeTest**

```java
// 修改 shouldHaveExactly25ErrorCodes_BaseSet 为 30
@Test
void shouldHaveExactly30ErrorCodes_AfterM5Extension() {
    assertThat(RptErrorCode.values()).hasSize(30);
}

// 新增 J 章 5 条扩展抽样
@Test
void shouldContainJChapterExportCodes() {
    assertThat(RptErrorCode.EXPORT_ROW_LIMIT_EXCEEDED.getCode()).isEqualTo("RPT-42207");
    assertThat(RptErrorCode.EXPORT_TASK_NOT_FOUND_OR_EXPIRED.getCode()).isEqualTo("RPT-42208");
    assertThat(RptErrorCode.EXPORT_DOWNLOAD_FORBIDDEN.getCode()).isEqualTo("RPT-42209");
    assertThat(RptErrorCode.EXPORT_FILTER_DATA_SCOPE_VIOLATION.getCode()).isEqualTo("RPT-42210");
    assertThat(RptErrorCode.EXPORT_METRIC_CODES_INVALID.getCode()).isEqualTo("RPT-42211");
}
```

- [ ] **Step 2：Run & 确认失败**（编译失败：5 个枚举常量不存在）

- [ ] **Step 3：Green — RptErrorCode 追加 5 条枚举**

```java
// enums/RptErrorCode.java 在 EXPORT_START_FAILED 之前追加：
// 422xx J 章导出业务限制（5 条，M5.4 扩展）
EXPORT_ROW_LIMIT_EXCEEDED("RPT-42207", "导出行数超过上限（500000）"),
EXPORT_TASK_NOT_FOUND_OR_EXPIRED("RPT-42208", "异步导出任务不存在或已过期"),
EXPORT_DOWNLOAD_FORBIDDEN("RPT-42209", "不能下载他人创建的导出任务"),
EXPORT_FILTER_DATA_SCOPE_VIOLATION("RPT-42210", "导出过滤条件未通过 DATA_SCOPE 校验"),
EXPORT_METRIC_CODES_INVALID("RPT-42211", "metricCodes 为空或包含未授权指标"),
```

- [ ] **Step 4：Run & 确认全绿**

- [ ] **Step 5：Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(report-v1): RptErrorCode J 章扩展 5 条（Green，Task M5.4.1，修复 F1）

- 扩展 RPT-42207~42211（导出业务限制）
- 守护数量 25 → 30
- 02 §6.4 RPT-50002 与 03 §J.4 RPT-50002 同码合并（已在基线，不重复扩展）

EOF
)"
```

#### Task M5.4.2：V1_0_6__rpt_export_pt_resources.sql + 全量回归

- [ ] **Step 1：Red — RptResourcesIT 在 M5 后增加 3 行 RPT_EXPORT_* 资源断言**

```java
@Test
void rptExportTasks_3Resources_shouldExistInPtResource() {
    Integer cnt = jdbc.queryForObject(
        "SELECT COUNT(*) FROM PT_RESOURCE WHERE resource_id IN " +
        "('RPT_EXPORT_STATUS','RPT_EXPORT_CANCEL','RPT_EXPORT_DOWNLOAD') " +
        "AND status='ACTIVE'", Integer.class);
    assertThat(cnt).isEqualTo(3);
}
```

- [ ] **Step 2：Run & 确认失败**

- [ ] **Step 3：Green — V1_0_6__rpt_export_pt_resources.sql**

```sql
-- 3 个 E 章导出任务接口资源注册（08 §5.2 表）
INSERT INTO PT_RESOURCE (resource_id, biz_type, biz_action, http_method, url_pattern, status, created_time)
VALUES
  ('RPT_EXPORT_STATUS','REPORT','READ','GET','/api/reports/export-tasks/*','ACTIVE',NOW()),
  ('RPT_EXPORT_CANCEL','REPORT','UPDATE','DELETE','/api/reports/export-tasks/*','ACTIVE',NOW()),
  ('RPT_EXPORT_DOWNLOAD','REPORT','EXPORT','GET','/api/reports/export-tasks/*/download','ACTIVE',NOW());

-- 角色绑定：R_PRESIDENT + R_ORG_HEAD + R_BACK_TECH + R_ADMIN
INSERT INTO PT_ROLE_RESOURCE (role_id, resource_id, granted_time)
SELECT r.role_id, p.resource_id, NOW()
FROM (SELECT role_id FROM PT_ROLE WHERE role_code IN ('R_PRESIDENT','R_ORG_HEAD','R_BACK_TECH','R_ADMIN')) r
CROSS JOIN (SELECT resource_id FROM PT_RESOURCE WHERE resource_id LIKE 'RPT_EXPORT_%') p;
```

- [ ] **Step 4：Run & 确认通过**（V1_0_6 Flyway 自动执行 + IT 绿）

- [ ] **Step 5：Commit + 全量 mvn clean verify**

---

## Milestone M6：收尾（全量回归 + 文档 + 技术债）

### Phase M6.1：全量回归 + 25 PT_RESOURCE 完整性校验

**Files:**
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/sql/RptResourcesIT.java`

#### Task M6.1.1：M0-M5 全量回归 + 25 PT_RESOURCE 守护

- [ ] **Step 1：Red — RptResourcesIT 守护 PT_RESOURCE 数量与覆盖**

```java
@SpringBootTest(classes = ReportTestApplication.class)
class RptResourcesIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    void rptResources_shouldHave25Records() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM PT_RESOURCE WHERE resource_id LIKE 'RPT_%' AND status='ACTIVE'",
            Integer.class);
        assertThat(cnt).isGreaterThanOrEqualTo(25);
    }

    @Test
    void rptSqlProbe_resources_belongToBackTechRoleOnly() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT role_id) FROM PT_ROLE_RESOURCE " +
            "WHERE resource_id IN ('RPT_SQL_EXEC','RPT_SQL_HISTORY')",
            Integer.class);
        assertThat(cnt).isLessThanOrEqualTo(2); // 仅 R_BACK_TECH + R_ADMIN
    }
}
```

- [ ] **Step 2：Run & 确认通过**（M1.6 / M2.4 / M3.4 / M4.4 / M5.4 已分批写入 PT_RESOURCE）

- [ ] **Step 3：Green — 如有遗漏补全 V1_0_7__rpt_resources_align.sql**

- [ ] **Step 4：完整 mvn clean verify**

```bash
cd D:/Project/oneplate
mvn -q -pl report-analytics-center clean verify
```

预期：surefire + failsafe 双绿，6 架构守护绿。

- [ ] **Step 5：Commit**

```bash
git add report-analytics-center/src/test/java/com/bank/branch/platform/report/sql/RptResourcesIT.java \
        report-analytics-center/src/main/resources/sql/V1_0_7__rpt_resources_align.sql
git commit -m "$(cat <<'EOF'
chore(report-v1): M6.1 全量回归 + 25 PT_RESOURCE 守护（Task M6.1.1）

- RptResourcesIT 守护：≥ 25 条 RPT_* + SQL 探查仅 R_BACK_TECH/R_ADMIN
- mvn clean verify 全绿（surefire + failsafe）
- 6 架构守护绿（BizAuth / NoEntityInController / NoMapperInController / PackageStructure / CrossModuleApiOnly / NoUoeInFacadeTests）

EOF
)"
```

---

### Phase M6.2：文档三份同步

**Files:**
- Modify: `D:\Project\oneplate\CLAUDE.md`（根项目 CLAUDE，模块状态：report-analytics-center 由"骨架"→"V1.0 已交付"）
- Create: `D:\Project\oneplate\report-analytics-center\CLAUDE.md`（新建模块级 CLAUDE）
- Modify: `docs\modules\report-analytics-center\04-对外API契约.md`（V1 维持"不暴露 Api"声明，但末尾追加"V1.0 交付状态：25 接口已落地（2026-04-25）"）

#### Task M6.2.1：根 CLAUDE.md 更新

- [ ] Step 1：修改根 CLAUDE.md 中模块状态表，将 `report-analytics-center` 由 "⏳ 骨架" 改为 "✅ V1.0 已交付（25 REST + 4 表 + 跨模块只读）"
- [ ] Step 2：模块依赖图：把 report-analytics-center 标为已实现节点
- [ ] Step 3：`已实现的模块` 表追加一行
- [ ] Step 4：Commit

```bash
git commit -m "docs(report-v1): 根 CLAUDE.md report-analytics-center 状态升级为 V1.0 已交付（Task M6.2.1）..."
```

#### Task M6.2.2：模块级 CLAUDE.md 新建

- [ ] 创建 `report-analytics-center/CLAUDE.md`，参考 `performance-engine-center/CLAUDE.md` 模板，包含：
  - 模块概述 + Maven 坐标 + 包结构
  - V1.0 交付内容：25 REST 接口清单 + 3 自有表 + 1 异步导出表
  - 跨模块依赖：4 个上游 Api（CurrentUserApi / BizScopeApi / OrgApi / DictApi / AuditApi / FileApi / MetricApi / KpiApi / CustomerQueryApi / TouchTaskQueryApi）
  - 不被任何模块依赖的红线
  - 错误码：基线 25 + 扩展 5 = 30 条
  - SQL 探查特殊性（独立 readOnlyDataSource + JSqlParser AST + 双写审计）
  - 异步导出参考（对齐 PerfExport V1.2 同步模型）
  - 测试数据前缀：`TEST_RPT_*` / `CONCUR_RPT_*`
- [ ] Commit

#### Task M6.2.3：04 对外契约 V1 状态备注

- [ ] 修改 04 文档末尾追加 V1.0 交付备注
- [ ] Commit

---

### Phase M6.3：技术债清算

**Files:**
- Modify: `report-analytics-center/CLAUDE.md`（新增"V1.0 已知技术债 / V1.1 规划"章节）

#### Task M6.3.1：V1.0 技术债清算 + V1.1 规划列表

- [ ] **Step 1：登记 V1.0 已知技术债**

```markdown
## V1.0 已知技术债（待 V1.1+ 处理）

| 序号 | 标题 | 优先级 | 来源 | 状态 |
|---|---|---|---|---|
| 1 | MetricApi.batchGet*MetricValues 性能优化（M1.2 / M2.2 当前用单条循环，规模大时 N+1 风险） | 中 | 09 文档规划态 + M1.2 实现 | 待 V1.1 上游 batchGet 提供后切换 |
| 2 | KpiApi.batchGetKpiTotal / getKpiRanking 同上规划态 | 中 | 09 文档规划态 + M3.2 实现 | 待 V1.1 上游 batchGet 提供后切换 |
| 3 | 异步导出 V1 同步执行（PerfExport V1.2 同模型）→ V1.1 切真异步线程池 | 低 | M5 决策 BR-2 | V1.1 引入 @Async + ThreadPoolExecutor |
| 4 | rpt_snapshot_task V1 仅建表不启用 → V2 启用条件：DAU > 200 或仪表盘 P99 > 1s | 低 | 决策 BR-3 + 05 §7 | V2 启用时间另议 |
| 5 | 仪表盘 V1.0 数据版本依赖 performance.SysControlApi（当前不存在），临时用 MetricApi 兜底；待 V1.1 SysControlApi 暴露后切换 | 低 | 调研发现 | 待 V1.1 上游 |
| 6 | DataScope WORKFLOW_PARTICIPANT 类型暂未在 report 落地（无 business_key 列） | 低 | 09 §X.6 | V2 引入快照表后再考虑 |

## V1.1 规划

- 性能优化：批量 Api 切换（M1.2 + M3.2）
- 异步导出：@Async + 线程池切换（参考 PerfExport V1.3+）
- C.x 固定报表导出端点：03 §I.5 V2 4 个 /export 端点（dashboard / touch / perf / custpool）
- C.bis 报表订阅推送（订阅 sys_control 事件后预热缓存）
```

- [ ] **Step 2：Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(report-v1): V1.0 技术债清算 + V1.1 规划登记（Task M6.3.1）

- 6 项 V1.0 已知技术债登记（性能/异步/快照/数据版本/WORKFLOW_PARTICIPANT/snapshot 启用）
- V1.1 规划清单（batch 切换 / 真异步 / 4 固定报表导出 / 报表订阅）

EOF
)"
```

---

### Phase M6.4：跨模块 V1.1 遗留观察项（performance Javadoc 顺手修补）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/MetricApi.java`（L18 Javadoc）
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/KpiApi.java`（L17 Javadoc）

#### Task M6.4.1：performance MetricApi/KpiApi Javadoc 同步 V1.1 真实交付状态

**背景（V1.1 P2.6 真实交付状态确认）:** 主代理已核实：
- `MetricApiImpl.java:42 / L279 / L285 / L291` — getEmpMetricValues / getOrgMetricValues / getCustMetricValues 真实方法体已落地（V1.1 P2.6）
- `KpiApiImpl.java:36-38 / L79 / L85 / L91` — getCurrentKpiTotal / getCurrentKpiResult / getKpiHistory 真实方法体已落地（V1.1 P2.6）
- 但接口文件 `MetricApi.java:18` / `KpiApi.java:17` 的 Javadoc 仍写 "⏳ V1.1 UOE 占位"（过时注释，未与实现同步）

**修补量：** 2 行 Javadoc 改写，跨模块小修补，不增加风险。本 Phase 在 report V1.0 收尾时一并处理，避免 V1.1 撰写者再被同样的过时注释误导（reviewer F2/F4 误判即源于此）。

- [ ] **Step 1：Read 现状**

```bash
# 阅读两个接口文件的 Javadoc 区域
sed -n '15,22p' performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/MetricApi.java
sed -n '14,20p' performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/KpiApi.java
```

- [ ] **Step 2：Edit — 修改 MetricApi.java Javadoc**

将 `MetricApi.java:18` 附近的 `⏳ V1.1 UOE 占位` 注释改为 `✅ V1.1 P2.6 已交付`，例如：

```java
/**
 * 指标查询 Api（跨模块只读）.
 * <p>✅ V1.1 P2.6 已交付：getEmpMetricValues / getOrgMetricValues / getCustMetricValues 真实实现
 *    （详见 MetricApiImpl L279/L285/L291，原 V1.0 UOE 占位已清零）
 */
public interface MetricApi { ... }
```

- [ ] **Step 3：Edit — 修改 KpiApi.java Javadoc**

将 `KpiApi.java:17` 附近的 `⏳ V1.1 UOE 占位` 注释改为 `✅ V1.1 P2.6 已交付`：

```java
/**
 * KPI 查询 Api（跨模块只读）.
 * <p>✅ V1.1 P2.6 已交付：getCurrentKpiTotal / getCurrentKpiResult / getKpiHistory 真实实现
 *    （详见 KpiApiImpl L79/L85/L91，原 V1.0 UOE 占位已清零）
 */
public interface KpiApi { ... }
```

- [ ] **Step 4：Run — 编译 performance + report**

```bash
mvn -q -pl performance-engine-center,report-analytics-center compile
```

预期：编译通过（仅注释改动）。

- [ ] **Step 5：Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(perf-v1.1): MetricApi/KpiApi Javadoc 同步 V1.1 P2.6 真实交付状态（Task M6.4.1）

- 移除过时 "⏳ V1.1 UOE 占位" 标注（实际 V1.1 P2.6 已 Green 交付）
- 改为 "✅ V1.1 P2.6 已交付" + 实现行号引用
- 副产物源由：report V1.0 plan reviewer 因过时 Javadoc 误判为 UOE 状态（F2/F4 误判修补）
- 跨模块小修补，不影响功能；防止 V1.1+ 撰写者同样被误导

EOF
)"
```

#### Task M6.4.2：报表 V1.0 plan reviewer 误判副产物登记

- [ ] 在 `report-analytics-center/CLAUDE.md` 新增章节 "## V1.0 plan 撰写期 reviewer 误判记录"，登记如下：

```markdown
## V1.0 plan 撰写期 reviewer 误判记录（2026-04-25）

| 误判项 | reviewer 误读 | 实际真相 | 根因 |
|---|---|---|---|
| F2 | M2/M3 调 MetricApi 仍是 UOE 占位 | V1.1 P2.6 已 Green | MetricApi.java:18 Javadoc 未与实现同步（M6.4.1 已修） |
| F4 | M3 调 KpiApi 仍是 UOE 占位 | V1.1 P2.6 已 Green | KpiApi.java:17 Javadoc 未与实现同步（M6.4.1 已修） |

启示：跨模块依赖的 *Api 接口 Javadoc 必须与 *Impl 状态同步，避免后续模块的 plan 撰写/审查被过时注释误导。
```

- [ ] Commit。

---

## 验收清单

V1.0 交付后必须全部满足：

- [ ] `mvn -q -pl report-analytics-center clean verify` 全绿（surefire + failsafe 双绿，预估 200-260 tests）
- [ ] 6 架构守护绿
  - BizAuth：所有 RestController 公共方法必有 @BizAuth
  - NoEntityInController：Controller 签名禁出现 Entity
  - NoMapperInController：Controller 禁注入 Mapper
  - PackageStructure：包结构严格对齐 02 §3
  - CrossModuleApiOnly：跨模块只走 *Api
  - NoUoeInFacadeTests：facade 测试禁 assertThrows(UOE)
- [ ] **错误码守护**：RptErrorCodeTest 30 条（基线 25 + J 章扩展 5）+ 中文消息 + 唯一性 + EXPORT_START_FAILED 必含
- [ ] **SQL 探查守护**：SqlSafeValidatorTest ≥ 15 边界用例
- [ ] **25 个 REST 接口在 PT_RESOURCE 注册**
  - 8 接口（M1：query-dimensions / dynamic-query×2 / saved-queries×4 + 1 占位）
  - 3 接口（M2：dashboard president/org/emp）
  - 6 接口（M3：3 view + 3 export）
  - 5 接口（M4：sql-probe execute/history×2/whitelist + 1 历史详情）
  - 3 接口（M5：export-tasks status/cancel/download）
  - 共 25 条 RPT_* 资源（资源 ID 缩写见 08 §5.2）
- [ ] **Flyway 链路完整**：V1_0_0__rpt_init.sql → V1_0_1__rpt_meta_pt_resources.sql → V1_0_2__rpt_dashboard_pt_resources.sql → V1_0_3__rpt_summary_pt_resources.sql → V1_0_4__rpt_sql_probe_pt_resources.sql → V1_0_5__rpt_export_task.sql → V1_0_6__rpt_export_pt_resources.sql → V1_0_7__rpt_resources_align.sql（共 8 个脚本）
- [ ] **DATA_SCOPE 7 类映射在 3 处生效**
  - 仪表盘（M2.2 R_PRESIDENT 角色 + ALL）
  - 汇总报表（M3.x ORG_SUBTREE / SELF）
  - SQL 探查（M4.2 R_BACK_TECH 角色限制；不走 DATA_SCOPE 行级过滤）
- [ ] **跨模块只读契约**：CrossModuleApiOnlyArchTest 守护，仅 4 个 *Api 包可被引用
- [ ] **不暴露 *Api**：04 §1.1 红线，模块根 `api/` 目录仅有占位 package-info（无生产 Api 接口）
- [ ] **文档三份同步**
  - 根 CLAUDE.md 模块状态升级为 V1.0 已交付
  - report-analytics-center/CLAUDE.md 新建
  - docs/modules/report-analytics-center/04-对外API契约.md 末尾备注
- [ ] **审计双写完整性**
  - SQL 探查：sql_probe_history（业务历史） + audit_log（governance 审计）
  - 异步导出：rpt_export_task（任务表） + audit_log（EXPORT 动作审计）
  - 仪表盘：audit_log（READ 敏感动作，async 包装）

---

## 风险与回滚

### 风险清单

| 风险 | 严重度 | 应对 |
|---|---|---|
| **R1: SqlSafeValidator 子查询深度递归遍历不全**：JSqlParser 4.9 的 SubSelect 在 JOIN/WHERE/HAVING/SELECT 项中均可出现，递归遍历漏字段会导致深度计算偏低 → 实际放过 4-5 层嵌套 SQL | 高 | M4.1 SqlSafeValidatorTest 必须含"4 层 IN 子查询拒绝"+"3 层 JOIN 子查询通过"两个对照 case；如发现漏分支，立即补充递归路径并补 IT；M4.4 联合 IT 用真实 perf_*/cust_* 表跑 5 类常见 SQL 模式 |
| **R2: SQL 探查白名单变更管控**：白名单写在 sys_config_kv，运维直改可能引入未审计变更 | 高 | 08 §1.3 已规定"白名单变更走 CAB 审批 + 备份脚本带日期前缀"。实施时在 M4.2 ConfigApi 读取处加 INFO 日志（"[SqlSafeValidator] 当前白名单 N 张表"），便于运维巡检 |
| **R3: 异步导出 Worker 失败重试策略**：参考 PerfExport V1.2，初版**不重试**，失败即标 FAILED + 错误信息入库；用户重新提交即可。M5.2.2 落地时务必明确这一行为 | 中 | M5.2.2 RptExportServiceImpl 注释明确"V1.0 不重试，参考 PerfExport V1.2 同设计"；M6.3.1 技术债登记重试 V1.1+ 规划 |
| **R4: 跨模块 Api 不稳定时的 fail-close 降级**：M2/M3 大量调 MetricApi.getEmpMetricValues 等，performance V1.1 实现已稳定但仍可能抛 RuntimeException | 中 | M1.2 / M2.2 / M3.x 实现都用 try-catch 包装上游异常，转换为 RPT-50001 CROSS_MODULE_CALL_FAILED；不传播原始 stacktrace 给前端 |
| **R5: DATA_SCOPE Fragment 的 SQL 注入防护**：09 §X.3 在 Java 层做 subjectIds 过滤而非 SQL 注入，理论上无风险；但若未来引入 ORG_SUBTREE 直接注入 SQL，需特别守护 | 低 | M1.2 + M3.x 实现严格遵守"Java 层过滤" + "MetricApi 内层过滤"双层模式，禁止在 report 模块直接拼接 ORG_SUBTREE SQL；CrossModuleApiOnlyArchTest 间接守护（无 Mapper 直查 perf/cust 表的途径） |
| **R6: 测试基础设施 H2 vs MySQL 差异**：JSqlParser 解析 + Flyway DDL 在 H2 兼容模式下行为可能与生产 MySQL 不同 | 低 | M0.2.1 用 `MODE=MySQL` 模式 + 关键 IT（M4.4 / M5.4）必要时引入 Testcontainers MySQL（参考 perf 已有用法）；V1.0 优先 H2，V1.1 再上 Testcontainers |
| **R7: Caffeine 缓存与 Redis 选型分歧**：02 §8.1 设计文档用 Redis（key 前缀 report:），M2.1.1 改用 Caffeine 本地缓存 | 低 | 决策依据：仪表盘 V1 单实例部署 + 5min TTL，Caffeine 简化部署；M6.2.2 模块 CLAUDE.md 注明 V1.1 多实例时切 Redis 的迁移路径 |

### 回滚策略

V1.0 6 个 Milestone **逐 Milestone 独立可回滚**：

1. **单 Milestone 回滚**：`git revert <Milestone last commit>..<Milestone first commit>`，DDL 配套有 undo 脚本（每个 Vx_x_y__*.sql 配 `Ux_x_y__*.sql`）
2. **整模块下线**：根 pom.xml 删除 `<module>report-analytics-center</module>` 即可——本模块不被任何业务模块依赖（07 红线），下线无连锁影响
3. **DDL 回滚**：M0.2 / M5.1 提供 undo 脚本：
   - `U1_0_0__rpt_init.sql`（DROP rpt_saved_query / sql_probe_history / rpt_snapshot_task）
   - `U1_0_5__rpt_export_task.sql`（DROP rpt_export_task）
4. **PT_RESOURCE 回滚**：DELETE FROM PT_RESOURCE WHERE resource_id LIKE 'RPT_%'

---

## 测试计数预估

| Milestone | surefire (UT) | failsafe (IT) | 小计 |
|---|---|---|---|
| M0 | 18（3 ArchTest×2 + RptErrorCodeTest×4 + DashboardMetricsTest×6 占位） | 6（V1_0_0FlywayIT×3 + RptSavedQueryMapperIT×2 + 1） | 24 |
| M1 | 32（MetaServiceTest×3 + DynamicQueryServiceTest×6 + SavedQueryServiceTest×8 + 占位） | 12 | 44 |
| M2 | 25（DashboardServiceTest×8 + 仪表盘 3 Service×6 + DTO/Caffeine 守护×11） | 8 | 33 |
| M3 | 30（3 报表 Service×8 + 3 export Service×6） | 12 | 42 |
| M4 | 40（SqlSafeValidatorTest≥15 + SqlProbeServiceTest×8 + 4 接口 Controller IT×17） | 12 | 52 |
| M5 | 35（RptExportServiceTest×8 + 4 ExportStrategy×16 + 3 接口 IT×11） | 10 | 45 |
| M6 | 5（RptResourcesIT + 跨模块可用性 IT 占位） | 3 | 8 |
| **合计** | **185** | **63** | **248** |

实际可能 ±10%，取决于 Service 拆分粒度与 mock 配比。M6.1 验收门槛：双绿 ≥ 220。

---

## 时间预估

| 阶段 | 任务数 | 预估 commit | 工时（独立专注） |
|---|---|---|---|
| M0 | 6 Phase × 1 Task = 6 Task | 9-13 | 7-9 小时（M0.6 BaseControllerIT 约 1 小时） |
| M1 | 6 Phase × ~1.5 Task = 9 Task | 12-15 | 12-15 小时 |
| M2 | 4 Phase × 1.5 Task = 6 Task | 8-10 | 8-10 小时 |
| M3 | 5 Phase × ~1.4 Task = 7 Task | 10-12 | 10-12 小时 |
| M4 | 5 Phase × ~1.4 Task = 7 Task | 10-12 | 12-15 小时（SqlSafeValidator 单独 4 小时） |
| M5 | 4 Phase × ~1.5 Task = 6 Task | 8-10 | 10-12 小时 |
| M6 | 4 Phase × ~1.25 Task = 5 Task | 6-9 | 5-7 小时（M6.4 跨模块 Javadoc + reviewer 误判记录约 1 小时） |
| **合计** | **46 Task / 34 Phase** | **63-81 commit** | **64-80 小时（≈ 8-10 工作日）** |

---

## 与已交付计划的关系

- **performance-engine-center V1.0-V1.5**（5 份 plan，截至 2026-04-24）：本计划复用其 perf_export_task 异步导出骨架（M5）+ Flyway 风格（M0.2）+ 6 架构守护模式（M0.4）
- **performance-engine-center V1.4 S1**：WorkflowQueryApi 新增并未对本模块产生直接影响（report V1 不查工作流任务）
- **system-governance-center**：本模块复用其 7 大 Api（DictApi / ConfigApi / AuditApi / FileApi / NotifyApi / CalendarApi 不用 / JobApi 不用）；与 governance 自身的 SqlProbeController（路径 /api/admin/sql-probe）功能并行存在，不冲突
- **customer-marketing-center V1.0**：本模块依赖其 5 个 Api 中的 2 个（CustomerQueryApi / TouchTaskQueryApi），不依赖 TagApi / LeadApi / ClaimApi
- **本计划之后**：V1.1 计划在 V1.0 上线运行 1-2 周后，按 M6.3 技术债清单展开（预估 30-40 commit）

---

## 实施顺序建议

按 Milestone 顺序串行执行（每个 Milestone 完成后 push 远程 + 全部完成合并 master，遵循用户记忆中 `feedback_phase_commit_push.md` 偏好）：

```
M0（6-8h）→ push → M1（12-15h）→ push → M2（8-10h）→ push
→ M3（10-12h）→ push → M4（12-15h）→ push → M5（10-12h）→ push
→ M6（4-6h）→ push → 合并 master
```

每个 Milestone 末执行 `git push origin <branch>`；M6 完成后切回 master 合并。

---

**计划编写时间**：2026-04-25
**最后更新**：2026-04-25




