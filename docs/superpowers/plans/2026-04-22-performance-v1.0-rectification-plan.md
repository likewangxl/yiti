# performance-engine-center V1.0 整改计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 V1.0 已交付代码与文档/DDL 权威源的偏离，使 V1.0 骨架在上线 V1.1 之前达到"契约闭环、DDL 完备、错误码可被跨模块消费"的稳定态。

**Architecture:**
本计划不引入新业务能力，只做"对齐"——DDL 补字段/加唯一键、错误码重排、Controller 返回值 DTO 化、根 CLAUDE.md 状态描述更新、PT_RESOURCE 与 status 枚举冲突消解。所有变更以 TDD 节奏推进，以 Flyway 增量脚本 `V1_0_3`/`V1_0_4` 形式交付，不修改已发布的 `V1_0_0/V1_0_1/V1_0_2` 脚本。

**Tech Stack:** Spring Boot 3.2.3、MyBatis 3.0.3、MySQL 8.0、Flyway、JUnit 5、Testcontainers（已在项目中就绪）。

**依赖文档（权威需求来源）:**
- `docs/modules/performance-engine-center/01-功能规格.md`
- `docs/modules/performance-engine-center/02-后端架构.md`
- `docs/modules/performance-engine-center/03-接口设计与报文.md`
- `docs/modules/performance-engine-center/04-对外API契约.md`
- `docs/modules/performance-engine-center/05-表结构DDL.md`
- `docs/modules/performance-engine-center/07-审计要求.md`
- `docs/schema/ddl-performance.sql`

**前置事实:**
- V1.0 代码基线见 `performance-engine-center/CLAUDE.md`（已声明 V1.0 / V1.1 / V1.2 分期）
- 本计划仅修复 P0 偏离，不做 V1.1/V1.2 的新增功能（见 `2026-04-22-performance-v1.1-iteration-plan.md` 与 `2026-04-22-performance-v1.2-iteration-plan.md`）
- 每个任务独立 commit；commit message 前缀使用 `fix(perf-v1.0):` 或 `docs(perf-v1.0):` 或 `chore(perf-v1.0):`

---

## 阶段概览

| 阶段 | 任务数 | 目标 |
|---|---|---|
| A | 2 | 根 CLAUDE.md 模块状态修正 + 主键类型契约 03/04/05 三份文档统一 |
| B | 8 | DDL 补字段/唯一键 + Entity/Mapper 对齐 + undo 反向脚本 |
| C | 4 | 错误码按 03 §K 权威清单重排 + 03 §A 正文同步 |
| D | 3 | Controller 返回值 DTO 化 + ArchUnit 架构守护 |
| E | 3 | PT_RESOURCE 冗余清理 + metric_def.status 统一（01/03/05 三份） |
| F | 2 | @BizAuth 单档策略固化 + 与 03 文档命名对齐 |
| Z | 1 | 整改验收清单 |

---

## 阶段 A：文档与契约决策正式化

### Task A1：根 CLAUDE.md 模块状态修正

**Files:**
- Modify: `CLAUDE.md:47` 与 `CLAUDE.md:62-65`（"当前已实现的模块"表与"尚未实现的模块"列表）

- [ ] **Step 1：定位当前错误描述**

执行 `Grep pattern="performance-engine-center" path="CLAUDE.md"`，确认：
- 第 47 行表格中 performance-engine-center 未出现（正确）
- 第 62-65 行的"尚未实现的模块（代码骨架和 DDL 已存在）"列表中包含 `performance-engine-center`

- [ ] **Step 2：修改为 V1.0 已交付骨架**

将 `performance-engine-center` 从"尚未实现"段落移到表格，并加一行：

```markdown
| `performance-engine-center` | com.bank.branch.platform.performance | V1.0 骨架已完成 | 绩效计算中心 (配置 CRUD + 版本管理骨架，V1.1/V1.2 规划中) |
```

- [ ] **Step 3：同步更新依赖图**

`CLAUDE.md:70-79` 的 "当前模块依赖图"文字段落中加一行：
```
performance-engine-center (依赖 auth + governance) ← V1.0 骨架，不依赖 workflow
```

- [ ] **Step 4：人工验证**

执行 `Read file_path="CLAUDE.md" offset=40 limit=80` 审阅整体一致性（命名、表格对齐）。

- [ ] **Step 5：Commit**

```bash
git add CLAUDE.md
git commit -m "docs(perf-v1.0): 修正 performance-engine-center 在根 CLAUDE.md 的实现状态"
```

---

### Task A2：主键类型契约决策同步到 03/04/05 三份文档

**Files:**
- Modify: `docs/modules/performance-engine-center/04-对外API契约.md`（§3 KpiApi、§4 TargetApi 的 Long 签名）
- Modify: `docs/modules/performance-engine-center/03-接口设计与报文.md`（路径参数 `{schemeId}` / `{planId}` / `{id}` 等类型描述）
- Modify: `docs/modules/performance-engine-center/05-表结构DDL.md`（若有 BIGINT 主键定义，改为 `varchar(32)`）

- [ ] **Step 1：定位三份文档中所有 Long/BIGINT 主键点**

```
Grep pattern="Long\\s+(schemeId|planId|kpiSchemeId|targetPlanId|metricId|allocId|adjustId)" path="docs/modules/performance-engine-center" -n output_mode="content"
Grep pattern="(BIGINT|bigint).*id.*PRIMARY|id.*BIGINT|id.*bigint" path="docs/modules/performance-engine-center/05-表结构DDL.md" -n output_mode="content"
Grep pattern="\\{schemeId\\}|\\{planId\\}|\\{id\\}" path="docs/modules/performance-engine-center/03-接口设计与报文.md" -n output_mode="content"
```

记录所有命中行号与文件。

- [ ] **Step 2：在 04 文档追加统一决策段落**

在 04 文档"§0 全局约定"末尾追加：
```markdown
### 0.5 主键类型统一为 String（2026-04-22 修订）

生产 DDL（`docs/schema/ddl-performance.sql`）中 `perf_kpi_scheme.id` / `perf_target_plan.id` / `sys_control.id` / `perf_metric_def.id` 均为 `varchar(32)`（业务编码主键），与原 04 契约的 `Long` 冲突。V1.0 已全局对齐为 `String`，本条为 04/03/05 三份文档的正式统一修正。跨模块消费方应使用 `String` 类型。
```

- [ ] **Step 3：逐行替换 04 文档签名**

将 §3、§4 及其它小节的 `Long schemeId`、`Long planId` 等全部替换为 `String schemeId`、`String planId`；字段表中的 `Long`/`BIGINT` 主键类型列改为 `String`/`varchar(32)`。

- [ ] **Step 4：同步修改 03 文档**

- 路径参数说明表（每个接口的"Path 参数"段）中，`schemeId` / `planId` / `{id}` 的类型描述从隐含的"Long 递增"改为"String（varchar(32) 业务编码）"。
- 请求/响应 JSON 示例中若出现 `"id": 123` 这类数字 ID，改为 `"id": "SCH_20260422_001"` 的字符串样例。

- [ ] **Step 5：同步修改 05 文档**

若 05 §2 各表 DDL 描述中的主键类型写为 `BIGINT AUTO_INCREMENT`，改为 `varchar(32)`，并在字段注释列补一句"业务编码主键，与生产 DDL ddl-performance.sql 对齐"。

- [ ] **Step 6：人工复核**

```
Grep pattern="Long\\s+(scheme|plan|kpi|target|metric|alloc|adjust)Id" path="docs/modules/performance-engine-center"
Grep pattern="(BIGINT|bigint).*id.*(AUTO_INCREMENT|PRIMARY)" path="docs/modules/performance-engine-center/05-表结构DDL.md"
```

预期：零结果。

- [ ] **Step 7：Commit**

```bash
git add docs/modules/performance-engine-center/04-对外API契约.md \
        docs/modules/performance-engine-center/03-接口设计与报文.md \
        docs/modules/performance-engine-center/05-表结构DDL.md
git commit -m "docs(perf-v1.0): 03/04/05 三份文档主键类型统一为 String"
```

---

## 阶段 B：DDL 字段补齐 + Entity/Mapper 对齐

> 策略：新增一个 Flyway 脚本 `V1_0_3__perf_metric_def_and_sys_control_alignment.sql`，**不修改**已发布的 `V1_0_0`。
> 验证：采用 Testcontainers + Flyway 自动迁移，每次启动都能从零库建到 `V1_0_3`。

