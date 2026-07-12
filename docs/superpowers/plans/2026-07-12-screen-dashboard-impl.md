# 经营管理大屏（三级视角 + 全配置化体系）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 report-analytics-center + xanzc_frontend 交付全配置化经营管理大屏：数据源定义（宽表引导/KPI引导/自定义SQL+白名单）、参数化布局配置、5 种可视化组件、区块内钻取与跨页跳转、省分行（陕西地图）/支行/个人三级大屏。

**Architecture:** 后端在 report 模块新增 screen 子域（4 张配置表 + 查询执行引擎走 `rptReadOnlyDataSource` 只读数据源 + 表白名单直查）；前端新增 `/screen/:screenCode` 顶层全屏深色路由（渲染引擎递归渲染 region→row→block）+ 两个嵌入现有管理框架的配置后台页。

**Tech Stack:** Spring Boot 3.2.3 / MyBatis-Plus 3.5.7 / JSqlParser 4.9（复用 `SqlSafeValidator`）/ MySQL 8.0；Vue 3 + Element Plus + echarts 5（按需引入）+ vue-echarts。

**Spec:** `docs/superpowers/specs/2026-07-12-screen-dashboard-design.md`（已批准，含 4 项决策 D1~D4）

## Global Constraints（每个任务隐含遵守）

- 全部回答/注释/文档用中文，文件一律 UTF-8。
- TDD 红线：先写测试（红）→ 最简实现（绿）→ 重构；每步独立 commit，commit message 尾部加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。
- 新增 DB 访问一律 MyBatis-Plus：Mapper `extends BaseMapper<T>`，单条 CRUD 用内置方法**禁止**写 XML；实体 `@TableName` + `@TableId(type = IdType.AUTO)`；report 模块**无需** `@MapperScan`（`@Mapper` 注解 + starter 自动扫描，已被既有 `RptSavedQueryMapper` 验证）。
- Flyway 禁令：DDL/种子数据全部写成 `docs/superpowers/sql/*.sql` 手工执行（目标库 `yiti` + 测试库 `onepl_test_bootstrap`，口令均 root/djdev）。
- report 模块惯例（与根 CLAUDE.md 有偏差处以此为准，已核实源码）：
  - Controller **不用** `@AuditLog` 注解；高危操作审计在 Service 层手工调 `governance.AuditApi.log(AuditLogCmd)`（参考 `SqlProbeServiceImpl.safelyAudit`）。
  - 鉴权注解 `@BizAuth(bizType = BizType.REPORT, action = BizAction.XXX)`，可用 action：`LIST/READ/WRITE/DELETE/EXPORT/EXECUTE_SQL`。
  - `RptException` 构造器仅 `(RptErrorCode)` 与 `(RptErrorCode, Throwable)` 两个，**无**变参格式化。
  - report 不暴露 `*Api`（架构守护 `RptModuleStructureArchTest`）；所有新 Controller 公共方法必有 `@BizAuth`（守护 `RptBizAuthConsistencyArchTest` 自动覆盖）。
- 单测命名 `*Test`（surefire），集成测试 `*IT`（failsafe，`mvn verify` 触发）；测试数据前缀 `TEST_SCR_*`。
- 跨模块改动后先 `mvn clean install -DskipTests`（本计划只改 report 模块 java，正常无需；若报 ConflictingBeanDefinition 类怪错先跑它）。
- 后端跑测命令模板：`mvn test -pl report-analytics-center -Dtest=类名`；IT：`mvn verify -pl report-analytics-center -Dit.test=类名IT -Dtest=RptErrorCodeTest`（surefire 用一个快测试占位，避免全量单测拖慢）。
- 前端 dev：`cd xanzc_frontend && npm run dev`（端口 8091，`/api` 代理到 `http://localhost:18081`）；后端 dev：`cd bootstrap && mvn spring-boot:run`。
- 前端 echarts 一律按需引入（`echarts/core` + `use([...])`）；注意 `PieChart` 与 `@element-plus/icons-vue` 的 `PieChart` 图标重名，图表类重命名为 `EPie` 导入。
- 涉及金额/数值展示保留 `style_json.decimals` 位小数；日志不打印完整 SQL 参数值中的敏感数据。

## File Structure（全量新增/修改文件地图）

```
后端（report-analytics-center）
  docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql            [Task 1] 4 张表 DDL
  docs/superpowers/sql/2026-07-12-screen-dashboard-resources.sql      [Task 10] PT_RESOURCE + 菜单 + 角色绑定
  docs/superpowers/sql/2026-07-12-screen-dashboard-seed.sql           [Task 10] 种子数据源/屏/区块/点位
  src/main/java/com/bank/branch/platform/report/
    enums/RptErrorCode.java                                           [Task 2 修改] +9 错误码
    entity/RptScreenDatasource.java / RptScreen.java
           / RptScreenBlock.java / RptScreenMapPoint.java             [Task 3]
    mapper/RptScreenDatasourceMapper.java / RptScreenMapper.java
           / RptScreenBlockMapper.java / RptScreenMapPointMapper.java [Task 3]
    resources/mapper/RptScreenBlockMapper.xml                         [Task 3]（仅 countByDsId 自定义 SQL）
    support/ScreenSqlTemplate.java                                    [Task 4] #{param} 占位处理
    support/ScreenPeriodResolver.java                                 [Task 5] 周期模板→日期范围
    service/screen/ScreenQueryEngine.java                             [Task 6] 查询执行引擎（核心）
    support/ScreenMetricSlotDao.java                                  [Task 7] PERF_METRIC_DEF 槽位翻译（只读源 JDBC）
    service/screen/ScreenDatasourceService.java / impl 内嵌           [Task 7]
    service/screen/ScreenConfigService.java                           [Task 8]
    dto/req/ScreenDataReqDTO.java                                     [Task 6]（引擎先用）
    dto/resp/ScreenDataRespDTO.java                                   [Task 6]（引擎先用）
    dto/req/ScreenDatasourceSaveReqDTO.java / ScreenTryRunReqDTO.java
           / ScreenSaveReqDTO.java / ScreenBlockDTO.java
           / MapPointDTO.java                                         [Task 7/8/9 按需]
    dto/resp/ScreenDatasourceRespDTO.java / ScreenDetailRespDTO.java
           / ScreenViewRespDTO.java                                   [Task 7/8/9 按需]
    controller/ScreenDatasourceAdminController.java
              / ScreenConfigAdminController.java
              / ScreenViewController.java / ScreenDataController.java [Task 9]
  src/test/java/com/bank/branch/platform/report/
    enums/RptErrorCodeTest.java                                       [Task 2 修改]
    mapper/RptScreenMapperIT.java                                     [Task 3]
    support/ScreenSqlTemplateTest.java                                [Task 4]
    support/ScreenPeriodResolverTest.java                             [Task 5]
    service/screen/ScreenQueryEngineTest.java                         [Task 6]
    service/screen/ScreenQueryEngineIT.java                           [Task 6]
    service/screen/ScreenDatasourceServiceTest.java                   [Task 7]
    service/screen/ScreenConfigServiceTest.java                       [Task 8]
    controller/ScreenControllerIT.java                                [Task 9]

前端（xanzc_frontend）
  src/api/screen.js                                                   [Task 11]
  src/router/index.js                                                 [Task 11 修改] 顶层全屏路由 + 管理页子路由
  src/styles/screen.scss                                              [Task 12] 深色大屏主题变量与公共样式
  src/views/screen/ScreenView.vue                                     [Task 12] 全屏入口（scale 自适应）
  src/views/screen/components/ScreenRenderer.vue                      [Task 12] region→row→block 递归渲染
  src/views/screen/components/BlockContainer.vue                      [Task 12] 区块外框 + 取数 + 轮询 + 钻取状态机
  src/views/screen/components/MetricCard.vue / LineTrend.vue
        / PieShare.vue / RankList.vue / FlowStatus.vue                [Task 13] 5 组件
  src/views/screen/components/DrillTrend.vue                          [Task 13] 钻取态
  src/assets/geo/shaanxi.json                                         [Task 14] 陕西 geoJSON（新建目录）
  src/views/screen/components/MapCenter.vue                           [Task 14] 地图 + 点位
  src/views/screen/admin/Datasources.vue                              [Task 15] 数据源管理
  src/views/screen/admin/Designer.vue                                 [Task 16] 大屏设计器
```

## 核心数据契约（所有任务共享，实现时严格对齐）

**config_json 三形态**（存 `RPT_SCREEN_DATASOURCE.config_json`）：

```json
// source_kind=WIDE_TABLE（保存时后端翻译并快照 slot）
{"table":"EMP_INDEX_RESULT","subjectCol":"emp_id","subjectParam":"empId",
 "metrics":[{"metricCode":"M_0001","metricName":"存款余额","slot":3}]}
// source_kind=KPI_RESULT
{"cycleType":"MONTHLY"}
// source_kind=CUSTOM_SQL（占位参数仅允许 orgCode/empId/dateFrom/dateTo 四个）
{"sql":"SELECT org_name, cnt FROM ...WHERE org_code = #{orgCode}","dateCol":null}
```

**统一取数请求/响应**（`POST /api/screen/data`）：

```json
// ScreenDataReqDTO
{"dsId":9001,"period":"LATEST","dateFrom":null,"dateTo":null,
 "contextParams":{"orgCode":"610100","empId":"E001"}}
// ScreenDataRespDTO —— 所有组件同构消费
{"columns":["data_date","存款余额"],"rows":[["2026-07-10",123.45]]}
```

period 取值：`LATEST` / `LAST_10D` / `LAST_1M` / `LAST_6M_EOM` / `RANGE`（RANGE 时 dateFrom/dateTo 必填）。

**区块 JSON 三列**（存 `RPT_SCREEN_BLOCK`）：

```json
// bind_json
{"dsId":9001,"period":"LATEST","items":[{"col":"存款余额","label":"存款"}],
 "nameCol":"org_name","valueCol":"存款余额"}
// style_json
{"title":"全省存款","unit":"亿元","decimals":2,"refreshSec":60,"colors":[]}
// drill_json（可空 "{}"）
{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M","LAST_6M_EOM"],
 "jump":{"targetScreenCode":"SCR_BRANCH","params":{"orgCode":"$col:org_code"}}}
```

`jump.params` 取值语法：`"$col:列名"`＝从被点击行取该列值；`"$ctx:orgCode|empId"`＝从当前页面上下文透传。

---

### Task 1: 4 张配置表 DDL 脚本并在双库执行

**Files:**
- Create: `docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql`

**Interfaces:**
- Produces: 表 `RPT_SCREEN_DATASOURCE` / `RPT_SCREEN` / `RPT_SCREEN_BLOCK` / `RPT_SCREEN_MAP_POINT`（列名见 DDL，Task 3 实体字段与之一一对应）

- [ ] **Step 1: 写 DDL 脚本**（幂等，`DROP TABLE IF EXISTS` 前置，风格对齐 `docs/schema/ddl-report.sql`）