### Task B1：建立 V1_0_3 脚本骨架（含 Testcontainers 测试基类）

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_0_3__perf_schema_alignment.sql`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/PerformanceFlywayTestBase.java`（所有 Flyway IT 共享基类）
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_0_3FlywayIT.java`

> 说明：Flyway 测试共用基类通过 Testcontainers 启动 MySQL 8 容器，并将 Flyway 迁移全量跑到最新版本，再在测试方法中查询 `information_schema` 验证 DDL 结果。

- [ ] **Step 0：创建测试基类（共享）**

```java
package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = PerformanceBootstrapConfig.class)  // 项目根 bootstrap 的测试配置类
@ExtendWith(SpringExtension.class)
@Testcontainers
public abstract class PerformanceFlywayTestBase {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.33")
        .withDatabaseName("onepl_test")
        .withUsername("root")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",    MYSQL::getUsername);
        registry.add("spring.datasource.password",    MYSQL::getPassword);
        registry.add("spring.flyway.enabled",         () -> "true");
        registry.add("spring.flyway.locations",       () -> "classpath:sql");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @Autowired
    protected JdbcTemplate jdbc;
}
```

若项目根 `bootstrap` 中已有 `PerformanceBootstrapConfig` 之类的测试配置类，直接复用；若无，在 Step 0 同时创建一个最小配置：

```java
package com.bank.branch.platform.performance.migration;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.performance")
public class PerformanceBootstrapConfig { }
```

> 前置依赖：在 `performance-engine-center/pom.xml` test scope 确认已引入 `org.testcontainers:mysql`、`org.testcontainers:junit-jupiter`；若无则追加。

- [ ] **Step 1：写失败测试**

```java
package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class V1_0_3FlywayIT extends PerformanceFlywayTestBase {

    @Test
    void sysControl_uniqueKey_includesCurrentVersion() {
        List<String> cols = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND INDEX_NAME='uk_scope_dim_date_version' ORDER BY SEQ_IN_INDEX",
            String.class);
        assertThat(cols).containsExactly("scope_dim", "latest_data_date", "current_version");
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd performance-engine-center
mvn -Dtest=V1_0_3FlywayIT test
```

预期：FAIL（脚本尚未创建，索引不存在）。

- [ ] **Step 3：创建脚本最小实现**

```sql
-- V1_0_3__perf_schema_alignment.sql
-- sys_control UK 加 current_version（拆旧 UK + 建新 UK）
ALTER TABLE sys_control DROP INDEX uk_scope_dim_date;
ALTER TABLE sys_control
  ADD UNIQUE KEY uk_scope_dim_date_version (scope_dim, latest_data_date, current_version);
```

- [ ] **Step 4：运行测试确认通过**

`mvn -Dtest=V1_0_3FlywayIT test` 预期 PASS。

- [ ] **Step 5：Commit**

```bash
git add performance-engine-center/src/main/resources/sql/V1_0_3__perf_schema_alignment.sql \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_0_3FlywayIT.java
git commit -m "fix(perf-v1.0): sys_control 唯一键补 current_version 列"
```

---

### Task B2：perf_metric_def 增加 uk_base_dim_slot 唯一键

**Files:**
- Modify: `performance-engine-center/src/main/resources/sql/V1_0_3__perf_schema_alignment.sql`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_0_3FlywayIT.java`

> 注意：V1_0_0 中 base_dim + val_slot 对 deleted 行（若有）也会冲突。V1.0 采用软删除字段 `deleted`（见 Task B4 引入），唯一键应排除已删除行。MySQL 8 支持 functional index，使用"槽位仅对 deleted=0 行唯一"的表达：`UNIQUE KEY uk_base_dim_slot ((IF(deleted=0, CONCAT(base_dim,'-',val_slot), NULL)))`。

- [ ] **Step 1：写失败测试**

```java
@Test
void perfMetricDef_slotConflict_rejectedByDatabase() {
    jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, deleted) VALUES ('T1','TEST_SLOT_1','T1','EMP',1,0)");
    assertThatThrownBy(() ->
        jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, deleted) VALUES ('T2','TEST_SLOT_2','T2','EMP',1,0)")
    ).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    // 软删同 slot 允许
    jdbc.update("UPDATE perf_metric_def SET deleted=1 WHERE id='T1'");
    jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, deleted) VALUES ('T3','TEST_SLOT_3','T3','EMP',1,0)");
}
```

- [ ] **Step 2：运行失败**

- [ ] **Step 3：追加 V1_0_3 SQL**

```sql
-- perf_metric_def 槽位唯一键（仅对未删除行）
ALTER TABLE perf_metric_def
  ADD UNIQUE KEY uk_base_dim_slot_alive
  ((IF(deleted=0, CONCAT(base_dim,'#',val_slot), NULL)));
```

> 前置：此步骤依赖 Task B4 引入 `deleted` 字段，调整任务顺序：先做 B4 的 `deleted` 字段再做 B2 索引。本处写为逻辑顺序；执行顺序见阶段 B 最终小结。

- [ ] **Step 4：运行测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): perf_metric_def 增加 base_dim+val_slot 唯一键（排除软删除行）"
```

---

### Task B3：sys_control 补 5 个字段

**Files:**
- Modify: `performance-engine-center/src/main/resources/sql/V1_0_3__perf_schema_alignment.sql`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/SysControl.java`
- Modify: `performance-engine-center/src/main/resources/mapper/performance/SysControlMapper.xml`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/SysControlMapperNewFieldsIT.java`

> 文档 01 §2.2 要求字段：`remark`、`updated_by`、`publish_source`、`publish_by`、`publish_time`

- [ ] **Step 1：写失败测试（继承 Mapper 测试基类）**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.testbase.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class SysControlMapperNewFieldsIT extends PerformanceMapperTestBase {

    @Autowired
    private SysControlMapper mapper;

    @Test
    void insert_and_read_withNewFields() {
        SysControl sc = new SysControl();
        sc.setId("TEST_SC_B3");
        sc.setScopeDim("GLOBAL");
        sc.setLatestDataDate(LocalDate.of(2026, 4, 1));
        sc.setCurrentVersion("v20260401");
        sc.setRemark("季度末切版");
        sc.setUpdatedBy("admin");
        sc.setPublishSource("MANUAL");
        sc.setPublishBy("admin");
        sc.setPublishTime(LocalDateTime.now());
        mapper.insert(sc);

        SysControl loaded = mapper.selectById("TEST_SC_B3");
        assertThat(loaded.getRemark()).isEqualTo("季度末切版");
        assertThat(loaded.getPublishSource()).isEqualTo("MANUAL");
        assertThat(loaded.getPublishBy()).isEqualTo("admin");
        assertThat(loaded.getPublishTime()).isNotNull();
        assertThat(loaded.getUpdatedBy()).isEqualTo("admin");
    }
}
```

> `PerformanceMapperTestBase` 在模块 CLAUDE.md §6 已有定义（含 `@Transactional` + 自动 rollback + Testcontainers MySQL），直接继承。

- [ ] **Step 2：运行失败**（字段不存在）

- [ ] **Step 3：V1_0_3 追加 DDL + 修改 Entity + Mapper XML**

```sql
ALTER TABLE sys_control
  ADD COLUMN remark VARCHAR(255) NULL AFTER current_version,
  ADD COLUMN updated_by VARCHAR(32) NULL,
  ADD COLUMN publish_source VARCHAR(32) NULL COMMENT 'MANUAL/AUTO/ROLLBACK',
  ADD COLUMN publish_by VARCHAR(32) NULL,
  ADD COLUMN publish_time DATETIME NULL;
```

Entity 补字段（5 个 private field + getter/setter）；Mapper XML 的 `<resultMap>`、`<sql id="Base_Column_List">`、`insert`、`updateByPrimaryKey` 四处同步。

- [ ] **Step 4：运行测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): sys_control 补 remark/updated_by/publish_source/publish_by/publish_time 五个字段"
```

---

### Task B4：perf_metric_def 补 4 个字段

**Files:**
- Modify: `performance-engine-center/src/main/resources/sql/V1_0_3__perf_schema_alignment.sql`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfMetricDef.java`
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfMetricDefMapper.xml`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfMetricDefNewFieldsIT.java`

> 文档 05 §2.2 要求字段：`unit`、`decimal_places`、`deleted`、`description`

- [ ] **Step 1：写失败测试**

```java
@Test
void insert_and_read_unitAndDecimalPlaces() {
    PerfMetricDef m = newSample("TEST_METRIC_B4");
    m.setUnit("万元"); m.setDecimalPlaces(2);
    m.setDescription("人均存款"); m.setDeleted(0);
    mapper.insert(m);
    PerfMetricDef loaded = mapper.selectById(m.getId());
    assertThat(loaded.getUnit()).isEqualTo("万元");
    assertThat(loaded.getDecimalPlaces()).isEqualTo(2);
}
```

- [ ] **Step 2：运行失败**

- [ ] **Step 3：V1_0_3 追加 DDL + 修改 Entity + Mapper XML**

```sql
ALTER TABLE perf_metric_def
  ADD COLUMN unit VARCHAR(16) NULL COMMENT '单位：元/万元/%',
  ADD COLUMN decimal_places TINYINT DEFAULT 2 COMMENT '小数位数',
  ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '0=存在 1=删除',
  ADD COLUMN description VARCHAR(500) NULL;
-- 所有现存行 deleted=0
UPDATE perf_metric_def SET deleted=0 WHERE deleted IS NULL;
```

Entity / Mapper 同步补字段。所有 `SELECT` 查询增加 `deleted=0` 过滤（Service 层统一，不改 XML 基础 SQL，改用 `<where>` 或 WrapperSpec；V1.0 为简化直接加 `AND deleted=0`）。

- [ ] **Step 4：运行测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): perf_metric_def 补 unit/decimal_places/deleted/description 字段 + 软删除过滤"
```

---

### Task B5：MetricDefService 软删除语义对齐

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricDefServiceTest.java`

- [ ] **Step 1：写失败测试（删除后查询不可见）**

```java
@Test
void deleteMetric_isSoftDelete_notReturnedInQueries() {
    PerfMetricDef m = service.createMetric(sample("TEST_METRIC_B5"));
    service.deleteMetric(m.getId());
    assertThat(service.getMetricById(m.getId())).isNull();
    assertThat(service.list(pageReq()).getList()).noneMatch(x -> x.getId().equals(m.getId()));
}
```

- [ ] **Step 2：运行失败**（当前是物理删除）

- [ ] **Step 3：改为软删除**

将 `mapper.deleteById(id)` 替换为 `mapper.softDelete(id)`（XML 新增一条 `UPDATE perf_metric_def SET deleted=1, updated_at=NOW() WHERE id=#{id}`）。所有查询 XML 增加 `AND deleted=0`。

- [ ] **Step 4：运行测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): MetricDef 切换为软删除语义（deleted=1）"
```

---

### Task B6：阶段 B 顺序修复与回归

**Files:**
- Modify: `performance-engine-center/src/main/resources/sql/V1_0_3__perf_schema_alignment.sql`（按正确顺序编排）

- [ ] **Step 1：检查 V1_0_3 脚本内语句顺序**

正确顺序：
1. `sys_control` UK 改造（Task B1）
2. `sys_control` 新字段（Task B3）
3. `perf_metric_def` 新字段（Task B4，含 `deleted`）
4. `perf_metric_def` 槽位唯一键（Task B2，依赖 `deleted`）

- [ ] **Step 2：本地库 drop + 全量重建验证**

```bash
mysql -uroot -p123456 -e "DROP DATABASE IF EXISTS onepl_test_v103; CREATE DATABASE onepl_test_v103;"
cd bootstrap
mvn -Dflyway.url=jdbc:mysql://localhost:3306/onepl_test_v103 flyway:migrate
```

预期：V1_0_0 → V1_0_3 依次成功执行。

- [ ] **Step 3：跑全量 performance-engine-center 测试**

```bash
cd performance-engine-center
mvn clean test
```

预期：全部通过（若回归失败，在本任务修复）。

- [ ] **Step 4：Commit**

```bash
git commit -m "fix(perf-v1.0): 整理 V1_0_3 脚本内语句顺序，全量迁移回归通过"
```

---

### Task B7：SysControl 切换版本时写入 publish_* 字段

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/SysControlService.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/SysControlServiceTest.java`

- [ ] **Step 1：写失败测试**

```java
@Test
void doSwitchVersion_records_publishMetadata() {
    SwitchVersionCmd cmd = new SwitchVersionCmd("GLOBAL", LocalDate.of(2026,4,1), "v20260401", "季度末", "MANUAL", "admin");
    service.doSwitchVersion(cmd);
    SysControl sc = service.query("GLOBAL");
    assertThat(sc.getPublishSource()).isEqualTo("MANUAL");
    assertThat(sc.getPublishBy()).isEqualTo("admin");
    assertThat(sc.getRemark()).isEqualTo("季度末");
    assertThat(sc.getPublishTime()).isNotNull();
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：修改 Service + Cmd DTO**

在 `SwitchVersionCmd` 增加 `remark`、`publishSource`、`operatorId` 字段；`doSwitchVersion` 将这些字段写入 `SysControl`。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): sys_control 切换版本写入 publish 元数据"
```

---

### Task B8：准备 V1_0_3 / V1_0_4 的 undo 反向脚本

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/undo/V1_0_3__undo.sql`
- Create: `performance-engine-center/src/main/resources/sql/undo/V1_0_4__undo.sql`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/UndoScriptSmokeIT.java`

> 背景：项目使用 Flyway Community Edition（无官方 undo 支持）。本任务产出的 undo 脚本**不接入 Flyway 自动回滚**，而是作为应急运维手册——紧急情况下 DBA 可手动执行以还原 DDL 状态。

- [ ] **Step 1：写失败测试（仅 smoke，验证脚本语法可被 MySQL 解析）**

```java
package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

class UndoScriptSmokeIT extends PerformanceFlywayTestBase {

    @Test
    void undoV1_0_3_revertsDdlChanges() throws Exception {
        // 先跑到 V1_0_4（基类默认行为）
        // 再执行 V1_0_3 undo
        String sql = Files.readString(
            Paths.get("src/main/resources/sql/undo/V1_0_3__undo.sql"));
        jdbc.execute(sql);

        // 验证：uk_scope_dim_date_version 不再存在，新字段已删除
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND INDEX_NAME='uk_scope_dim_date_version'", Integer.class);
        assertThat(cnt).isZero();

        Integer fieldCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND COLUMN_NAME IN ('remark','updated_by','publish_source','publish_by','publish_time')",
            Integer.class);
        assertThat(fieldCnt).isZero();
    }

    @Test
    void undoV1_0_4_revertsResourceCleanup() throws Exception {
        String sql = Files.readString(
            Paths.get("src/main/resources/sql/undo/V1_0_4__undo.sql"));
        jdbc.execute(sql);

        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pt_resource " +
            "WHERE ID IN ('P_PERF_METRIC_EXECUTE') AND status='PENDING'",
            Integer.class);
        assertThat(cnt).isZero(); // PENDING 已被撤销
    }
}
```

- [ ] **Step 2：失败（脚本不存在）**

- [ ] **Step 3：创建 undo 脚本**

```sql
-- undo/V1_0_3__undo.sql
-- 紧急回滚：撤销 V1_0_3 的 sys_control + perf_metric_def DDL 变更
-- 注意：执行前确保对应字段中的业务数据已经备份或迁移

-- 1. 还原 sys_control 唯一键
ALTER TABLE sys_control DROP INDEX uk_scope_dim_date_version;
ALTER TABLE sys_control ADD UNIQUE KEY uk_scope_dim_date(scope_dim, latest_data_date);

-- 2. 删除 sys_control 新增字段
ALTER TABLE sys_control
  DROP COLUMN remark,
  DROP COLUMN updated_by,
  DROP COLUMN publish_source,
  DROP COLUMN publish_by,
  DROP COLUMN publish_time;

-- 3. 删除 perf_metric_def 槽位唯一键
ALTER TABLE perf_metric_def DROP INDEX uk_base_dim_slot_alive;

-- 4. 删除 perf_metric_def 新增字段
ALTER TABLE perf_metric_def
  DROP COLUMN unit,
  DROP COLUMN decimal_places,
  DROP COLUMN deleted,
  DROP COLUMN description;
```

```sql
-- undo/V1_0_4__undo.sql
-- 紧急回滚：撤销 V1_0_4 的 PT_RESOURCE PENDING 标记与字典项修改

UPDATE pt_resource SET status='ACTIVE' WHERE id IN (
  'P_PERF_METRIC_EXECUTE','P_PERF_METRIC_TRIAL_RUN','P_PERF_IMPORT_UPLOAD',
  'P_PERF_ALLOC_ADJUST_CREATE','P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC',
  'P_PERF_DATA_TASK_STATUS','P_PERF_SYS_CONTROL_ROLLBACK',
  'P_PERF_EXPORT_KPI','P_PERF_EXPORT_ALLOC'
);

UPDATE sys_dict_item SET status='ACTIVE'
 WHERE dict_code='PERF_METRIC_STATUS' AND item_code IN ('DRAFT','PUBLISHED');
```