```sql
-- 经营管理大屏 4 张配置表（2026-07-12）
-- 目标库：yiti + onepl_test_bootstrap 手工执行（root/djdev）
-- 幂等：DROP 后重建（首发无存量数据；后续结构变更须另写 ALTER 脚本，禁止重跑本脚本）

DROP TABLE IF EXISTS `RPT_SCREEN_DATASOURCE`;
CREATE TABLE `RPT_SCREEN_DATASOURCE` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ds_code`         VARCHAR(64)  NOT NULL COMMENT '数据源编码（应用层保证 deleted=0 内唯一）',
  `ds_name`         VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `ds_type`         VARCHAR(20)  NOT NULL COMMENT '能力标签：TIMESERIES 时序/SINGLE 单值',
  `source_kind`     VARCHAR(20)  NOT NULL COMMENT '来源：WIDE_TABLE/KPI_RESULT/CUSTOM_SQL',
  `config_json`     TEXT         NOT NULL COMMENT '类型化配置 JSON（三形态见 spec §5）',
  `time_param_json` VARCHAR(500) DEFAULT NULL COMMENT '允许的预设周期 JSON 数组，如 ["LATEST","LAST_10D"]',
  `status`          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by`      VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_ds_code` (`ds_code`),
  KEY `idx_scr_ds_type` (`ds_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏数据源定义';

DROP TABLE IF EXISTS `RPT_SCREEN`;
CREATE TABLE `RPT_SCREEN` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_code`  VARCHAR(64)  NOT NULL COMMENT '大屏编码（应用层保证 deleted=0 内唯一）',
  `screen_name`  VARCHAR(100) NOT NULL COMMENT '大屏名称',
  `view_level`   VARCHAR(20)  NOT NULL COMMENT '视角：PROVINCE/BRANCH/PERSON',
  `theme_json`   VARCHAR(1000) DEFAULT NULL COMMENT '主题变量覆盖 JSON（一期留空）',
  `status`       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_by`   VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_code` (`screen_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏定义';

DROP TABLE IF EXISTS `RPT_SCREEN_BLOCK`;
CREATE TABLE `RPT_SCREEN_BLOCK` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`      BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `region`         VARCHAR(10) NOT NULL COMMENT '区域：LEFT/MAIN/RIGHT',
  `row_no`         INT         NOT NULL DEFAULT 1 COMMENT '区域内行号（从 1 起）',
  `col_no`         INT         NOT NULL DEFAULT 1 COMMENT '行内列号（从 1 起）',
  `width_pct`      INT         NOT NULL DEFAULT 100 COMMENT '行内宽度百分比 1~100',
  `height_pct`     INT         NOT NULL DEFAULT 100 COMMENT '区域内行高百分比 1~100（同行取首块值）',
  `component_type` VARCHAR(20) NOT NULL COMMENT 'METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS',
  `bind_json`      TEXT        NOT NULL COMMENT '数据绑定 JSON',
  `style_json`     TEXT        DEFAULT NULL COMMENT '样式 JSON',
  `drill_json`     TEXT        DEFAULT NULL COMMENT '钻取/跳转 JSON',
  `created_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_block_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏区块（布局+组件+绑定+钻取）';

DROP TABLE IF EXISTS `RPT_SCREEN_MAP_POINT`;
CREATE TABLE `RPT_SCREEN_MAP_POINT` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_code`           VARCHAR(32)   NOT NULL COMMENT '支行机构号',
  `org_name`           VARCHAR(100)  NOT NULL COMMENT '支行名称',
  `lng`                DECIMAL(10,6) NOT NULL COMMENT '经度',
  `lat`                DECIMAL(10,6) NOT NULL COMMENT '纬度',
  `target_screen_code` VARCHAR(64)   DEFAULT 'SCR_BRANCH' COMMENT '点击跳转目标屏编码',
  `status`             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scr_map_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏地图支行点位';
```

- [ ] **Step 2: 在两个库执行并验证**

```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql
mysql -uroot -pdjdev -e "SHOW TABLES LIKE 'RPT_SCREEN%'" yiti
```
Expected: 两库均无报错；最后一条输出 4 行（RPT_SCREEN / RPT_SCREEN_BLOCK / RPT_SCREEN_DATASOURCE / RPT_SCREEN_MAP_POINT）。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql
git commit -m "feat(screen): 大屏 4 张配置表 DDL（数据源/屏/区块/地图点位）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: RptErrorCode 新增 9 个 43xxx 错误码（TDD）

**Files:**
- Modify: `report-analytics-center/src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java`
- Modify: `report-analytics-center/src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java`

**Interfaces:**
- Produces: 枚举值 `SCREEN_DS_NOT_FOUND / SCREEN_DS_SQL_INVALID / SCREEN_DS_TIMESERIES_NEED_DATECOL / SCREEN_NOT_FOUND / SCREEN_BLOCK_BIND_MISMATCH / SCREEN_LAYOUT_INVALID / SCREEN_DS_IN_USE / SCREEN_DATA_QUERY_FAILED / SCREEN_DS_CONFIG_INVALID`（后续所有任务 `throw new RptException(RptErrorCode.XXX)` 使用）

- [ ] **Step 1: 改测试（红）**。打开 `RptErrorCodeTest.java`，找到总数断言（形如 `assertThat(RptErrorCode.values()).hasSize(N)`，N 为当前实际值），将 N 改为 `N + 9`；并新增一个测试方法：

```java
@Test
void screenErrorCodes_shouldExistWith43xxxPrefix() {
    // 大屏子域 8 个错误码：RPT-43001 ~ RPT-43008
    assertThat(RptErrorCode.SCREEN_DS_NOT_FOUND.getCode()).isEqualTo("RPT-43001");
    assertThat(RptErrorCode.SCREEN_DS_SQL_INVALID.getCode()).isEqualTo("RPT-43002");
    assertThat(RptErrorCode.SCREEN_DS_TIMESERIES_NEED_DATECOL.getCode()).isEqualTo("RPT-43003");
    assertThat(RptErrorCode.SCREEN_NOT_FOUND.getCode()).isEqualTo("RPT-43004");
    assertThat(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH.getCode()).isEqualTo("RPT-43005");
    assertThat(RptErrorCode.SCREEN_LAYOUT_INVALID.getCode()).isEqualTo("RPT-43006");
    assertThat(RptErrorCode.SCREEN_DS_IN_USE.getCode()).isEqualTo("RPT-43007");
    assertThat(RptErrorCode.SCREEN_DATA_QUERY_FAILED.getCode()).isEqualTo("RPT-43008");
    assertThat(RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode()).isEqualTo("RPT-43009");
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn test -pl report-analytics-center -Dtest=RptErrorCodeTest`
Expected: FAIL（编译错误：SCREEN_DS_NOT_FOUND 符号不存在）

- [ ] **Step 3: 实现（绿）**。在 `RptErrorCode.java` 的 `// 500xx 系统错误` 注释块**之前**插入（注意上一个枚举值末尾的 `,`/`;` 调整）：

```java
    // 430xx 大屏子域（8 条，2026-07-12 screen-dashboard）
    SCREEN_DS_NOT_FOUND("RPT-43001", "大屏数据源不存在"),
    SCREEN_DS_SQL_INVALID("RPT-43002", "自定义 SQL 校验不通过"),
    SCREEN_DS_TIMESERIES_NEED_DATECOL("RPT-43003", "时序型自定义 SQL 数据源必须声明日期列"),
    SCREEN_NOT_FOUND("RPT-43004", "大屏不存在"),
    SCREEN_BLOCK_BIND_MISMATCH("RPT-43005", "组件类型与数据源能力不匹配"),
    SCREEN_LAYOUT_INVALID("RPT-43006", "大屏布局配置非法"),
    SCREEN_DS_IN_USE("RPT-43007", "数据源已被大屏区块引用，不可删除"),
    SCREEN_DATA_QUERY_FAILED("RPT-43008", "大屏取数执行失败"),
    SCREEN_DS_CONFIG_INVALID("RPT-43009", "数据源配置非法"),
```
（43009 用于：宽表 metrics 为空/指标不存在/指标维度与表不匹配、KPI cycleType 非法、sourceKind 未知等配置态错误。）

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn test -pl report-analytics-center -Dtest=RptErrorCodeTest`
Expected: PASS（全部用例绿，含原有唯一性/中文消息守护）

- [ ] **Step 5: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java
git commit -m "feat(screen): RptErrorCode 新增大屏子域 9 个错误码 RPT-43001~43009

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: 实体 ×4 + Mapper ×4 + Mapper IT

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreenDatasource.java`、`RptScreen.java`、`RptScreenBlock.java`、`RptScreenMapPoint.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptScreenDatasourceMapper.java`、`RptScreenMapper.java`、`RptScreenBlockMapper.java`、`RptScreenMapPointMapper.java`
- Create: `report-analytics-center/src/main/resources/mapper/RptScreenBlockMapper.xml`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/mapper/RptScreenMapperIT.java`

**Interfaces:**
- Consumes: Task 1 的 4 张表
- Produces: 4 个实体类（字段名=表列驼峰）；`RptScreenBlockMapper.countByDsId(Long dsId): int`（Task 7 删除保护用）；其余 CRUD 全走 BaseMapper 内置 + `LambdaQueryWrapper`

- [ ] **Step 1: 写 Mapper IT（红）**

```java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大屏 4 张配置表 Mapper 集成测试（真库 onepl_test_bootstrap，事务回滚）.
 */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
@Transactional
class RptScreenMapperIT {

    @Autowired private RptScreenDatasourceMapper dsMapper;
    @Autowired private RptScreenMapper screenMapper;
    @Autowired private RptScreenBlockMapper blockMapper;
    @Autowired private RptScreenMapPointMapper pointMapper;

    @Test
    void datasource_insertAndSelect_roundTrip() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setDsCode("TEST_SCR_DS_1");
        ds.setDsName("测试数据源");
        ds.setDsType("TIMESERIES");
        ds.setSourceKind("KPI_RESULT");
        ds.setConfigJson("{\"cycleType\":\"MONTHLY\"}");
        ds.setStatus("ACTIVE");
        dsMapper.insert(ds);

        assertThat(ds.getId()).isNotNull(); // AUTO 回填
        RptScreenDatasource got = dsMapper.selectById(ds.getId());
        assertThat(got.getDsCode()).isEqualTo("TEST_SCR_DS_1");
        assertThat(got.getDeleted()).isZero();
    }

    @Test
    void screenAndBlock_insert_thenCountByDsId() {
        RptScreen s = new RptScreen();
        s.setScreenCode("TEST_SCR_S_1");
        s.setScreenName("测试屏");
        s.setViewLevel("BRANCH");
        s.setStatus("ACTIVE");
        screenMapper.insert(s);

        RptScreenBlock b = new RptScreenBlock();
        b.setScreenId(s.getId());
        b.setRegion("LEFT");
        b.setRowNo(1);
        b.setColNo(1);
        b.setWidthPct(100);
        b.setHeightPct(50);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":987654321}");
        blockMapper.insert(b);

        assertThat(blockMapper.countByDsId(987654321L)).isEqualTo(1);
        assertThat(blockMapper.countByDsId(111L)).isZero();
    }

    @Test
    void mapPoint_insertAndSelect_roundTrip() {
        RptScreenMapPoint p = new RptScreenMapPoint();
        p.setOrgCode("TEST_SCR_ORG1");
        p.setOrgName("测试支行");
        p.setLng(new BigDecimal("108.948024"));
        p.setLat(new BigDecimal("34.263161"));
        p.setTargetScreenCode("SCR_BRANCH");
        p.setStatus("ACTIVE");
        pointMapper.insert(p);

        RptScreenMapPoint got = pointMapper.selectById(p.getId());
        assertThat(got.getLng()).isEqualByComparingTo("108.948024");
    }
}
```

- [ ] **Step 2: 跑 IT 确认失败**

Run: `mvn verify -pl report-analytics-center -Dit.test=RptScreenMapperIT -Dtest=RptErrorCodeTest`
Expected: FAIL（编译错误：实体/Mapper 不存在）

- [ ] **Step 3: 写 4 个实体**（贫血模型，`@TableLogic` 仅前两个有）

`RptScreenDatasource.java`：
```java
package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN_DATASOURCE 实体 —— 大屏数据源定义.
 *
 * <p>贫血模型，字段对齐 2026-07-12-screen-dashboard-ddl.sql。
 */
@Data
@TableName("RPT_SCREEN_DATASOURCE")
public class RptScreenDatasource {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据源编码（deleted=0 内应用层唯一） */
    private String dsCode;

    /** 数据源名称 */
    private String dsName;

    /** 能力标签：TIMESERIES 时序 / SINGLE 单值 */
    private String dsType;

    /** 来源：WIDE_TABLE / KPI_RESULT / CUSTOM_SQL */
    private String sourceKind;

    /** 类型化配置 JSON（三形态见 spec §5） */
    private String configJson;

    /** 允许的预设周期 JSON 数组 */
    private String timeParamJson;

    /** ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人工号 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 逻辑删除 0否1是 */
    @TableLogic
    private Integer deleted;
}
```

`RptScreen.java`：
```java
package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN 实体 —— 大屏定义.
 */
@Data
@TableName("RPT_SCREEN")
public class RptScreen {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 大屏编码（deleted=0 内应用层唯一） */
    private String screenCode;

    /** 大屏名称 */
    private String screenName;

    /** 视角：PROVINCE / BRANCH / PERSON */
    private String viewLevel;

    /** 主题变量覆盖 JSON（一期留空） */
    private String themeJson;

    /** ACTIVE / DISABLED */
    private String status;

    /** 创建人工号 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 逻辑删除 0否1是 */
    @TableLogic
    private Integer deleted;
}
```

`RptScreenBlock.java`：
```java
package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN_BLOCK 实体 —— 大屏区块（布局 + 组件 + 绑定 + 钻取）.
 */
@Data
@TableName("RPT_SCREEN_BLOCK")
public class RptScreenBlock {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属大屏 RPT_SCREEN.id */
    private Long screenId;

    /** 区域：LEFT / MAIN / RIGHT */
    private String region;

    /** 区域内行号（从 1 起） */
    private Integer rowNo;

    /** 行内列号（从 1 起） */
    private Integer colNo;

    /** 行内宽度百分比 1~100 */
    private Integer widthPct;

    /** 区域内行高百分比 1~100（同行取首块值） */
    private Integer heightPct;

    /** METRIC_CARD / LINE_TREND / PIE_SHARE / RANK_LIST / FLOW_STATUS */
    private String componentType;

    /** 数据绑定 JSON */
    private String bindJson;

    /** 样式 JSON */
    private String styleJson;

    /** 钻取/跳转 JSON */
    private String drillJson;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
```

`RptScreenMapPoint.java`：
```java
package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RPT_SCREEN_MAP_POINT 实体 —— 大屏地图支行点位.
 */
@Data
@TableName("RPT_SCREEN_MAP_POINT")
public class RptScreenMapPoint {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 支行机构号（唯一） */
    private String orgCode;

    /** 支行名称 */
    private String orgName;

    /** 经度 */
    private BigDecimal lng;

    /** 纬度 */
    private BigDecimal lat;

    /** 点击跳转目标屏编码 */
    private String targetScreenCode;

    /** ACTIVE / DISABLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
```

- [ ] **Step 4: 写 4 个 Mapper + 1 个 XML**

`RptScreenDatasourceMapper.java` / `RptScreenMapper.java` / `RptScreenMapPointMapper.java`（三个同构，仅 BaseMapper）：
```java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大屏数据源 Mapper（CRUD 全走 MyBatis-Plus BaseMapper）.
 */
@Mapper
public interface RptScreenDatasourceMapper extends BaseMapper<RptScreenDatasource> {
}
```
（`RptScreenMapper` / `RptScreenMapPointMapper` 同款，替换实体泛型与注释即可。）

`RptScreenBlockMapper.java`：
```java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 大屏区块 Mapper.
 */
@Mapper
public interface RptScreenBlockMapper extends BaseMapper<RptScreenBlock> {

    /**
     * 统计引用某数据源的区块数（bind_json.$.dsId 命中），用于数据源删除保护.
     */
    int countByDsId(@Param("dsId") Long dsId);
}
```

`RptScreenBlockMapper.xml`（JSON_EXTRACT 属 BaseMapper 覆盖不到的自定义 SQL，落 XML 合规）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.report.mapper.RptScreenBlockMapper">

    <select id="countByDsId" resultType="int">
        SELECT COUNT(*)
          FROM RPT_SCREEN_BLOCK
         WHERE JSON_EXTRACT(bind_json, '$.dsId') = #{dsId}
    </select>

</mapper>
```

- [ ] **Step 5: 跑 IT 确认通过**

Run: `mvn verify -pl report-analytics-center -Dit.test=RptScreenMapperIT -Dtest=RptErrorCodeTest`
Expected: PASS（3 个 IT 全绿；若报表不存在，先确认 Task 1 Step 2 已在 onepl_test_bootstrap 执行）

- [ ] **Step 6: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreen*.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptScreen*.java \
        report-analytics-center/src/main/resources/mapper/RptScreenBlockMapper.xml \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/mapper/RptScreenMapperIT.java
git commit -m "feat(screen): 大屏 4 实体 + 4 Mapper（MyBatis-Plus）+ Mapper IT

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: ScreenSqlTemplate 占位参数工具（TDD）

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/support/ScreenSqlTemplate.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/support/ScreenSqlTemplateTest.java`

**Interfaces:**
- Consumes: Task 2 的 `RptErrorCode.SCREEN_DS_SQL_INVALID`
- Produces: `ScreenSqlTemplate.parse(String template): Parsed`（`record Parsed(String jdbcSql, List<String> paramNames)`，`#{x}` → `?`，参数名按出现顺序可重复）；`ScreenSqlTemplate.toValidatable(String template): String`（`#{x}` → `'1'`，供 JSqlParser 校验）；常量 `ALLOWED_PARAMS = Set.of("orgCode","empId","dateFrom","dateTo")`

- [ ] **Step 1: 写测试（红）**

```java
package com.bank.branch.platform.report.support;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScreenSqlTemplate 占位参数工具单测.
 */
class ScreenSqlTemplateTest {

    @Test
    void parse_replacesPlaceholdersInOrder() {
        ScreenSqlTemplate.Parsed p = ScreenSqlTemplate.parse(
                "SELECT a FROM t WHERE org = #{orgCode} AND d BETWEEN #{dateFrom} AND #{dateTo}");
        assertThat(p.jdbcSql()).isEqualTo("SELECT a FROM t WHERE org = ? AND d BETWEEN ? AND ?");
        assertThat(p.paramNames()).containsExactly("orgCode", "dateFrom", "dateTo");
    }

    @Test
    void parse_repeatedPlaceholder_collectedTwice() {
        ScreenSqlTemplate.Parsed p = ScreenSqlTemplate.parse(
                "SELECT * FROM t WHERE a = #{empId} OR b = #{empId}");
        assertThat(p.jdbcSql()).isEqualTo("SELECT * FROM t WHERE a = ? OR b = ?");
        assertThat(p.paramNames()).containsExactly("empId", "empId");
    }

    @Test
    void parse_illegalParamName_throws43002() {
        assertThatThrownBy(() -> ScreenSqlTemplate.parse("SELECT * FROM t WHERE a = #{evil}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void parse_noPlaceholder_passthrough() {
        ScreenSqlTemplate.Parsed p = ScreenSqlTemplate.parse("SELECT 1 FROM DUAL");
        assertThat(p.jdbcSql()).isEqualTo("SELECT 1 FROM DUAL");
        assertThat(p.paramNames()).isEmpty();
    }

    @Test
    void toValidatable_substitutesLiteral() {
        String v = ScreenSqlTemplate.toValidatable("SELECT a FROM t WHERE org = #{orgCode}");
        assertThat(v).isEqualTo("SELECT a FROM t WHERE org = '1'");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenSqlTemplateTest`
Expected: FAIL（编译错误：ScreenSqlTemplate 不存在）

- [ ] **Step 3: 最简实现（绿）**

```java
package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大屏自定义 SQL 模板占位参数工具.
 *
 * <p>占位语法 {@code #{name}}，仅允许 orgCode / empId / dateFrom / dateTo 四个参数名；
 * {@link #parse} 产出 JDBC 可执行 SQL（? 占位）与按出现顺序的参数名列表（可重复）；
 * {@link #toValidatable} 将占位替换为常量字面量 '1'，供 JSqlParser 白名单校验使用
 * （校验与执行分离：先校验 substitute 后的静态 SQL，再以 PreparedStatement 绑定真实参数，杜绝拼接注入）。
 */
public final class ScreenSqlTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(\\w+)\\}");

    /** 允许的占位参数名（超出即 RPT-43002 拒绝） */
    public static final Set<String> ALLOWED_PARAMS = Set.of("orgCode", "empId", "dateFrom", "dateTo");

    private ScreenSqlTemplate() {
    }

    /** 解析结果：jdbcSql 为 ? 占位 SQL，paramNames 按出现顺序（同名重复出现则重复收集） */
    public record Parsed(String jdbcSql, List<String> paramNames) {
    }

    /** 模板 → JDBC SQL + 有序参数名 */
    public static Parsed parse(String template) {
        List<String> names = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            String name = m.group(1);
            if (!ALLOWED_PARAMS.contains(name)) {
                throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID);
            }
            names.add(name);
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        return new Parsed(sb.toString(), names);
    }

    /** 模板 → 可静态校验 SQL（占位替换为 '1'） */
    public static String toValidatable(String template) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            if (!ALLOWED_PARAMS.contains(m.group(1))) {
                throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID);
            }
            m.appendReplacement(sb, "'1'");
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenSqlTemplateTest`
Expected: PASS（5 case 全绿）

- [ ] **Step 5: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/support/ScreenSqlTemplate.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/support/ScreenSqlTemplateTest.java
git commit -m "feat(screen): 自定义 SQL 占位参数模板工具（#{param} → ?/'1'）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: ScreenPeriodResolver 周期模板解析（TDD）

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/support/ScreenPeriodResolver.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/support/ScreenPeriodResolverTest.java`

**Interfaces:**
- Consumes: `RptErrorCode.SCREEN_DATA_QUERY_FAILED`
- Produces: `ScreenPeriodResolver.resolve(String period, String dateFrom, String dateTo, LocalDate today): ResolvedPeriod`；`record ResolvedPeriod(LocalDate from, LocalDate to, boolean latestOnly, boolean eomOnly)`。语义：period 空/`LATEST` → `latestOnly=true` 且 from=to=today（供自定义 SQL 的 dateFrom/dateTo 占位取值）；`LAST_10D` → [today-9, today]；`LAST_1M` → [today-1月, today]；`LAST_6M_EOM` → [today-6月的月初, today] 且 `eomOnly=true`；`RANGE` → 取入参 dateFrom/dateTo（必填、from≤to）；其余值 → RPT-43008

- [ ] **Step 1: 写测试（红）**

```java
package com.bank.branch.platform.report.support;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScreenPeriodResolver 周期解析单测（固定 today=2026-07-12 消除时间不确定性）.
 */
class ScreenPeriodResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    @Test
    void resolve_nullOrLatest_latestOnly() {
        var p1 = ScreenPeriodResolver.resolve(null, null, null, TODAY);
        assertThat(p1.latestOnly()).isTrue();
        assertThat(p1.from()).isEqualTo(TODAY);
        assertThat(p1.to()).isEqualTo(TODAY);
        var p2 = ScreenPeriodResolver.resolve("LATEST", null, null, TODAY);
        assertThat(p2.latestOnly()).isTrue();
    }

    @Test
    void resolve_last10d_nineDaysBack() {
        var p = ScreenPeriodResolver.resolve("LAST_10D", null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(p.to()).isEqualTo(TODAY);
        assertThat(p.latestOnly()).isFalse();
        assertThat(p.eomOnly()).isFalse();
    }

    @Test
    void resolve_last1m_oneMonthBack() {
        var p = ScreenPeriodResolver.resolve("LAST_1M", null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(p.to()).isEqualTo(TODAY);
    }

    @Test
    void resolve_last6mEom_firstDayOfMonthSixMonthsBack_eomOnly() {
        var p = ScreenPeriodResolver.resolve("LAST_6M_EOM", null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(p.to()).isEqualTo(TODAY);
        assertThat(p.eomOnly()).isTrue();
    }

    @Test
    void resolve_range_usesGivenDates() {
        var p = ScreenPeriodResolver.resolve("RANGE", "2026-01-01", "2026-03-31", TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void resolve_rangeMissingOrInverted_throws43008() {
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("RANGE", null, "2026-01-01", TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("RANGE", "2026-02-01", "2026-01-01", TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("RANGE", "bad-date", "2026-01-01", TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
    }

    @Test
    void resolve_unknownPeriod_throws43008() {
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("LAST_100Y", null, null, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenPeriodResolverTest`
Expected: FAIL（编译错误：ScreenPeriodResolver 不存在）

- [ ] **Step 3: 最简实现（绿）**

```java
package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 大屏预设周期模板解析器.
 *
 * <p>把 period（LATEST/LAST_10D/LAST_1M/LAST_6M_EOM/RANGE）解析为日期范围：
 * latestOnly=true 时宽表/KPI 走"最新一条"查询形态（from/to 仍填 today，供自定义 SQL 占位取值）；
 * eomOnly=true 时宽表追加"仅月末时点"过滤。
 */
public final class ScreenPeriodResolver {

    private ScreenPeriodResolver() {
    }

    /** 解析结果 */
    public record ResolvedPeriod(LocalDate from, LocalDate to, boolean latestOnly, boolean eomOnly) {
    }

    public static ResolvedPeriod resolve(String period, String dateFrom, String dateTo, LocalDate today) {
        String p = (period == null || period.isBlank()) ? "LATEST" : period;
        switch (p) {
            case "LATEST":
                return new ResolvedPeriod(today, today, true, false);
            case "LAST_10D":
                return new ResolvedPeriod(today.minusDays(9), today, false, false);
            case "LAST_1M":
                return new ResolvedPeriod(today.minusMonths(1), today, false, false);
            case "LAST_6M_EOM":
                return new ResolvedPeriod(today.minusMonths(6).withDayOfMonth(1), today, false, true);
            case "RANGE":
                if (dateFrom == null || dateTo == null) {
                    throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED);
                }
                LocalDate f;
                LocalDate t;
                try {
                    f = LocalDate.parse(dateFrom);
                    t = LocalDate.parse(dateTo);
                } catch (DateTimeParseException e) {
                    throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
                }
                if (f.isAfter(t)) {
                    throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED);
                }
                return new ResolvedPeriod(f, t, false, false);
            default:
                throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED);
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenPeriodResolverTest`
Expected: PASS（7 case 全绿）

- [ ] **Step 5: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/support/ScreenPeriodResolver.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/support/ScreenPeriodResolverTest.java
git commit -m "feat(screen): 预设周期模板解析器（LATEST/近10天/近1月/近6个月末/自定义范围）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: ScreenQueryEngine 查询执行引擎（TDD：SQL 构造单测 + 真库执行 IT）

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenDataReqDTO.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenDataRespDTO.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngine.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngineTest.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngineIT.java`

**Interfaces:**
- Consumes: Task 3 实体 `RptScreenDatasource`；Task 4 `ScreenSqlTemplate`；Task 5 `ScreenPeriodResolver`；既有 `SqlSafeValidator`（**内部 `new`，不注册 Bean**——避免与 SQL 探查的 `SqlSafeValidator` Bean 按类型注入冲突）；`rptReadOnlyDataSource`
- Produces（Task 7/9 依赖的精确签名）:
  - `ScreenDataRespDTO query(RptScreenDatasource ds, ScreenDataReqDTO req)`
  - `ScreenDataRespDTO tryRun(String sourceKind, String configJson, ScreenDataReqDTO req)`（LIMIT 10）
  - `void validateCustomSql(String sqlTemplate)`（校验失败抛 RPT-43002）
  - DTO：`ScreenDataReqDTO{Long dsId; String period; String dateFrom; String dateTo; Map<String,String> contextParams}`、`ScreenDataRespDTO{List<String> columns; List<List<Object>> rows}`

- [ ] **Step 1: 写两个 DTO**（无逻辑，直接建）

`dto/req/ScreenDataReqDTO.java`：
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 大屏统一取数请求.
 */
@Data
public class ScreenDataReqDTO {

    /** 数据源 ID（/api/screen/data 必填；try-run 场景不使用） */
    @NotNull
    private Long dsId;

    /** 预设周期：LATEST/LAST_10D/LAST_1M/LAST_6M_EOM/RANGE（空=LATEST） */
    private String period;

    /** RANGE 时必填 yyyy-MM-dd */
    private String dateFrom;

    /** RANGE 时必填 yyyy-MM-dd */
    private String dateTo;

    /** 上下文参数：orgCode / empId（大屏路由参数透传） */
    private Map<String, String> contextParams;
}
```

`dto/resp/ScreenDataRespDTO.java`：
```java
package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大屏统一取数响应（所有可视化组件同构消费的二维结构）.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenDataRespDTO {

    /** 列名（宽表指标列为指标名别名） */
    private List<String> columns;

    /** 数据行（值为 Number/String，日期已转字符串） */
    private List<List<Object>> rows;
}
```

- [ ] **Step 2: 写 SQL 构造单测（红）**——引擎构造器传 `null` DataSource，只测包级 `build` 方法

```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScreenQueryEngine SQL 构造纯逻辑单测（不触库，DataSource 传 null）.
 */
class ScreenQueryEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    private final ScreenQueryEngine engine = new ScreenQueryEngine(
            null,
            List.of("EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT", "KPI_RESULT",
                    "SYS_CONTROL", "EXT_ORG_INFO", "ACT_RU_TASK"),
            List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT"),
            1000);

    private ScreenDataReqDTO req(String period, Map<String, String> ctx) {
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod(period);
        r.setContextParams(ctx);
        return r;
    }

    // ===== WIDE_TABLE =====

    @Test
    void buildWide_latest_ordersByDateDescLimit1_withAliasAndVersion() {
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("FROM EMP_INDEX_RESULT");
        assertThat(q.sql()).contains("val_3 AS `存款余额`");
        assertThat(q.sql()).contains("emp_id = ?");
        assertThat(q.sql()).contains("COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = 'EMP'");
        assertThat(q.sql()).endsWith("ORDER BY data_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001");
    }

    @Test
    void buildWide_last6mEom_addsEomFilterAndRange() {
        String cfg = "{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\",\"subjectParam\":\"orgCode\","
                + "\"metrics\":[{\"metricCode\":\"M_0002\",\"metricName\":\"贷款余额\",\"slot\":7}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LAST_6M_EOM", Map.of("orgCode", "610100")), 1000, TODAY);
        assertThat(q.sql()).contains("data_date BETWEEN ? AND ?");
        assertThat(q.sql()).contains("data_date = LAST_DAY(data_date)");
        assertThat(q.sql()).endsWith("ORDER BY data_date LIMIT 1000");
        assertThat(q.params()).containsExactly("610100", LocalDate.of(2026, 1, 1), TODAY);
    }

    @Test
    void buildWide_tableNotAllowed_throws43009() {
        String cfg = "{\"table\":\"SECRET_T\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"x\",\"slot\":1}]}";
        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWide_missingContextParam_throws43008() {
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"x\",\"slot\":1}]}";
        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
    }

    // ===== KPI_RESULT =====

    @Test
    void buildKpi_latest_rowNumberDedupByAsOfDate() {
        var q = engine.build("KPI_RESULT", "{\"cycleType\":\"MONTHLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC)");
        assertThat(q.sql()).contains("FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?");
        assertThat(q.sql()).endsWith("ORDER BY cycle_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001", "MONTHLY");
    }

    @Test
    void buildKpi_illegalCycleType_throws43009() {
        assertThatThrownBy(() -> engine.build("KPI_RESULT", "{\"cycleType\":\"HOURLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    // ===== CUSTOM_SQL =====

    @Test
    void buildCustom_placeholdersBoundInOrder_andWrappedWithLimit() {
        String cfg = "{\"sql\":\"SELECT org_code, COUNT(*) AS cnt FROM ACT_RU_TASK"
                + " WHERE tenant_id_ = #{orgCode} AND create_time_ BETWEEN #{dateFrom} AND #{dateTo}"
                + " GROUP BY org_code\",\"dateCol\":null}";
        var q = engine.build("CUSTOM_SQL", cfg,
                req("LAST_10D", Map.of("orgCode", "610100")), 10, TODAY);
        assertThat(q.sql()).startsWith("SELECT * FROM (");
        assertThat(q.sql()).endsWith(") rpt_scr_q LIMIT 10");
        assertThat(q.sql()).doesNotContain("#{");
        assertThat(q.params()).containsExactly("610100", LocalDate.of(2026, 7, 3), TODAY);
    }

    @Test
    void buildCustom_tableNotWhitelisted_throws43002() {
        String cfg = "{\"sql\":\"SELECT * FROM PT_USER\",\"dateCol\":null}";
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void buildCustom_missingContextParam_throws43008() {
        String cfg = "{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK WHERE assignee_ = #{empId}\",\"dateCol\":null}";
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
    }

    @Test
    void build_unknownSourceKind_throws43009() {
        assertThatThrownBy(() -> engine.build("MAGIC", "{}", req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }
}
```

- [ ] **Step 3: 跑单测确认失败**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenQueryEngineTest`
Expected: FAIL（编译错误：ScreenQueryEngine 不存在）

- [ ] **Step 4: 实现 ScreenQueryEngine（绿）**

```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.support.ScreenPeriodResolver;
import com.bank.branch.platform.report.support.ScreenPeriodResolver.ResolvedPeriod;
import com.bank.branch.platform.report.support.ScreenSqlTemplate;
import com.bank.branch.platform.report.support.SqlSafeValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大屏查询执行引擎（核心）.
 *
 * <p>三类数据源统一出口：宽表引导式（EMP/ORG/CUST_INDEX_RESULT，按 SYS_CONTROL 当前版本 + 周期过滤）、
 * KPI 结果引导式（KPI_RESULT 行式表，每周期取 as_of_date 最新一条）、自定义 SQL
 * （#{param} 占位 → PreparedStatement 绑定，JSqlParser 白名单校验，外层强制 LIMIT）。
 * 全部跑在 rptReadOnlyDataSource 只读数据源上，query timeout 5s。
 *
 * <p>SqlSafeValidator 在此内部 new（不注册 Bean），避免与 SQL 探查的同类型 Bean 注入冲突。
 */
@Slf4j
@Service
public class ScreenQueryEngine {

    /** 查询超时（秒），大屏接口要求快速失败 */
    private static final int QUERY_TIMEOUT_SEC = 5;

    /** 宽表引导式允许的表 → [主体列, 上下文参数名, SYS_CONTROL.scope_dim] */
    private static final Map<String, String[]> WIDE_TABLES = Map.of(
            "EMP_INDEX_RESULT", new String[]{"emp_id", "empId", "EMP"},
            "ORG_INDEX_RESULT", new String[]{"org_code", "orgCode", "ORG"},
            "CUST_INDEX_RESULT", new String[]{"cust_no", "custNo", "CUST"});

    private static final Set<String> KPI_CYCLE_TYPES = Set.of("MONTHLY", "QUARTERLY");

    private final DataSource readOnlyDataSource;
    private final SqlSafeValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxRows;

    public ScreenQueryEngine(
            @Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource,
            @Value("#{'${rpt.screen.whitelist-tables:EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,"
                    + "KPI_RESULT,PERF_METRIC_DEF,PERF_TARGET_VALUE,PERF_TARGET_PLAN,SYS_CONTROL,EXT_ORG_INFO,"
                    + "ADDRBOOK_EMPLOYEE,TOUCH_TASK,TOUCH_LOG,ACT_RU_TASK,ACT_HI_PROCINST,BIZ_PROCESS_MAP}'"
                    + ".split(',')}") List<String> whitelistTables,
            @Value("#{'${rpt.screen.forbidden-keywords:DROP,DELETE,UPDATE,INSERT,TRUNCATE,ALTER,CREATE,RENAME,"
                    + "REPLACE,GRANT,REVOKE,LOCK,UNLOCK,CALL,EXEC,EXECUTE,LOAD,SHUTDOWN,USE,DESCRIBE,EXPLAIN,SHOW,"
                    + "COMMIT,ROLLBACK,SAVEPOINT,DECLARE,HANDLER,SIGNAL,RESIGNAL}'.split(',')}")
                    List<String> forbiddenKeywords,
            @Value("${rpt.screen.max-rows:1000}") int maxRows) {
        this.readOnlyDataSource = readOnlyDataSource;
        this.maxRows = maxRows;
        this.validator = new SqlSafeValidator(whitelistTables, forbiddenKeywords, maxRows, 8000, 3);
    }

    /** 校验自定义 SQL 模板（保存/试跑/每次执行都调用），失败抛 RPT-43002 */
    public void validateCustomSql(String sqlTemplate) {
        try {
            validator.validateAndNormalize(ScreenSqlTemplate.toValidatable(sqlTemplate));
        } catch (RptException e) {
            throw e;
        } catch (BizException e) {
            // SqlSafeValidator 抛的 RPT-42xxx 统一收敛为大屏语义的 43002（保留原因链）
            throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID, e);
        }
    }

    /** 正式取数（LIMIT = maxRows） */
    public ScreenDataRespDTO query(RptScreenDatasource ds, ScreenDataReqDTO req) {
        BuiltQuery q = build(ds.getSourceKind(), ds.getConfigJson(), req, maxRows, LocalDate.now());
        return execute(q);
    }

    /** 配置态试跑（LIMIT = 10） */
    public ScreenDataRespDTO tryRun(String sourceKind, String configJson, ScreenDataReqDTO req) {
        BuiltQuery q = build(sourceKind, configJson, req, 10, LocalDate.now());
        return execute(q);
    }

    /** 构造结果：最终 SQL（已含 LIMIT）+ 有序绑定参数 */
    record BuiltQuery(String sql, List<Object> params) {
    }

    /** 包级可见，供单测注入固定 today */
    BuiltQuery build(String sourceKind, String configJson, ScreenDataReqDTO req, int limit, LocalDate today) {
        JsonNode cfg = readConfig(configJson);
        return switch (sourceKind == null ? "" : sourceKind) {
            case "WIDE_TABLE" -> buildWideTableQuery(cfg, req, limit, today);
            case "KPI_RESULT" -> buildKpiQuery(cfg, req, limit, today);
            case "CUSTOM_SQL" -> buildCustomQuery(cfg, req, limit, today);
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    private JsonNode readConfig(String configJson) {
        try {
            return objectMapper.readTree(configJson == null ? "{}" : configJson);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, e);
        }
    }

    private BuiltQuery buildWideTableQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String table = cfg.path("table").asText();
        String[] meta = WIDE_TABLES.get(table);
        if (meta == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        JsonNode metrics = cfg.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String subjectVal = ctxParam(req, meta[1]);

        StringBuilder cols = new StringBuilder("data_date");
        for (JsonNode m : metrics) {
            int slot = m.path("slot").asInt();
            if (slot < 1 || slot > 400) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            // 别名用指标名（剔除可破坏反引号包裹的字符），前端直接以列名展示
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            cols.append(", val_").append(slot).append(" AS `").append(alias).append("`");
        }

        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM ").append(table)
                .append(" WHERE ").append(meta[0]).append(" = ?")
                .append(" AND version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = '")
                .append(meta[2]).append("' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')");
        List<Object> params = new ArrayList<>();
        params.add(subjectVal);
        if (p.latestOnly()) {
            sql.append(" ORDER BY data_date DESC LIMIT 1");
        } else {
            sql.append(" AND data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            if (p.eomOnly()) {
                sql.append(" AND data_date = LAST_DAY(data_date)");
            }
            sql.append(" ORDER BY data_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    private BuiltQuery buildKpiQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String cycleType = cfg.path("cycleType").asText("MONTHLY");
        if (!KPI_CYCLE_TYPES.contains(cycleType)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String empId = ctxParam(req, "empId");
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        // 每个周期日取 as_of_date 最新一次计算结果
        StringBuilder sql = new StringBuilder(
                "SELECT cycle_date AS data_date, kpi_total_score AS `KPI总分` FROM ("
                        + "SELECT cycle_date, kpi_total_score, "
                        + "ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC) rn "
                        + "FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?) t WHERE rn = 1");
        List<Object> params = new ArrayList<>(List.of(empId, cycleType));
        if (p.latestOnly()) {
            sql.append(" ORDER BY cycle_date DESC LIMIT 1");
        } else {
            sql.append(" AND cycle_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            sql.append(" ORDER BY cycle_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    private BuiltQuery buildCustomQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String template = cfg.path("sql").asText();
        validateCustomSql(template);
        ScreenSqlTemplate.Parsed parsed = ScreenSqlTemplate.parse(template);
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        Map<String, Object> vals = new HashMap<>();
        vals.put("orgCode", req.getContextParams() == null ? null : req.getContextParams().get("orgCode"));
        vals.put("empId", req.getContextParams() == null ? null : req.getContextParams().get("empId"));
        vals.put("dateFrom", p.from());
        vals.put("dateTo", p.to());

        List<Object> params = new ArrayList<>();
        for (String name : parsed.paramNames()) {
            Object v = vals.get(name);
            if (v == null) {
                // SQL 用到了 orgCode/empId 占位但上下文没传 → 快速失败
                throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED);
            }
            params.add(v);
        }
        String sql = "SELECT * FROM (" + parsed.jdbcSql() + ") rpt_scr_q LIMIT " + limit;
        return new BuiltQuery(sql, params);
    }

    private String ctxParam(ScreenDataReqDTO req, String name) {
        String v = req.getContextParams() == null ? null : req.getContextParams().get(name);
        if (v == null || v.isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED);
        }
        return v;
    }

    private ScreenDataRespDTO execute(BuiltQuery q) {
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(q.sql())) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            for (int i = 0; i < q.params().size(); i++) {
                Object v = q.params().get(i);
                if (v instanceof LocalDate d) {
                    stmt.setObject(i + 1, java.sql.Date.valueOf(d));
                } else {
                    stmt.setObject(i + 1, v);
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    columns.add(md.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                return new ScreenDataRespDTO(columns, rows);
            }
        } catch (SQLException e) {
            log.warn("[ScreenQueryEngine] 取数失败 sql={} cause={}", q.sql(), e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }

    /** JDBC 值 → JSON 友好值（日期/时间戳转字符串，数值保持原样） */
    private Object normalize(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        return v;
    }
}
```

注意：`SqlSafeValidator` 的真实包名以现有源码为准（`report/support/`，import 按现状写；若在其他包，调整 import 即可）。

- [ ] **Step 5: 跑单测确认通过**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenQueryEngineTest`
Expected: PASS（10 case 全绿）

- [ ] **Step 6: 写真库执行 IT（红→绿）**——注意**不能** `@Transactional` 回滚：引擎走独立只读数据源连接，看不见未提交数据；改用 `@AfterEach` 前缀清理

```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScreenQueryEngine 真库执行 IT（onepl_test_bootstrap；只读源与主源同库不同连接，
 * 故不用 @Transactional 回滚，改为 @AfterEach 按 TEST_SCR_ 前缀清理）.
 */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
class ScreenQueryEngineIT {

    @Autowired
    private ScreenQueryEngine engine;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String version;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanup();
        // 与引擎同口径取当前版本（无 SYS_CONTROL 行时回退 V1），保证插入行可被查中
        version = jdbc.queryForObject(
                "SELECT COALESCE((SELECT current_version FROM SYS_CONTROL "
                        + "WHERE scope_dim = 'EMP' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')",
                String.class);
        jdbc.update("INSERT INTO EMP_INDEX_RESULT (data_date, version, emp_id, val_1) VALUES (?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-01"), version, "TEST_SCR_E1", new BigDecimal("111.5"));
        jdbc.update("INSERT INTO EMP_INDEX_RESULT (data_date, version, emp_id, val_1) VALUES (?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-02"), version, "TEST_SCR_E1", new BigDecimal("222.5"));
    }

    @AfterEach
    void cleanup() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        jdbc.update("DELETE FROM EMP_INDEX_RESULT WHERE emp_id LIKE 'TEST_SCR_%'");
    }

    private RptScreenDatasource wideDs() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("WIDE_TABLE");
        ds.setDsType("TIMESERIES");
        ds.setConfigJson("{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_TEST\",\"metricName\":\"存款余额\",\"slot\":1}]}");
        return ds;
    }

    private ScreenDataReqDTO req(String period, String dateFrom, String dateTo) {
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod(period);
        r.setDateFrom(dateFrom);
        r.setDateTo(dateTo);
        r.setContextParams(Map.of("empId", "TEST_SCR_E1"));
        return r;
    }

    @Test
    void wideLatest_returnsNewestSingleRow() {
        ScreenDataRespDTO resp = engine.query(wideDs(), req("LATEST", null, null));
        assertThat(resp.getColumns()).containsExactly("data_date", "存款余额");
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0).get(0)).isEqualTo("2026-07-02");
        assertThat(new BigDecimal(resp.getRows().get(0).get(1).toString())).isEqualByComparingTo("222.5");
    }

    @Test
    void wideRange_returnsRowsAscending() {
        ScreenDataRespDTO resp = engine.query(wideDs(), req("RANGE", "2026-07-01", "2026-07-02"));
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0).get(0)).isEqualTo("2026-07-01");
        assertThat(resp.getRows().get(1).get(0)).isEqualTo("2026-07-02");
    }

    @Test
    void customSql_countByEmpPlaceholder_returnsSingleValue() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("CUSTOM_SQL");
        ds.setDsType("SINGLE");
        ds.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM EMP_INDEX_RESULT WHERE emp_id = #{empId}\","
                + "\"dateCol\":null}");
        ScreenDataRespDTO resp = engine.query(ds, req("LATEST", null, null));
        assertThat(resp.getColumns()).containsExactly("cnt");
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0).get(0).toString()).isEqualTo("2");
    }
}
```

- [ ] **Step 7: 跑 IT 确认通过**

Run: `mvn verify -pl report-analytics-center -Dit.test=ScreenQueryEngineIT -Dtest=ScreenQueryEngineTest`
Expected: PASS（3 IT 全绿）

- [ ] **Step 8: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenDataReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenDataRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngine.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngineTest.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngineIT.java
git commit -m "feat(screen): 大屏查询执行引擎（宽表/KPI/自定义SQL 三路 + 只读源 + 白名单）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: ScreenMetricSlotDao + ScreenDatasourceService（TDD）

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/support/ScreenMetricSlotDao.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenDatasourceSaveReqDTO.java`、`dto/req/ScreenTryRunReqDTO.java`、`dto/resp/ScreenDatasourceRespDTO.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenDatasourceService.java`（接口）、`ScreenDatasourceServiceImpl.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenDatasourceServiceTest.java`

**Interfaces:**
- Consumes: Task 3 `RptScreenDatasourceMapper`/`RptScreenBlockMapper.countByDsId`；Task 6 `ScreenQueryEngine.{validateCustomSql, tryRun, query}`；`CurrentUserApi.getCurrentEmpId()`；`AuditApi.log(AuditLogCmd)`（import 与 `SqlProbeServiceImpl` 相同）
- Produces（Task 9 Controller 依赖）:
  - `List<ScreenDatasourceRespDTO> list(String dsType, String keyword)`
  - `Long save(ScreenDatasourceSaveReqDTO req)` / `void update(Long id, ScreenDatasourceSaveReqDTO req)` / `void delete(Long id)`
  - `ScreenDataRespDTO tryRun(ScreenTryRunReqDTO req)`
  - `ScreenDataRespDTO queryData(ScreenDataReqDTO req)`（Controller 不触碰实体，规避 `RptNoEntityInControllerLocalsArchTest`）
  - `ScreenMetricSlotDao.selectByCodes(List<String>): List<MetricSlot>`；`record MetricSlot(String metricCode, String metricName, Integer valSlot, String baseDim)`

**业务规则（normalize + validate，save/update/tryRun 共用）：**
1. `WIDE_TABLE`：前端只传 `{"table":"...","metrics":[{"metricCode":"M_0001"},...]}`；服务端查 `ScreenMetricSlotDao` 校验指标存在（缺失→43009）、`base_dim` 与表匹配（EMP_INDEX_RESULT↔EMP / ORG↔ORG / CUST↔CUST，不匹配→43009）、回填 `metricName/slot` 与派生 `subjectCol/subjectParam` 重写 config_json；`dsType` **强制** TIMESERIES。
2. `KPI_RESULT`：`cycleType` ∈ MONTHLY/QUARTERLY（否则 43009）；`dsType` 强制 TIMESERIES。
3. `CUSTOM_SQL`：`engine.validateCustomSql(sql)`（失败 43002 直接透传）；`dsType=TIMESERIES` 且 `dateCol` 空 → 43003；保存/试跑手工审计（`safelyAudit`）。
4. `ds_code` 服务端生成 `"SCRDS_" + UUID 前 8 位大写`，前端不传。
5. `delete`：`countByDsId>0` → 43007；否则 `deleteById`（@TableLogic 逻辑删）。
6. `queryData`：`selectById` 为空或 `status=DISABLED` → 43001；否则委托 `engine.query`。

- [ ] **Step 1: 写单测（红）**

```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScreenDatasourceService 单测（Mockito，不起 Spring）.
 */
@ExtendWith(MockitoExtension.class)
class ScreenDatasourceServiceTest {

    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private ScreenQueryEngine engine;
    @Mock private ScreenMetricSlotDao slotDao;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private AuditApi auditApi;

    private ScreenDatasourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenDatasourceServiceImpl(dsMapper, blockMapper, engine, slotDao, currentUserApi, auditApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private ScreenDatasourceSaveReqDTO wideReq() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("员工存款");
        req.setSourceKind("WIDE_TABLE");
        req.setConfigJson("{\"table\":\"EMP_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_0001\"}]}");
        return req;
    }

    @Test
    void save_wideTable_translatesSlotsAndForcesTimeseries() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));

        service.save(wideReq());

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        RptScreenDatasource saved = cap.getValue();
        assertThat(saved.getDsType()).isEqualTo("TIMESERIES");
        assertThat(saved.getDsCode()).startsWith("SCRDS_");
        assertThat(saved.getCreatedBy()).isEqualTo("E001");
        assertThat(saved.getConfigJson()).contains("\"slot\":3");
        assertThat(saved.getConfigJson()).contains("\"metricName\":\"存款余额\"");
        assertThat(saved.getConfigJson()).contains("\"subjectParam\":\"empId\"");
    }

    @Test
    void save_wideTable_unknownMetric_throws43009() {
        when(slotDao.selectByCodes(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> service.save(wideReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_wideTable_dimMismatch_throws43009() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "ORG")));
        assertThatThrownBy(() -> service.save(wideReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpi_illegalCycleType_throws43009() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("KPI");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"HOURLY\"}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_customSql_timeseriesWithoutDateCol_throws43003() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("趋势SQL");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("TIMESERIES");
        req.setConfigJson("{\"sql\":\"SELECT 1\",\"dateCol\":null}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43003");
    }

    @Test
    void save_customSql_invalidSql_propagates43002() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("坏SQL");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT * FROM PT_USER\",\"dateCol\":null}");
        doThrow(new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID))
                .when(engine).validateCustomSql("SELECT * FROM PT_USER");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void save_customSql_ok_audits() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("流程数");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setReason("配置大屏流程组件");

        service.save(req);

        verify(dsMapper).insert(any(RptScreenDatasource.class));
        verify(auditApi).log(any());
    }

    @Test
    void delete_referenced_throws43007() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(2);
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43007");
        verify(dsMapper, never()).deleteById(9L);
    }

    @Test
    void delete_unreferenced_logicDeletes() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(0);
        service.delete(9L);
        verify(dsMapper).deleteById(9L);
    }

    @Test
    void queryData_notFoundOrDisabled_throws43001() {
        when(dsMapper.selectById(1L)).thenReturn(null);
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setDsId(1L);
        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");

        RptScreenDatasource disabled = new RptScreenDatasource();
        disabled.setId(2L);
        disabled.setStatus("DISABLED");
        when(dsMapper.selectById(2L)).thenReturn(disabled);
        req.setDsId(2L);
        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
    }

    @Test
    void tryRun_delegatesToEngineWithLimit10_andAudits() {
        ScreenTryRunReqDTO req = new ScreenTryRunReqDTO();
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setContextParams(Map.of("orgCode", "610100"));
        req.setReason("试跑");
        when(engine.tryRun(any(), any(), any())).thenReturn(new ScreenDataRespDTO(List.of("cnt"), List.of()));

        ScreenDataRespDTO resp = service.tryRun(req);

        assertThat(resp.getColumns()).containsExactly("cnt");
        verify(engine).tryRun(any(), any(), any());
        verify(auditApi).log(any());
    }
}
```

- [ ] **Step 2: 跑单测确认失败**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenDatasourceServiceTest`
Expected: FAIL（编译错误：Impl/DTO/Dao 不存在）

- [ ] **Step 3: 写 3 个 DTO**

`dto/req/ScreenDatasourceSaveReqDTO.java`：
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 大屏数据源保存/更新请求.
 */
@Data
public class ScreenDatasourceSaveReqDTO {

    /** 名称 */
    @NotBlank
    private String dsName;

    /** TIMESERIES/SINGLE；WIDE_TABLE/KPI_RESULT 会被服务端强制 TIMESERIES */
    private String dsType;

    /** WIDE_TABLE / KPI_RESULT / CUSTOM_SQL */
    @NotBlank
    private String sourceKind;

    /** 类型化配置 JSON（三形态见计划头部契约） */
    @NotBlank
    private String configJson;

    /** 允许的预设周期 JSON 数组 */
    private String timeParamJson;

    /** ACTIVE / DISABLED（空=ACTIVE） */
    private String status;

    /** 备注 */
    private String remark;

    /** CUSTOM_SQL 高危保存的审计原因 */
    private String reason;
}
```

`dto/req/ScreenTryRunReqDTO.java`：
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 数据源配置态试跑请求（未落库前预览 10 行）.
 */
@Data
public class ScreenTryRunReqDTO {

    /** WIDE_TABLE / KPI_RESULT / CUSTOM_SQL */
    @NotBlank
    private String sourceKind;

    /** TIMESERIES / SINGLE */
    private String dsType;

    /** 类型化配置 JSON；WIDE_TABLE 试跑须已含 metrics[].slot（前端先保存再试跑，或由列表带出） */
    @NotBlank
    private String configJson;

    /** 预设周期 */
    private String period;

    private String dateFrom;

    private String dateTo;

    /** orgCode / empId */
    private Map<String, String> contextParams;

    /** 审计原因（高危） */
    private String reason;
}
```

`dto/resp/ScreenDatasourceRespDTO.java`：
```java
package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大屏数据源响应.
 */
@Data
public class ScreenDatasourceRespDTO {

    private Long id;

    private String dsCode;

    private String dsName;

    /** TIMESERIES / SINGLE（前端组件联动过滤的依据） */
    private String dsType;

    private String sourceKind;

    private String configJson;

    private String timeParamJson;

    private String status;

    private String remark;

    private LocalDateTime createdTime;
}
```

- [ ] **Step 4: 写 ScreenMetricSlotDao**

```java
package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PERF_METRIC_DEF 槽位查询 DAO（走只读数据源，宽表数据源保存时做指标校验与槽位翻译）.
 *
 * <p>不走 performance 模块 Api：大屏数据访问按 D1 决策统一走只读源 + 白名单直查。
 */
@Slf4j
@Component
public class ScreenMetricSlotDao {

    private final DataSource readOnlyDataSource;

    public ScreenMetricSlotDao(@Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource) {
        this.readOnlyDataSource = readOnlyDataSource;
    }

    /** 指标定义快照 */
    public record MetricSlot(String metricCode, String metricName, Integer valSlot, String baseDim) {
    }

    /** 按编码批量查有效指标定义（deleted=0） */
    public List<MetricSlot> selectByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", Collections.nCopies(codes.size(), "?"));
        String sql = "SELECT metric_code, metric_name, val_slot, base_dim FROM PERF_METRIC_DEF"
                + " WHERE deleted = 0 AND metric_code IN (" + in + ")";
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(5);
            for (int i = 0; i < codes.size(); i++) {
                stmt.setString(i + 1, codes.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<MetricSlot> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new MetricSlot(rs.getString("metric_code"), rs.getString("metric_name"),
                            (Integer) rs.getObject("val_slot"), rs.getString("base_dim")));
                }
                return out;
            }
        } catch (SQLException e) {
            log.warn("[ScreenMetricSlotDao] 指标定义查询失败 cause={}", e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }
}
```

- [ ] **Step 5: 写接口 + Impl（绿）**

`service/screen/ScreenDatasourceService.java`：
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;

import java.util.List;

/**
 * 大屏数据源管理服务.
 */
public interface ScreenDatasourceService {

    /** 列表（可按能力标签/名称关键字过滤） */
    List<ScreenDatasourceRespDTO> list(String dsType, String keyword);

    /** 新建（返回 id）；WIDE_TABLE 保存时翻译槽位快照 */
    Long save(ScreenDatasourceSaveReqDTO req);

    /** 更新 */
    void update(Long id, ScreenDatasourceSaveReqDTO req);

    /** 删除（被区块引用时 RPT-43007 拒绝） */
    void delete(Long id);

    /** 配置态试跑（LIMIT 10，高危审计） */
    ScreenDataRespDTO tryRun(ScreenTryRunReqDTO req);

    /** 运行时统一取数（Controller 唯一入口，内部完成实体加载） */
    ScreenDataRespDTO queryData(ScreenDataReqDTO req);
}
```

`service/screen/ScreenDatasourceServiceImpl.java`：
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大屏数据源管理服务实现.
 *
 * <p>审计：CUSTOM_SQL 的保存与所有试跑属高危操作，手工双写 governance 审计
 * （模块惯例，参考 SqlProbeServiceImpl.safelyAudit，非 @AuditLog 切面）。
 */
@Slf4j
@Service
public class ScreenDatasourceServiceImpl implements ScreenDatasourceService {

    /** 宽表 → [subjectCol, subjectParam, baseDim] */
    private static final Map<String, String[]> WIDE_TABLES = Map.of(
            "EMP_INDEX_RESULT", new String[]{"emp_id", "empId", "EMP"},
            "ORG_INDEX_RESULT", new String[]{"org_code", "orgCode", "ORG"},
            "CUST_INDEX_RESULT", new String[]{"cust_no", "custNo", "CUST"});

    private static final Set<String> KPI_CYCLE_TYPES = Set.of("MONTHLY", "QUARTERLY");

    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenBlockMapper blockMapper;
    private final ScreenQueryEngine engine;
    private final ScreenMetricSlotDao slotDao;
    private final CurrentUserApi currentUserApi;
    private final AuditApi auditApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenDatasourceServiceImpl(RptScreenDatasourceMapper dsMapper,
                                       RptScreenBlockMapper blockMapper,
                                       ScreenQueryEngine engine,
                                       ScreenMetricSlotDao slotDao,
                                       CurrentUserApi currentUserApi,
                                       AuditApi auditApi) {
        this.dsMapper = dsMapper;
        this.blockMapper = blockMapper;
        this.engine = engine;
        this.slotDao = slotDao;
        this.currentUserApi = currentUserApi;
        this.auditApi = auditApi;
    }

    @Override
    public List<ScreenDatasourceRespDTO> list(String dsType, String keyword) {
        LambdaQueryWrapper<RptScreenDatasource> qw = new LambdaQueryWrapper<>();
        if (dsType != null && !dsType.isBlank()) {
            qw.eq(RptScreenDatasource::getDsType, dsType);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(RptScreenDatasource::getDsName, keyword);
        }
        qw.orderByDesc(RptScreenDatasource::getId);
        return dsMapper.selectList(qw).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScreenDatasourceSaveReqDTO req) {
        RptScreenDatasource e = new RptScreenDatasource();
        applyValidated(e, req);
        e.setDsCode("SCRDS_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT));
        e.setCreatedBy(currentUserApi.getCurrentEmpId());
        dsMapper.insert(e);
        if ("CUSTOM_SQL".equals(e.getSourceKind())) {
            safelyAudit("WRITE", "saveDs id=" + e.getId() + ", name=" + e.getDsName(), req.getReason());
        }
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScreenDatasourceSaveReqDTO req) {
        RptScreenDatasource e = requireDs(id);
        applyValidated(e, req);
        dsMapper.updateById(e);
        if ("CUSTOM_SQL".equals(e.getSourceKind())) {
            safelyAudit("WRITE", "updateDs id=" + id, req.getReason());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDs(id);
        if (blockMapper.countByDsId(id) > 0) {
            throw new RptException(RptErrorCode.SCREEN_DS_IN_USE);
        }
        dsMapper.deleteById(id);
    }

    @Override
    public ScreenDataRespDTO tryRun(ScreenTryRunReqDTO req) {
        // 试跑也走完整校验（含 CUSTOM_SQL 白名单），随后 LIMIT 10 执行
        RptScreenDatasource probe = new RptScreenDatasource();
        ScreenDatasourceSaveReqDTO fake = new ScreenDatasourceSaveReqDTO();
        fake.setDsName("__tryrun__");
        fake.setDsType(req.getDsType());
        fake.setSourceKind(req.getSourceKind());
        fake.setConfigJson(req.getConfigJson());
        applyValidated(probe, fake);

        ScreenDataReqDTO dataReq = new ScreenDataReqDTO();
        dataReq.setPeriod(req.getPeriod());
        dataReq.setDateFrom(req.getDateFrom());
        dataReq.setDateTo(req.getDateTo());
        dataReq.setContextParams(req.getContextParams());
        ScreenDataRespDTO resp = engine.tryRun(probe.getSourceKind(), probe.getConfigJson(), dataReq);
        safelyAudit("EXECUTE_SQL", "tryRun kind=" + req.getSourceKind(), req.getReason());
        return resp;
    }

    @Override
    public ScreenDataRespDTO queryData(ScreenDataReqDTO req) {
        RptScreenDatasource ds = dsMapper.selectById(req.getDsId());
        if (ds == null || "DISABLED".equals(ds.getStatus())) {
            throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
        }
        return engine.query(ds, req);
    }

    // ===== 内部 =====

    private RptScreenDatasource requireDs(Long id) {
        RptScreenDatasource e = dsMapper.selectById(id);
        if (e == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
        }
        return e;
    }

    /** 按 sourceKind 归一化 + 校验，结果写入实体（save/update/tryRun 共用） */
    private void applyValidated(RptScreenDatasource e, ScreenDatasourceSaveReqDTO req) {
        String kind = req.getSourceKind();
        JsonNode cfg = readJson(req.getConfigJson());
        switch (kind == null ? "" : kind) {
            case "WIDE_TABLE" -> {
                String table = cfg.path("table").asText();
                String[] meta = WIDE_TABLES.get(table);
                if (meta == null) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                List<String> codes = new ArrayList<>();
                cfg.path("metrics").forEach(m -> codes.add(m.path("metricCode").asText()));
                if (codes.isEmpty()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                Map<String, ScreenMetricSlotDao.MetricSlot> found = slotDao.selectByCodes(codes).stream()
                        .collect(Collectors.toMap(ScreenMetricSlotDao.MetricSlot::metricCode, Function.identity()));
                // 全部存在 + 维度匹配，重写 metrics 快照
                ObjectNode newCfg = objectMapper.createObjectNode();
                newCfg.put("table", table);
                newCfg.put("subjectCol", meta[0]);
                newCfg.put("subjectParam", meta[1]);
                ArrayNode arr = newCfg.putArray("metrics");
                for (String code : codes) {
                    ScreenMetricSlotDao.MetricSlot slot = found.get(code);
                    if (slot == null || slot.valSlot() == null || !meta[2].equals(slot.baseDim())) {
                        throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                    }
                    ObjectNode m = arr.addObject();
                    m.put("metricCode", slot.metricCode());
                    m.put("metricName", slot.metricName());
                    m.put("slot", slot.valSlot());
                }
                e.setConfigJson(newCfg.toString());
                e.setDsType("TIMESERIES");
            }
            case "KPI_RESULT" -> {
                String cycleType = cfg.path("cycleType").asText("MONTHLY");
                if (!KPI_CYCLE_TYPES.contains(cycleType)) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                e.setConfigJson(req.getConfigJson());
                e.setDsType("TIMESERIES");
            }
            case "CUSTOM_SQL" -> {
                String sql = cfg.path("sql").asText();
                engine.validateCustomSql(sql);
                String dsType = req.getDsType() == null ? "SINGLE" : req.getDsType();
                String dateCol = cfg.path("dateCol").asText(null);
                if ("TIMESERIES".equals(dsType) && (dateCol == null || dateCol.isBlank())) {
                    throw new RptException(RptErrorCode.SCREEN_DS_TIMESERIES_NEED_DATECOL);
                }
                e.setConfigJson(req.getConfigJson());
                e.setDsType(dsType);
            }
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        e.setDsName(req.getDsName());
        e.setSourceKind(kind);
        e.setTimeParamJson(req.getTimeParamJson());
        e.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "ACTIVE" : req.getStatus());
        e.setRemark(req.getRemark());
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
    }

    private ScreenDatasourceRespDTO toDto(RptScreenDatasource e) {
        ScreenDatasourceRespDTO d = new ScreenDatasourceRespDTO();
        d.setId(e.getId());
        d.setDsCode(e.getDsCode());
        d.setDsName(e.getDsName());
        d.setDsType(e.getDsType());
        d.setSourceKind(e.getSourceKind());
        d.setConfigJson(e.getConfigJson());
        d.setTimeParamJson(e.getTimeParamJson());
        d.setStatus(e.getStatus());
        d.setRemark(e.getRemark());
        d.setCreatedTime(e.getCreatedTime());
        return d;
    }

    /** 高危操作手工审计（失败仅告警不阻断业务，模块惯例） */
    private void safelyAudit(String action, String detail, String reason) {
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction(action)
                    .resourceUrl("/api/screen/admin/datasources")
                    .requestMethod("POST")
                    .requestParams(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail)
                    .responseStatus(200)
                    .reason(reason)
                    .build();
            auditApi.log(cmd);
        } catch (RuntimeException ex) {
            log.warn("[ScreenDatasourceService] AuditApi.log 失败 cause={}", ex.getMessage());
        }
    }
}
```

注意：`AuditLogCmd` 的真实 import 以 `SqlProbeServiceImpl` 现有 import 为准（若非 `governance.api.dto` 包则同步调整）。

- [ ] **Step 6: 跑单测确认通过**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenDatasourceServiceTest`
Expected: PASS（11 case 全绿）

- [ ] **Step 7: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/support/ScreenMetricSlotDao.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenDatasourceSaveReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenTryRunReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenDatasourceRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenDatasourceService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenDatasourceServiceImpl.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenDatasourceServiceTest.java
git commit -m "feat(screen): 数据源管理服务（宽表槽位翻译/KPI/自定义SQL校验+试跑+审计+删除保护）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: ScreenConfigService 屏/区块/地图点位配置服务（TDD）

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenBlockDTO.java`、`dto/req/ScreenSaveReqDTO.java`、`dto/req/MapPointDTO.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenDetailRespDTO.java`、`dto/resp/ScreenViewRespDTO.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenConfigService.java`（接口）、`ScreenConfigServiceImpl.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenConfigServiceTest.java`

**Interfaces:**
- Consumes: Task 3 四个 Mapper；`CurrentUserApi.getCurrentEmpId()`
- Produces（Task 9 Controller 依赖）:
  - `List<ScreenDetailRespDTO> listScreens()`（概要，blocks=null）
  - `ScreenDetailRespDTO getScreen(Long id)`（含 blocks）
  - `Long saveScreen(ScreenSaveReqDTO req)`（upsert；事务内校验 + 先删后插区块）
  - `void deleteScreen(Long id)`（屏逻辑删 + 区块物理删）
  - `List<MapPointDTO> listMapPoints()` / `void saveMapPoints(List<MapPointDTO> points)`（覆盖式）
  - `ScreenViewRespDTO getViewByCode(String screenCode)`（运行时：ACTIVE 屏 + 区块；PROVINCE 附 ACTIVE 点位）

**校验规则（saveScreen）：**
1. `viewLevel` ∈ PROVINCE/BRANCH/PERSON，否则 43006。
2. `screenCode` 空则生成 `"SCR_" + UUID 前 8 位大写`；显式传入且与其他屏（deleted=0、非自身）重复 → 43006。
3. 每个区块：`region` ∈ LEFT/MAIN/RIGHT；**PROVINCE 屏 MAIN 区禁配区块**（中心固定为地图，代码层保证）；`componentType` ∈ 5 种；`widthPct/heightPct` ∈ [1,100]——违规均 43006。
4. 同 `region+rowNo` 的 `widthPct` 合计 > 100 → 43006。
5. `bind_json.dsId` 必填且数据源存在（缺失/不存在 → 43001）；`LINE_TREND` 组件或 `drill_json.drillEnabled=true` 时数据源必须 TIMESERIES（否则 43005）。

- [ ] **Step 1: 写单测（红）**

```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScreenConfigService 单测（Mockito）.
 */
@ExtendWith(MockitoExtension.class)
class ScreenConfigServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenMapPointMapper pointMapper;
    @Mock private CurrentUserApi currentUserApi;

    private ScreenConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenConfigServiceImpl(screenMapper, blockMapper, dsMapper, pointMapper, currentUserApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private ScreenBlockDTO block(String region, int rowNo, int colNo, int widthPct,
                                 String componentType, String bindJson, String drillJson) {
        ScreenBlockDTO b = new ScreenBlockDTO();
        b.setRegion(region);
        b.setRowNo(rowNo);
        b.setColNo(colNo);
        b.setWidthPct(widthPct);
        b.setHeightPct(50);
        b.setComponentType(componentType);
        b.setBindJson(bindJson);
        b.setDrillJson(drillJson);
        return b;
    }

    private ScreenSaveReqDTO reqWith(String viewLevel, ScreenBlockDTO... blocks) {
        ScreenSaveReqDTO r = new ScreenSaveReqDTO();
        r.setScreenName("测试屏");
        r.setViewLevel(viewLevel);
        r.setBlocks(List.of(blocks));
        return r;
    }

    private RptScreenDatasource ds(long id, String dsType) {
        RptScreenDatasource d = new RptScreenDatasource();
        d.setId(id);
        d.setDsType(dsType);
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void saveScreen_provinceMainBlock_throws43006() {
        ScreenSaveReqDTO req = reqWith("PROVINCE",
                block("MAIN", 1, 1, 100, "METRIC_CARD", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void saveScreen_rowWidthOver100_throws43006() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 60, "METRIC_CARD", "{\"dsId\":1}", null),
                block("LEFT", 1, 2, 50, "METRIC_CARD", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void saveScreen_lineTrendOnSingleDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "LINE_TREND", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void saveScreen_drillEnabledOnSingleDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "METRIC_CARD", "{\"dsId\":1}",
                        "{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\"]}"));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void saveScreen_unknownDsId_throws43001() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "METRIC_CARD", "{\"dsId\":99}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
    }

    @Test
    void saveScreen_ok_insertsScreenThenDeletesThenInsertsBlocks() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "TIMESERIES")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "LINE_TREND", "{\"dsId\":1}", null),
                block("RIGHT", 1, 1, 100, "METRIC_CARD", "{\"dsId\":1}", null));

        service.saveScreen(req);

        InOrder order = inOrder(screenMapper, blockMapper);
        order.verify(screenMapper).insert(any(RptScreen.class));
        order.verify(blockMapper).delete(any(Wrapper.class));
        order.verify(blockMapper, times(2)).insert(any(RptScreenBlock.class));
    }

    @Test
    void getViewByCode_notFound_throws43004() {
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.getViewByCode("SCR_NONE"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43004");
    }

    @Test
    void getViewByCode_province_includesActiveMapPoints() {
        RptScreen s = new RptScreen();
        s.setId(7L);
        s.setScreenCode("SCR_PROVINCE");
        s.setScreenName("总览");
        s.setViewLevel("PROVINCE");
        s.setStatus("ACTIVE");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        RptScreenMapPoint p = new RptScreenMapPoint();
        p.setOrgCode("610100");
        p.setOrgName("西安分行");
        p.setLng(new BigDecimal("108.948024"));
        p.setLat(new BigDecimal("34.263161"));
        when(pointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(p));

        ScreenViewRespDTO view = service.getViewByCode("SCR_PROVINCE");

        assertThat(view.getScreen().getViewLevel()).isEqualTo("PROVINCE");
        assertThat(view.getMapPoints()).hasSize(1);
        assertThat(view.getMapPoints().get(0).getOrgCode()).isEqualTo("610100");
    }

    @Test
    void deleteScreen_logicDeletesScreenAndHardDeletesBlocks() {
        RptScreen s = new RptScreen();
        s.setId(7L);
        when(screenMapper.selectById(7L)).thenReturn(s);

        service.deleteScreen(7L);

        verify(screenMapper).deleteById(7L);
        verify(blockMapper).delete(any(Wrapper.class));
    }
}
```

- [ ] **Step 2: 跑单测确认失败**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenConfigServiceTest`
Expected: FAIL（编译错误）

- [ ] **Step 3: 写 5 个 DTO**

`dto/req/ScreenBlockDTO.java`：
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 大屏区块 DTO（配置保存与运行时读取共用）.
 */
@Data
public class ScreenBlockDTO {

    private Long id;

    /** LEFT / MAIN / RIGHT */
    @NotBlank
    private String region;

    @NotNull
    private Integer rowNo;

    @NotNull
    private Integer colNo;

    /** 行内宽度百分比 1~100 */
    @NotNull
    private Integer widthPct;

    /** 区域内行高百分比 1~100 */
    @NotNull
    private Integer heightPct;

    /** METRIC_CARD / LINE_TREND / PIE_SHARE / RANK_LIST / FLOW_STATUS */
    @NotBlank
    private String componentType;

    /** 数据绑定 JSON */
    @NotBlank
    private String bindJson;

    /** 样式 JSON */
    private String styleJson;

    /** 钻取/跳转 JSON */
    private String drillJson;
}
```

`dto/req/ScreenSaveReqDTO.java`：
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 大屏整体保存请求（屏 + 区块一次提交）.
 */
@Data
public class ScreenSaveReqDTO {

    /** 空=新建；非空=更新 */
    private Long id;

    /** 空则服务端生成 SCR_XXXXXXXX */
    private String screenCode;

    @NotBlank
    private String screenName;

    /** PROVINCE / BRANCH / PERSON */
    @NotBlank
    private String viewLevel;

    private String themeJson;

    /** ACTIVE / DISABLED（空=ACTIVE） */
    private String status;

    @Valid
    private List<ScreenBlockDTO> blocks;
}
```

`dto/req/MapPointDTO.java`：
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 地图支行点位 DTO.
 */
@Data
public class MapPointDTO {

    private Long id;

    @NotBlank
    private String orgCode;

    @NotBlank
    private String orgName;

    @NotNull
    private BigDecimal lng;

    @NotNull
    private BigDecimal lat;

    /** 点击跳转目标屏编码（空=SCR_BRANCH） */
    private String targetScreenCode;

    /** ACTIVE / DISABLED */
    private String status;
}
```

`dto/resp/ScreenDetailRespDTO.java`：
```java
package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大屏详情响应（列表场景 blocks=null）.
 */
@Data
public class ScreenDetailRespDTO {

    private Long id;

    private String screenCode;

    private String screenName;

    private String viewLevel;

    private String themeJson;

    private String status;

    private LocalDateTime createdTime;

    private List<ScreenBlockDTO> blocks;
}
```

`dto/resp/ScreenViewRespDTO.java`：
```java
package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;

import java.util.List;

/**
 * 大屏运行时整屏配置响应.
 */
@Data
public class ScreenViewRespDTO {

    /** 屏信息（blocks 置 null，区块在外层） */
    private ScreenDetailRespDTO screen;

    private List<ScreenBlockDTO> blocks;

    /** 仅 PROVINCE 屏返回（其余为空列表） */
    private List<MapPointDTO> mapPoints;
}
```

- [ ] **Step 4: 写接口 + Impl（绿）**

`service/screen/ScreenConfigService.java`：
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;

import java.util.List;

/**
 * 大屏布局/区块/地图点位配置服务.
 */
public interface ScreenConfigService {

    /** 屏列表（概要，不含区块） */
    List<ScreenDetailRespDTO> listScreens();

    /** 屏详情（含区块） */
    ScreenDetailRespDTO getScreen(Long id);

    /** 整体保存（upsert 屏 + 先删后插区块），返回屏 id */
    Long saveScreen(ScreenSaveReqDTO req);

    /** 删除（屏逻辑删 + 区块物理删） */
    void deleteScreen(Long id);

    /** 点位列表 */
    List<MapPointDTO> listMapPoints();

    /** 点位整表覆盖保存 */
    void saveMapPoints(List<MapPointDTO> points);

    /** 运行时读取整屏配置（ACTIVE；PROVINCE 附 ACTIVE 点位） */
    ScreenViewRespDTO getViewByCode(String screenCode);
}
```

`service/screen/ScreenConfigServiceImpl.java`：
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大屏布局/区块/地图点位配置服务实现.
 */
@Slf4j
@Service
public class ScreenConfigServiceImpl implements ScreenConfigService {

    private static final Set<String> VIEW_LEVELS = Set.of("PROVINCE", "BRANCH", "PERSON");
    private static final Set<String> REGIONS = Set.of("LEFT", "MAIN", "RIGHT");
    private static final Set<String> COMPONENT_TYPES =
            Set.of("METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS");
    /** 需要时序型数据源的组件 */
    private static final Set<String> TIMESERIES_ONLY_COMPONENTS = Set.of("LINE_TREND");

    private final RptScreenMapper screenMapper;
    private final RptScreenBlockMapper blockMapper;
    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenMapPointMapper pointMapper;
    private final CurrentUserApi currentUserApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenConfigServiceImpl(RptScreenMapper screenMapper,
                                   RptScreenBlockMapper blockMapper,
                                   RptScreenDatasourceMapper dsMapper,
                                   RptScreenMapPointMapper pointMapper,
                                   CurrentUserApi currentUserApi) {
        this.screenMapper = screenMapper;
        this.blockMapper = blockMapper;
        this.dsMapper = dsMapper;
        this.pointMapper = pointMapper;
        this.currentUserApi = currentUserApi;
    }

    @Override
    public List<ScreenDetailRespDTO> listScreens() {
        LambdaQueryWrapper<RptScreen> qw = new LambdaQueryWrapper<RptScreen>().orderByDesc(RptScreen::getId);
        return screenMapper.selectList(qw).stream().map(s -> toDetail(s, null)).collect(Collectors.toList());
    }

    @Override
    public ScreenDetailRespDTO getScreen(Long id) {
        RptScreen s = requireScreen(id);
        return toDetail(s, listBlocks(s.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveScreen(ScreenSaveReqDTO req) {
        validate(req);
        RptScreen s;
        if (req.getId() == null) {
            s = new RptScreen();
            s.setScreenCode(req.getScreenCode() == null || req.getScreenCode().isBlank()
                    ? "SCR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT)
                    : req.getScreenCode());
            s.setCreatedBy(currentUserApi.getCurrentEmpId());
            applyScreenFields(s, req);
            screenMapper.insert(s);
        } else {
            s = requireScreen(req.getId());
            if (req.getScreenCode() != null && !req.getScreenCode().isBlank()) {
                s.setScreenCode(req.getScreenCode());
            }
            applyScreenFields(s, req);
            screenMapper.updateById(s);
        }
        // screenCode 唯一性（deleted=0 内，排除自身）
        LambdaQueryWrapper<RptScreen> dup = new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, s.getScreenCode())
                .ne(RptScreen::getId, s.getId());
        if (screenMapper.selectCount(dup) > 0) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        // 区块先删后插（整体覆盖语义）
        blockMapper.delete(new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        if (req.getBlocks() != null) {
            for (ScreenBlockDTO b : req.getBlocks()) {
                RptScreenBlock e = new RptScreenBlock();
                e.setScreenId(s.getId());
                e.setRegion(b.getRegion());
                e.setRowNo(b.getRowNo());
                e.setColNo(b.getColNo());
                e.setWidthPct(b.getWidthPct());
                e.setHeightPct(b.getHeightPct());
                e.setComponentType(b.getComponentType());
                e.setBindJson(b.getBindJson());
                e.setStyleJson(b.getStyleJson());
                e.setDrillJson(b.getDrillJson());
                blockMapper.insert(e);
            }
        }
        return s.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScreen(Long id) {
        requireScreen(id);
        screenMapper.deleteById(id);
        blockMapper.delete(new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, id));
    }

    @Override
    public List<MapPointDTO> listMapPoints() {
        return pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                        .orderByAsc(RptScreenMapPoint::getOrgCode))
                .stream().map(this::toPointDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMapPoints(List<MapPointDTO> points) {
        pointMapper.delete(new LambdaQueryWrapper<>());
        if (points == null) {
            return;
        }
        for (MapPointDTO p : points) {
            RptScreenMapPoint e = new RptScreenMapPoint();
            e.setOrgCode(p.getOrgCode());
            e.setOrgName(p.getOrgName());
            e.setLng(p.getLng());
            e.setLat(p.getLat());
            e.setTargetScreenCode(p.getTargetScreenCode() == null || p.getTargetScreenCode().isBlank()
                    ? "SCR_BRANCH" : p.getTargetScreenCode());
            e.setStatus(p.getStatus() == null || p.getStatus().isBlank() ? "ACTIVE" : p.getStatus());
            pointMapper.insert(e);
        }
    }

    @Override
    public ScreenViewRespDTO getViewByCode(String screenCode) {
        List<RptScreen> hits = screenMapper.selectList(new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, screenCode)
                .eq(RptScreen::getStatus, "ACTIVE"));
        if (hits.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        RptScreen s = hits.get(0);
        ScreenViewRespDTO view = new ScreenViewRespDTO();
        view.setScreen(toDetail(s, null));
        view.setBlocks(listBlocks(s.getId()));
        if ("PROVINCE".equals(s.getViewLevel())) {
            view.setMapPoints(pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                            .eq(RptScreenMapPoint::getStatus, "ACTIVE"))
                    .stream().map(this::toPointDto).collect(Collectors.toList()));
        } else {
            view.setMapPoints(List.of());
        }
        return view;
    }

    // ===== 内部 =====

    private void validate(ScreenSaveReqDTO req) {
        if (!VIEW_LEVELS.contains(req.getViewLevel())) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        List<ScreenBlockDTO> blocks = req.getBlocks() == null ? List.of() : req.getBlocks();
        Map<String, Integer> rowWidth = new HashMap<>();
        Set<Long> dsIds = new HashSet<>();
        for (ScreenBlockDTO b : blocks) {
            if (!REGIONS.contains(b.getRegion())
                    || !COMPONENT_TYPES.contains(b.getComponentType())
                    || b.getWidthPct() == null || b.getWidthPct() < 1 || b.getWidthPct() > 100
                    || b.getHeightPct() == null || b.getHeightPct() < 1 || b.getHeightPct() > 100) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            // PROVINCE 中心区固定为地图，不允许配置普通区块
            if ("PROVINCE".equals(req.getViewLevel()) && "MAIN".equals(b.getRegion())) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            String rowKey = b.getRegion() + "#" + b.getRowNo();
            int sum = rowWidth.merge(rowKey, b.getWidthPct(), Integer::sum);
            if (sum > 100) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            Long dsId = readDsId(b.getBindJson());
            if (dsId == null) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            dsIds.add(dsId);
        }
        if (dsIds.isEmpty()) {
            return;
        }
        Map<Long, RptScreenDatasource> dsMap = dsMapper.selectBatchIds(dsIds).stream()
                .collect(Collectors.toMap(RptScreenDatasource::getId, Function.identity()));
        for (ScreenBlockDTO b : blocks) {
            RptScreenDatasource ds = dsMap.get(readDsId(b.getBindJson()));
            if (ds == null) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            boolean needTs = TIMESERIES_ONLY_COMPONENTS.contains(b.getComponentType()) || drillEnabled(b.getDrillJson());
            if (needTs && !"TIMESERIES".equals(ds.getDsType())) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
            }
        }
    }

    private Long readDsId(String bindJson) {
        try {
            JsonNode n = objectMapper.readTree(bindJson == null ? "{}" : bindJson).path("dsId");
            return n.isNumber() ? n.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean drillEnabled(String drillJson) {
        try {
            return objectMapper.readTree(drillJson == null ? "{}" : drillJson)
                    .path("drillEnabled").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private RptScreen requireScreen(Long id) {
        RptScreen s = screenMapper.selectById(id);
        if (s == null) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        return s;
    }

    private void applyScreenFields(RptScreen s, ScreenSaveReqDTO req) {
        s.setScreenName(req.getScreenName());
        s.setViewLevel(req.getViewLevel());
        s.setThemeJson(req.getThemeJson());
        s.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "ACTIVE" : req.getStatus());
    }

    private List<ScreenBlockDTO> listBlocks(Long screenId) {
        return blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                        .eq(RptScreenBlock::getScreenId, screenId)
                        .orderByAsc(RptScreenBlock::getRegion)
                        .orderByAsc(RptScreenBlock::getRowNo)
                        .orderByAsc(RptScreenBlock::getColNo))
                .stream().map(this::toBlockDto).collect(Collectors.toList());
    }

    private ScreenBlockDTO toBlockDto(RptScreenBlock e) {
        ScreenBlockDTO d = new ScreenBlockDTO();
        d.setId(e.getId());
        d.setRegion(e.getRegion());
        d.setRowNo(e.getRowNo());
        d.setColNo(e.getColNo());
        d.setWidthPct(e.getWidthPct());
        d.setHeightPct(e.getHeightPct());
        d.setComponentType(e.getComponentType());
        d.setBindJson(e.getBindJson());
        d.setStyleJson(e.getStyleJson());
        d.setDrillJson(e.getDrillJson());
        return d;
    }

    private ScreenDetailRespDTO toDetail(RptScreen s, List<ScreenBlockDTO> blocks) {
        ScreenDetailRespDTO d = new ScreenDetailRespDTO();
        d.setId(s.getId());
        d.setScreenCode(s.getScreenCode());
        d.setScreenName(s.getScreenName());
        d.setViewLevel(s.getViewLevel());
        d.setThemeJson(s.getThemeJson());
        d.setStatus(s.getStatus());
        d.setCreatedTime(s.getCreatedTime());
        d.setBlocks(blocks);
        return d;
    }

    private MapPointDTO toPointDto(RptScreenMapPoint e) {
        MapPointDTO d = new MapPointDTO();
        d.setId(e.getId());
        d.setOrgCode(e.getOrgCode());
        d.setOrgName(e.getOrgName());
        d.setLng(e.getLng());
        d.setLat(e.getLat());
        d.setTargetScreenCode(e.getTargetScreenCode());
        d.setStatus(e.getStatus());
        return d;
    }
}
```

- [ ] **Step 5: 跑单测确认通过**

Run: `mvn test -pl report-analytics-center -Dtest=ScreenConfigServiceTest`
Expected: PASS（9 case 全绿）

- [ ] **Step 6: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenBlockDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenSaveReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/MapPointDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenDetailRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenViewRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenConfigService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenConfigServiceImpl.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenConfigServiceTest.java
git commit -m "feat(screen): 大屏布局配置服务（联动校验/布局校验/固定区约束/点位覆盖保存）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: 4 个 Controller + Controller IT

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ScreenDatasourceAdminController.java`、`ScreenConfigAdminController.java`、`ScreenViewController.java`、`ScreenDataController.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/controller/ScreenControllerIT.java`

**Interfaces:**
- Consumes: Task 7 `ScreenDatasourceService`、Task 8 `ScreenConfigService`
- Produces: 12 个 REST 端点（路径/鉴权见下表，Task 10 的 PT_RESOURCE 与 Task 11 前端 api/screen.js 严格对齐）

| 端点 | @BizAuth action | RESOURCE_ID |
|---|---|---|
| GET  /api/screen/admin/datasources | LIST | R_RPT_SCR_DS_LIST |
| POST /api/screen/admin/datasources | WRITE | R_RPT_SCR_DS_SAVE |
| PUT  /api/screen/admin/datasources/{id} | WRITE | R_RPT_SCR_DS_UPD |
| DELETE /api/screen/admin/datasources/{id} | DELETE | R_RPT_SCR_DS_DEL |
| POST /api/screen/admin/datasources/try-run | EXECUTE_SQL | R_RPT_SCR_DS_TRY |
| GET  /api/screen/admin/screens | LIST | R_RPT_SCR_CFG_LIST |
| GET  /api/screen/admin/screens/{id} | READ | R_RPT_SCR_CFG_GET |
| POST /api/screen/admin/screens | WRITE | R_RPT_SCR_CFG_SAVE |
| DELETE /api/screen/admin/screens/{id} | DELETE | R_RPT_SCR_CFG_DEL |
| GET  /api/screen/admin/map-points | LIST | R_RPT_SCR_MAP_LIST（独立资源，ResourceMatcher 按 URL+METHOD 匹配） |
| PUT  /api/screen/admin/map-points | WRITE | R_RPT_SCR_MAP_SAVE |
| GET  /api/screen/view/{screenCode} | READ | R_RPT_SCR_VIEW |
| POST /api/screen/data | READ | R_RPT_SCR_DATA |

- [ ] **Step 1: 写 Controller IT（红）**——继承 `BaseControllerIT`（MockMvc + Mock 鉴权拦截器 + Mock 上游 Api），落库走真库

```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 大屏 4 Controller 端到端 IT（MockMvc，鉴权拦截器 Mock 放行，业务落真库）.
 */
class ScreenControllerIT extends BaseControllerIT {

    @BeforeEach
    void stubCurrentUser() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("TEST_SCR_E9");
    }

    @Test
    void saveKpiDatasource_thenListContainsIt() throws Exception {
        String body = """
                {"dsName":"TEST_SCR_KPI源","sourceKind":"KPI_RESULT",
                 "configJson":"{\\"cycleType\\":\\"MONTHLY\\"}"}
                """;
        mvc.perform(post("/api/screen/admin/datasources")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isNumber());

        mvc.perform(get("/api/screen/admin/datasources").param("keyword", "TEST_SCR_KPI源"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dsType").value("TIMESERIES"));
    }

    @Test
    void saveScreen_lineTrendOnMissingDs_returns43001() throws Exception {
        String body = """
                {"screenName":"TEST_SCR_坏屏","viewLevel":"BRANCH",
                 "blocks":[{"region":"LEFT","rowNo":1,"colNo":1,"widthPct":100,"heightPct":50,
                            "componentType":"LINE_TREND","bindJson":"{\\"dsId\\":987654999}"}]}
                """;
        mvc.perform(post("/api/screen/admin/screens")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("RPT-43001"));
    }

    @Test
    void viewUnknownScreen_returns43004() throws Exception {
        mvc.perform(get("/api/screen/view/SCR_NOT_EXIST"))
                .andExpect(jsonPath("$.code").value("RPT-43004"));
    }

    @Test
    void queryData_unknownDs_returns43001() throws Exception {
        mvc.perform(post("/api/screen/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dsId\":987654999}"))
                .andExpect(jsonPath("$.code").value("RPT-43001"));
    }
}
```

说明：IT 会向真库写入 `TEST_SCR_KPI源` 数据源（无自动回滚——BaseControllerIT 无 @Transactional）。在测试类补 `@AfterEach` 清理：
```java
    @Autowired
    private com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper dsMapper;
    @Autowired
    private com.bank.branch.platform.report.mapper.RptScreenMapper screenMapper;

    @org.junit.jupiter.api.AfterEach
    void cleanupTestData() {
        dsMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
                <com.bank.branch.platform.report.entity.RptScreenDatasource>()
                .likeRight(com.bank.branch.platform.report.entity.RptScreenDatasource::getDsName, "TEST_SCR_"));
        screenMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
                <com.bank.branch.platform.report.entity.RptScreen>()
                .likeRight(com.bank.branch.platform.report.entity.RptScreen::getScreenName, "TEST_SCR_"));
    }
```
（@TableLogic 下 `delete` 是逻辑删，满足前缀隔离即可。）

- [ ] **Step 2: 跑 IT 确认失败**

Run: `mvn verify -pl report-analytics-center -Dit.test=ScreenControllerIT -Dtest=RptErrorCodeTest`
Expected: FAIL（编译错误：Controller 不存在）

- [ ] **Step 3: 写 4 个 Controller（绿）**

`ScreenDatasourceAdminController.java`：
```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏数据源配置管理（管理端）.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin/datasources")
@Tag(name = "大屏-数据源配置", description = "数据源定义 CRUD + 试跑（CUSTOM_SQL 高危独立授权）")
@Validated
@RequiredArgsConstructor
public class ScreenDatasourceAdminController {

    private final ScreenDatasourceService datasourceService;

    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "数据源列表（可按能力标签/关键字过滤）")
    public ResponseWrapper<List<ScreenDatasourceRespDTO>> list(
            @RequestParam(required = false) String dsType,
            @RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(datasourceService.list(dsType, keyword));
    }

    @PostMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "新建数据源（宽表引导式保存时翻译槽位）")
    public ResponseWrapper<Long> save(@Valid @RequestBody ScreenDatasourceSaveReqDTO req) {
        return ResponseWrapper.success(datasourceService.save(req));
    }

    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "更新数据源")
    public ResponseWrapper<Void> update(@PathVariable Long id,
                                        @Valid @RequestBody ScreenDatasourceSaveReqDTO req) {
        datasourceService.update(id, req);
        return ResponseWrapper.success();
    }

    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    @Operation(summary = "删除数据源（被区块引用时拒绝）")
    public ResponseWrapper<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return ResponseWrapper.success();
    }

    @PostMapping("/try-run")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXECUTE_SQL)
    @Operation(summary = "配置态试跑（LIMIT 10，高危：白名单校验 + 手工审计）")
    public ResponseWrapper<ScreenDataRespDTO> tryRun(@Valid @RequestBody ScreenTryRunReqDTO req) {
        return ResponseWrapper.success(datasourceService.tryRun(req));
    }
}
```

`ScreenConfigAdminController.java`：
```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏布局/区块/地图点位配置管理（管理端）.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin")
@Tag(name = "大屏-布局配置", description = "屏/区块整体保存 + 地图点位维护")
@Validated
@RequiredArgsConstructor
public class ScreenConfigAdminController {

    private final ScreenConfigService configService;

    @GetMapping("/screens")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "大屏列表（概要）")
    public ResponseWrapper<List<ScreenDetailRespDTO>> listScreens() {
        return ResponseWrapper.success(configService.listScreens());
    }

    @GetMapping("/screens/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "大屏详情（含区块）")
    public ResponseWrapper<ScreenDetailRespDTO> getScreen(@PathVariable Long id) {
        return ResponseWrapper.success(configService.getScreen(id));
    }

    @PostMapping("/screens")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "大屏整体保存（屏 + 区块，upsert）")
    public ResponseWrapper<Long> saveScreen(@Valid @RequestBody ScreenSaveReqDTO req) {
        return ResponseWrapper.success(configService.saveScreen(req));
    }

    @DeleteMapping("/screens/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    @Operation(summary = "删除大屏")
    public ResponseWrapper<Void> deleteScreen(@PathVariable Long id) {
        configService.deleteScreen(id);
        return ResponseWrapper.success();
    }

    @GetMapping("/map-points")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "地图点位列表")
    public ResponseWrapper<List<MapPointDTO>> listMapPoints() {
        return ResponseWrapper.success(configService.listMapPoints());
    }

    @PutMapping("/map-points")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "地图点位整表覆盖保存")
    public ResponseWrapper<Void> saveMapPoints(@Valid @RequestBody List<MapPointDTO> points) {
        configService.saveMapPoints(points);
        return ResponseWrapper.success();
    }
}
```

`ScreenViewController.java`：
```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏运行时整屏配置读取.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/view")
@Tag(name = "大屏-运行时", description = "整屏配置读取（屏+区块+PROVINCE 点位）")
@RequiredArgsConstructor
public class ScreenViewController {

    private final ScreenConfigService configService;

    @GetMapping("/{screenCode}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "按编码读取整屏配置")
    public ResponseWrapper<ScreenViewRespDTO> view(@PathVariable String screenCode) {
        return ResponseWrapper.success(configService.getViewByCode(screenCode));
    }
}
```

`ScreenDataController.java`：
```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏统一取数端点（所有组件共用）.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/data")
@Tag(name = "大屏-取数", description = "统一取数（dsId + 周期 + 上下文参数 → columns/rows）")
@RequiredArgsConstructor
public class ScreenDataController {

    private final ScreenDatasourceService datasourceService;

    @PostMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "统一取数")
    public ResponseWrapper<ScreenDataRespDTO> data(@Valid @RequestBody ScreenDataReqDTO req) {
        return ResponseWrapper.success(datasourceService.queryData(req));
    }
}
```

- [ ] **Step 4: 跑 IT 确认通过**

Run: `mvn verify -pl report-analytics-center -Dit.test=ScreenControllerIT -Dtest=RptErrorCodeTest`
Expected: PASS（4 IT 全绿）

- [ ] **Step 5: 全模块回归（含 6 个既有架构守护）**

Run: `mvn test -pl report-analytics-center`
Expected: PASS——特别关注 `RptBizAuthConsistencyArchTest`（新 Controller 均有 @BizAuth）、`RptNoEntityInControllerLocalsArchTest`（新 Controller 无实体局部变量）不红。

- [ ] **Step 6: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/Screen*.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/controller/ScreenControllerIT.java
git commit -m "feat(screen): 大屏 12 REST 端点（数据源/布局/运行时/取数 4 Controller）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: PT_RESOURCE 注册 + 菜单 + 种子配置 SQL 并执行

**Files:**
- Create: `docs/superpowers/sql/2026-07-12-screen-dashboard-resources.sql`
- Create: `docs/superpowers/sql/2026-07-12-screen-dashboard-seed.sql`

**Interfaces:**
- Consumes: Task 9 的 12 端点路径；Task 1 的 4 张表
- Produces: 12 条 API 资源 + 2 条管理页菜单 + 角色绑定；种子数据源（id 9001~9005）/三块屏（SCR_PROVINCE/SCR_BRANCH/SCR_PERSON）/区块/示例点位——Task 12~16 前端联调依赖这批种子

- [ ] **Step 1: 先核实 PT_RESOURCE 菜单行真实结构**（菜单树的父子组织方式此前未核实过，照现有"报表分析"组菜单依样画瓢）

```bash
mysql -uroot -pdjdev -e "SHOW COLUMNS FROM PT_RESOURCE" yiti
mysql -uroot -pdjdev -e "SELECT * FROM PT_RESOURCE WHERE ISMENU=1 AND MENU_NAME LIKE '%报表%' LIMIT 5\G" yiti
```
观察要点：是否有 PARENT_ID/父级列；`报表分析` 分组菜单行的 RESOURCE_ID 与叶子行的关联方式；`MENU_RANK_NO` 取值区间。**下一步脚本里的 2 条菜单行字段按此结果对齐**（URL 分别为 `/screen-admin/datasources` 与 `/screen-admin/designer`，挂在报表分析组下）。

- [ ] **Step 2: 写资源注册脚本**（幂等：先删后插；角色绑定用 INSERT...SELECT 复制既有资源的角色集）

```sql
-- 大屏端点 PT_RESOURCE 资源注册 + 角色绑定（2026-07-12）
-- 12 API 资源 + 2 管理页菜单。目标库 yiti + onepl_test_bootstrap 手工执行。
-- 角色策略：查看类(VIEW/DATA)复制 R_RPT_DASH_PRES 的角色集；管理类复制 R_RPT_SQL_EXEC 的
-- 角色集（R_BACK_TECH）并补 R_ADMIN；后续可在 系统管理→资源管理 界面调整。

-- 1) 清理旧行（幂等重跑）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_DS_LIST','R_RPT_SCR_DS_SAVE','R_RPT_SCR_DS_UPD','R_RPT_SCR_DS_DEL','R_RPT_SCR_DS_TRY',
   'R_RPT_SCR_CFG_LIST','R_RPT_SCR_CFG_GET','R_RPT_SCR_CFG_SAVE','R_RPT_SCR_CFG_DEL','R_RPT_SCR_MAP_SAVE',
   'R_RPT_SCR_VIEW','R_RPT_SCR_DATA','M_RPT_SCR_DS','M_RPT_SCR_DSN');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_DS_LIST','R_RPT_SCR_DS_SAVE','R_RPT_SCR_DS_UPD','R_RPT_SCR_DS_DEL','R_RPT_SCR_DS_TRY',
   'R_RPT_SCR_CFG_LIST','R_RPT_SCR_CFG_GET','R_RPT_SCR_CFG_SAVE','R_RPT_SCR_CFG_DEL','R_RPT_SCR_MAP_SAVE',
   'R_RPT_SCR_VIEW','R_RPT_SCR_DATA','M_RPT_SCR_DS','M_RPT_SCR_DSN');

-- 2) API 资源（ISMENU=0，STATUS=0 启用；RESOURCE_ID ≤20 字符）
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_DS_LIST','/api/screen/admin/datasources',        'GET',   '大屏-数据源列表', 0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_DS_SAVE','/api/screen/admin/datasources',        'POST',  '大屏-数据源新建', 0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_DS_UPD', '/api/screen/admin/datasources/*',      'PUT',   '大屏-数据源更新', 0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_DS_DEL', '/api/screen/admin/datasources/*',      'DELETE','大屏-数据源删除', 0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_DS_TRY', '/api/screen/admin/datasources/try-run','POST',  '大屏-数据源试跑(高危)',0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_CFG_LIST','/api/screen/admin/screens',           'GET',   '大屏-屏列表',     0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_CFG_GET','/api/screen/admin/screens/*',          'GET',   '大屏-屏详情',     0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_CFG_SAVE','/api/screen/admin/screens',           'POST',  '大屏-屏保存',     0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_CFG_DEL','/api/screen/admin/screens/*',          'DELETE','大屏-屏删除',     0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_MAP_SAVE','/api/screen/admin/map-points',        'PUT',   '大屏-点位保存',   0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_VIEW',   '/api/screen/view/*',                   'GET',   '大屏-整屏读取',   0,0,0,0,'PLATFORM',NOW(),NOW()),
  ('R_RPT_SCR_DATA',   '/api/screen/data',                     'POST',  '大屏-统一取数',   0,0,0,0,'PLATFORM',NOW(),NOW());
-- 注意：GET /api/screen/admin/map-points 复用 R_RPT_SCR_CFG_LIST 需要一条 GET 通配資源？
-- 不需要——ResourceMatcher 按 URL+METHOD 匹配，须单独補一行：
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_MAP_LIST','/api/screen/admin/map-points','GET','大屏-点位列表',0,0,0,0,'PLATFORM',NOW(),NOW());

-- 3) 菜单行（字段按 Step 1 核实结果对齐后再定稿；示例假设无父子列、以 MENU_RANK_NO 排序展示在"报表分析"组）
--    如核实存在 PARENT_ID 类列，需补父级"报表分析"分组的真实 RESOURCE_ID。
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('M_RPT_SCR_DS',  '/screen-admin/datasources', 'GET', '大屏数据源', 95, 1, 1, 0, 'PLATFORM', NOW(), NOW()),
  ('M_RPT_SCR_DSN', '/screen-admin/designer',    'GET', '大屏设计器', 96, 1, 1, 0, 'PLATFORM', NOW(), NOW());

-- 4) 角色绑定
-- 4a) 查看类（VIEW/DATA + 两条菜单）复制 R_RPT_DASH_PRES 的角色集
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRV_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_VIEW' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_DATA'
) t
WHERE r.RESOURCE_ID = 'R_RPT_DASH_PRES';

-- 4b) 管理类（含菜单）复制 R_RPT_SQL_EXEC 的角色集（R_BACK_TECH），再补 R_ADMIN
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRA_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_DS_LIST' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_DS_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_DS_UPD'  UNION ALL SELECT 'R_RPT_SCR_DS_DEL'  UNION ALL SELECT 'R_RPT_SCR_DS_TRY' UNION ALL
  SELECT 'R_RPT_SCR_CFG_LIST' UNION ALL SELECT 'R_RPT_SCR_CFG_GET' UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CFG_DEL' UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE' UNION ALL SELECT 'R_RPT_SCR_MAP_LIST' UNION ALL
  SELECT 'M_RPT_SCR_DS' UNION ALL SELECT 'M_RPT_SCR_DSN'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SQL_EXEC';

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRB_', t.RESOURCE_ID, '_R_ADMIN'), 'R_ADMIN', t.RESOURCE_ID, 'PLATFORM', NOW()
FROM (
  SELECT 'R_RPT_SCR_DS_LIST' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_DS_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_DS_UPD'  UNION ALL SELECT 'R_RPT_SCR_DS_DEL'  UNION ALL SELECT 'R_RPT_SCR_DS_TRY' UNION ALL
  SELECT 'R_RPT_SCR_CFG_LIST' UNION ALL SELECT 'R_RPT_SCR_CFG_GET' UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CFG_DEL' UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE' UNION ALL SELECT 'R_RPT_SCR_MAP_LIST' UNION ALL
  SELECT 'R_RPT_SCR_VIEW' UNION ALL SELECT 'R_RPT_SCR_DATA' UNION ALL
  SELECT 'M_RPT_SCR_DS' UNION ALL SELECT 'M_RPT_SCR_DSN'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM PT_ROLE_RESOURCE x WHERE x.ROLE_ID = 'R_ADMIN' AND x.RESOURCE_ID = t.RESOURCE_ID
);
```

**注意**：脚本中"点位列表补行"的思考已内联为 `R_RPT_SCR_MAP_LIST`（第 14 条资源）；Task 9 表格里 `GET /map-points 复用 R_RPT_SCR_CFG_LIST` 的说法**以本脚本为准**（独立资源更贴合 ResourceMatcher URL+METHOD 匹配机制）。菜单行若核实出父子结构需补父级列后再执行。

- [ ] **Step 3: 写种子配置脚本**（固定 id 便于区块引用；宽表数据源的指标 code/slot 需先查真实值替换）

先查真实指标与机构（把结果替换进下方模板的 `<替换>` 位置）：
```bash
mysql -uroot -pdjdev -e "SELECT metric_code, metric_name, val_slot FROM PERF_METRIC_DEF WHERE base_dim='EMP' AND status='ACTIVE' AND deleted=0 ORDER BY metric_code LIMIT 2" yiti
mysql -uroot -pdjdev -e "SELECT metric_code, metric_name, val_slot FROM PERF_METRIC_DEF WHERE base_dim='ORG' AND status='ACTIVE' AND deleted=0 ORDER BY metric_code LIMIT 2" yiti
mysql -uroot -pdjdev -e "SELECT org_code, org_name FROM EXT_ORG_INFO LIMIT 5" yiti
```

```sql
-- 大屏种子配置（2026-07-12）：5 数据源 + 3 屏 + 区块 + 3 示例点位
-- 目标库 yiti 手工执行（onepl_test_bootstrap 不需要种子）。幂等：先删后插。
-- ⚠ 执行前：把 <EMP_M1_*>/<ORG_M1_*> 等占位替换为上一步查出的真实 metric_code/metric_name/val_slot；
--          把 <ORG1..3_*> 替换为真实机构号/名称；坐标为示例（西安/宝鸡/渭南市区），管理员可后台再调。

DELETE FROM RPT_SCREEN_BLOCK WHERE screen_id IN (9101,9102,9103);
DELETE FROM RPT_SCREEN WHERE id IN (9101,9102,9103);
DELETE FROM RPT_SCREEN_DATASOURCE WHERE id BETWEEN 9001 AND 9005;
DELETE FROM RPT_SCREEN_MAP_POINT WHERE org_code IN ('<ORG1_CODE>','<ORG2_CODE>','<ORG3_CODE>');

-- 数据源
INSERT INTO RPT_SCREEN_DATASOURCE (id, ds_code, ds_name, ds_type, source_kind, config_json, time_param_json, status, remark, created_by) VALUES
(9001,'SCRDS_SEED01','员工核心指标(宽表)','TIMESERIES','WIDE_TABLE',
 '{"table":"EMP_INDEX_RESULT","subjectCol":"emp_id","subjectParam":"empId","metrics":[{"metricCode":"<EMP_M1_CODE>","metricName":"<EMP_M1_NAME>","slot":<EMP_M1_SLOT>},{"metricCode":"<EMP_M2_CODE>","metricName":"<EMP_M2_NAME>","slot":<EMP_M2_SLOT>}]}',
 '["LATEST","LAST_10D","LAST_1M","LAST_6M_EOM"]','ACTIVE','种子','SEED'),
(9002,'SCRDS_SEED02','机构核心指标(宽表)','TIMESERIES','WIDE_TABLE',
 '{"table":"ORG_INDEX_RESULT","subjectCol":"org_code","subjectParam":"orgCode","metrics":[{"metricCode":"<ORG_M1_CODE>","metricName":"<ORG_M1_NAME>","slot":<ORG_M1_SLOT>},{"metricCode":"<ORG_M2_CODE>","metricName":"<ORG_M2_NAME>","slot":<ORG_M2_SLOT>}]}',
 '["LATEST","LAST_10D","LAST_1M","LAST_6M_EOM"]','ACTIVE','种子','SEED'),
(9003,'SCRDS_SEED03','个人KPI(月度)','TIMESERIES','KPI_RESULT',
 '{"cycleType":"MONTHLY"}','["LATEST","LAST_6M_EOM"]','ACTIVE','种子','SEED'),
(9004,'SCRDS_SEED04','全省机构排名(存款)','SINGLE','CUSTOM_SQL',
 '{"sql":"SELECT r.org_code, o.org_name, r.val_<ORG_M1_SLOT> AS 指标值, RANK() OVER (ORDER BY r.val_<ORG_M1_SLOT> DESC) AS 排名 FROM ORG_INDEX_RESULT r JOIN EXT_ORG_INFO o ON o.org_code = r.org_code WHERE r.data_date = (SELECT MAX(data_date) FROM ORG_INDEX_RESULT) ORDER BY r.val_<ORG_M1_SLOT> DESC","dateCol":null}',
 NULL,'ACTIVE','种子','SEED'),
(9005,'SCRDS_SEED05','在途流程概览','SINGLE','CUSTOM_SQL',
 '{"sql":"SELECT ''在途任务'' AS 名称, COUNT(*) AS 数量 FROM ACT_RU_TASK","dateCol":null}',
 NULL,'ACTIVE','种子','SEED');

-- 屏
INSERT INTO RPT_SCREEN (id, screen_code, screen_name, view_level, status, created_by) VALUES
(9101,'SCR_PROVINCE','省分行经营总览','PROVINCE','ACTIVE','SEED'),
(9102,'SCR_BRANCH','支行经营详情','BRANCH','ACTIVE','SEED'),
(9103,'SCR_PERSON','个人业绩详情','PERSON','ACTIVE','SEED');

-- 省分行总览：LEFT 指标卡+趋势，RIGHT 排名（跳支行）+流程（MAIN=地图，不配区块）
INSERT INTO RPT_SCREEN_BLOCK (screen_id, region, row_no, col_no, width_pct, height_pct, component_type, bind_json, style_json, drill_json) VALUES
(9101,'LEFT',1,1,100,40,'METRIC_CARD',
 '{"dsId":9002,"period":"LATEST","items":[{"col":"<ORG_M1_NAME>","label":"<ORG_M1_NAME>"},{"col":"<ORG_M2_NAME>","label":"<ORG_M2_NAME>"}]}',
 '{"title":"全省核心指标","unit":"","decimals":2,"refreshSec":60}',
 '{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M","LAST_6M_EOM"]}'),
(9101,'LEFT',2,1,100,60,'LINE_TREND',
 '{"dsId":9002,"period":"LAST_1M","items":[{"col":"<ORG_M1_NAME>","label":"<ORG_M1_NAME>"}]}',
 '{"title":"<ORG_M1_NAME>趋势(近1月)","decimals":2,"refreshSec":300}', '{}'),
(9101,'RIGHT',1,1,100,60,'RANK_LIST',
 '{"dsId":9004,"period":"LATEST","nameCol":"org_name","valueCol":"指标值"}',
 '{"title":"支行排名","decimals":2,"refreshSec":300}',
 '{"jump":{"targetScreenCode":"SCR_BRANCH","params":{"orgCode":"$col:org_code"}}}'),
(9101,'RIGHT',2,1,100,40,'FLOW_STATUS',
 '{"dsId":9005,"period":"LATEST","nameCol":"名称","valueCol":"数量"}',
 '{"title":"流程概览","refreshSec":120}', '{}');

-- 支行详情：核心指标卡 + 趋势 + 排名列表（占位：人员列表一期用机构排名 SQL 同款思路，管理员可改）
INSERT INTO RPT_SCREEN_BLOCK (screen_id, region, row_no, col_no, width_pct, height_pct, component_type, bind_json, style_json, drill_json) VALUES
(9102,'MAIN',1,1,100,30,'METRIC_CARD',
 '{"dsId":9002,"period":"LATEST","items":[{"col":"<ORG_M1_NAME>","label":"<ORG_M1_NAME>"},{"col":"<ORG_M2_NAME>","label":"<ORG_M2_NAME>"}]}',
 '{"title":"本支行核心指标","decimals":2,"refreshSec":60}',
 '{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M","LAST_6M_EOM"]}'),
(9102,'MAIN',2,1,100,40,'LINE_TREND',
 '{"dsId":9002,"period":"LAST_6M_EOM","items":[{"col":"<ORG_M1_NAME>","label":"<ORG_M1_NAME>"},{"col":"<ORG_M2_NAME>","label":"<ORG_M2_NAME>"}]}',
 '{"title":"核心指标趋势(近6个月末)","decimals":2,"refreshSec":300}', '{}'),
(9102,'MAIN',3,1,100,30,'PIE_SHARE',
 '{"dsId":9004,"period":"LATEST","nameCol":"org_name","valueCol":"指标值"}',
 '{"title":"全省份额占比","refreshSec":300}', '{}');

-- 个人详情：KPI 卡 + KPI 趋势 + 个人指标卡
INSERT INTO RPT_SCREEN_BLOCK (screen_id, region, row_no, col_no, width_pct, height_pct, component_type, bind_json, style_json, drill_json) VALUES
(9103,'MAIN',1,1,50,30,'METRIC_CARD',
 '{"dsId":9003,"period":"LATEST","items":[{"col":"KPI总分","label":"KPI总分"}]}',
 '{"title":"最新KPI","decimals":2,"refreshSec":300}',
 '{"drillEnabled":true,"drillPeriods":["LAST_6M_EOM"]}'),
(9103,'MAIN',1,2,50,30,'METRIC_CARD',
 '{"dsId":9001,"period":"LATEST","items":[{"col":"<EMP_M1_NAME>","label":"<EMP_M1_NAME>"},{"col":"<EMP_M2_NAME>","label":"<EMP_M2_NAME>"}]}',
 '{"title":"个人核心指标","decimals":2,"refreshSec":300}',
 '{"drillEnabled":true,"drillPeriods":["LAST_10D","LAST_1M"]}'),
(9103,'MAIN',2,1,100,70,'LINE_TREND',
 '{"dsId":9003,"period":"LAST_6M_EOM","items":[{"col":"KPI总分","label":"KPI总分"}]}',
 '{"title":"KPI 历史趋势","decimals":2,"refreshSec":300}', '{}');

-- 示例点位（坐标：西安/宝鸡/渭南市中心附近，管理员后台可改）
INSERT INTO RPT_SCREEN_MAP_POINT (org_code, org_name, lng, lat, target_screen_code, status) VALUES
('<ORG1_CODE>','<ORG1_NAME>',108.948024,34.263161,'SCR_BRANCH','ACTIVE'),
('<ORG2_CODE>','<ORG2_NAME>',107.237743,34.361979,'SCR_BRANCH','ACTIVE'),
('<ORG3_CODE>','<ORG3_NAME>',109.502882,34.499381,'SCR_BRANCH','ACTIVE');
```

- [ ] **Step 4: 执行并验证**

```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-07-12-screen-dashboard-resources.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-12-screen-dashboard-resources.sql
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-07-12-screen-dashboard-seed.sql
mysql -uroot -pdjdev -e "SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'R_RPT_SCR%' OR RESOURCE_ID LIKE 'M_RPT_SCR%'" yiti
mysql -uroot -pdjdev -e "SELECT id, screen_code FROM RPT_SCREEN WHERE id IN (9101,9102,9103)" yiti
```
Expected: 资源计数 15（13 API + 2 菜单）；3 块屏存在。

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/sql/2026-07-12-screen-dashboard-resources.sql \
        docs/superpowers/sql/2026-07-12-screen-dashboard-seed.sql
git commit -m "feat(screen): PT_RESOURCE 16 资源注册 + 三级大屏种子配置

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 11: 前端 api/screen.js + 路由注册

**Files:**
- Create: `xanzc_frontend/src/api/screen.js`
- Modify: `xanzc_frontend/src/router/index.js`（顶层全屏路由 + DefaultLayout 内两条管理页子路由）

**Interfaces:**
- Consumes: Task 9 的 12 端点；`src/api/http.js` 的 `call(method, url, config, fallback)`（url 不含 `/api` 前缀）
- Produces（Task 12~16 依赖的函数签名）: `listScreenDatasources / saveScreenDatasource / updateScreenDatasource / deleteScreenDatasource / tryRunScreenDatasource / listScreens / getScreen / saveScreen / deleteScreen / listMapPoints / saveMapPoints / getScreenView / queryScreenData`；路由 `/screen/:screenCode?orgCode=&empId=`、`/screen-admin/datasources`、`/screen-admin/designer`

- [ ] **Step 1: 写 api/screen.js**

```js
// 经营管理大屏 API —— 对接 yiti report-analytics-center screen 子域
//
// 后端控制器（report-analytics-center/controller/）：
//   - ScreenDatasourceAdminController  /api/screen/admin/datasources[...]
//   - ScreenConfigAdminController      /api/screen/admin/screens[...] + /map-points
//   - ScreenViewController             GET /api/screen/view/{screenCode}
//   - ScreenDataController             POST /api/screen/data
//
// 大屏展示类接口一律不给 mock 兜底（宁可空屏不给假数据，同 Dashboard.vue 先例）。
import { call } from './http';

// ===== 数据源管理 =====
export function listScreenDatasources(params = {}) {
  return call('get', '/screen/admin/datasources', { params }, []);
}
export function saveScreenDatasource(data) {
  return call('post', '/screen/admin/datasources', { data });
}
export function updateScreenDatasource(id, data) {
  return call('put', `/screen/admin/datasources/${id}`, { data });
}
export function deleteScreenDatasource(id) {
  return call('delete', `/screen/admin/datasources/${id}`, {});
}
export function tryRunScreenDatasource(data) {
  return call('post', '/screen/admin/datasources/try-run', { data });
}

// ===== 大屏布局管理 =====
export function listScreens() {
  return call('get', '/screen/admin/screens', {}, []);
}
export function getScreen(id) {
  return call('get', `/screen/admin/screens/${id}`, {}, null);
}
export function saveScreen(data) {
  return call('post', '/screen/admin/screens', { data });
}
export function deleteScreen(id) {
  return call('delete', `/screen/admin/screens/${id}`, {});
}
export function listMapPoints() {
  return call('get', '/screen/admin/map-points', {}, []);
}
export function saveMapPoints(points) {
  return call('put', '/screen/admin/map-points', { data: points });
}

// ===== 大屏运行时 =====
export function getScreenView(screenCode) {
  return call('get', `/screen/view/${screenCode}`, {}, null);
}
export function queryScreenData(body) {
  return call('post', '/screen/data', { data: body }, null);
}
```

- [ ] **Step 2: 改路由**。在 `src/router/index.js` 的 routes 数组中，`/login` 路由对象**之后**、`DefaultLayout` 对象**之前**插入顶层全屏路由：

```js
  // 经营大屏：顶层全屏路由（不进 DefaultLayout，无 sidebar/header；仍走登录守卫）
  {
    path: '/screen/:screenCode',
    name: 'ScreenView',
    component: () => import('@/views/screen/ScreenView.vue'),
    meta: { title: '经营大屏' }
  },
```

在 `DefaultLayout.children` 内"报表分析"分组的路由后追加两条管理页：

```js
      { path: 'screen-admin/datasources', name: 'ScreenAdminDs',       component: () => import('@/views/screen/admin/Datasources.vue'), meta: { title: '大屏数据源', group: '报表分析' } },
      { path: 'screen-admin/designer',    name: 'ScreenAdminDesigner', component: () => import('@/views/screen/admin/Designer.vue'),    meta: { title: '大屏设计器', group: '报表分析' } },
```

- [ ] **Step 3: 验证路由编译**（组件文件尚未创建，先建两个占位文件避免 dev server 报错——Task 12/15/16 会覆写）

```bash
# 若 node_modules 未安装，先在 xanzc_frontend 下执行 npm install
mkdir -p xanzc_frontend/src/views/screen/admin xanzc_frontend/src/views/screen/components
printf '<template><div>placeholder</div></template>\n' > xanzc_frontend/src/views/screen/ScreenView.vue
printf '<template><div>placeholder</div></template>\n' > xanzc_frontend/src/views/screen/admin/Datasources.vue
printf '<template><div>placeholder</div></template>\n' > xanzc_frontend/src/views/screen/admin/Designer.vue
cd xanzc_frontend && npx vite build --logLevel error && cd ..
```
Expected: 构建通过无报错。

- [ ] **Step 4: Commit**

```bash
git add xanzc_frontend/src/api/screen.js xanzc_frontend/src/router/index.js xanzc_frontend/src/views/screen/
git commit -m "feat(screen): 前端大屏 API 封装 + 全屏路由/管理页路由注册

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 12: 深色主题 + ScreenView / ScreenRenderer / BlockContainer 渲染骨架

**Files:**
- Create: `xanzc_frontend/src/styles/screen.scss`
- Create(覆写占位): `xanzc_frontend/src/views/screen/ScreenView.vue`
- Create: `xanzc_frontend/src/views/screen/components/ScreenRenderer.vue`
- Create: `xanzc_frontend/src/views/screen/components/BlockContainer.vue`

**Interfaces:**
- Consumes: Task 11 `getScreenView / queryScreenData`
- Produces（Task 13/14 依赖的组件契约）:
  - `ScreenRenderer` props: `{ screen, blocks, mapPoints, context }`（context = `{orgCode, empId}`）
  - `BlockContainer` props: `{ block, context }`；内部把 `bind_json/style_json/drill_json` 解析为 `bind/styleCfg/drill` 传给子组件
  - 子组件统一 props: `{ columns, rows, bind, styleCfg }`，统一事件 `@item-click="{ col, label, row }"`（row 为 `{列名: 值}` 映射）——5 个组件与 DrillTrend 都遵守
  - 深色主题 CSS 变量：`--scr-bg / --scr-bg-card / --scr-border / --scr-cyan / --scr-text / --scr-text-dim / --scr-num`

- [ ] **Step 1: 写 screen.scss**

```scss
// 经营大屏深色主题（独立于后台浅色 tokens；.screen-root 作用域内生效）
.screen-root {
  --scr-bg: #050e2b;
  --scr-bg-deep: #03081c;
  --scr-bg-card: rgba(10, 32, 74, 0.55);
  --scr-border: rgba(0, 229, 255, 0.25);
  --scr-cyan: #00e5ff;
  --scr-blue: #3d7eff;
  --scr-text: #d5e6ff;
  --scr-text-dim: #7d9bc9;
  --scr-num: #ffd76a;
  --scr-up: #00e676;
  --scr-down: #ff5252;

  position: fixed;
  inset: 0;
  background: radial-gradient(ellipse at 50% 0%, #0a1f4e 0%, var(--scr-bg) 45%, var(--scr-bg-deep) 100%);
  color: var(--scr-text);
  overflow: hidden;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;

  .scr-stage {           // 1920×1080 设计稿，transform: scale 等比缩放居中
    position: absolute;
    top: 50%;
    left: 50%;
    width: 1920px;
    height: 1080px;
    transform-origin: center center;
    display: flex;
    flex-direction: column;
  }

  .scr-header {
    height: 72px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    border-bottom: 1px solid var(--scr-border);

    .scr-title {
      font-size: 30px;
      font-weight: 700;
      letter-spacing: 6px;
      background: linear-gradient(180deg, #fff 20%, var(--scr-cyan) 100%);
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
    }
    .scr-back {
      position: absolute;
      left: 24px;
      color: var(--scr-text-dim);
      cursor: pointer;
      font-size: 14px;
      &:hover { color: var(--scr-cyan); }
    }
    .scr-clock {
      position: absolute;
      right: 24px;
      color: var(--scr-text-dim);
      font-size: 15px;
      font-variant-numeric: tabular-nums;
    }
  }

  .scr-body { flex: 1; display: flex; gap: 12px; padding: 12px 16px 16px; min-height: 0; }
  .scr-col { display: flex; flex-direction: column; gap: 12px; min-width: 0; min-height: 0; }
  .scr-row { display: flex; gap: 12px; min-height: 0; }

  .scr-block {
    background: var(--scr-bg-card);
    border: 1px solid var(--scr-border);
    border-radius: 6px;
    box-shadow: inset 0 0 24px rgba(0, 229, 255, 0.05);
    display: flex;
    flex-direction: column;
    min-width: 0;
    min-height: 0;
    overflow: hidden;

    .scr-block-h {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px 4px;
      font-size: 15px;
      color: var(--scr-cyan);
      letter-spacing: 2px;
      flex: none;
      &::before { content: '▍'; margin-right: 4px; }
    }
    .scr-block-body { flex: 1; min-height: 0; padding: 4px 10px 10px; position: relative; }
    .scr-block-err { color: var(--scr-down); font-size: 13px; padding: 12px; }
  }
}
```

- [ ] **Step 2: 覆写 ScreenView.vue**

```vue
<template>
  <div class="screen-root">
    <div class="scr-stage" :style="{ transform: `translate(-50%, -50%) scale(${scale})` }">
      <div class="scr-header">
        <span class="scr-back" @click="goBack">‹ 返回</span>
        <span class="scr-title">{{ view?.screen?.screenName || '经营管理大屏' }}</span>
        <span class="scr-clock">{{ clock }}</span>
      </div>
      <div class="scr-body" v-if="view">
        <ScreenRenderer :screen="view.screen" :blocks="view.blocks"
                        :map-points="view.mapPoints" :context="context" />
      </div>
      <div v-else-if="loadError" class="scr-block-err" style="margin:auto">
        大屏配置加载失败：{{ loadError }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getScreenView } from '@/api/screen';
import ScreenRenderer from './components/ScreenRenderer.vue';

const route = useRoute();
const router = useRouter();

const view = ref(null);
const loadError = ref('');
const scale = ref(1);
const clock = ref('');

// 路由参数即取数上下文：同一份屏配置服务所有支行/员工
const context = computed(() => ({
  orgCode: route.query.orgCode || '',
  empId: route.query.empId || ''
}));

async function load() {
  loadError.value = '';
  view.value = null;
  try {
    view.value = await getScreenView(route.params.screenCode);
  } catch (e) {
    loadError.value = e?.message || '未知错误';
  }
}

function fit() {
  // 1920×1080 设计稿等比缩放，电视墙/投屏一致
  scale.value = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
}

let clockTimer = null;
function tick() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/workspace');
}

watch(() => [route.params.screenCode, route.query.orgCode, route.query.empId], load);

onMounted(() => {
  fit();
  window.addEventListener('resize', fit);
  tick();
  clockTimer = setInterval(tick, 1000);
  load();
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', fit);
  clearInterval(clockTimer);
});
</script>

<style lang="scss">
@use '@/styles/screen.scss';
</style>
```

- [ ] **Step 3: 写 ScreenRenderer.vue**

```vue
<template>
  <template v-for="col in columns" :key="col.region">
    <!-- PROVINCE 的 MAIN 固定渲染地图 -->
    <div class="scr-col" :style="{ flex: col.flex }">
      <MapCenter v-if="col.isMap" :map-points="mapPoints" />
      <template v-else>
        <div v-for="row in col.rows" :key="row.rowNo" class="scr-row"
             :style="{ flex: row.heightPct + ' 1 0' }">
          <BlockContainer v-for="b in row.blocks" :key="b.id"
                          :block="b" :context="context"
                          :style="{ flex: b.widthPct + ' 1 0' }" />
        </div>
      </template>
    </div>
  </template>
</template>

<script setup>
import { computed } from 'vue';
import BlockContainer from './BlockContainer.vue';
import MapCenter from './MapCenter.vue';

const props = defineProps({
  screen: { type: Object, required: true },
  blocks: { type: Array, default: () => [] },
  mapPoints: { type: Array, default: () => [] },
  context: { type: Object, default: () => ({}) }
});

// region → 行分组（rowNo 升序，行内 colNo 升序，行高取首块 heightPct）
function groupRows(regionBlocks) {
  const byRow = new Map();
  for (const b of regionBlocks) {
    if (!byRow.has(b.rowNo)) byRow.set(b.rowNo, []);
    byRow.get(b.rowNo).push(b);
  }
  return [...byRow.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([rowNo, list]) => ({
      rowNo,
      heightPct: list[0]?.heightPct || 50,
      blocks: list.sort((a, b) => a.colNo - b.colNo)
    }));
}

const columns = computed(() => {
  const isProvince = props.screen?.viewLevel === 'PROVINCE';
  const regions = ['LEFT', 'MAIN', 'RIGHT'];
  const out = [];
  for (const region of regions) {
    const regionBlocks = props.blocks.filter(b => b.region === region);
    const isMap = isProvince && region === 'MAIN';
    if (!isMap && regionBlocks.length === 0) continue; // 空区域不占位（MAIN 地图除外）
    out.push({
      region,
      isMap,
      flex: region === 'MAIN' ? 2 : 1, // MAIN 双倍宽（省级 25/50/25）
      rows: groupRows(regionBlocks)
    });
  }
  return out;
});
</script>
```

- [ ] **Step 4: 写 BlockContainer.vue**（取数 + 轮询 + 钻取状态机 + 跳转）

```vue
<template>
  <div class="scr-block">
    <div class="scr-block-h">
      <span>{{ drillItem ? `${drillItem.label} · 趋势钻取` : (styleCfg.title || '未命名区块') }}</span>
      <span v-if="drillItem" style="cursor:pointer;font-size:13px" @click="drillItem = null">‹ 返回</span>
    </div>
    <div class="scr-block-body" v-loading="loading" element-loading-background="rgba(5,14,43,.6)">
      <div v-if="error" class="scr-block-err">{{ error }}</div>
      <DrillTrend v-else-if="drillItem" :bind="bind" :context="context"
                  :item="drillItem" :periods="drill.drillPeriods || ['LAST_10D']" />
      <component v-else-if="data" :is="componentMap[block.componentType]"
                 :columns="data.columns" :rows="data.rows"
                 :bind="bind" :style-cfg="styleCfg" @item-click="onItemClick" />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { queryScreenData } from '@/api/screen';
import MetricCard from './MetricCard.vue';
import LineTrend from './LineTrend.vue';
import PieShare from './PieShare.vue';
import RankList from './RankList.vue';
import FlowStatus from './FlowStatus.vue';
import DrillTrend from './DrillTrend.vue';

const props = defineProps({
  block: { type: Object, required: true },
  context: { type: Object, default: () => ({}) }
});

const router = useRouter();
const componentMap = {
  METRIC_CARD: MetricCard,
  LINE_TREND: LineTrend,
  PIE_SHARE: PieShare,
  RANK_LIST: RankList,
  FLOW_STATUS: FlowStatus
};

function parse(json, fallback = {}) {
  try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
}
const bind = computed(() => parse(props.block.bindJson));
const styleCfg = computed(() => parse(props.block.styleJson));
const drill = computed(() => parse(props.block.drillJson));

const data = ref(null);
const loading = ref(false);
const error = ref('');
const drillItem = ref(null); // { col, label } —— 非空即钻取态

async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await queryScreenData({
      dsId: bind.value.dsId,
      period: bind.value.period || 'LATEST',
      contextParams: { orgCode: props.context.orgCode || null, empId: props.context.empId || null }
    });
  } catch (e) {
    error.value = e?.message || '取数失败';
  } finally {
    loading.value = false;
  }
}

// 点击数据项：优先区块内钻取（时序数据源），否则按 jump 配置跳屏
function onItemClick({ col, label, row }) {
  if (drill.value.drillEnabled) {
    drillItem.value = { col, label };
    return;
  }
  const jump = drill.value.jump;
  if (!jump?.targetScreenCode) return;
  const query = {};
  for (const [k, v] of Object.entries(jump.params || {})) {
    if (typeof v === 'string' && v.startsWith('$col:')) query[k] = row?.[v.slice(5)];
    else if (typeof v === 'string' && v.startsWith('$ctx:')) query[k] = props.context[v.slice(5)];
    else query[k] = v;
  }
  router.push({ path: `/screen/${jump.targetScreenCode}`, query });
}

// 区块级轮询（refreshSec，0=不刷新；页面隐藏时跳过）
let timer = null;
onMounted(() => {
  load();
  const sec = Number(styleCfg.value.refreshSec || 60);
  if (sec > 0) {
    timer = setInterval(() => { if (!document.hidden && !drillItem.value) load(); }, sec * 1000);
  }
});
onBeforeUnmount(() => { if (timer) clearInterval(timer); });
</script>
```

- [ ] **Step 5: 编译验证**（5 个子组件尚未创建，先建占位再编译——Task 13/14 覆写）

```bash
for f in MetricCard LineTrend PieShare RankList FlowStatus DrillTrend MapCenter; do
  printf '<template><div class="scr-block-err">%s 待实现</div></template>\n<script setup>\ndefineProps(["columns","rows","bind","styleCfg","mapPoints","context","item","periods"]);\n</script>\n' "$f" \
    > "xanzc_frontend/src/views/screen/components/$f.vue"
done
cd xanzc_frontend && npx vite build --logLevel error && cd ..
```
Expected: 构建通过。

- [ ] **Step 6: Commit**

```bash
git add xanzc_frontend/src/styles/screen.scss xanzc_frontend/src/views/screen/
git commit -m "feat(screen): 大屏深色主题 + 渲染引擎骨架（scale自适应/轮询/钻取状态机/跳转）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 13: 5 个可视化组件 + DrillTrend 钻取组件

**Files:**
- Create(覆写占位): `xanzc_frontend/src/views/screen/components/MetricCard.vue`、`LineTrend.vue`、`PieShare.vue`、`RankList.vue`、`FlowStatus.vue`、`DrillTrend.vue`

**Interfaces:**
- Consumes: Task 12 的统一 props 契约 `{columns, rows, bind, styleCfg}` + `@item-click`；Task 11 `queryScreenData`（仅 DrillTrend 自取数）
- Produces: 5 个组件可被 BlockContainer 动态分发；DrillTrend props `{bind, context, item, periods}`

**共享约定**：`rows` 二维数组按 `columns` 定位取值；数值格式化 `decimals`（默认 2）+ 千分位；echarts 均按需引入（`PieChart as EPie` 规避图标重名坑）。

- [ ] **Step 1: MetricCard.vue**（取最后一行=最新时点；每个绑定项一张数值卡；点击项触发钻取/跳转）

```vue
<template>
  <div class="mc-wrap">
    <div v-for="it in items" :key="it.col" class="mc-item" @click="onClick(it)">
      <div class="mc-label">{{ it.label || it.col }}</div>
      <div class="mc-value">{{ fmt(valueOf(it.col)) }}<span class="mc-unit">{{ styleCfg.unit || '' }}</span></div>
    </div>
    <div v-if="!items.length" class="scr-block-err">未绑定数据项</div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const items = computed(() => props.bind.items || []);
const lastRow = computed(() => props.rows.length ? props.rows[props.rows.length - 1] : null);

function valueOf(col) {
  if (!lastRow.value) return null;
  const idx = props.columns.indexOf(col);
  return idx >= 0 ? lastRow.value[idx] : null;
}
function fmt(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  const d = props.styleCfg.decimals ?? 2;
  return n.toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
}
function rowMap() {
  const m = {};
  if (lastRow.value) props.columns.forEach((c, i) => { m[c] = lastRow.value[i]; });
  return m;
}
function onClick(it) {
  emit('item-click', { col: it.col, label: it.label || it.col, row: rowMap() });
}
</script>

<style lang="scss" scoped>
.mc-wrap { display: flex; flex-wrap: wrap; gap: 10px; height: 100%; align-content: center; }
.mc-item {
  flex: 1 1 40%;
  min-width: 120px;
  text-align: center;
  padding: 10px 6px;
  border: 1px solid var(--scr-border);
  border-radius: 6px;
  cursor: pointer;
  transition: box-shadow .2s;
  &:hover { box-shadow: 0 0 12px rgba(0, 229, 255, .35); }
}
.mc-label { font-size: 14px; color: var(--scr-text-dim); margin-bottom: 6px; }
.mc-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--scr-num);
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 14px rgba(255, 215, 106, .45);
}
.mc-unit { font-size: 13px; color: var(--scr-text-dim); margin-left: 4px; }
</style>
```

- [ ] **Step 2: LineTrend.vue**（第一列为 X 轴；items 指定的列为多条序列；点击序列点触发 item-click）

```vue
<template>
  <v-chart class="lt-chart" :option="option" autoresize @click="onChartClick" />
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const PALETTE = ['#00e5ff', '#ffd76a', '#3d7eff', '#00e676', '#ff8a65'];

const seriesCols = computed(() => {
  const its = props.bind.items || [];
  return its.length ? its.map(i => i.col) : props.columns.slice(1);
});

const option = computed(() => ({
  color: props.styleCfg.colors?.length ? props.styleCfg.colors : PALETTE,
  grid: { top: 34, right: 16, bottom: 26, left: 56 },
  tooltip: { trigger: 'axis', backgroundColor: 'rgba(5,14,43,.9)', borderColor: 'rgba(0,229,255,.4)',
             textStyle: { color: '#d5e6ff' } },
  legend: { top: 4, textStyle: { color: '#7d9bc9' } },
  xAxis: { type: 'category', data: props.rows.map(r => r[0]),
           axisLine: { lineStyle: { color: 'rgba(125,155,201,.4)' } },
           axisLabel: { color: '#7d9bc9' } },
  yAxis: { type: 'value', axisLabel: { color: '#7d9bc9' },
           splitLine: { lineStyle: { color: 'rgba(125,155,201,.15)' } } },
  series: seriesCols.value.map(col => {
    const idx = props.columns.indexOf(col);
    return {
      name: col, type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      data: props.rows.map(r => (idx >= 0 ? r[idx] : null))
    };
  })
}));

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const row = {};
  props.columns.forEach((c, i) => { row[c] = props.rows[p.dataIndex]?.[i]; });
  emit('item-click', { col: p.seriesName, label: p.seriesName, row });
}
</script>

<style scoped>
.lt-chart { width: 100%; height: 100%; }
</style>
```

- [ ] **Step 3: PieShare.vue**（nameCol/valueCol → 占比饼图，取前 10）

```vue
<template>
  <v-chart class="ps-chart" :option="option" autoresize @click="onChartClick" />
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { PieChart as EPie } from 'echarts/charts';
import { TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';

use([CanvasRenderer, EPie, TooltipComponent, LegendComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const pieData = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  return props.rows.slice(0, 10).map(r => ({ name: String(r[ni]), value: Number(r[vi]) || 0 }));
});

const option = computed(() => ({
  color: ['#00e5ff', '#3d7eff', '#ffd76a', '#00e676', '#ff8a65', '#ba68c8', '#4dd0e1', '#fff176', '#90caf9', '#a5d6a7'],
  tooltip: { trigger: 'item', backgroundColor: 'rgba(5,14,43,.9)', textStyle: { color: '#d5e6ff' } },
  legend: { orient: 'vertical', right: 4, top: 'middle', textStyle: { color: '#7d9bc9', fontSize: 12 } },
  series: [{
    type: 'pie', radius: ['38%', '68%'], center: ['38%', '50%'],
    label: { color: '#d5e6ff', formatter: '{b}\n{d}%' },
    itemStyle: { borderColor: '#050e2b', borderWidth: 2 },
    data: pieData.value
  }]
}));

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const src = props.rows[props.rows.findIndex(r => String(r[props.columns.indexOf(props.bind.nameCol)]) === p.name)];
  const row = {};
  props.columns.forEach((c, i) => { row[c] = src?.[i]; });
  emit('item-click', { col: props.bind.valueCol, label: p.name, row });
}
</script>

<style scoped>
.ps-chart { width: 100%; height: 100%; }
</style>
```

- [ ] **Step 4: RankList.vue**（nameCol/valueCol → 排名条形列表；行点击触发跳转）

```vue
<template>
  <div class="rl-wrap">
    <div v-for="(r, i) in ranked" :key="i" class="rl-row" @click="onClick(r)">
      <span class="rl-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
      <span class="rl-name">{{ r.name }}</span>
      <div class="rl-bar"><div class="rl-fill" :style="{ width: r.pct + '%' }" /></div>
      <span class="rl-val">{{ fmt(r.value) }}</span>
    </div>
    <div v-if="!ranked.length" class="scr-block-err">暂无数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const ranked = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  const list = props.rows.map(r => ({ name: String(r[ni]), value: Number(r[vi]) || 0, raw: r }))
      .sort((a, b) => b.value - a.value);
  const max = list[0]?.value || 1;
  return list.map(x => ({ ...x, pct: Math.max(4, Math.round((x.value / max) * 100)) }));
});

function fmt(v) {
  const d = props.styleCfg.decimals ?? 2;
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
}
function onClick(r) {
  const row = {};
  props.columns.forEach((c, i) => { row[c] = r.raw[i]; });
  emit('item-click', { col: props.bind.valueCol, label: r.name, row });
}
</script>

<style lang="scss" scoped>
.rl-wrap { height: 100%; overflow-y: auto; display: flex; flex-direction: column; gap: 6px;
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(0, 229, 255, .3); border-radius: 2px; } }
.rl-row { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 3px 2px;
  &:hover { background: rgba(0, 229, 255, .08); border-radius: 4px; } }
.rl-no { width: 22px; height: 22px; border-radius: 4px; text-align: center; line-height: 22px;
  font-size: 13px; background: rgba(125, 155, 201, .2); color: var(--scr-text-dim); flex: none;
  &.top { background: linear-gradient(135deg, #ffd76a, #ff8a65); color: #1b1b1b; font-weight: 700; } }
.rl-name { width: 96px; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: none; }
.rl-bar { flex: 1; height: 8px; background: rgba(125, 155, 201, .15); border-radius: 4px; overflow: hidden; }
.rl-fill { height: 100%; background: linear-gradient(90deg, #3d7eff, #00e5ff); border-radius: 4px; }
.rl-val { width: 90px; text-align: right; font-size: 14px; color: var(--scr-num);
  font-variant-numeric: tabular-nums; flex: none; }
</style>
```

- [ ] **Step 5: FlowStatus.vue**（nameCol/valueCol → 状态磁贴网格）

```vue
<template>
  <div class="fs-wrap">
    <div v-for="(t, i) in tiles" :key="i" class="fs-tile" @click="onClick(t)">
      <div class="fs-num">{{ t.value }}</div>
      <div class="fs-name">{{ t.name }}</div>
    </div>
    <div v-if="!tiles.length" class="scr-block-err">暂无流程数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const tiles = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  return props.rows.map(r => ({ name: String(r[ni]), value: r[vi], raw: r }));
});

function onClick(t) {
  const row = {};
  props.columns.forEach((c, i) => { row[c] = t.raw[i]; });
  emit('item-click', { col: props.bind.valueCol, label: t.name, row });
}
</script>

<style lang="scss" scoped>
.fs-wrap { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); gap: 10px;
  height: 100%; align-content: center; }
.fs-tile { text-align: center; padding: 12px 4px; border: 1px solid var(--scr-border); border-radius: 6px;
  cursor: pointer;
  &:hover { box-shadow: 0 0 12px rgba(0, 229, 255, .35); } }
.fs-num { font-size: 26px; font-weight: 700; color: var(--scr-cyan);
  text-shadow: 0 0 12px rgba(0, 229, 255, .5); font-variant-numeric: tabular-nums; }
.fs-name { font-size: 13px; color: var(--scr-text-dim); margin-top: 4px; }
</style>
```

- [ ] **Step 6: DrillTrend.vue**（钻取态：按周期重新取数渲染单指标折线 + 周期 tabs）

```vue
<template>
  <div class="dt-wrap">
    <div class="dt-tabs">
      <span v-for="p in periods" :key="p" class="dt-tab" :class="{ on: p === period }"
            @click="switchPeriod(p)">{{ PERIOD_LABELS[p] || p }}</span>
    </div>
    <div class="dt-chart-wrap" v-loading="loading" element-loading-background="rgba(5,14,43,.6)">
      <div v-if="error" class="scr-block-err">{{ error }}</div>
      <v-chart v-else class="dt-chart" :option="option" autoresize />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { queryScreenData } from '@/api/screen';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent]);

const props = defineProps({
  bind: { type: Object, required: true },      // 原区块 bind（含 dsId）
  context: { type: Object, default: () => ({}) },
  item: { type: Object, required: true },       // { col, label } 被钻取指标
  periods: { type: Array, default: () => ['LAST_10D'] }
});

const PERIOD_LABELS = { LAST_10D: '近10天', LAST_1M: '近1个月', LAST_6M_EOM: '近6个月末', LATEST: '最新' };

const period = ref(props.periods[0] || 'LAST_10D');
const data = ref(null);
const loading = ref(false);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await queryScreenData({
      dsId: props.bind.dsId,
      period: period.value,
      contextParams: { orgCode: props.context.orgCode || null, empId: props.context.empId || null }
    });
  } catch (e) {
    error.value = e?.message || '取数失败';
  } finally {
    loading.value = false;
  }
}
function switchPeriod(p) { period.value = p; load(); }
onMounted(load);

const option = computed(() => {
  const cols = data.value?.columns || [];
  const rows = data.value?.rows || [];
  const idx = cols.indexOf(props.item.col);
  return {
    grid: { top: 20, right: 16, bottom: 26, left: 56 },
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(5,14,43,.9)', textStyle: { color: '#d5e6ff' } },
    xAxis: { type: 'category', data: rows.map(r => r[0]),
             axisLabel: { color: '#7d9bc9' }, axisLine: { lineStyle: { color: 'rgba(125,155,201,.4)' } } },
    yAxis: { type: 'value', axisLabel: { color: '#7d9bc9' },
             splitLine: { lineStyle: { color: 'rgba(125,155,201,.15)' } } },
    series: [{
      name: props.item.label, type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: '#00e5ff', width: 2 }, itemStyle: { color: '#00e5ff' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(0,229,255,.35)' }, { offset: 1, color: 'rgba(0,229,255,0)' }] } },
      data: rows.map(r => (idx >= 0 ? r[idx] : null))
    }]
  };
});
</script>

<style lang="scss" scoped>
.dt-wrap { height: 100%; display: flex; flex-direction: column; }
.dt-tabs { display: flex; gap: 8px; flex: none; padding-bottom: 4px; }
.dt-tab { font-size: 13px; color: var(--scr-text-dim); cursor: pointer; padding: 2px 10px;
  border: 1px solid transparent; border-radius: 10px;
  &.on { color: var(--scr-cyan); border-color: var(--scr-border); } }
.dt-chart-wrap { flex: 1; min-height: 0; position: relative; }
.dt-chart { width: 100%; height: 100%; }
</style>
```

- [ ] **Step 7: 编译验证 + Commit**

```bash
cd xanzc_frontend && npx vite build --logLevel error && cd ..
git add xanzc_frontend/src/views/screen/components/
git commit -m "feat(screen): 5 种可视化组件 + 区块内趋势钻取组件

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 14: 陕西 geoJSON + MapCenter 地图组件

**Files:**
- Create: `xanzc_frontend/src/assets/geo/shaanxi.json`（新建 assets 目录，项目此前无此惯例）
- Create(覆写占位): `xanzc_frontend/src/views/screen/components/MapCenter.vue`

**Interfaces:**
- Consumes: Task 12 契约 `MapCenter` props `{mapPoints}`（`[{orgCode, orgName, lng, lat, targetScreenCode}]`）
- Produces: 省级屏中心地图；点位点击 → `/screen/{targetScreenCode}?orgCode=...`

- [ ] **Step 1: 获取陕西省 geoJSON**（阿里 DataV 公开数据，610000=陕西省含地级市边界）

```bash
mkdir -p xanzc_frontend/src/assets/geo
curl -sSf 'https://geo.datav.aliyun.com/areas_v3/bound/610000_full.json' \
  -o xanzc_frontend/src/assets/geo/shaanxi.json
head -c 120 xanzc_frontend/src/assets/geo/shaanxi.json
```
Expected: 输出以 `{"type":"FeatureCollection"` 开头。**若无外网**：改从任一本机可用途径获取同名文件放入该路径（内容必须是 FeatureCollection 格式的陕西省边界），此步不可跳过。

- [ ] **Step 2: 写 MapCenter.vue**

```vue
<template>
  <div class="scr-block mp-block">
    <v-chart class="mp-chart" :option="option" autoresize @click="onChartClick" />
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { use, registerMap } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { MapChart, EffectScatterChart } from 'echarts/charts';
import { GeoComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { useRouter } from 'vue-router';
import shaanxiGeo from '@/assets/geo/shaanxi.json';

use([CanvasRenderer, MapChart, EffectScatterChart, GeoComponent, TooltipComponent]);
registerMap('shaanxi', shaanxiGeo);

const props = defineProps({
  mapPoints: { type: Array, default: () => [] }
});
const router = useRouter();

const option = computed(() => ({
  tooltip: { backgroundColor: 'rgba(5,14,43,.9)', borderColor: 'rgba(0,229,255,.4)',
             textStyle: { color: '#d5e6ff' } },
  geo: {
    map: 'shaanxi',
    roam: false,
    layoutCenter: ['50%', '52%'],
    layoutSize: '92%',
    label: { show: true, color: '#7d9bc9', fontSize: 12 },
    itemStyle: {
      areaColor: 'rgba(13, 40, 96, .8)',
      borderColor: 'rgba(0, 229, 255, .6)',
      borderWidth: 1.2,
      shadowColor: 'rgba(0, 229, 255, .35)',
      shadowBlur: 16
    },
    emphasis: {
      label: { color: '#fff' },
      itemStyle: { areaColor: 'rgba(0, 229, 255, .25)' }
    }
  },
  series: [{
    name: '支行',
    type: 'effectScatter',
    coordinateSystem: 'geo',
    symbolSize: 14,
    rippleEffect: { brushType: 'stroke', scale: 3.2 },
    label: { show: true, position: 'right', color: '#ffd76a', fontSize: 13,
             formatter: p => p.name },
    itemStyle: { color: '#ffd76a', shadowColor: 'rgba(255,215,106,.8)', shadowBlur: 10 },
    tooltip: { formatter: p => `${p.name}<br/>点击进入支行大屏` },
    data: props.mapPoints.map(p => ({
      name: p.orgName,
      value: [Number(p.lng), Number(p.lat)],
      orgCode: p.orgCode,
      target: p.targetScreenCode || 'SCR_BRANCH'
    }))
  }]
}));

// 点击支行点位 → 跳对应支行详情屏（需求 3.1）
function onChartClick(p) {
  if (p?.seriesType !== 'effectScatter') return;
  const d = p.data || {};
  if (d.orgCode) {
    router.push({ path: `/screen/${d.target}`, query: { orgCode: d.orgCode } });
  }
}
</script>

<style scoped>
.mp-block { height: 100%; }
.mp-chart { width: 100%; height: 100%; }
</style>
```

- [ ] **Step 3: 编译验证 + Commit**

```bash
cd xanzc_frontend && npx vite build --logLevel error && cd ..
git add xanzc_frontend/src/assets/geo/shaanxi.json \
        xanzc_frontend/src/views/screen/components/MapCenter.vue
git commit -m "feat(screen): 陕西省地图组件（geoJSON 内置 + 支行点位涟漪散点 + 点击跳支行屏）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 15: 配置后台——Datasources.vue 数据源管理页

**Files:**
- Create(覆写占位): `xanzc_frontend/src/views/screen/admin/Datasources.vue`

**Interfaces:**
- Consumes: Task 11 `listScreenDatasources / saveScreenDatasource / updateScreenDatasource / deleteScreenDatasource / tryRunScreenDatasource`；既有 `api/metrics.js` 的 `listMetrics()`（宽表指标多选下拉，含 `metricCode/metricName/baseDim` 字段）
- Produces: 管理员可完成三类数据源全生命周期管理；能力标签列驱动 Designer 联动过滤

- [ ] **Step 1: 实现页面**（列表 + `el-dialog` 三态表单 + 试跑预览，风格对齐 `views/perf/Metrics.vue`）

```vue
<template>
  <div>
    <div class="page-h">
      <h1>大屏数据源</h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新建数据源</el-button>
      </div>
    </div>

    <div class="card-section" v-loading="loading">
      <el-table :data="list" size="default">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="dsCode" label="编码" width="140" />
        <el-table-column prop="dsName" label="名称" min-width="160" />
        <el-table-column label="能力标签" width="100">
          <template #default="{ row }">
            <el-tag :type="row.dsType === 'TIMESERIES' ? 'success' : 'info'" size="small">
              {{ row.dsType === 'TIMESERIES' ? '时序型' : '单值型' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ KIND_LABELS[row.sourceKind] || row.sourceKind }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link @click="openTryRun(row)">试跑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建/编辑 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑数据源' : '新建数据源'" width="720px" top="5vh">
      <el-form :model="dlg.form" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="dlg.form.dsName" maxlength="100" />
        </el-form-item>
        <el-form-item label="来源类型" required>
          <el-radio-group v-model="dlg.form.sourceKind" :disabled="!!dlg.editing">
            <el-radio-button label="WIDE_TABLE">指标宽表(引导式)</el-radio-button>
            <el-radio-button label="KPI_RESULT">KPI结果(引导式)</el-radio-button>
            <el-radio-button label="CUSTOM_SQL">自定义 SQL</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 宽表引导式 -->
        <template v-if="dlg.form.sourceKind === 'WIDE_TABLE'">
          <el-form-item label="宽表" required>
            <el-select v-model="dlg.wide.table" style="width:100%">
              <el-option label="员工指标宽表 (EMP_INDEX_RESULT)" value="EMP_INDEX_RESULT" />
              <el-option label="机构指标宽表 (ORG_INDEX_RESULT)" value="ORG_INDEX_RESULT" />
            </el-select>
          </el-form-item>
          <el-form-item label="指标（多选，保存时自动绑定宽表字段槽位）" required>
            <el-select v-model="dlg.wide.metricCodes" multiple filterable style="width:100%">
              <el-option v-for="m in metricOptions" :key="m.metricCode"
                         :label="`${m.metricName} (${m.metricCode})`" :value="m.metricCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许的预设周期（存 timeParamJson，供设计器周期下拉参考）">
            <el-select v-model="dlg.wide.timeParams" multiple style="width:100%">
              <el-option v-for="p in ['LATEST','LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-alert type="info" :closable="false"
                    title="宽表数据源自动标记为【时序型】，支持趋势/钻取组件" />
        </template>

        <!-- KPI 引导式 -->
        <template v-else-if="dlg.form.sourceKind === 'KPI_RESULT'">
          <el-form-item label="周期类型" required>
            <el-radio-group v-model="dlg.kpi.cycleType">
              <el-radio label="MONTHLY">月度</el-radio>
              <el-radio label="QUARTERLY">季度</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 自定义 SQL -->
        <template v-else>
          <el-form-item label="SELECT 语句（占位参数：#{orgCode} #{empId} #{dateFrom} #{dateTo}；仅白名单表）" required>
            <el-input v-model="dlg.sql.text" type="textarea" :rows="6"
                      placeholder="SELECT org_name, cnt FROM ... WHERE org_code = #{orgCode}" />
          </el-form-item>
          <el-form-item label="能力标签" required>
            <el-radio-group v-model="dlg.form.dsType">
              <el-radio label="SINGLE">单值型（仅最新统计结果）</el-radio>
              <el-radio label="TIMESERIES">时序型（须声明日期列）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="dlg.form.dsType === 'TIMESERIES'" label="日期列名" required>
            <el-input v-model="dlg.sql.dateCol" placeholder="如 stat_date" />
          </el-form-item>
          <el-form-item label="操作原因（高危审计必填）" required>
            <el-input v-model="dlg.form.reason" maxlength="200" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button :loading="dlg.saving" type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 试跑预览 -->
    <el-dialog v-model="tr.show" title="试跑预览（前 10 行）" width="720px">
      <el-form inline>
        <el-form-item label="orgCode"><el-input v-model="tr.orgCode" style="width:140px" /></el-form-item>
        <el-form-item label="empId"><el-input v-model="tr.empId" style="width:140px" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="tr.period" style="width:140px">
            <el-option v-for="p in ['LATEST','LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="tr.running" @click="runTry">执行</el-button>
      </el-form>
      <el-table v-if="tr.result" :data="trRows" size="small" border max-height="360">
        <el-table-column v-for="(c, i) in tr.result.columns" :key="c" :label="c">
          <template #default="{ row }">{{ row[i] }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listScreenDatasources, saveScreenDatasource, updateScreenDatasource,
  deleteScreenDatasource, tryRunScreenDatasource
} from '@/api/screen';
import { listMetrics } from '@/api/metrics';

const KIND_LABELS = { WIDE_TABLE: '指标宽表', KPI_RESULT: 'KPI结果', CUSTOM_SQL: '自定义SQL' };

const list = ref([]);
const loading = ref(false);
const metrics = ref([]);

const dlg = reactive({
  show: false, editing: null, saving: false,
  form: { dsName: '', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', reason: '' },
  wide: { table: 'EMP_INDEX_RESULT', metricCodes: [], timeParams: ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'] },
  kpi: { cycleType: 'MONTHLY' },
  sql: { text: '', dateCol: '' }
});

// 宽表指标下拉：按所选宽表的维度过滤（EMP 表→EMP 指标）
const metricOptions = computed(() => {
  const dim = dlg.wide.table.startsWith('EMP') ? 'EMP' : 'ORG';
  return metrics.value.filter(m => m.baseDim === dim);
});

async function reload() {
  loading.value = true;
  try {
    list.value = await listScreenDatasources();
  } finally {
    loading.value = false;
  }
}

function buildConfigJson() {
  if (dlg.form.sourceKind === 'WIDE_TABLE') {
    // 只传 metricCode，槽位翻译由后端完成
    return JSON.stringify({
      table: dlg.wide.table,
      metrics: dlg.wide.metricCodes.map(c => ({ metricCode: c }))
    });
  }
  if (dlg.form.sourceKind === 'KPI_RESULT') {
    return JSON.stringify({ cycleType: dlg.kpi.cycleType });
  }
  return JSON.stringify({ sql: dlg.sql.text, dateCol: dlg.sql.dateCol || null });
}

function openCreate() {
  dlg.editing = null;
  Object.assign(dlg.form, { dsName: '', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', reason: '' });
  Object.assign(dlg.wide, { table: 'EMP_INDEX_RESULT', metricCodes: [], timeParams: ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'] });
  Object.assign(dlg.kpi, { cycleType: 'MONTHLY' });
  Object.assign(dlg.sql, { text: '', dateCol: '' });
  dlg.show = true;
}

function openEdit(row) {
  dlg.editing = row.id;
  const cfg = JSON.parse(row.configJson || '{}');
  Object.assign(dlg.form, {
    dsName: row.dsName, sourceKind: row.sourceKind, dsType: row.dsType, reason: ''
  });
  if (row.sourceKind === 'WIDE_TABLE') {
    Object.assign(dlg.wide, { table: cfg.table, metricCodes: (cfg.metrics || []).map(m => m.metricCode),
      timeParams: JSON.parse(row.timeParamJson || '[]') });
  } else if (row.sourceKind === 'KPI_RESULT') {
    Object.assign(dlg.kpi, { cycleType: cfg.cycleType || 'MONTHLY' });
  } else {
    Object.assign(dlg.sql, { text: cfg.sql || '', dateCol: cfg.dateCol || '' });
  }
  dlg.show = true;
}

async function onSave() {
  if (!dlg.form.dsName) { ElMessage.warning('名称必填'); return; }
  dlg.saving = true;
  try {
    const body = {
      dsName: dlg.form.dsName,
      sourceKind: dlg.form.sourceKind,
      dsType: dlg.form.dsType,
      configJson: buildConfigJson(),
      timeParamJson: dlg.form.sourceKind === 'WIDE_TABLE' ? JSON.stringify(dlg.wide.timeParams) : null,
      reason: dlg.form.reason
    };
    if (dlg.editing) await updateScreenDatasource(dlg.editing, body);
    else await saveScreenDatasource(body);
    ElMessage.success('已保存');
    dlg.show = false;
    reload();
  } catch (e) {
    // http.js 已弹错误 toast
  } finally {
    dlg.saving = false;
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.dsName}」？被大屏区块引用时将拒绝删除。`, '删除确认', { type: 'warning' });
  } catch { return; }
  await deleteScreenDatasource(row.id);
  ElMessage.success('已删除');
  reload();
}

// 试跑
const tr = reactive({ show: false, row: null, orgCode: '', empId: '', period: 'LATEST', running: false, result: null });
const trRows = computed(() => tr.result?.rows || []);

function openTryRun(row) {
  Object.assign(tr, { show: true, row, result: null });
}
async function runTry() {
  tr.running = true;
  try {
    tr.result = await tryRunScreenDatasource({
      sourceKind: tr.row.sourceKind,
      dsType: tr.row.dsType,
      configJson: tr.row.configJson,
      period: tr.period,
      contextParams: { orgCode: tr.orgCode || null, empId: tr.empId || null },
      reason: '配置态试跑'
    });
  } finally {
    tr.running = false;
  }
}

onMounted(async () => {
  reload();
  const r = await listMetrics({ pageSize: 100 });
  metrics.value = Array.isArray(r) ? r : (r?.records || []);
});
</script>
```

- [ ] **Step 2: 编译验证 + Commit**

```bash
cd xanzc_frontend && npx vite build --logLevel error && cd ..
git add xanzc_frontend/src/views/screen/admin/Datasources.vue
git commit -m "feat(screen): 数据源管理页（三态引导表单 + 试跑预览 + 能力标签）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 16: 配置后台——Designer.vue 大屏设计器（参数化网格 + 实时预览）

**Files:**
- Create(覆写占位): `xanzc_frontend/src/views/screen/admin/Designer.vue`

**Interfaces:**
- Consumes: Task 11 全部布局管理 API；Task 12 `ScreenRenderer`（中栏实时预览直接复用运行时渲染引擎——所见即所得）
- Produces: 屏选择/新建 → 左栏结构树（区域→行→块，增删/上下移/占比输入）→ 中栏 16:9 缩略实时预览 → 右栏属性面板（组件类型↔数据源联动过滤、数据项、样式、钻取/跳转）→ 整体保存；PROVINCE 屏 MAIN 区锁定 + 点位管理对话框

- [ ] **Step 1: 实现页面**

```vue
<template>
  <div class="dsn-page">
    <div class="page-h">
      <h1>大屏设计器</h1>
      <div class="actions">
        <el-select v-model="curId" placeholder="选择大屏" style="width:220px" @change="loadScreen">
          <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName} (${s.viewLevel})`" :value="s.id" />
        </el-select>
        <el-button @click="openCreateScreen">+ 新建屏</el-button>
        <el-button v-if="model && model.viewLevel === 'PROVINCE'" @click="pointDlg.show = true">地图点位</el-button>
        <el-button v-if="model" @click="previewFull">全屏预览</el-button>
        <el-button v-if="model" type="primary" :loading="saving" @click="onSave">保 存</el-button>
      </div>
    </div>

    <div v-if="model" class="dsn-cols">
      <!-- 左：结构树 -->
      <div class="card-section dsn-tree">
        <div v-for="region in editableRegions" :key="region" class="dsn-region">
          <div class="dsn-region-h">
            <b>{{ REGION_LABELS[region] }}</b>
            <el-button link size="small" @click="addRow(region)">+ 行</el-button>
          </div>
          <div v-for="(row, ri) in rowsOf(region)" :key="ri" class="dsn-row-item">
            <div class="dsn-row-h">
              <span>第 {{ row.rowNo }} 行 · 高</span>
              <el-input-number v-model="row.blocks[0].heightPct" :min="1" :max="100" size="small"
                               style="width:90px" @change="syncRowHeight(row)" />%
              <el-button link size="small" @click="moveRow(region, ri, -1)">↑</el-button>
              <el-button link size="small" @click="moveRow(region, ri, 1)">↓</el-button>
              <el-button link size="small" @click="addBlock(region, row.rowNo)">+块</el-button>
              <el-button link type="danger" size="small" @click="removeRow(region, row.rowNo)">删行</el-button>
            </div>
            <div v-for="b in row.blocks" :key="b._key" class="dsn-block-item"
                 :class="{ on: selected === b }" @click="selected = b">
              <span>{{ TYPE_LABELS[b.componentType] }}</span>
              <span class="dsn-b-title">{{ styleOf(b).title || '未命名' }}</span>
              <span>宽<el-input-number v-model="b.widthPct" :min="1" :max="100" size="small"
                                       style="width:80px" @click.stop />%</span>
              <el-button link type="danger" size="small" @click.stop="removeBlock(b)">✕</el-button>
            </div>
          </div>
        </div>
        <el-alert v-if="model.viewLevel === 'PROVINCE'" type="info" :closable="false"
                  title="省级屏中心区固定为陕西地图，仅左右两侧可配置" />
      </div>

      <!-- 中：实时预览（复用运行时渲染引擎，所见即所得） -->
      <div class="card-section dsn-preview-wrap">
        <div class="dsn-preview screen-root" ref="previewRef">
          <div class="scr-stage" :style="{ transform: `translate(-50%, -50%) scale(${previewScale})` }">
            <div class="scr-header"><span class="scr-title">{{ model.screenName }}</span></div>
            <div class="scr-body">
              <ScreenRenderer :screen="model" :blocks="renderBlocks" :map-points="points"
                              :context="previewCtx" :key="renderKey" />
            </div>
          </div>
        </div>
        <el-form inline size="small" class="dsn-ctx">
          <el-form-item label="预览 orgCode"><el-input v-model="previewCtx.orgCode" style="width:130px" /></el-form-item>
          <el-form-item label="预览 empId"><el-input v-model="previewCtx.empId" style="width:130px" /></el-form-item>
          <el-button size="small" @click="renderKey++">刷新预览</el-button>
        </el-form>
      </div>

      <!-- 右：属性面板 -->
      <div class="card-section dsn-props">
        <template v-if="selected">
          <el-form label-position="top" size="default">
            <el-form-item label="组件类型">
              <el-select v-model="selected.componentType" style="width:100%">
                <el-option v-for="(l, t) in TYPE_LABELS" :key="t" :label="l" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源（按组件类型联动过滤）">
              <el-select :model-value="bindOf(selected).dsId" style="width:100%"
                         @update:model-value="v => patchBind(selected, { dsId: v })">
                <el-option v-for="d in dsOptions" :key="d.id"
                           :label="`${d.dsName}【${d.dsType === 'TIMESERIES' ? '时序' : '单值'}】`" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="周期">
              <el-select :model-value="bindOf(selected).period || 'LATEST'" style="width:100%"
                         @update:model-value="v => patchBind(selected, { period: v })">
                <el-option v-for="p in ['LATEST','LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据项（卡片/折线；一行一个：列名|显示名）">
              <el-input :model-value="itemsText(selected)" type="textarea" :rows="3"
                        @update:model-value="v => setItemsText(selected, v)" />
            </el-form-item>
            <el-form-item label="名称列 / 数值列（排名/饼图/流程）">
              <div style="display:flex;gap:6px">
                <el-input :model-value="bindOf(selected).nameCol" placeholder="nameCol"
                          @update:model-value="v => patchBind(selected, { nameCol: v })" />
                <el-input :model-value="bindOf(selected).valueCol" placeholder="valueCol"
                          @update:model-value="v => patchBind(selected, { valueCol: v })" />
              </div>
            </el-form-item>
            <el-form-item label="标题 / 单位 / 小数位 / 刷新秒">
              <div style="display:flex;gap:6px">
                <el-input :model-value="styleOf(selected).title" placeholder="标题"
                          @update:model-value="v => patchStyle(selected, { title: v })" />
                <el-input :model-value="styleOf(selected).unit" placeholder="单位" style="width:90px"
                          @update:model-value="v => patchStyle(selected, { unit: v })" />
                <el-input-number :model-value="styleOf(selected).decimals ?? 2" :min="0" :max="6"
                                 @update:model-value="v => patchStyle(selected, { decimals: v })" />
                <el-input-number :model-value="styleOf(selected).refreshSec ?? 60" :min="0" :max="3600"
                                 @update:model-value="v => patchStyle(selected, { refreshSec: v })" />
              </div>
            </el-form-item>
            <el-divider>钻取与跳转</el-divider>
            <el-form-item>
              <el-switch :model-value="drillOf(selected).drillEnabled" active-text="区块内趋势钻取（仅时序数据源）"
                         @update:model-value="v => patchDrill(selected, { drillEnabled: v })" />
            </el-form-item>
            <el-form-item v-if="drillOf(selected).drillEnabled" label="钻取周期">
              <el-select :model-value="drillOf(selected).drillPeriods || []" multiple style="width:100%"
                         @update:model-value="v => patchDrill(selected, { drillPeriods: v })">
                <el-option v-for="p in ['LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="点击跳转目标屏（留空不跳转）">
              <el-select :model-value="drillOf(selected).jump?.targetScreenCode" clearable style="width:100%"
                         @update:model-value="v => patchJump(selected, v)">
                <el-option v-for="s in screens" :key="s.screenCode" :label="s.screenName" :value="s.screenCode" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="drillOf(selected).jump" label='跳转参数映射 JSON（如 {"orgCode":"$col:org_code"}）'>
              <el-input :model-value="JSON.stringify(drillOf(selected).jump?.params || {})" type="textarea" :rows="2"
                        @update:model-value="v => patchJumpParams(selected, v)" />
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else description="点击左侧区块编辑属性" />
      </div>
    </div>

    <!-- 新建屏 -->
    <el-dialog v-model="createDlg.show" title="新建大屏" width="420px">
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="createDlg.name" /></el-form-item>
        <el-form-item label="视角" required>
          <el-radio-group v-model="createDlg.viewLevel">
            <el-radio label="PROVINCE">省分行总览</el-radio>
            <el-radio label="BRANCH">支行详情</el-radio>
            <el-radio label="PERSON">个人详情</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg.show = false">取消</el-button>
        <el-button type="primary" @click="onCreateScreen">创建</el-button>
      </template>
    </el-dialog>

    <!-- 地图点位管理（PROVINCE） -->
    <el-dialog v-model="pointDlg.show" title="地图支行点位" width="760px">
      <el-button size="small" @click="points.push({ orgCode: '', orgName: '', lng: 108.9, lat: 34.2, targetScreenCode: 'SCR_BRANCH', status: 'ACTIVE' })">+ 点位</el-button>
      <el-table :data="points" size="small" max-height="380">
        <el-table-column label="机构号" width="130">
          <template #default="{ row }"><el-input v-model="row.orgCode" size="small" /></template>
        </el-table-column>
        <el-table-column label="名称" width="150">
          <template #default="{ row }"><el-input v-model="row.orgName" size="small" /></template>
        </el-table-column>
        <el-table-column label="经度" width="120">
          <template #default="{ row }"><el-input-number v-model="row.lng" :precision="6" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="纬度" width="120">
          <template #default="{ row }"><el-input-number v-model="row.lat" :precision="6" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="目标屏" width="130">
          <template #default="{ row }"><el-input v-model="row.targetScreenCode" size="small" /></template>
        </el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="points.splice($index, 1)">✕</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pointDlg.show = false">关闭</el-button>
        <el-button type="primary" @click="onSavePoints">保存点位</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listScreens, getScreen, saveScreen, listMapPoints, saveMapPoints, listScreenDatasources
} from '@/api/screen';
import ScreenRenderer from '../components/ScreenRenderer.vue';

const REGION_LABELS = { LEFT: '左侧区域', MAIN: '中心区域', RIGHT: '右侧区域' };
const TYPE_LABELS = {
  METRIC_CARD: '数值指标卡片', LINE_TREND: '折线趋势图', PIE_SHARE: '饼状占比图',
  RANK_LIST: '排名列表', FLOW_STATUS: '流程状态概览'
};

const screens = ref([]);
const datasources = ref([]);
const points = ref([]);
const curId = ref(null);
const model = ref(null);      // { id, screenCode, screenName, viewLevel, status, blocks: [] }
const selected = ref(null);
const saving = ref(false);
const renderKey = ref(0);
const previewScale = ref(0.32);
const previewRef = ref(null);
const previewCtx = reactive({ orgCode: '', empId: '' });
const createDlg = reactive({ show: false, name: '', viewLevel: 'BRANCH' });
const pointDlg = reactive({ show: false });
let blockKeySeq = 1;

// PROVINCE 屏 MAIN 区锁定（需求 3.1：中心固定地图）
const editableRegions = computed(() =>
  model.value?.viewLevel === 'PROVINCE' ? ['LEFT', 'RIGHT'] : ['LEFT', 'MAIN', 'RIGHT']);

// 联动过滤：趋势类组件 / 已开钻取 → 仅时序数据源（需求 4.3.2）
const dsOptions = computed(() => {
  if (!selected.value) return datasources.value;
  const needTs = selected.value.componentType === 'LINE_TREND' || drillOf(selected.value).drillEnabled;
  return needTs ? datasources.value.filter(d => d.dsType === 'TIMESERIES') : datasources.value;
});

const renderBlocks = computed(() => (model.value?.blocks || []).map(b => ({ ...b })));

// ==== JSON 三列的读写助手（区块上保存字符串，编辑面板上操作对象） ====
function parseJ(s, fb = {}) { try { return s ? JSON.parse(s) : fb; } catch { return fb; } }
function bindOf(b) { return parseJ(b.bindJson); }
function styleOf(b) { return parseJ(b.styleJson); }
function drillOf(b) { return parseJ(b.drillJson); }
function patchBind(b, patch) { b.bindJson = JSON.stringify({ ...bindOf(b), ...patch }); renderKey.value++; }
function patchStyle(b, patch) { b.styleJson = JSON.stringify({ ...styleOf(b), ...patch }); renderKey.value++; }
function patchDrill(b, patch) { b.drillJson = JSON.stringify({ ...drillOf(b), ...patch }); renderKey.value++; }
function patchJump(b, code) {
  const d = drillOf(b);
  if (!code) delete d.jump;
  else d.jump = { targetScreenCode: code, params: d.jump?.params || {} };
  b.drillJson = JSON.stringify(d);
}
function patchJumpParams(b, text) {
  try {
    const d = drillOf(b);
    if (d.jump) { d.jump.params = JSON.parse(text || '{}'); b.drillJson = JSON.stringify(d); }
  } catch { /* 输入中容忍非法 JSON */ }
}
function itemsText(b) { return (bindOf(b).items || []).map(i => `${i.col}|${i.label || i.col}`).join('\n'); }
function setItemsText(b, v) {
  const items = String(v || '').split('\n').map(s => s.trim()).filter(Boolean)
    .map(line => { const [col, label] = line.split('|'); return { col: col.trim(), label: (label || col).trim() }; });
  patchBind(b, { items });
}

// ==== 结构树操作 ====
function rowsOf(region) {
  const list = (model.value?.blocks || []).filter(b => b.region === region);
  const byRow = new Map();
  for (const b of list) {
    if (!byRow.has(b.rowNo)) byRow.set(b.rowNo, []);
    byRow.get(b.rowNo).push(b);
  }
  return [...byRow.entries()].sort((a, b) => a[0] - b[0])
    .map(([rowNo, blocks]) => ({ rowNo, blocks: blocks.sort((a, b) => a.colNo - b.colNo) }));
}
function newBlock(region, rowNo, colNo) {
  return {
    _key: `nb${blockKeySeq++}`, region, rowNo, colNo,
    widthPct: 100, heightPct: 50, componentType: 'METRIC_CARD',
    bindJson: '{}', styleJson: '{"title":"新区块","refreshSec":60}', drillJson: '{}'
  };
}
function addRow(region) {
  const maxRow = Math.max(0, ...rowsOf(region).map(r => r.rowNo));
  model.value.blocks.push(newBlock(region, maxRow + 1, 1));
}
function addBlock(region, rowNo) {
  const row = rowsOf(region).find(r => r.rowNo === rowNo);
  model.value.blocks.push(newBlock(region, rowNo, (row?.blocks.length || 0) + 1));
}
function removeBlock(b) {
  model.value.blocks = model.value.blocks.filter(x => x !== b);
  if (selected.value === b) selected.value = null;
}
function removeRow(region, rowNo) {
  model.value.blocks = model.value.blocks.filter(b => !(b.region === region && b.rowNo === rowNo));
}
function moveRow(region, ri, dir) {
  const rows = rowsOf(region);
  const j = ri + dir;
  if (j < 0 || j >= rows.length) return;
  // 交换两行的 rowNo
  const a = rows[ri].rowNo;
  const b = rows[j].rowNo;
  for (const blk of model.value.blocks) {
    if (blk.region !== region) continue;
    if (blk.rowNo === a) blk.rowNo = b;
    else if (blk.rowNo === b) blk.rowNo = a;
  }
}
function syncRowHeight(row) {
  for (const b of row.blocks) b.heightPct = row.blocks[0].heightPct;
}

// ==== 加载/保存 ====
async function reloadScreens() { screens.value = await listScreens(); }
async function loadScreen(id) {
  const d = await getScreen(id);
  d.blocks = (d.blocks || []).map(b => ({ ...b, _key: `db${b.id}` }));
  model.value = d;
  selected.value = null;
  renderKey.value++;
}
function openCreateScreen() { Object.assign(createDlg, { show: true, name: '', viewLevel: 'BRANCH' }); }
async function onCreateScreen() {
  if (!createDlg.name) { ElMessage.warning('名称必填'); return; }
  const id = await saveScreen({ screenName: createDlg.name, viewLevel: createDlg.viewLevel, blocks: [] });
  createDlg.show = false;
  await reloadScreens();
  curId.value = id;
  await loadScreen(id);
}
async function onSave() {
  saving.value = true;
  try {
    await saveScreen({
      id: model.value.id,
      screenCode: model.value.screenCode,
      screenName: model.value.screenName,
      viewLevel: model.value.viewLevel,
      status: model.value.status,
      blocks: model.value.blocks.map(({ _key, ...b }) => b)
    });
    ElMessage.success('已保存');
    renderKey.value++;
  } finally {
    saving.value = false;
  }
}
async function onSavePoints() {
  await saveMapPoints(points.value);
  ElMessage.success('点位已保存');
  pointDlg.show = false;
  renderKey.value++;
}
function previewFull() {
  const q = [];
  if (previewCtx.orgCode) q.push(`orgCode=${previewCtx.orgCode}`);
  if (previewCtx.empId) q.push(`empId=${previewCtx.empId}`);
  window.open(`#/screen/${model.value.screenCode}${q.length ? '?' + q.join('&') : ''}`, '_blank');
}

function fitPreview() {
  const w = previewRef.value?.clientWidth || 640;
  previewScale.value = Math.min(w / 1920, (w * 9 / 16) / 1080);
}

onMounted(async () => {
  await reloadScreens();
  datasources.value = await listScreenDatasources();
  points.value = await listMapPoints();
  if (screens.value.length) { curId.value = screens.value[0].id; await loadScreen(curId.value); }
  setTimeout(fitPreview, 0);
  window.addEventListener('resize', fitPreview);
});
</script>

<style lang="scss" scoped>
.dsn-cols { display: grid; grid-template-columns: 320px 1fr 340px; gap: 12px; align-items: start; }
.dsn-tree { max-height: 76vh; overflow-y: auto; }
.dsn-region { margin-bottom: 10px; }
.dsn-region-h { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; }
.dsn-row-item { border: 1px dashed #d0d7e2; border-radius: 6px; padding: 6px; margin-bottom: 6px; }
.dsn-row-h { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #6b7a90; flex-wrap: wrap; }
.dsn-block-item { display: flex; align-items: center; gap: 6px; font-size: 13px; padding: 4px 6px;
  border-radius: 4px; cursor: pointer; margin-top: 4px;
  &.on { background: #e8f3ff; outline: 1px solid #409eff; } }
.dsn-b-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #909db1; }
.dsn-preview-wrap { overflow: hidden; }
.dsn-preview { position: relative; width: 100%; aspect-ratio: 16 / 9; border-radius: 6px; overflow: hidden;
  // 覆盖 screen-root 的 fixed 定位，改为容器内预览
  position: relative !important; inset: auto !important; }
.dsn-ctx { margin-top: 8px; }
.dsn-props { max-height: 76vh; overflow-y: auto; }
</style>
```

- [ ] **Step 2: 编译验证 + Commit**

```bash
cd xanzc_frontend && npx vite build --logLevel error && cd ..
git add xanzc_frontend/src/views/screen/admin/Designer.vue
git commit -m "feat(screen): 大屏设计器（结构树+实时预览+属性面板+联动过滤+点位管理）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 17: 联调验收 + 文档同步

**Files:**
- Modify: `report-analytics-center/CLAUDE.md`（追加大屏子域变更日志）
- Modify: `docs/schema/ddl-report.sql`（追加 4 张大屏表基线 DDL，与 Task 1 脚本一致）
- Modify: `docs/superpowers/specs/2026-07-12-screen-dashboard-design.md`（错误码补 RPT-43009、map-points 独立资源 R_RPT_SCR_MAP_LIST 两处对齐实现）

- [ ] **Step 1: 后端全量回归**

```bash
mvn test -pl report-analytics-center
mvn verify -pl report-analytics-center -Dit.test='RptScreenMapperIT,ScreenQueryEngineIT,ScreenControllerIT' -Dtest=RptErrorCodeTest
```
Expected: 全绿（含 6 个既有架构守护）。

- [ ] **Step 2: 启动前后端联调**

```bash
cd bootstrap && mvn spring-boot:run   # 后台运行，等待 "Started" 日志
cd xanzc_frontend && npm run dev      # 后台运行，端口 8091
```

- [ ] **Step 3: 手工验收清单**（用 admin 或绑定了 R_ADMIN 的账号登录 http://localhost:8091）

1. 侧边栏出现「大屏数据源」「大屏设计器」菜单（若无：核对 Task 10 菜单行与角色绑定，刷新页面）。
2. 数据源管理：列表显示 5 个种子数据源；新建一个宽表数据源（选 2 个指标）→ 保存成功且列表显示「时序型」；对 9005 试跑 → 返回「在途任务/数量」列与行。
3. 设计器：选择「省分行经营总览」→ 左栏仅 LEFT/RIGHT 可编辑 + 中心区锁定提示；中栏预览渲染出地图 + 左右区块（有数据时含真实数字）；选中 LEFT 指标卡 → 右栏改标题 → 预览即时变化 → 保存成功。
4. 联动校验：给某区块把组件类型切为「折线趋势图」→ 数据源下拉只剩时序型；强行用 API 提交单值源+折线（可用 curl）→ 返回 RPT-43005。
5. 全屏预览省级屏：`http://localhost:8091/#/screen/SCR_PROVINCE`——深色主题、1920×1080 等比缩放（拉伸窗口验证）、时钟走秒。
6. 地图点位点击 → 跳 `/screen/SCR_BRANCH?orgCode=<对应机构>`，支行屏指标卡/趋势按该机构取数。
7. 支行屏排名列表点击行（种子 9101 RIGHT 配了 jump）→ 跳支行屏并携带 orgCode。
8. 个人屏：`#/screen/SCR_PERSON?empId=<有 KPI 数据的工号>` → KPI 卡与趋势有值。
9. 钻取：省级屏 LEFT 指标卡点任一指标 → 区块内切换为趋势 + 周期 tabs 可切换 + 返回按钮恢复。
10. 轮询：把某区块 refreshSec 配为 5 → 观察 Network 每 5s 一次 /api/screen/data。

若第 2/8 步因目标库无宽表/KPI 数据而空白：用 `docs/指标结果模板.xlsx` 走绩效导入通道造数（或直接 INSERT 测试行），属数据问题不属本功能缺陷。

- [ ] **Step 4: 文档同步**。`report-analytics-center/CLAUDE.md` 在「模块概述」版本段后追加：

```markdown
> 2026-07-12 screen 子域：经营管理大屏（三级视角 + 全配置化）——新增 4 张配置表
> `RPT_SCREEN_DATASOURCE`/`RPT_SCREEN`/`RPT_SCREEN_BLOCK`/`RPT_SCREEN_MAP_POINT`（MyBatis-Plus），
> 查询执行引擎 `ScreenQueryEngine` 走 rptReadOnlyDataSource + 表白名单直查（D1 决策，白名单见
> `rpt.screen.whitelist-tables` 默认值），三类数据源（宽表引导/KPI引导/自定义SQL#{param}占位）。
> 13 API 资源 + 2 菜单（R_RPT_SCR_*/M_RPT_SCR_*），错误码 RPT-43001~43009。
> 前端 /screen/:screenCode 全屏深色三级大屏（陕西地图+钻取+跳转）+ 两个配置后台页。
> DDL/资源/种子脚本：docs/superpowers/sql/2026-07-12-screen-dashboard-{ddl,resources,seed}.sql。
> spec/plan：docs/superpowers/specs/2026-07-12-screen-dashboard-design.md /
> docs/superpowers/plans/2026-07-12-screen-dashboard-impl.md。
```

`docs/schema/ddl-report.sql` 末尾追加 Task 1 的 4 段 CREATE TABLE（保持基线与真库一致）；spec 同步 4 处实现对齐：§6.2 错误码表补一行 RPT-43009、§6.1 map-points 行改为独立资源 `R_RPT_SCR_MAP_LIST`、§5.5 取数请求 timeParam 嵌套结构改为扁平 `period/dateFrom/dateTo` 字段、§11 "ScreenSqlValidator ≥15 用例"改为"复用既有 SqlSafeValidator（其 ≥15 边界用例守护继续生效）+ ScreenSqlTemplate/ScreenQueryEngine 新增用例"。

- [ ] **Step 5: 最终 Commit**

```bash
git add report-analytics-center/CLAUDE.md docs/schema/ddl-report.sql \
        docs/superpowers/specs/2026-07-12-screen-dashboard-design.md \
        docs/superpowers/plans/2026-07-12-screen-dashboard-impl.md
git commit -m "docs(screen): 大屏交付文档同步（模块日志/DDL基线/spec对齐）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