- [ ] **Step 4：测试通过**

```bash
mvn -Dtest=UndoScriptSmokeIT test
```

- [ ] **Step 5：Commit**

```bash
git add performance-engine-center/src/main/resources/sql/undo/V1_0_3__undo.sql \
        performance-engine-center/src/main/resources/sql/undo/V1_0_4__undo.sql \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/UndoScriptSmokeIT.java
git commit -m "fix(perf-v1.0): 备妥 V1_0_3/V1_0_4 undo 反向脚本 + smoke IT"
```

---

## 阶段 C：错误码体系对齐

### Task C1：PerfErrorCode 按 03 §K 权威清单重排

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/enums/PerfErrorCodeTest.java`

> **权威来源**：`docs/modules/performance-engine-center/03-接口设计与报文.md` §K「错误码完整汇总（2026-04-10 新增）」。§K 明确标注为"完整汇总"，凡与 §A 章节内正文的错误码用法冲突（如 §A.3 第 157-159 行把 40002/40003/40004 误用于指标重复/层级/槽位冲突），以 §K 为准；§A 的误用点由 **Task C4**（新增）同步修正。
> **命名规则**：常量名保持现有 Java 风格（大写蛇形），与消息语义贴合；编码严格匹配 §K。

**§K 权威映射表（完整）：**

| 常量名 | 新编码（§K 权威） | 旧编码（V1.0 代码） | §K 消息 |
|---|---|---|---|
| **K.1 400xx 参数错误 / 资源不存在** | | | |
| METRIC_NOT_FOUND | PERF-40001 | PERF-40401 | 指标不存在 |
| BIZ_KIND_INVALID | PERF-40002 | （缺失，新增） | bizKind 参数无效 |
| KPI_SCHEME_NOT_FOUND | PERF-40003 | PERF-40402 | KPI 方案不存在 |
| TARGET_PLAN_NOT_FOUND | PERF-40004 | PERF-40403 | 目标方案不存在 |
| SYS_CONTROL_VERSION_NOT_FOUND | PERF-40012 | PERF-40406（含义迁移） | sys_control 版本不存在 |
| ALLOC_RELATION_NOT_FOUND | PERF-40014 | PERF-40407 | 分配关系记录不存在 |
| IMPORT_BATCH_NOT_FOUND | PERF-40017 | （缺失，新增） | 导入批次不存在 |
| CYCLE_PARAM_INVALID | PERF-40019 | （缺失，新增） | 周期参数不合法 |
| TARGET_ADJUST_APPLY_NOT_FOUND | PERF-40020 | （缺失，新增） | 目标修正申请不存在 |
| **K.2 409xx 业务冲突 / 幂等** | | | |
| METRIC_CODE_DUP | PERF-40901 | PERF-40903 | 指标编码已存在 |
| METRIC_HAS_DOWNSTREAM_REF | PERF-40902 | （缺失，新增） | 指标存在下游引用，不可删除 |
| SYS_CONTROL_VERSION_CONFLICT | PERF-40903 | PERF-40914 | sys_control 同维度同日期版本冲突 |
| TARGET_ADJUST_APPLY_RUNNING | PERF-40906 | （缺失，新增） | 目标修正申请流程已发起 |
| **K.3 422xx 参数校验 / 业务规则** | | | |
| VALIDATION_FAILED | PERF-42200 | （缺失，新增） | 参数校验失败（通用） |
| METRIC_CALC_LOGIC_INVALID | PERF-42201 | （对应 V1.0 原 METRIC_LEVEL_INVALID=40911 语义的部分场景） | 指标计算逻辑非法（如 SQL 中引用了下游指标） |
| KPI_WEIGHT_SUM_INVALID | PERF-42202 | PERF-40913（KPI_ITEM_WEIGHT_INVALID）| KPI 方案权重之和不等于 100 |
| IMPORT_COLUMN_MAPPING_INVALID | PERF-42203 | （缺失，新增） | 目标值导入 Excel 列映射错误 |
| TRIAL_RUN_TIMEOUT | PERF-42205 | （缺失，新增） | 试运行超时（30 秒） |
| BATCH_QUERY_EXCEEDS_LIMIT | PERF-42206 | （缺失，新增） | 批量查询超过上限（500 条） |
| EXPORT_ROWS_EXCEEDS_LIMIT | PERF-42207 | （缺失，新增） | 导出行数超过上限（200000） |
| EXPORT_TASK_NOT_FOUND | PERF-42208 | （缺失，新增） | 异步导出任务不存在或已过期 |
| EXPORT_TASK_OWNER_MISMATCH | PERF-42209 | （缺失，新增，HTTP 403）| 不能下载他人创建的导出任务 |
| EXPORT_FILTER_SCOPE_VIOLATION | PERF-42210 | （缺失，新增） | 导出过滤条件未通过 DATA_SCOPE 校验 |
| **K.4 500xx 系统错误** | | | |
| EXPORT_FILE_GENERATE_FAILED | PERF-50002 | （缺失，新增） | 导出文件生成失败 |
| CALC_JOB_FAILED | PERF-50007 | （缺失，新增） | 指标/KPI 计算 Job 执行失败 |

**§K 未覆盖但 V1.0 已使用的错误码（特殊处理）：**

§A.3 正文使用 40003 指代"指标层级与上级不符"、40004 指代"指标槽位冲突"，但 §K 将这两个编号分配给其它语义。根据"§K 为权威汇总"原则，**V1.0 现有常量 `METRIC_LEVEL_INVALID` 与 `METRIC_SLOT_CONFLICT` 按以下规则归并到 §K 编号体系**：

- `METRIC_LEVEL_INVALID` → 归入 `METRIC_CALC_LOGIC_INVALID(PERF-42201)`，message 细化为"指标层级与上级不符（不是合法的下级引用）"
- `METRIC_SLOT_CONFLICT` → 合并到 `METRIC_CODE_DUP(PERF-40901)` 的 message 派生版本，采用独立常量名 `METRIC_SLOT_CONFLICT` 但编号复用 `PERF-40901`（HTTP 409，语义为"指标槽位已占用"）

**若校对时发现常量名与编号存在业务歧义，回退到"新增编号并同步回 03 §K"的处理路径**：以 `docs/superpowers/plans/2026-04-22-performance-v1.0-rectification-notes.md` 留档并通报架构评审，不在本计划内自行新增编号。

- [ ] **Step 1：写测试锁定全部新映射（无省略号）**

```java
// PerfErrorCodeTest.java
import static org.assertj.core.api.Assertions.assertThat;

class PerfErrorCodeTest {

    @Test
    void k1_400xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.METRIC_NOT_FOUND.getCode()).isEqualTo("PERF-40001");
        assertThat(PerfErrorCode.BIZ_KIND_INVALID.getCode()).isEqualTo("PERF-40002");
        assertThat(PerfErrorCode.KPI_SCHEME_NOT_FOUND.getCode()).isEqualTo("PERF-40003");
        assertThat(PerfErrorCode.TARGET_PLAN_NOT_FOUND.getCode()).isEqualTo("PERF-40004");
        assertThat(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND.getCode()).isEqualTo("PERF-40012");
        assertThat(PerfErrorCode.ALLOC_RELATION_NOT_FOUND.getCode()).isEqualTo("PERF-40014");
        assertThat(PerfErrorCode.IMPORT_BATCH_NOT_FOUND.getCode()).isEqualTo("PERF-40017");
        assertThat(PerfErrorCode.CYCLE_PARAM_INVALID.getCode()).isEqualTo("PERF-40019");
        assertThat(PerfErrorCode.TARGET_ADJUST_APPLY_NOT_FOUND.getCode()).isEqualTo("PERF-40020");
    }

    @Test
    void k2_409xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.METRIC_CODE_DUP.getCode()).isEqualTo("PERF-40901");
        assertThat(PerfErrorCode.METRIC_SLOT_CONFLICT.getCode()).isEqualTo("PERF-40901"); // 复用 K.2 编号
        assertThat(PerfErrorCode.METRIC_HAS_DOWNSTREAM_REF.getCode()).isEqualTo("PERF-40902");
        assertThat(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT.getCode()).isEqualTo("PERF-40903");
        assertThat(PerfErrorCode.TARGET_ADJUST_APPLY_RUNNING.getCode()).isEqualTo("PERF-40906");
    }

    @Test
    void k3_422xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.VALIDATION_FAILED.getCode()).isEqualTo("PERF-42200");
        assertThat(PerfErrorCode.METRIC_CALC_LOGIC_INVALID.getCode()).isEqualTo("PERF-42201");
        assertThat(PerfErrorCode.KPI_WEIGHT_SUM_INVALID.getCode()).isEqualTo("PERF-42202");
        assertThat(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID.getCode()).isEqualTo("PERF-42203");
        assertThat(PerfErrorCode.TRIAL_RUN_TIMEOUT.getCode()).isEqualTo("PERF-42205");
        assertThat(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT.getCode()).isEqualTo("PERF-42206");
        assertThat(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT.getCode()).isEqualTo("PERF-42207");
        assertThat(PerfErrorCode.EXPORT_TASK_NOT_FOUND.getCode()).isEqualTo("PERF-42208");
        assertThat(PerfErrorCode.EXPORT_TASK_OWNER_MISMATCH.getCode()).isEqualTo("PERF-42209");
        assertThat(PerfErrorCode.EXPORT_FILTER_SCOPE_VIOLATION.getCode()).isEqualTo("PERF-42210");
    }

    @Test
    void k4_500xxCodes_alignWithDoc03SectionK() {
        assertThat(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED.getCode()).isEqualTo("PERF-50002");
        assertThat(PerfErrorCode.CALC_JOB_FAILED.getCode()).isEqualTo("PERF-50007");
    }

    /** 旧的 PERF-40401/40402/40403/40406/40407/40903/40904/40905/40911/40912/40913/40914
     *  均已迁移到 §K 新编号；本测试防止历史编码被意外引用。 */
    @Test
    void deprecatedCodes_notUsedAnywhere() {
        List<String> deprecated = List.of(
            "PERF-40401","PERF-40402","PERF-40403","PERF-40406","PERF-40407",
            "PERF-40904","PERF-40905","PERF-40911","PERF-40912","PERF-40913","PERF-40914");
        for (PerfErrorCode c : PerfErrorCode.values()) {
            assertThat(deprecated)
                .as("常量 " + c.name() + " 使用了已废弃编号 " + c.getCode())
                .doesNotContain(c.getCode());
        }
    }
}
```

- [ ] **Step 2：运行失败**

```bash
cd performance-engine-center
mvn -Dtest=PerfErrorCodeTest test
```

- [ ] **Step 3：按 §K 权威清单重写 PerfErrorCode 枚举**

```java
public enum PerfErrorCode {
    // K.1 400xx
    METRIC_NOT_FOUND("PERF-40001", "指标不存在"),
    BIZ_KIND_INVALID("PERF-40002", "bizKind 参数无效"),
    KPI_SCHEME_NOT_FOUND("PERF-40003", "KPI 方案不存在"),
    TARGET_PLAN_NOT_FOUND("PERF-40004", "目标方案不存在"),
    SYS_CONTROL_VERSION_NOT_FOUND("PERF-40012", "sys_control 版本不存在"),
    ALLOC_RELATION_NOT_FOUND("PERF-40014", "分配关系记录不存在"),
    IMPORT_BATCH_NOT_FOUND("PERF-40017", "导入批次不存在"),
    CYCLE_PARAM_INVALID("PERF-40019", "周期参数不合法"),
    TARGET_ADJUST_APPLY_NOT_FOUND("PERF-40020", "目标修正申请不存在"),

    // K.2 409xx
    METRIC_CODE_DUP("PERF-40901", "指标编码已存在"),
    METRIC_SLOT_CONFLICT("PERF-40901", "指标槽位已占用"),   // 复用 40901，message 区分语义
    METRIC_HAS_DOWNSTREAM_REF("PERF-40902", "指标存在下游引用，不可删除"),
    SYS_CONTROL_VERSION_CONFLICT("PERF-40903", "sys_control 同维度同日期版本冲突"),
    TARGET_ADJUST_APPLY_RUNNING("PERF-40906", "目标修正申请流程已发起"),

    // K.3 422xx
    VALIDATION_FAILED("PERF-42200", "参数校验失败"),
    METRIC_CALC_LOGIC_INVALID("PERF-42201", "指标计算逻辑非法"),
    KPI_WEIGHT_SUM_INVALID("PERF-42202", "KPI 方案权重之和不等于 100"),
    IMPORT_COLUMN_MAPPING_INVALID("PERF-42203", "目标值导入 Excel 列映射错误"),
    TRIAL_RUN_TIMEOUT("PERF-42205", "试运行超时（30 秒）"),
    BATCH_QUERY_EXCEEDS_LIMIT("PERF-42206", "批量查询超过上限（500 条）"),
    EXPORT_ROWS_EXCEEDS_LIMIT("PERF-42207", "导出行数超过上限（200000）"),
    EXPORT_TASK_NOT_FOUND("PERF-42208", "异步导出任务不存在或已过期"),
    EXPORT_TASK_OWNER_MISMATCH("PERF-42209", "不能下载他人创建的导出任务"),
    EXPORT_FILTER_SCOPE_VIOLATION("PERF-42210", "导出过滤条件未通过 DATA_SCOPE 校验"),

    // K.4 500xx
    EXPORT_FILE_GENERATE_FAILED("PERF-50002", "导出文件生成失败"),
    CALC_JOB_FAILED("PERF-50007", "指标/KPI 计算 Job 执行失败");

    private final String code;
    private final String message;
    PerfErrorCode(String code, String message) { this.code = code; this.message = message; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
```

- [ ] **Step 4：运行通过**

```bash
mvn -Dtest=PerfErrorCodeTest test
```

预期：PASS。

- [ ] **Step 5：Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/enums/PerfErrorCodeTest.java
git commit -m "fix(perf-v1.0): PerfErrorCode 严格对齐 03 §K 权威清单"
```

---

### Task C2：补齐 V1.1/V1.2 将使用的错误码占位（严格限定 §K 范围）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/enums/PerfErrorCodeTest.java`

> **说明**：Task C1 已经把 §K 全表 25 条编码全部纳入 `PerfErrorCode` 枚举。**本任务仅做两件事**：
> 1. **审计**：Grep 实际代码，确认 V1.0 只真正抛出 §K 中编号的异常（§K.1/K.2 的部分）；V1.1/V1.2 占位编号只定义不使用。
> 2. **补齐 §K 未覆盖但 03 §A 正文有使用的业务错误码**（仅针对 C4 新发现的缺口）。
>
> **严格约束**：禁止引入 §K 未定义的编号（例如旧计划曾写过的 `PERF-50001` 在 §K 中不存在，必须删除）。若实际代码遇到 §K 未覆盖场景，通过 Task C4 反向提单更新 §K，不在此处自行扩展。

- [ ] **Step 1：Grep 当前代码实际抛出的错误码**

```
Grep pattern="PerfErrorCode\\.[A-Z_]+" path="performance-engine-center/src/main/java" output_mode="content" -n
```

记录每条 `throw new PerfException(PerfErrorCode.XXX, ...)` 的位置与编号。

- [ ] **Step 2：比对 §K 清单**

对每个 Grep 结果，查 C1 的枚举是否已覆盖（C1 完成后应全部覆盖）。

- [ ] **Step 3：写测试——全部 §K 常量存在且枚举值总数 = §K 条目数**

```java
@Test
void enumSize_equalsSectionKTotal() {
    // §K 共 25 条（K.1: 9 + K.2: 4 + K.3: 10 + K.4: 2；METRIC_SLOT_CONFLICT 复用 40901，独立常量 +1 = 26）
    assertThat(PerfErrorCode.values()).hasSize(26);
}

@Test
void noLegacyOrUndocumentedCode_exists() {
    List<String> allowedCodes = List.of(
        "PERF-40001","PERF-40002","PERF-40003","PERF-40004",
        "PERF-40012","PERF-40014","PERF-40017","PERF-40019","PERF-40020",
        "PERF-40901","PERF-40902","PERF-40903","PERF-40906",
        "PERF-42200","PERF-42201","PERF-42202","PERF-42203",
        "PERF-42205","PERF-42206","PERF-42207","PERF-42208","PERF-42209","PERF-42210",
        "PERF-50002","PERF-50007");
    for (PerfErrorCode c : PerfErrorCode.values()) {
        assertThat(allowedCodes)
            .as("常量 " + c.name() + " 编号 " + c.getCode() + " 不在 §K 授权清单")
            .contains(c.getCode());
    }
}
```

- [ ] **Step 4：若 Step 1 发现 Grep 结果中有常量在 C1 未定义，追加到枚举（遵守 §K 范围）**；若无，测试直接 PASS。

```bash
mvn -Dtest=PerfErrorCodeTest test
```

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): 审计 PerfErrorCode 枚举，锁定在 §K 范围内"
```

---

### Task C3：替换代码中硬编码错误码字符串

**Files:**
- 全模块 `Grep pattern="\"PERF-\\d{5}\"" output_mode="content"` 返回的所有文件
- Test: 无新增测试（由 C1 的 `deprecatedCodes_notUsedAnywhere` 守护）

- [ ] **Step 1：Grep 硬编码字符串**

```
Grep pattern="\"PERF-\\d{5}\"" path="performance-engine-center/src/main" output_mode="content" -n
```

- [ ] **Step 2：对每条命中，改为 `PerfErrorCode.XXX.getCode()` 常量引用**

例：`throw new PerfException("PERF-40911", ...)` → `throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ...)`。

- [ ] **Step 3：Grep 确认已无硬编码残留**

```
Grep pattern="\"PERF-\\d{5}\"" path="performance-engine-center/src/main" output_mode="files_with_matches"
```

预期：零结果。

- [ ] **Step 4：跑全模块测试回归**

```bash
cd performance-engine-center; mvn clean test
```

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): 替换硬编码错误码字符串为 PerfErrorCode 常量引用"
```

---

### Task C4：同步修复 03 §A 正文与 §K 错误码冲突

**Files:**
- Modify: `docs/modules/performance-engine-center/03-接口设计与报文.md`
  - §A.3 业务校验段落（第 155-163 行附近）：
    - "metricCode 未在 `perf_metric_def` 中存在, 否则 `PERF-40002`" → 应为 `PERF-40901`（指标编码已存在）
    - "层级引用严格匹配..., 否则 `PERF-40003`" → 应为 `PERF-42201`（指标计算逻辑非法）
    - "valSlot 在 (baseDim, valSlot) 不重复, 否则 `PERF-40004`" → 应为 `PERF-40901`（METRIC_SLOT_CONFLICT 复用 40901）
    - "Groovy 语法与沙箱校验, 否则 `PERF-42202`" → 应为 `PERF-42201`（与 §K 核对，§K 的 42202 为"KPI 方案权重之和不等于 100"）
    - "循环依赖检查, 否则 `PERF-42206`" → 应为 `PERF-42201`（§K 的 42206 为"批量查询超过上限"）
  - 同类扫描 §A.5/A.6/A.8、§B、§C、§D、§E、§F 各节正文使用的错误码，逐一核对是否与 §K 一致。

- [ ] **Step 1：定位所有冲突点**

```
Grep pattern="PERF-\\d{5}" path="docs/modules/performance-engine-center/03-接口设计与报文.md" -n output_mode="content"
```

记录每行的编号 + 语义描述，比对 §K 清单，列出不一致项。

> **实施重点提示（2026-04-22 reviewer 标注）**：
> - §A.3 第 176 行附近 `PERF-40906`（"状态从 PUBLISHED 改到 DISABLED 时存在下游引用则抛 40906"）—— 语义应为"存在下游引用"，正确编号是 `PERF-40902 METRIC_HAS_DOWNSTREAM_REF`，不是 §K 里的 `PERF-40906`（目标修正申请流程已发起）。
> - §A.6 第 248 行附近 `PERF-40906`（"存在下游引用禁止删除"）—— 同上，应改为 `PERF-40902`。
> - 这两处是 03 文档自身的语义笔误，Step 2 的对照 §K 修正必须覆盖。

- [ ] **Step 2：逐行修正 03 文档**

将每处不一致的编号替换为 §K 正确编号。修改时同时在段落末追加一行注释：
```markdown
> 错误码以 §K「错误码完整汇总」为准（2026-04-22 对齐）。
```

- [ ] **Step 3：复核**

```
Grep pattern="PERF-\\d{5}" path="docs/modules/performance-engine-center/03-接口设计与报文.md" -n
```

对照 §K 确认 100% 一致。

- [ ] **Step 4：Commit**

```bash
git add docs/modules/performance-engine-center/03-接口设计与报文.md
git commit -m "docs(perf-v1.0): 03 §A-F 正文错误码对齐 §K 权威清单"
```

---

## 阶段 D：Controller 返回值 DTO 化

### Task D1：MetricDefController 去除 entity 返回

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java:65-226`（精确行号以实际代码为准）
- Create/Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/MetricDefRespDTO.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/assembler/MetricDefAssembler.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java`

- [ ] **Step 1：写失败测试（响应体结构验证）**

```java
@Test
void list_returns_MetricDefRespDTO_notEntity() {
    String body = mockMvc.perform(get("/api/perf/metric-def/list").param("page","1").param("size","20"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    // 断言：返回字段是 DTO 约定（如 metricCode / metricName / unit / decimalPlaces / level），
    // 而不是 entity 的 createdAt / updatedAt / deleted 等内部字段
    assertThat(body).contains("\"metricCode\"").doesNotContain("\"deleted\"").doesNotContain("\"createdAt\"");
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：建 DTO + Assembler + Controller 切换**

```java
// MetricDefRespDTO.java
@Data
public class MetricDefRespDTO {
    private String id;
    private String metricCode;
    private String metricName;
    private String baseDim;
    private String level;
    private Integer valSlot;
    private String unit;
    private Integer decimalPlaces;
    private String status;
    private String description;
}
```

```java
// Assembler 增加 toRespDTO(PerfMetricDef)
```

Controller 所有返回 `PerfMetricDef` 的方法改为 `MetricDefRespDTO`（含 list/getById/create/update 返回）。

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): MetricDefController 去除 entity 泄漏，返回 MetricDefRespDTO"
```

---

### Task D2：扫描其他 Controller 同类问题并修复

**Files:**
- 全模块：`performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/*.java`

- [ ] **Step 1：Grep 识别 entity 返回**

```
Grep pattern="ResponseWrapper<Perf\\w+>|ResponseWrapper<SysControl>|ResponseWrapper<CustAllocRelation>" path="performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller" output_mode="content"
```

- [ ] **Step 2：对每个命中点：**
  - 为对应 Controller 方法写 IT 断言（同 D1 风格）
  - 运行失败
  - 建 RespDTO + Assembler 方法
  - Controller 切换返回
  - 运行通过
  - 单独 commit

- [ ] **Step 3：整体回归**

```bash
mvn clean test
```

- [ ] **Step 4：Commit（若 Step 2 已逐个 commit，则此步仅收尾）**

```bash
git commit --allow-empty -m "fix(perf-v1.0): 所有 Controller 返回值 DTO 化完成"
```

---

### Task D3：禁止 entity 返回的架构守护测试

**Files:**
- Modify: `performance-engine-center/pom.xml`（若未引入 ArchUnit，追加 test-scope 依赖）
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/arch/NoEntityInControllerArchTest.java`

- [ ] **Step 1：确认 ArchUnit 依赖**

```
Grep pattern="archunit" path="performance-engine-center/pom.xml"
```

若无命中，在 `<dependencies>` 末尾追加：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.2.1</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2：写架构测试（完整可编译代码）**

```java
package com.bank.branch.platform.performance.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.bank.branch.platform.performance")
public class NoEntityInControllerArchTest {

    private static final String ENTITY_PACKAGE_PREFIX =
        "com.bank.branch.platform.performance.entity.";

    /**
     * Controller 类中的 public 方法 **不得** 返回 entity 包下的类型，
     * 包括直接返回和作为 ResponseWrapper / PageResult 等容器的泛型参数。
     */
    @ArchTest
    public static final ArchRule controllers_shouldNot_expose_entity_types =
        methods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should(notExposeEntityInReturnType());

    private static ArchCondition<JavaMethod> notExposeEntityInReturnType() {
        return new ArchCondition<JavaMethod>("return type must not reference entity package") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // 1. 直接返回类型
                JavaClass rawReturnType = method.getRawReturnType();
                if (isEntity(rawReturnType)) {
                    events.add(SimpleConditionEvent.violated(method,
                        method.getFullName() + " 直接返回 entity: " + rawReturnType.getName()));
                    return;
                }
                // 2. 泛型参数中的类型（ResponseWrapper<PerfMetricDef> / PageResult<SysControl> 等）
                method.getReturnType().getAllInvolvedRawTypes().forEach(raw -> {
                    if (isEntity(raw)) {
                        events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 返回类型泛型参数含 entity: " + raw.getName()));
                    }
                });
            }

            private boolean isEntity(JavaClass clazz) {
                return clazz.getPackageName().startsWith(
                    "com.bank.branch.platform.performance.entity");
            }
        };
    }
}
```

- [ ] **Step 3：运行测试**

```bash
cd performance-engine-center
mvn -Dtest=NoEntityInControllerArchTest test
```

- 若 D1/D2 已经完全清理：预期 PASS（守护场景）
- 若仍有 entity 泄漏：测试报告将列出违规的方法全名 + 包路径

- [ ] **Step 4：修复残留（如 Step 3 失败）**

对每条违规，追加对应 Controller 的 DTO 化修复（复用 D1/D2 的模式），使测试转绿。

- [ ] **Step 5：Commit**

```bash
git add performance-engine-center/pom.xml \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/arch/NoEntityInControllerArchTest.java
git commit -m "test(perf-v1.0): Controller 禁止返回 entity 的 ArchUnit 守护"
```

---

## 阶段 E：PT_RESOURCE 冗余 + metric_def.status 取值统一

### Task E1：清理 10 条指向缺失端点的 PT_RESOURCE

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_0_4__perf_resource_cleanup.sql`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/migration/V1_0_4FlywayIT.java`

> 思路：**不删除**已登记的 V1.1/V1.2 资源，避免 V1.1 上线时又补回；改为**标记**为 `status='PENDING'`（或沿用 `enabled=0`，字段名以 pt_resource 实际列为准）。

- [ ] **Step 1：清点偏离**

```
Grep pattern="INSERT INTO pt_resource" path="performance-engine-center/src/main/resources/sql/V1_0_1__performance_resources.sql" output_mode="content"
```

对照实际 Controller 端点列表，人工列出 10 条（或实际数量）指向不存在 URL 的资源 id。

- [ ] **Step 2：写失败测试**

```java
@Test
void pendingResources_haveFlagDisabled() {
    List<String> ids = jdbc.queryForList(
        "SELECT id FROM pt_resource WHERE id LIKE 'P_PERF_%' AND status='PENDING'",
        String.class);
    assertThat(ids).contains("P_PERF_METRIC_EXECUTE", "P_PERF_METRIC_TRIAL_RUN",
        "P_PERF_IMPORT_UPLOAD", "P_PERF_ALLOC_ADJUST_CREATE", "P_PERF_KPI_TRIGGER",
        "P_PERF_KPI_RECALC", "P_PERF_DATA_TASK_STATUS", "P_PERF_SYS_CONTROL_ROLLBACK",
        "P_PERF_EXPORT_KPI", "P_PERF_EXPORT_ALLOC");
}
```

- [ ] **Step 3：创建 V1_0_4 脚本**

```sql
-- V1_0_4__perf_resource_cleanup.sql
UPDATE pt_resource SET status='PENDING' WHERE id IN (
  'P_PERF_METRIC_EXECUTE','P_PERF_METRIC_TRIAL_RUN','P_PERF_IMPORT_UPLOAD',
  'P_PERF_ALLOC_ADJUST_CREATE','P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC',
  'P_PERF_DATA_TASK_STATUS','P_PERF_SYS_CONTROL_ROLLBACK',
  'P_PERF_EXPORT_KPI','P_PERF_EXPORT_ALLOC'
);
```

- [ ] **Step 4：测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "fix(perf-v1.0): 10 条 V1.1/V1.2 资源标记 PENDING，避免误授权"
```

---

### Task E2：metric_def.status 取值最终决策（同步 01/03/05 三份文档）

**Files:**
- Modify: `docs/modules/performance-engine-center/01-功能规格.md` §3.2
- Modify: `docs/modules/performance-engine-center/03-接口设计与报文.md` §A.1（第 74 行 status 取值）、§A.3（若有 status 字段描述）
- Modify: `docs/modules/performance-engine-center/05-表结构DDL.md` §2.2

> 决策方向：**保留 V1.0 代码现状 `ACTIVE/DISABLED`**，理由：
> - 当前数据已写入 `ACTIVE`，切换为 DRAFT/PUBLISHED/DISABLED 需要数据迁移 + 业务上线发布态概念（V1.1 才真正有"发布"动作）
> - 03 §A.1 当前定义 `status = DRAFT/PUBLISHED/DISABLED`、05 §2.2 定义 `tinyint 1/0`、01 §3.2 定义 `DRAFT/PUBLISHED/DISABLED`——三份文档与代码现状四方冲突，本任务一次性对齐到 `ACTIVE/DISABLED`
> - V1.1 若需要 DRAFT 状态，届时通过 `V1_1_x` 脚本迁移

- [ ] **Step 1：Grep 定位三份文档的所有 status 取值描述**

```
Grep pattern="DRAFT|PUBLISHED|ACTIVE|DISABLED" path="docs/modules/performance-engine-center/01-功能规格.md" -n output_mode="content"
Grep pattern="DRAFT|PUBLISHED|ACTIVE|DISABLED" path="docs/modules/performance-engine-center/03-接口设计与报文.md" -n output_mode="content"
Grep pattern="DRAFT|PUBLISHED|ACTIVE|DISABLED|status.*tinyint" path="docs/modules/performance-engine-center/05-表结构DDL.md" -n output_mode="content"
```

- [ ] **Step 2：更新 01 §3.2**

将"状态：DRAFT/PUBLISHED/DISABLED"改为：
```markdown
**状态（V1.0）：`ACTIVE` / `DISABLED`。**
> V1.1 将引入 DRAFT → PUBLISHED 的发布流，届时枚举扩展为 `DRAFT/ACTIVE/DISABLED`。
```

- [ ] **Step 3：更新 03 §A.1 Query 参数表**

将第 74 行：
```
| status | String | 否 | `DRAFT` / `PUBLISHED` / `DISABLED` |
```
改为：
```
| status | String | 否 | `ACTIVE` / `DISABLED`（V1.0；V1.1 将扩展 DRAFT） |
```

响应 JSON 示例中 `"status": "PUBLISHED"` 改为 `"status": "ACTIVE"`。同步扫描 §A.3/§A.4/§A.8 请求体字段表。

- [ ] **Step 4：更新 05 §2.2**

将 `status tinyint(1)` 改为 `status varchar(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED'`。

- [ ] **Step 5：同步字典项 V1_0_2**

若 `sys_dict_item` 中已注册 DRAFT/PUBLISHED 字典项，追加 V1_0_4 脚本标记 PENDING：
```sql
UPDATE sys_dict_item SET status='PENDING'
 WHERE dict_code='PERF_METRIC_STATUS' AND item_code IN ('DRAFT','PUBLISHED');
```

- [ ] **Step 6：Commit**

```bash
git add docs/modules/performance-engine-center/01-功能规格.md \
        docs/modules/performance-engine-center/03-接口设计与报文.md \
        docs/modules/performance-engine-center/05-表结构DDL.md \
        performance-engine-center/src/main/resources/sql/V1_0_4__perf_resource_cleanup.sql
git commit -m "docs(perf-v1.0): 统一 metric_def.status 取值为 ACTIVE/DISABLED（01/03/05 三份同步）"
```

---

### Task E3：V1_0_4 脚本顺序回归

- [ ] **Step 1：本地全量迁移**

```bash
mysql -uroot -p123456 -e "DROP DATABASE IF EXISTS onepl_test_v104; CREATE DATABASE onepl_test_v104;"
cd bootstrap
mvn -Dflyway.url=jdbc:mysql://localhost:3306/onepl_test_v104 flyway:migrate
```

预期：V1_0_0 → V1_0_4 全部成功。

- [ ] **Step 2：全模块测试回归**

```bash
cd performance-engine-center; mvn clean test
```

- [ ] **Step 3：Commit（空提交留痕）**

```bash
git commit --allow-empty -m "chore(perf-v1.0): 阶段 E 回归通过"
```

---

## 阶段 F：@BizAuth 策略固化（用户决策：不扩展 BizType 枚举）

> **决策（2026-04-22）：** 保留 `BizType.PERF_CONFIG` 单档，**不扩展** common-security 的 BizType 枚举；查询/写入/高危的细粒度区分完全依赖 PT_RESOURCE 资源 ID + 角色绑定关系实现。
> **理由：** common-security 为跨模块共享基础设施，扩展枚举的影响面超出 performance 模块边界；PT_RESOURCE 已能支撑细粒度授权。

### Task F1：将"保留单档"写入模块 CLAUDE.md 与开发规范

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（在"关键设计原则 §7"现有段落基础上追加决策备注）
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/arch/BizAuthConsistencyArchTest.java`

- [ ] **Step 1：写失败架构测试（所有 Controller 方法 `@BizAuth` 的 bizType 必须等于 PERF_CONFIG）**

```java
@AnalyzeClasses(packages = "com.bank.branch.platform.performance.controller")
class BizAuthConsistencyArchTest {
    @ArchTest
    static final ArchRule allBizAuth_useSinglePerfConfig =
        methods()
          .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
          .and().arePublic()
          .and().areAnnotatedWith(BizAuth.class)
          .should(new ArchCondition<JavaMethod>("@BizAuth.bizType must be PERF_CONFIG") {
              @Override public void check(JavaMethod method, ConditionEvents events) {
                  BizAuth ann = method.reflect().getAnnotation(BizAuth.class);
                  if (ann.bizType() != BizType.PERF_CONFIG) {
                      events.add(SimpleConditionEvent.violated(method,
                          method.getFullName() + " 使用了非 PERF_CONFIG 的 BizType: " + ann.bizType()));
                  }
              }
          });
}
```

- [ ] **Step 2：运行测试**

若当前全部 Controller 都使用 PERF_CONFIG（整改分析报告已确认），本测试第一次就 PASS —— 这是"守护测试"场景（validates existing contract，不是红-绿-重构的 Red）。
若存在偏离，先在 Task F2 修复再回来跑 Green。

```bash
cd performance-engine-center
mvn -Dtest=BizAuthConsistencyArchTest test
```

预期：PASS。

- [ ] **Step 3：更新模块 CLAUDE.md**

在 `performance-engine-center/CLAUDE.md` §7 之后追加：

```markdown
### 7.1 BizType 单档决策（2026-04-22）

经架构评审，performance-engine-center **不扩展 common-security 的 BizType 枚举**。所有 Controller 端点统一使用 `@BizAuth(bizType = BizType.PERF_CONFIG, action = <具体动作>)`，细粒度授权通过 `@BizAuth.action` 字段 + PT_RESOURCE 资源 ID (`P_PERF_*`) + 角色-资源绑定矩阵实现：

- 查询类端点：resourceId 形如 `P_PERF_METRIC_QUERY`、`P_PERF_KPI_QUERY`（角色绑定"绩效查询员"）
- 配置类端点：resourceId 形如 `P_PERF_METRIC_CREATE`、`P_PERF_KPI_PUBLISH`（角色绑定"绩效配置员"）
- 高危端点：resourceId 形如 `P_PERF_SYS_CONTROL_ROLLBACK`、`P_PERF_METRIC_DELETE`（角色绑定"绩效管理员" + `@AuditLog(reasonRequired=true)`）

架构测试：`BizAuthConsistencyArchTest` 守护此约束，任何 Controller 若使用其它 BizType 值将 CI 失败。
```

- [ ] **Step 4：Commit**

```bash
git add performance-engine-center/CLAUDE.md \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/arch/BizAuthConsistencyArchTest.java
git commit -m "docs(perf-v1.0): 固化 BizType 单档策略 + 架构守护测试"
```

---

### Task F2：梳理 PT_RESOURCE 资源 ID 与 @BizAuth action 的对应关系

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（在 §7.1 段落后追加资源清单表）
- Verify（不改代码，仅审查）：各 Controller 的 `@BizAuth.action` 是否与 03 接口文档定义一致

> **命名规则定稿（2026-04-22）**：
> - 03 文档现有 action 命名风格（`"LIST"` / `"DETAIL"` / `"WRITE"` / `"IMPORT"` / `"EXPORT"` / `"CALC"` 等动词或动词缩写）是**权威风格**。
> - 本任务**不**追溯修改 03 文档的 action 命名，也**不**引入"去掉 P_PERF_ 前缀"的新规则。
> - 仅当发现 Controller 里的 `@BizAuth.action` 与 03 文档对同一端点声明的 action 不一致时，**以 03 文档为准修改 Controller**。
> - PT_RESOURCE 资源 ID 和 action 两套命名独立共存：资源 ID 用 `P_PERF_<RESOURCE>_<VERB>` 风格（V1_0_1 已登记）；action 用 03 文档的简短动词风格。两者通过 Controller 方法作为"桥"关联。

- [ ] **Step 1：Grep 代码中所有 @BizAuth 使用点**

```
Grep pattern="@BizAuth\\(" path="performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller" -A 2 output_mode="content"
```

对每条结果记录：`Controller.method` / 代码实际 `action` 值。

- [ ] **Step 2：Grep 03 文档中各端点的 action 声明**

```
Grep pattern="@BizAuth\\(bizType.*action" path="docs/modules/performance-engine-center/03-接口设计与报文.md" -n output_mode="content"
```

- [ ] **Step 3：比对两张清单**

生成一张"端点 → 代码 action → 03 文档 action"三列表，标记不一致行。

- [ ] **Step 4：若存在偏离，修改 Controller 代码将 action 值改为 03 文档所声明的字符串**

- [ ] **Step 5：若有修改，跑回归测试**

```bash
mvn clean test
```

- [ ] **Step 6：Commit**

若 Step 4 有修改：
```bash
git commit -m "fix(perf-v1.0): @BizAuth.action 命名对齐 03 文档声明"
```

若 Step 3 无偏离（当前已一致）：
```bash
git commit --allow-empty -m "chore(perf-v1.0): @BizAuth.action 与 03 文档一致性已核验通过"
```

---

## 收尾

### Task Z1：整改验收清单

- [ ] **Step 1：重放整改前的 Opus 分析报告**

对照 Top 5 风险清单：
1. 主键类型契约 → Task A2（03/04/05 三份文档统一到 String）✅
2. 错误码编号 → Task C1/C2/C3/C4（§K 权威重排 + 03 §A 正文同步）✅
3. 核心能力缺失 → 本计划**不处理**，归 V1.1/V1.2
4. pom 依赖 → 本计划**不处理**，归 V1.1/V1.2
5. val_slot 唯一性 → Task B2（函数索引排除软删除行）✅

DDL 字段缺失 → Task B3/B4（sys_control 补 5 字段、metric_def 补 4 字段 + 软删除）✅
Controller entity 泄漏 → Task D1/D2/D3（DTO 化 + ArchUnit 守护）✅
PT_RESOURCE 冗余 → Task E1（10 条标记 PENDING）✅
metric_def.status 冲突 → Task E2（01/03/05 统一到 ACTIVE/DISABLED）✅
@BizAuth 策略固化 → Task F1/F2（保留单档 + 架构守护 + 与 03 文档对齐）✅
undo 回滚脚本 → Task B8（V1_0_3/V1_0_4 反向脚本）✅

- [ ] **Step 2：跑全工程测试**

```bash
cd D:\Project\oneplate
mvn clean test
```

- [ ] **Step 3：更新 performance-engine-center/CLAUDE.md**

- 把"v1.2 决策：全部采用 docs/schema/ddl-performance.sql 原始结构, 不做 DDL 修改"段落改为：
  > "**V1.0 整改决策（2026-04-22）**：已通过 V1_0_3（DDL 字段与唯一键补齐）与 V1_0_4（PT_RESOURCE 冗余清理 + 字典项对齐）两批 Flyway 脚本补齐 DDL 偏离，详细过程见 `docs/superpowers/plans/2026-04-22-performance-v1.0-rectification-plan.md`。"
- 主键类型段落补充一行：指向 Task A2 已将 03/04/05 三份文档的主键类型统一修订为 `String (varchar(32))`，技术债已结清。

- [ ] **Step 4：Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(perf-v1.0): 模块 CLAUDE.md 反映整改结果与 V1_0_3/V1_0_4 迁移"
```

---

## 风险与回滚

| 风险 | 可能性 | 应对 |
|---|---|---|
| V1_0_3 `DROP INDEX uk_scope_dim_date` 在存量数据上触发唯一性冲突（存在同 scope_dim + 同 date 不同 version 的旧行） | 低 | 脚本中先跑 `SELECT COUNT(*)` 校验；若冲突，清理历史数据或先跑 `UPDATE` 合并 |
| `perf_metric_def` 加 `deleted` 后，旧查询忘记过滤产生数据泄漏 | 中 | Task B4 要求所有 Mapper SELECT 加 `AND deleted=0`，配合 Grep 检查 `SELECT.*FROM perf_metric_def` 所有出现点 |
| 错误码重排后消费方（若有）捕获旧编号失败 | 低 | V1.0 模块尚未被任何下游真正消费，窗口安全；V1.1 上线前此整改必须先完成 |
| Flyway 迁移在生产环境中耗时 | 低 | 两个 ALTER 均为 online DDL（MySQL 8 instant add column），影响极小 |

**回滚策略：** 每个 V1_0_3/V1_0_4 ALTER 在 `undo` 目录准备反向脚本（`DROP COLUMN` / 还原 UK），用于应急。
