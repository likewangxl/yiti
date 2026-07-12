# 大屏画布设计器(RPT_SCREEN V2)一期 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 report-analytics-center 的「行/块百分比」大屏升级为参照 DataEase 移植裁剪的自由拖拽画布设计器(拖拽/8点缩放/吸附对齐/撤销重做/图层/右键/属性面板 + 5 素材 + 9 图表),配套草稿/发布双态后端(乐观锁保存、发布归档、回滚、放弃草稿)与双态渲染,并手工重配 3 屏。

**Architecture:** 画布布局树整体存 `RPT_SCREEN.CANVAS_DRAFT_JSON`(编辑态)/`CANVAS_PUBLISHED_JSON`(发布态渲染包),图表取数配置仍按行存 `RPT_SCREEN_BLOCK`(职责收窄),二者靠 `blockId` 弱关联(照搬 DataEase componentData/core_chart_view 分层边界)。前端设计器用 Pinia setup-store 持有 `componentData`,画布容器 `transform: scale`、鼠标位移除以 scale 换算,设计态坐标恒 1920×1080 基准(幂等,不采用 DataEase 增量换算)。运行时渲染只读 `CANVAS_PUBLISHED_JSON`,`?preview=draft` 读草稿。

**Tech Stack:** 后端 Spring Boot 3.2.3 + JDK17 + MyBatis-Plus 3.5.7 + Jackson + JUnit5/Mockito/AssertJ + MockMvc IT;前端 Vue 3.3 + Vite 4 + Pinia 2.0 + Element Plus + echarts,新增 vitest(仅测纯函数 utils);数据库 MySQL 8(yiti + onepl_test_bootstrap,root/djdev),schema 手工 SQL 执行(禁 Flyway)。

---

## Global Constraints

逐条硬约束,每个任务都必须满足:

- **MyBatis-Plus 红线(2026-06-10 起)**:所有新增 DB 访问统一 MyBatis-Plus。Mapper 接口 `extends BaseMapper<T>`;单条 CRUD(`insert`/`selectById`/`updateById`/`deleteById`)直接用 BaseMapper 内置方法,**禁止**为其重复写 XML;动态/简单条件用 `LambdaQueryWrapper`/`LambdaUpdateWrapper`;XML 只写 BaseMapper 覆盖不到的自定义 SQL(如乐观锁 `UPDATE ... WHERE version=?`、批量、聚合)。实体用 `@TableName`/`@TableId(type=IdType.AUTO)`/`@TableLogic`。既有纯 MyBatis 不强制回改,新增一律本规范。
- **Flyway 禁令(绝对红线)**:禁止引入 `flyway-core`/`flyway-mysql` 任何依赖;禁止 `application*.yml` 出现 `spring.flyway.*`;禁止新增 `V*__*.sql`/`U*__*.sql` 命名脚本;禁止 `*FlywayIT`/`*FlywayTestBase`。schema 变更全部走 SQL 直接在目标库执行(手工)。
- **TDD 红-绿-重构(绝对红线)**:一切特性/Bug 修复必须先写测试(让它失败,Red)→ 写最简代码让它通过(Green)→ 重构(Refactor)。**禁止事后狂补测试**(先写一大堆业务逻辑再凑测试)。本计划每个改代码任务都按「写失败测试 → 跑测试确认失败 → 最小实现 → 跑测试确认通过 → 提交」编排。
- **pathspec 提交纪律**:每步独立 commit,`git add` **只列本步实际改动的精确路径**(逐一列出文件,禁止 `git add -A`/`git add .`/`git add -u`)。这样天然隔离工作区既有未提交文件。
- **工作区既有未提交文件不得卷入**:以下文件在本任务开始前已是 modified/untracked,**任何提交都不得包含它们**——`bootstrap/src/main/resources/application.yml`、`performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/PerfTestConfig.java`、`xanzc_frontend/vite.config.js`、根目录 `大屏需求.txt`/`AGENT-TEAM要求.txt`/`大屏设计器.png`、`docs/superpowers/prompts/`。(靠上一条 pathspec 纪律保证。)
- **后端回归基线**:实现后 `mvn test -pl report-analytics-center` 的失败数 **不得超过** 任务启动时实测的 pre-existing 基线(spec §6 记为「≤ 9 个 pre-existing」,diag-backend 附录记为 10;**任务启动第一步先跑一次记录真实基线数,后续以该实测值为准,不得增加**)。新增测试必须全绿。
- **跨模块改动先预构建**:`report-analytics-center` 的 java 改动被 bootstrap `@SpringBootTest`/运行时加载前,必须先 `mvn clean install -DskipTests` 把上游 install 到本地 .m2(防 stale jar 引发 `ConflictingBeanDefinitionException` 等怪错),再 `mvn test -pl report-analytics-center` 或重启后端。
- **前端构建门禁**:`cd xanzc_frontend && npx vite build --logLevel error` 必须 exit 0;新增 vitest 用 `npx vitest run` 全绿。
- **全中文注释 + UTF-8**:所有新增/修改文件用 UTF-8 编码;Service 类与 public 方法必须中文注释;复杂逻辑行注释说明「为什么」。
- **PT_RESOURCE + @BizAuth 登记**:每个新 REST 端点必须 `@BizAuth(bizType=BizType.REPORT, action=...)`,并在 `docs/superpowers/sql/2026-07-12-screen-canvas-resources.sql` 登记 PT_RESOURCE + 角色绑定(含 R_ADMIN 兜底行,防 AUTH-40304)。高危发布/回滚独立资源、单独授权、单独审计。
- **curl 本机必须 `--noproxy '*'`**:任何 `curl localhost:18081` 手工验证都要带 `--noproxy '*'`,否则走代理失败。
- **后端重启方法**(见 `.omc/handoffs/diag-backend.md` 附录 B):改后端 java 后 `mvn clean install -DskipTests`(红线)→ kill 本目录 18081 进程(**切勿碰 /home/djdev/lf/yiti 的 18080 实例 pid 1592594**)→ `nohup mvn -f /home/djdev/leid/yiti/bootstrap/pom.xml spring-boot:run > logs/backend-diag-2026-07-12.log 2>&1 &` → `until grep -q "Started BranchPlatformApplication" logs/backend-diag-2026-07-12.log; do sleep 2; done`。重启后旧 cookie 失效,重新 `curl --noproxy '*' -c /tmp/scr_cookie.txt -X POST .../api/auth/login`。
- **DDL 基线只读**:`docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql` 是既有 4 表基线,**严禁重跑/修改**;本期新增的字段/表用**新脚本** `2026-07-12-screen-canvas-ddl.sql`(ALTER + CREATE,含回滚注释),手工在 yiti + onepl_test_bootstrap 执行。
- **子代理模型 ≥ sonnet**:如派遣 subagent,model 只能 `sonnet` 或 `opus`,禁止 `haiku`。

---

## 路径/命名对齐说明(实况核实结论,实施必须遵守)

以下为逐一打开代码库核实后的结论,与 spec 若有出入以**代码实况**为准(团队规范:计划路径/类名/字段名必须与实况一致):

1. **URL 前缀**:spec §3 写作 `/api/report/screen/...`,但既有 4 个 Screen Controller 与 13 条 PT_RESOURCE 实际前缀是 **`/api/screen/...`**(`ScreenConfigAdminController` = `/api/screen/admin`,`ScreenViewController` = `/api/screen/view`,`ScreenDataController` = `/api/screen/data`,`ScreenDatasourceAdminController` = `/api/screen/admin/datasources`)。本计划新端点一律挂 **`/api/screen/admin/canvas/...`**,与实况一致。
2. **模块惯例**:report 不暴露 `*Api`;异常仅 `RptException(code)`/`RptException(code, Throwable)`;审计走**手工** `AuditApi.log(AuditLogCmd.builder()...)`(参考 `ScreenDatasourceServiceImpl.safelyAudit`,非 `@AuditLog` 切面);错误码集中在 `enums/RptErrorCode.java`,守护测试 `enums/RptErrorCodeTest.java`。
3. **实体现状**:`RptScreen` 现有字段 = id/screenCode/screenName/viewLevel/themeJson/status/createdBy/createdTime/updatedTime/deleted(`@TableLogic`);本期新增 7 列(见 Task 1)。`RptScreenBlock` 现有 = id/screenId/region/rowNo/colNo/widthPct/heightPct/componentType/bindJson/styleJson/drillJson/createdTime/updatedTime(无逻辑删除列)。
4. **错误码现状**:`RptErrorCode` 当前 **44 条**(43001~43011 大屏子域已在),`RptErrorCodeTest` 守护 `hasSize(44)` 与 `distinct==44`。本期新增 **RPT-43012** → 45 条,守护同步。
5. **测试基座**:单测 `@ExtendWith(MockitoExtension.class)` + `new XxxServiceImpl(mock...)`(见 `ScreenConfigServiceTest`);IT `extends BaseControllerIT`(Mock `AuthorizationInterceptor` 全放行 + Mock 全部上游 Api,`@ActiveProfiles("test")`,库 `onepl_test_bootstrap`,`ReportTestApplication`),MockMvc 落真库(见 `ScreenControllerIT`)。
6. **前端现状**:`package.json` **无 vitest**;pinia 2.0.35 已装,`main.js` 用 `app.use(createPinia())`;既有 store `src/stores/user.js` 用 **setup-store 写法**(`defineStore('user', () => {...})`),新 store 沿用。`src/api/screen.js` 已有 12 个函数 + `call('post', url, {data, silent:true})` 契约。`src/router/index.js` 第 66 行 `screen-admin/designer` 现指向 `admin/Designer.vue`(511 行,行/块结构树设计器),本期替换。样式 `src/styles/_screen-theme.scss` 提供 `scr-surface` mixin 与 `.scr-*` 类,`screen.scss` 全屏定位,设计器复用。
7. **渲染现状**:`ScreenView.vue` → `getScreenView(code)` → `{screen, blocks, mapPoints}` → `ScreenRenderer.vue`(region/row/block flex 布局)→ `BlockContainer.vue`(每块取数 + 43010 引导态/silent toast 契约)。本期渲染层改读发布态渲染包(绝对定位),`BlockContainer` 取数逻辑复用进 `ChartWidget`。

---

## 数据流与 JSON Schema(全任务共享契约)

三个 JSON 字段的文档根均携带 `schemaVersion`(自 v1 起),读时兼容集中在唯一适配函数。

**CANVAS_STYLE_JSON**(画布全局样式):
```json
{ "schemaVersion": 1, "designWidth": 1920, "designHeight": 1080,
  "background": "#050e2b", "adaptor": "keepProportion",
  "themeOverride": {} }
```
`adaptor` ∈ `keep | keepProportion | widthFirst | heightFirst`(TS/JS 联合枚举锁死,全仓唯一来源 = `utils/scale.js` 的 `ADAPTORS`)。

**CANVAS_DRAFT_JSON**(编辑态组件树,编辑器唯一读写对象):
```json
{ "schemaVersion": 1,
  "components": [
    { "id": "w-8f3a2c", "component": "ChartWidget", "innerType": "METRIC_CARD",
      "blockId": 1008, "style": {"top":120,"left":240,"width":600,"height":320},
      "propValue": {}, "isLock": false, "isShow": true },
    { "id": "w-1b0e77", "component": "TextLabel", "blockId": null,
      "style": {"top":40,"left":60,"width":300,"height":48},
      "propValue": {"text":"营收看板","fontSize":24,"color":"#d5e6ff"},
      "isLock": false, "isShow": true }
  ] }
```
- 图层顺序 = `components` 数组顺序(无 zIndex,照搬 DataEase);坐标恒 1920×1080 设计基准像素(幂等)。
- **仅 ChartWidget 节点带 `blockId`**;取数配置绝不进组件树,只在 `RPT_SCREEN_BLOCK` 行。素材组件 `blockId=null`,私有配置在 `propValue`。

**CANVAS_PUBLISHED_JSON**(发布态渲染包 = 组件树 + 图表绑定快照):
```json
{ "schemaVersion": 1,
  "canvasStyle": { "...同 STYLE_JSON..." : "" },
  "components": [ "...同 DRAFT components(结构一致)..." ],
  "bindSnapshots": {
    "1008": { "dsId": 9001, "period": "LATEST",
              "styleCfg": {"title":"月度营收","refreshSec":60},
              "drill": {"drillEnabled":false} } } }
```
- `bindSnapshots` 是发布时刻从 `RPT_SCREEN_BLOCK` 行合成的快照(key = blockId 字符串),运行时 ChartWidget 直接读它,不再回查 block 行。
- `discard`(放弃草稿)= `DRAFT.components := PUBLISHED.components`(结构同源,直接覆盖)。

---

## Task 1: DDL 变更脚本 + RptScreen 实体/新表实体·Mapper(MyBatis-Plus)

给 `RPT_SCREEN` 加画布双态 6 字段(7 列),新增 `RPT_SCREEN_PUBLISH_LOG` 归档表;实体/Mapper 用 MyBatis-Plus 就绪。本任务 DDL 是手工执行脚本(非自动 migrate),实体/Mapper 用 mapper IT 做 Red→Green。

**Files:**
- Create `docs/superpowers/sql/2026-07-12-screen-canvas-ddl.sql`
- Modify `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreen.java`(在 `deleted` 字段前追加 7 列字段)
- Create `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreenPublishLog.java`
- Create `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptScreenPublishLogMapper.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/mapper/RptScreenPublishLogMapperIT.java`

**Interfaces:**
- Produces: `RptScreen` 新增 getter/setter(canvasStyleJson/canvasDraftJson/canvasPublishedJson/canvasVersion/publishStatus/publishedAt/publishedBy);`RptScreenPublishLog` 实体;`RptScreenPublishLogMapper extends BaseMapper<RptScreenPublishLog>`。
- Consumes: 无(纯持久层)。

**步骤:**

- [ ] 1.1 写 DDL 脚本(含回滚注释与手工执行说明)。创建 `docs/superpowers/sql/2026-07-12-screen-canvas-ddl.sql`:
```sql
-- 大屏画布设计器 V2 —— RPT_SCREEN 双态字段 + 发布归档新表(2026-07-12)
-- 目标库:yiti + onepl_test_bootstrap 手工执行(root/djdev)。
-- 【严禁重跑既有基线 2026-07-12-screen-dashboard-ddl.sql】本脚本为增量 ALTER + CREATE。
-- 幂等策略:ALTER 用 IF NOT EXISTS(MySQL 8.0 支持);新表 CREATE IF NOT EXISTS。
--
-- ===== 正向变更 =====
ALTER TABLE `RPT_SCREEN`
  ADD COLUMN IF NOT EXISTS `canvas_style_json`     LONGTEXT     DEFAULT NULL COMMENT '画布全局样式JSON(设计基准/背景/适配策略/主题覆盖,schemaVersion)',
  ADD COLUMN IF NOT EXISTS `canvas_draft_json`     LONGTEXT     DEFAULT NULL COMMENT '编辑态组件树JSON(草稿,编辑器唯一读写对象)',
  ADD COLUMN IF NOT EXISTS `canvas_published_json` LONGTEXT     DEFAULT NULL COMMENT '发布态渲染包JSON=组件树+图表绑定快照,线上/预览只读它',
  ADD COLUMN IF NOT EXISTS `canvas_version`        INT          NOT NULL DEFAULT 0 COMMENT '真乐观锁:保存 WHERE canvas_version=? 并自增,冲突RPT-43012',
  ADD COLUMN IF NOT EXISTS `publish_status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发布/1已发布/2已发布但有未发布修改',
  ADD COLUMN IF NOT EXISTS `published_at`          DATETIME     DEFAULT NULL COMMENT '最近一次发布时间',
  ADD COLUMN IF NOT EXISTS `published_by`          VARCHAR(32)  DEFAULT NULL COMMENT '最近一次发布人工号';

CREATE TABLE IF NOT EXISTS `RPT_SCREEN_PUBLISH_LOG` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`     BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `snapshot_json` LONGTEXT    NOT NULL COMMENT '发布时的渲染包(CANVAS_PUBLISHED_JSON 全量)',
  `published_by`  VARCHAR(32) DEFAULT NULL COMMENT '发布人工号',
  `published_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_pub_log_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏发布归档(按屏滚动保留最近10次)';

-- ===== 回滚脚本(如需撤销本次变更,手工执行以下语句;生产慎用,会丢发布归档与草稿) =====
-- ALTER TABLE `RPT_SCREEN`
--   DROP COLUMN `canvas_style_json`, DROP COLUMN `canvas_draft_json`,
--   DROP COLUMN `canvas_published_json`, DROP COLUMN `canvas_version`,
--   DROP COLUMN `publish_status`, DROP COLUMN `published_at`, DROP COLUMN `published_by`;
-- DROP TABLE IF EXISTS `RPT_SCREEN_PUBLISH_LOG`;
```
  手工执行(实现者本地):
```bash
mysql -uroot -pdjdev yiti                  < docs/superpowers/sql/2026-07-12-screen-canvas-ddl.sql
mysql -uroot -pdjdev onepl_test_bootstrap  < docs/superpowers/sql/2026-07-12-screen-canvas-ddl.sql
```
  提交:
```bash
git add docs/superpowers/sql/2026-07-12-screen-canvas-ddl.sql
git commit -m "feat(screen): 画布双态DDL——RPT_SCREEN 7列 + RPT_SCREEN_PUBLISH_LOG 新表(手工执行,禁Flyway)"
```

- [ ] 1.2 写失败测试:`RptScreenPublishLogMapperIT`(参照既有 `RptScreenMapperIT` 的 `@SpringBootTest` mapper IT 范式;若该文件用 `ReportTestApplication`+`@ActiveProfiles("test")`,照抄)。创建 `report-analytics-center/src/test/java/com/bank/branch/platform/report/mapper/RptScreenPublishLogMapperIT.java`:
```java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** RPT_SCREEN_PUBLISH_LOG Mapper IT(MyBatis-Plus BaseMapper 落真库 onepl_test_bootstrap). */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
class RptScreenPublishLogMapperIT {

    private static final Long SCREEN_ID = -998877L; // 负值测试屏,避开真实数据

    @Autowired
    private RptScreenPublishLogMapper mapper;

    @AfterEach
    void cleanup() {
        mapper.delete(new LambdaQueryWrapper<RptScreenPublishLog>()
                .eq(RptScreenPublishLog::getScreenId, SCREEN_ID));
    }

    @Test
    void insertThenSelectByScreen_roundTrips() {
        RptScreenPublishLog log = new RptScreenPublishLog();
        log.setScreenId(SCREEN_ID);
        log.setSnapshotJson("{\"schemaVersion\":1,\"components\":[]}");
        log.setPublishedBy("TEST_SCR_E9");
        log.setPublishedAt(LocalDateTime.now());
        mapper.insert(log);

        assertThat(log.getId()).isNotNull();
        var rows = mapper.selectList(new LambdaQueryWrapper<RptScreenPublishLog>()
                .eq(RptScreenPublishLog::getScreenId, SCREEN_ID));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSnapshotJson()).contains("schemaVersion");
    }
}
```

- [ ] 1.3 跑测试确认失败(编译不过 = Red,`RptScreenPublishLog`/`RptScreenPublishLogMapper` 尚不存在):
```bash
mvn -q -pl report-analytics-center test -Dtest=RptScreenPublishLogMapperIT
```
  预期:编译错误 `cannot find symbol: class RptScreenPublishLog`(Red 成立)。

- [ ] 1.4 最小实现——新表实体。创建 `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreenPublishLog.java`:
```java
package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN_PUBLISH_LOG 实体 —— 大屏发布归档(按屏滚动保留最近 10 次).
 *
 * <p>避坑:DataEase 双态互相覆盖后历史彻底丢失;本表在每次发布时留一份渲染包快照,支撑回滚。
 */
@Data
@TableName("RPT_SCREEN_PUBLISH_LOG")
public class RptScreenPublishLog {

    /** 主键(自增) */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属大屏 RPT_SCREEN.id */
    private Long screenId;

    /** 发布时的渲染包(CANVAS_PUBLISHED_JSON 全量) */
    private String snapshotJson;

    /** 发布人工号 */
    private String publishedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;
}
```

- [ ] 1.5 最小实现——新表 Mapper。创建 `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptScreenPublishLogMapper.java`:
```java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大屏发布归档 Mapper(CRUD 全走 MyBatis-Plus BaseMapper).
 */
@Mapper
public interface RptScreenPublishLogMapper extends BaseMapper<RptScreenPublishLog> {
}
```

- [ ] 1.6 最小实现——`RptScreen` 追加 7 字段。在 `report-analytics-center/.../entity/RptScreen.java` 的 `status` 字段之后、`createdBy` 之前插入(保持贫血模型 + 中文注释):
```java
    /** 画布全局样式JSON(设计基准/背景/适配策略/主题覆盖,携带 schemaVersion) */
    private String canvasStyleJson;

    /** 编辑态组件树JSON(草稿,编辑器唯一读写对象) */
    private String canvasDraftJson;

    /** 发布态渲染包JSON=组件树+图表绑定快照,线上/预览只读它 */
    private String canvasPublishedJson;

    /** 真乐观锁版本号:保存 WHERE canvas_version=? 并自增,冲突返回 RPT-43012 */
    private Integer canvasVersion;

    /** 发布状态:0未发布/1已发布/2已发布但有未发布修改 */
    private Integer publishStatus;

    /** 最近一次发布时间 */
    private java.time.LocalDateTime publishedAt;

    /** 最近一次发布人工号 */
    private String publishedBy;
```
  (`RptScreen` 已 `import java.time.LocalDateTime`,可直接用 `LocalDateTime publishedAt`;此处写全限定名以防漏 import,实现时二选一,保持编译通过。)

- [ ] 1.7 跑测试确认通过(Green)。跨模块改动先预构建再跑:
```bash
mvn -q clean install -DskipTests -pl report-analytics-center -am
mvn -q -pl report-analytics-center test -Dtest=RptScreenPublishLogMapperIT
```
  预期:`RptScreenPublishLogMapperIT` 1 个用例绿(需 1.1 的 DDL 已在 onepl_test_bootstrap 执行)。

- [ ] 1.8 提交(pathspec 精确列举):
```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreen.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptScreenPublishLog.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptScreenPublishLogMapper.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/mapper/RptScreenPublishLogMapperIT.java
git commit -m "feat(screen): RptScreen 画布双态字段 + RptScreenPublishLog 实体/Mapper(MyBatis-Plus,mapper IT 绿)"
```

**验收标准:** DDL 在两库执行成功(SHOW COLUMNS 见 7 新列 + RPT_SCREEN_PUBLISH_LOG 表存在);`RptScreenPublishLogMapperIT` 绿;`RptScreen` 编译通过、既有 `ScreenConfigServiceTest`/`ScreenControllerIT` 不受影响。

---

## Task 2: 错误码 RPT-43012 + 画布加载/保存服务(乐观锁·白名单·归属·上限·范围)

新增 `RPT-43012 画布保存冲突`,守护测试同步;实现 `ScreenCanvasService.loadCanvas/saveCanvas`。保存在单事务内:乐观锁自增、组件类型白名单、blockId 归属校验、JSON≤2MB、坐标/尺寸数值范围、blocks 增删改(upsert,保留 id,禁先删后插以免 blockId 引用失效)。

**Files:**
- Modify `report-analytics-center/.../enums/RptErrorCode.java`(在 43011 后加 43012)
- Modify `report-analytics-center/src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java`(44→45)
- Create `report-analytics-center/.../dto/req/CanvasComponentDTO.java`
- Create `report-analytics-center/.../dto/req/CanvasStyleDTO.java`
- Create `report-analytics-center/.../dto/req/ScreenCanvasSaveReqDTO.java`
- Create `report-analytics-center/.../dto/resp/ScreenCanvasEditorRespDTO.java`
- Create `report-analytics-center/.../dto/resp/ScreenCanvasSaveRespDTO.java`
- Create `report-analytics-center/.../service/screen/ScreenCanvasService.java`
- Create `report-analytics-center/.../service/screen/ScreenCanvasServiceImpl.java`
- Create `report-analytics-center/.../mapper/RptScreenCanvasMapper.java`(乐观锁自增 UPDATE 走 XML)
- Create `report-analytics-center/src/main/resources/mapper/RptScreenCanvasMapper.xml`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenCanvasServiceTest.java`

**Interfaces:**
- Produces:
  - `ScreenCanvasEditorRespDTO ScreenCanvasService.loadCanvas(Long id)`(styleJson/draftJson/blocks/canvasVersion/publishStatus)
  - `ScreenCanvasSaveRespDTO ScreenCanvasService.saveCanvas(ScreenCanvasSaveReqDTO req)`(canvasVersion/draftJson,回吐 resolved blockId)
  - `int RptScreenCanvasMapper.bumpVersion(@Param("id") Long id, @Param("expected") int expectedVersion, ...)`(乐观锁自增,返回受影响行数)
- Consumes: `RptScreenMapper`,`RptScreenBlockMapper`,`RptScreenDatasourceMapper`,`CurrentUserApi`,`ObjectMapper`。

**步骤:**

- [ ] 2.1 写失败测试:错误码守护 45 条 + 抽样。编辑 `RptErrorCodeTest.java`:把两处 `44` 改为 `45`(`hasSize(45)` 与 `isEqualTo(45)`),并在 `screenErrorCodes_shouldExistWith43xxxPrefix()` 末尾追加:
```java
        // 一期画布设计器新增:43012 画布保存冲突(真乐观锁)
        assertThat(RptErrorCode.SCREEN_CANVAS_CONFLICT.getCode()).isEqualTo("RPT-43012");
```
  同步注释里 44→45(把「V1 合计 44 条」相关注释更新为 45,说明「+ 画布保存冲突 RPT-43012 = 45」)。

- [ ] 2.2 跑测试确认失败(Red,`SCREEN_CANVAS_CONFLICT` 未定义 → 编译错误):
```bash
mvn -q -pl report-analytics-center test -Dtest=RptErrorCodeTest
```
  预期:编译错误 `cannot find symbol: SCREEN_CANVAS_CONFLICT`。

- [ ] 2.3 最小实现:`RptErrorCode` 在 `SCREEN_PERIOD_INVALID("RPT-43011", ...)` 之后追加:
```java
    /** 画布保存冲突:CANVAS_VERSION 乐观锁 WHERE 命中 0 行(他人/他标签页已保存),前端带最新 version 二次确认覆盖 */
    SCREEN_CANVAS_CONFLICT("RPT-43012", "画布保存冲突,请刷新后重试"),
```
  跑 `mvn -q -pl report-analytics-center test -Dtest=RptErrorCodeTest` 确认绿(Green)。提交:
```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/enums/RptErrorCodeTest.java
git commit -m "feat(screen): 新增错误码 RPT-43012 画布保存冲突 + RptErrorCodeTest 守护 45 条"
```

- [ ] 2.4 建 DTO(纯数据类,先建齐供后续步骤编译)。创建 5 个 DTO:

  `dto/req/CanvasStyleDTO.java`:
```java
package com.bank.branch.platform.report.dto.req;

import lombok.Data;
import java.util.Map;

/** 画布全局样式(对应 CANVAS_STYLE_JSON 的强类型入参). */
@Data
public class CanvasStyleDTO {
    /** schema 版本,自 v1 起 */
    private Integer schemaVersion = 1;
    private Integer designWidth = 1920;
    private Integer designHeight = 1080;
    private String background;
    /** 适配策略:keep/keepProportion/widthFirst/heightFirst */
    private String adaptor = "keepProportion";
    private Map<String, Object> themeOverride;
}
```

  `dto/req/CanvasComponentDTO.java`:
```java
package com.bank.branch.platform.report.dto.req;

import lombok.Data;
import java.util.Map;

/** 画布单个组件节点(对应 DRAFT.components[i]). */
@Data
public class CanvasComponentDTO {
    /** 客户端生成的稳定 id(w-xxxx) */
    private String id;
    /** 组件类型:ChartWidget/TextLabel/ImageBox/RectShape/BorderDecor/ClockWidget */
    private String component;
    /** 仅 ChartWidget:METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS */
    private String innerType;
    /** 仅 ChartWidget:关联的区块 id;素材组件为 null */
    private Long blockId;
    /** 位置尺寸(top/left/width/height,1920×1080 设计基准像素) */
    private Map<String, Object> style;
    /** 素材组件私有配置 */
    private Map<String, Object> propValue;
    /** 仅 ChartWidget 保存时携带绑定配置(bind/style/drill 三段 JSON 字符串),后端 upsert 进 block 行 */
    private String bindJson;
    private String styleJson;
    private String drillJson;
    private Boolean isLock = false;
    private Boolean isShow = true;
}
```

  `dto/req/ScreenCanvasSaveReqDTO.java`:
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/** 画布保存请求(styleJson + 组件树 + 乐观锁版本,单事务). */
@Data
public class ScreenCanvasSaveReqDTO {
    @NotNull
    private Long screenId;
    @Valid
    private CanvasStyleDTO canvasStyle;
    @Valid
    private List<CanvasComponentDTO> components;
    /** 客户端持有的期望版本(乐观锁);冲突返回 RPT-43012 */
    @NotNull
    private Integer expectedVersion;
}
```

  `dto/resp/ScreenCanvasEditorRespDTO.java`:
```java
package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;
import java.util.List;

/** 画布编辑器加载响应:styleJson + draftJson + blocks 行 + 版本/发布态. */
@Data
public class ScreenCanvasEditorRespDTO {
    private Long screenId;
    private String screenCode;
    private String screenName;
    private String viewLevel;
    /** 画布全局样式 JSON 字符串(前端 JSON.parse) */
    private String canvasStyleJson;
    /** 编辑态组件树 JSON 字符串 */
    private String canvasDraftJson;
    /** 区块行(取数配置,前端按 blockId 关联 ChartWidget) */
    private List<ScreenBlockDTO> blocks;
    private Integer canvasVersion;
    private Integer publishStatus;
}
```

  `dto/resp/ScreenCanvasSaveRespDTO.java`:
```java
package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/** 画布保存响应:新版本号 + 回吐 resolved blockId 后的 draftJson(前端采纳新 id). */
@Data
public class ScreenCanvasSaveRespDTO {
    private Integer canvasVersion;
    private String canvasDraftJson;
}
```

- [ ] 2.5 写失败测试:`ScreenCanvasServiceTest`(Mockito 单测,参照 `ScreenConfigServiceTest` 构造函数注入 mock)。创建 `.../service/screen/ScreenCanvasServiceTest.java`:
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** ScreenCanvasService 保存/加载单测(乐观锁冲突、白名单、归属、上限、范围). */
@ExtendWith(MockitoExtension.class)
class ScreenCanvasServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private CurrentUserApi currentUserApi;

    private ScreenCanvasServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenCanvasServiceImpl(screenMapper, blockMapper, dsMapper, canvasMapper, currentUserApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private RptScreen screen(long id, int version) {
        RptScreen s = new RptScreen();
        s.setId(id);
        s.setScreenCode("SCR_T");
        s.setViewLevel("BRANCH");
        s.setCanvasVersion(version);
        return s;
    }

    private CanvasComponentDTO comp(String component, String innerType, Map<String, Object> style) {
        CanvasComponentDTO c = new CanvasComponentDTO();
        c.setId("w-1");
        c.setComponent(component);
        c.setInnerType(innerType);
        c.setStyle(style);
        return c;
    }

    private ScreenCanvasSaveReqDTO req(long screenId, int expected, CanvasComponentDTO... comps) {
        ScreenCanvasSaveReqDTO r = new ScreenCanvasSaveReqDTO();
        r.setScreenId(screenId);
        r.setExpectedVersion(expected);
        r.setCanvasStyle(new CanvasStyleDTO());
        r.setComponents(List.of(comps));
        return r;
    }

    @Test
    void save_unknownScreen_throws43004() {
        when(screenMapper.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43004");
    }

    @Test
    void save_unknownComponentType_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("EvilWidget", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void save_negativeCoordinate_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("TextLabel", null, Map.of("top", -5, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void save_versionConflict_throws43012() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        // 乐观锁自增命中 0 行 = 冲突
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 3,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43012");
    }

    @Test
    void save_ok_bumpsVersionAndReturnsDraft() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        var resp = service.saveCanvas(req(7L, 3,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40))));
        org.assertj.core.api.Assertions.assertThat(resp.getCanvasVersion()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(resp.getCanvasDraftJson()).contains("TextLabel");
    }
}
```

- [ ] 2.6 跑测试确认失败(Red,`ScreenCanvasServiceImpl`/`RptScreenCanvasMapper` 尚不存在 → 编译错误):
```bash
mvn -q -pl report-analytics-center test -Dtest=ScreenCanvasServiceTest
```
  预期:编译错误 `cannot find symbol: ScreenCanvasServiceImpl`。

- [ ] 2.7 最小实现——乐观锁 Mapper。创建 `mapper/RptScreenCanvasMapper.java`:
```java
package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreen;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 大屏画布双态自定义 SQL(乐观锁自增/发布态整体更新).
 *
 * <p>单条 CRUD 复用 RptScreenMapper 的 BaseMapper;这里只放 BaseMapper 覆盖不到的
 * 「WHERE canvas_version=? 并自增」乐观锁 UPDATE。
 */
@Mapper
public interface RptScreenCanvasMapper extends BaseMapper<RptScreen> {

    /**
     * 乐观锁保存草稿:仅当 canvas_version=expected 时更新 style/draft 并自增版本。
     * @return 受影响行数(0=版本冲突)
     */
    int bumpVersion(@Param("id") Long id,
                    @Param("expected") int expectedVersion,
                    @Param("styleJson") String styleJson,
                    @Param("draftJson") String draftJson,
                    @Param("empId") String empId);

    /**
     * 发布态整体更新:写 published_json + published_at/by,并按目标状态置 publish_status。
     */
    int applyPublished(@Param("id") Long id,
                       @Param("publishedJson") String publishedJson,
                       @Param("publishStatus") int publishStatus,
                       @Param("empId") String empId);
}
```

- [ ] 2.8 最小实现——Mapper XML。创建 `src/main/resources/mapper/RptScreenCanvasMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.report.mapper.RptScreenCanvasMapper">

    <!-- 乐观锁:仅 canvas_version=#{expected} 时更新草稿并自增版本;命中 0 行=冲突 -->
    <update id="bumpVersion">
        UPDATE RPT_SCREEN
        SET canvas_style_json = #{styleJson},
            canvas_draft_json = #{draftJson},
            canvas_version    = canvas_version + 1,
            publish_status    = CASE WHEN publish_status = 1 THEN 2 ELSE publish_status END,
            updated_time      = NOW()
        WHERE id = #{id} AND canvas_version = #{expected} AND deleted = 0
    </update>

    <!-- 发布态整体更新 -->
    <update id="applyPublished">
        UPDATE RPT_SCREEN
        SET canvas_published_json = #{publishedJson},
            publish_status        = #{publishStatus},
            published_at          = NOW(),
            published_by          = #{empId},
            updated_time          = NOW()
        WHERE id = #{id} AND deleted = 0
    </update>
</mapper>
```
  (MyBatis mapper 位置 `classpath*:mapper/**/*Mapper.xml` 已在 application.yml 配置,开箱即用。)

- [ ] 2.9 最小实现——Service 接口 + 实现。创建 `service/screen/ScreenCanvasService.java`:
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;

/**
 * 大屏画布双态服务(加载/保存草稿;发布/回滚/放弃草稿见 Task 3).
 */
public interface ScreenCanvasService {

    /** 编辑器加载:styleJson + draftJson + blocks 行 + 版本/发布态 */
    ScreenCanvasEditorRespDTO loadCanvas(Long id);

    /** 保存草稿(乐观锁、白名单、归属、上限、范围;blocks 增删改单事务),返回新版本 + resolved draft */
    ScreenCanvasSaveRespDTO saveCanvas(ScreenCanvasSaveReqDTO req);
}
```
  创建 `service/screen/ScreenCanvasServiceImpl.java`(完整实现;结构化校验,禁字符串 contains):
```java
package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大屏画布双态服务实现(加载/保存草稿).
 *
 * <p>坐标模型:设计态恒 1920×1080 基准像素(幂等)。取数配置不进组件树,只在 RPT_SCREEN_BLOCK 行,
 * 组件树 ChartWidget 节点靠 blockId 弱关联(照搬 DataEase componentData/core_chart_view 分层边界)。
 */
@Slf4j
@Service
public class ScreenCanvasServiceImpl implements ScreenCanvasService {

    /** 组件类型白名单(用户输入必须验证红线) */
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "ChartWidget", "TextLabel", "ImageBox", "RectShape", "BorderDecor", "ClockWidget");
    /** ChartWidget 的 innerType 白名单(复用现有 5 图表) */
    private static final Set<String> INNER_TYPES = Set.of(
            "METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS");
    /** 画布 JSON 上限 2MB(字符数近似) */
    private static final int MAX_JSON_LEN = 2 * 1024 * 1024;
    private static final int DESIGN_W = 1920;
    private static final int DESIGN_H = 1080;

    private final RptScreenMapper screenMapper;
    private final RptScreenBlockMapper blockMapper;
    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenCanvasMapper canvasMapper;
    private final CurrentUserApi currentUserApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenCanvasServiceImpl(RptScreenMapper screenMapper,
                                   RptScreenBlockMapper blockMapper,
                                   RptScreenDatasourceMapper dsMapper,
                                   RptScreenCanvasMapper canvasMapper,
                                   CurrentUserApi currentUserApi) {
        this.screenMapper = screenMapper;
        this.blockMapper = blockMapper;
        this.dsMapper = dsMapper;
        this.canvasMapper = canvasMapper;
        this.currentUserApi = currentUserApi;
    }

    @Override
    public ScreenCanvasEditorRespDTO loadCanvas(Long id) {
        RptScreen s = requireScreen(id);
        ScreenCanvasEditorRespDTO d = new ScreenCanvasEditorRespDTO();
        d.setScreenId(s.getId());
        d.setScreenCode(s.getScreenCode());
        d.setScreenName(s.getScreenName());
        d.setViewLevel(s.getViewLevel());
        d.setCanvasStyleJson(s.getCanvasStyleJson());
        d.setCanvasDraftJson(s.getCanvasDraftJson());
        d.setCanvasVersion(s.getCanvasVersion() == null ? 0 : s.getCanvasVersion());
        d.setPublishStatus(s.getPublishStatus() == null ? 0 : s.getPublishStatus());
        d.setBlocks(listBlocks(s.getId()));
        return d;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenCanvasSaveRespDTO saveCanvas(ScreenCanvasSaveReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        List<CanvasComponentDTO> comps = req.getComponents() == null ? List.of() : req.getComponents();

        // 1) 结构化校验:组件类型白名单 + innerType + 坐标/尺寸范围
        for (CanvasComponentDTO c : comps) {
            if (c.getComponent() == null || !COMPONENT_TYPES.contains(c.getComponent())) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            if ("ChartWidget".equals(c.getComponent())
                    && (c.getInnerType() == null || !INNER_TYPES.contains(c.getInnerType()))) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            validateStyle(c.getStyle());
        }

        // 2) blocks 增删改(upsert,保留 id,禁先删后插以免 blockId 引用失效)。
        //    仅 ChartWidget 参与;素材组件不占 block 行。
        List<CanvasComponentDTO> chartNodes = comps.stream()
                .filter(c -> "ChartWidget".equals(c.getComponent()))
                .collect(Collectors.toList());
        Set<Long> keepBlockIds = new HashSet<>();
        for (CanvasComponentDTO c : chartNodes) {
            if (c.getBlockId() != null) {
                // 归属校验:禁越权引用他屏 block
                RptScreenBlock existing = blockMapper.selectById(c.getBlockId());
                if (existing == null || !existing.getScreenId().equals(s.getId())) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
                existing.setComponentType(c.getInnerType());
                existing.setBindJson(nullToEmptyObj(c.getBindJson()));
                existing.setStyleJson(c.getStyleJson());
                existing.setDrillJson(c.getDrillJson());
                blockMapper.updateById(existing);
                keepBlockIds.add(existing.getId());
            } else {
                RptScreenBlock e = new RptScreenBlock();
                e.setScreenId(s.getId());
                // 区块表 region/rowNo/colNo/widthPct/heightPct 是旧行/块布局遗留 NOT NULL 列,
                // 画布态不再用它们定位,置默认值只为满足非空约束(旧布局字段保留只读备份)。
                e.setRegion("MAIN");
                e.setRowNo(1);
                e.setColNo(1);
                e.setWidthPct(100);
                e.setHeightPct(100);
                e.setComponentType(c.getInnerType());
                e.setBindJson(nullToEmptyObj(c.getBindJson()));
                e.setStyleJson(c.getStyleJson());
                e.setDrillJson(c.getDrillJson());
                blockMapper.insert(e);
                c.setBlockId(e.getId()); // 回吐 resolved id
                keepBlockIds.add(e.getId());
            }
        }
        // 删除本屏不再被引用的孤儿 block 行
        List<RptScreenBlock> owned = blockMapper.selectList(
                new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        for (RptScreenBlock b : owned) {
            if (!keepBlockIds.contains(b.getId())) {
                blockMapper.deleteById(b.getId());
            }
        }

        // 3) 序列化组件树(含 resolved blockId)+ 样式;上限校验
        String styleJson = writeJson(req.getCanvasStyle());
        String draftJson = buildDraftJson(comps);
        if (draftJson.length() > MAX_JSON_LEN || styleJson.length() > MAX_JSON_LEN) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }

        // 4) 乐观锁自增(WHERE canvas_version=expected);命中 0 行=冲突
        int rows = canvasMapper.bumpVersion(s.getId(), req.getExpectedVersion(),
                styleJson, draftJson, currentUserApi.getCurrentEmpId());
        if (rows == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }

        ScreenCanvasSaveRespDTO resp = new ScreenCanvasSaveRespDTO();
        resp.setCanvasVersion(req.getExpectedVersion() + 1);
        resp.setCanvasDraftJson(draftJson);
        return resp;
    }

    // ===== 内部 =====

    /** 坐标/尺寸数值范围:top/left ∈ [0, 设计基准],width/height ∈ [1, 设计基准] */
    private void validateStyle(java.util.Map<String, Object> style) {
        if (style == null) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        int top = intOf(style.get("top"));
        int left = intOf(style.get("left"));
        int width = intOf(style.get("width"));
        int height = intOf(style.get("height"));
        if (top < 0 || left < 0 || width < 1 || height < 1
                || left + width > DESIGN_W || top + height > DESIGN_H) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
    }

    private int intOf(Object o) {
        if (o instanceof Number n) return n.intValue();
        throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
    }

    private String nullToEmptyObj(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private String buildDraftJson(List<CanvasComponentDTO> comps) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode arr = root.putArray("components");
        for (CanvasComponentDTO c : comps) {
            arr.add(objectMapper.valueToTree(c));
        }
        return root.toString();
    }

    private String writeJson(Object o) {
        try {
            return o == null ? "{}" : objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    private RptScreen requireScreen(Long id) {
        RptScreen s = screenMapper.selectById(id);
        if (s == null) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        return s;
    }

    private List<ScreenBlockDTO> listBlocks(Long screenId) {
        return blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                        .eq(RptScreenBlock::getScreenId, screenId))
                .stream().map(e -> {
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
                }).collect(Collectors.toList());
    }
}
```
  说明:测试用 `expectedVersion=req.getExpectedVersion()`,成功后返回 `expected+1` 与 DB `canvas_version+1` 一致。`dsMapper` 暂未在保存路径直接用(能力匹配校验留给发布/前端),保留注入以备后续绑定校验扩展并与测试签名一致。

- [ ] 2.10 跑测试确认通过(Green):
```bash
mvn -q -pl report-analytics-center test -Dtest=ScreenCanvasServiceTest
```
  预期:5 个用例全绿(43004/43006/43006/43012 + ok)。

- [ ] 2.11 提交:
```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/CanvasComponentDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/CanvasStyleDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenCanvasSaveReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenCanvasEditorRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenCanvasSaveRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptScreenCanvasMapper.java \
        report-analytics-center/src/main/resources/mapper/RptScreenCanvasMapper.xml \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenCanvasService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenCanvasServiceImpl.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenCanvasServiceTest.java
git commit -m "feat(screen): 画布加载/保存服务——乐观锁RPT-43012+白名单+blockId归属+JSON2MB+坐标范围(TDD绿)"
```

**验收标准:** `ScreenCanvasServiceTest` 5 绿;保存走乐观锁 XML 自增;blocks upsert 保留 id 不先删后插;越权 blockId/未知组件类型/负坐标/超界/版本冲突全部被对应错误码拦截。

---

## Task 3: 发布/回滚/放弃草稿服务(结构化解析·渲染包合成·归档滚动10份·状态机·审计)

在 `ScreenCanvasService` 追加 `publishCanvas/rollbackCanvas/discardDraft/listPublishLogs`。发布:结构化解析 DRAFT 收集 ChartWidget 的 blockId 集合,与本屏 block 行集合做一致性交叉校验(禁字符串 contains),合成渲染包(嵌入 bindSnapshots),写 PUBLISHED_JSON + 状态机流转 + PUBLISH_LOG 滚动保留 10 份 + 手工审计。回滚:从归档取一份覆盖 PUBLISHED_JSON。放弃草稿:发布态覆盖 DRAFT_JSON。

**Files:**
- Modify `.../service/screen/ScreenCanvasService.java`(加 4 方法)
- Modify `.../service/screen/ScreenCanvasServiceImpl.java`(实现 + 注入 `RptScreenPublishLogMapper`、`AuditApi`)
- Create `.../dto/req/ScreenCanvasPublishReqDTO.java`、`.../dto/req/ScreenCanvasRollbackReqDTO.java`
- Create `.../dto/resp/ScreenPublishLogRespDTO.java`
- Modify `.../service/screen/ScreenCanvasServiceTest.java`(加发布/回滚/放弃用例;构造函数多注入 2 个 mock)

**Interfaces:**
- Produces:
  - `void publishCanvas(ScreenCanvasPublishReqDTO req)`
  - `void rollbackCanvas(ScreenCanvasRollbackReqDTO req)`
  - `void discardDraft(Long screenId)`
  - `List<ScreenPublishLogRespDTO> listPublishLogs(Long screenId)`
- Consumes: `RptScreenPublishLogMapper`,`AuditApi.log(AuditLogCmd)`。

**步骤:**

- [ ] 3.1 建 DTO。`dto/req/ScreenCanvasPublishReqDTO.java`:
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发布请求(带乐观锁版本,防发布陈旧草稿). */
@Data
public class ScreenCanvasPublishReqDTO {
    @NotNull
    private Long screenId;
    /** 期望版本:与当前草稿版本不一致则拒绝(避免发布看到的不是最新草稿) */
    @NotNull
    private Integer expectedVersion;
}
```
  `dto/req/ScreenCanvasRollbackReqDTO.java`:
```java
package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 回滚请求:把某条归档发布回滚到 PUBLISHED_JSON. */
@Data
public class ScreenCanvasRollbackReqDTO {
    @NotNull
    private Long screenId;
    @NotNull
    private Long publishLogId;
}
```
  `dto/resp/ScreenPublishLogRespDTO.java`:
```java
package com.bank.branch.platform.report.dto.resp;

import lombok.Data;
import java.time.LocalDateTime;

/** 发布归档条目(回滚选择列表用,不回传 snapshotJson 全文). */
@Data
public class ScreenPublishLogRespDTO {
    private Long id;
    private Long screenId;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
```

- [ ] 3.2 写失败测试:在 `ScreenCanvasServiceTest` 追加发布/放弃用例。先把 `setUp()` 的构造改为多注入 2 个 mock(在类里加 `@Mock private RptScreenPublishLogMapper publishLogMapper;` 与 `@Mock private com.bank.branch.platform.governance.api.AuditApi auditApi;`,并改 `new ScreenCanvasServiceImpl(screenMapper, blockMapper, dsMapper, canvasMapper, currentUserApi, publishLogMapper, auditApi)`)。追加:
```java
    @org.junit.jupiter.api.Test
    void publish_blockIdSetMismatch_throws43006() {
        RptScreen s = screen(7L, 5);
        // 草稿引用 blockId=1001,但本屏 block 行集合不含它 → 一致性校验失败
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(blockMapper.selectList(any())).thenReturn(java.util.List.of()); // 无 block 行
        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @org.junit.jupiter.api.Test
    void publish_ok_writesPublishedAndArchives() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1,\"adaptor\":\"keepProportion\"}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenBlock b =
                new com.bank.branch.platform.report.entity.RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001,\"period\":\"LATEST\"}");
        when(blockMapper.selectList(any())).thenReturn(java.util.List.of(b));
        when(canvasMapper.applyPublished(anyLong(), anyString(), anyInt(), anyString())).thenReturn(1);
        // 归档滚动:selectList 查历史条数(返回空即无需裁剪)
        when(publishLogMapper.selectList(any())).thenReturn(java.util.List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        service.publishCanvas(req);

        org.mockito.Mockito.verify(canvasMapper).applyPublished(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.contains("bindSnapshots"),
                anyInt(), anyString());
        org.mockito.Mockito.verify(publishLogMapper).insert(
                any(com.bank.branch.platform.report.entity.RptScreenPublishLog.class));
    }

    @org.junit.jupiter.api.Test
    void discard_copiesPublishedComponentsToDraft() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"canvasStyle\":{},"
                + "\"components\":[{\"id\":\"w-9\",\"component\":\"TextLabel\","
                + "\"style\":{\"top\":0,\"left\":0,\"width\":10,\"height\":10}}],\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        service.discardDraft(7L);
        // 放弃后 draft 组件树来自 published.components
        org.mockito.Mockito.verify(screenMapper).updateById(
                org.mockito.ArgumentMatchers.argThat(x ->
                        x.getCanvasDraftJson() != null && x.getCanvasDraftJson().contains("w-9")));
    }
```
  (需在测试类 import 相应类型或用全限定名,如上。`anyInt`/`anyString`/`contains`/`eq` 从 `org.mockito.ArgumentMatchers` 静态引入或全限定。)

- [ ] 3.3 跑测试确认失败(Red,`publishCanvas`/`discardDraft` 未定义 + 构造函数签名变更 → 编译错误):
```bash
mvn -q -pl report-analytics-center test -Dtest=ScreenCanvasServiceTest
```
  预期:编译错误(方法/构造缺失)。

- [ ] 3.4 最小实现:`ScreenCanvasService` 追加 4 方法声明:
```java
    /** 发布:结构化解析草稿→blockId 一致性校验→合成渲染包→写 PUBLISHED+归档+状态机+审计 */
    void publishCanvas(com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO req);

    /** 从归档回滚指定一次发布到 PUBLISHED_JSON */
    void rollbackCanvas(com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO req);

    /** 放弃草稿:发布态组件树覆盖 DRAFT_JSON */
    void discardDraft(Long screenId);

    /** 发布归档列表(回滚选择用) */
    java.util.List<com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO> listPublishLogs(Long screenId);
```
  `ScreenCanvasServiceImpl` 改构造函数(多注入 2 个)并实现。构造:
```java
    private final RptScreenPublishLogMapper publishLogMapper;
    private final com.bank.branch.platform.governance.api.AuditApi auditApi;
    /** 每屏发布归档滚动保留份数(避坑:DataEase 双态互相覆盖后历史彻底丢失) */
    private static final int PUBLISH_LOG_KEEP = 10;

    public ScreenCanvasServiceImpl(RptScreenMapper screenMapper,
                                   RptScreenBlockMapper blockMapper,
                                   RptScreenDatasourceMapper dsMapper,
                                   RptScreenCanvasMapper canvasMapper,
                                   CurrentUserApi currentUserApi,
                                   RptScreenPublishLogMapper publishLogMapper,
                                   com.bank.branch.platform.governance.api.AuditApi auditApi) {
        this.screenMapper = screenMapper;
        this.blockMapper = blockMapper;
        this.dsMapper = dsMapper;
        this.canvasMapper = canvasMapper;
        this.currentUserApi = currentUserApi;
        this.publishLogMapper = publishLogMapper;
        this.auditApi = auditApi;
    }
```
  发布实现(结构化解析,禁字符串 contains):
```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishCanvas(ScreenCanvasPublishReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        int curVersion = s.getCanvasVersion() == null ? 0 : s.getCanvasVersion();
        if (!Integer.valueOf(curVersion).equals(req.getExpectedVersion())) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        // 1) 结构化解析 DRAFT 收集 ChartWidget 的 blockId 集合(不做字符串 contains)
        com.fasterxml.jackson.databind.JsonNode draft;
        try {
            draft = objectMapper.readTree(s.getCanvasDraftJson() == null ? "{}" : s.getCanvasDraftJson());
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        Set<Long> draftBlockIds = new HashSet<>();
        com.fasterxml.jackson.databind.JsonNode comps = draft.path("components");
        if (comps.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : comps) {
                if ("ChartWidget".equals(n.path("component").asText())) {
                    com.fasterxml.jackson.databind.JsonNode bid = n.path("blockId");
                    if (bid.isNumber()) {
                        draftBlockIds.add(bid.asLong());
                    } else {
                        // ChartWidget 必须有 blockId
                        throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                    }
                }
            }
        }
        // 2) 与本屏 block 行集合交叉一致性校验
        List<RptScreenBlock> rows = blockMapper.selectList(
                new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        Set<Long> rowIds = rows.stream().map(RptScreenBlock::getId).collect(Collectors.toSet());
        if (!rowIds.containsAll(draftBlockIds)) {
            // 草稿引用了不属于本屏的 block → 拒绝发布(发布渲染包与 block 行漂移防线)
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        // 3) 合成渲染包 = canvasStyle + components + bindSnapshots(从 block 行快照)
        ObjectNode pkg = objectMapper.createObjectNode();
        pkg.put("schemaVersion", 1);
        try {
            pkg.set("canvasStyle", objectMapper.readTree(
                    s.getCanvasStyleJson() == null ? "{}" : s.getCanvasStyleJson()));
            pkg.set("components", draft.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : draft.path("components"));
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        ObjectNode snaps = pkg.putObject("bindSnapshots");
        for (RptScreenBlock b : rows) {
            if (!draftBlockIds.contains(b.getId())) continue; // 只快照被引用的
            ObjectNode snap = snaps.putObject(String.valueOf(b.getId()));
            try {
                com.fasterxml.jackson.databind.JsonNode bind =
                        objectMapper.readTree(b.getBindJson() == null ? "{}" : b.getBindJson());
                snap.set("bind", bind);
                snap.put("componentType", b.getComponentType());
                snap.set("styleCfg", objectMapper.readTree(
                        b.getStyleJson() == null ? "{}" : b.getStyleJson()));
                snap.set("drill", objectMapper.readTree(
                        b.getDrillJson() == null ? "{}" : b.getDrillJson()));
            } catch (Exception e) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
            }
        }
        String publishedJson = pkg.toString();
        // 4) 状态机:发布后 publish_status=1;写 published + 审计
        int rowsUpd = canvasMapper.applyPublished(s.getId(), publishedJson, 1,
                currentUserApi.getCurrentEmpId());
        if (rowsUpd == 0) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        // 5) 归档 + 滚动保留最近 10 份
        RptScreenPublishLog logEntry = new RptScreenPublishLog();
        logEntry.setScreenId(s.getId());
        logEntry.setSnapshotJson(publishedJson);
        logEntry.setPublishedBy(currentUserApi.getCurrentEmpId());
        logEntry.setPublishedAt(java.time.LocalDateTime.now());
        publishLogMapper.insert(logEntry);
        trimPublishLogs(s.getId());
        // 6) 高危发布手工审计
        safelyAudit("SCREEN_PUBLISH", "publish screenId=" + s.getId(), "CONFIG");
    }
```
  回滚 / 放弃 / 列表 / 归档裁剪 / 审计:
```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCanvas(ScreenCanvasRollbackReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        RptScreenPublishLog logEntry = publishLogMapper.selectById(req.getPublishLogId());
        if (logEntry == null || !logEntry.getScreenId().equals(s.getId())) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        int rows = canvasMapper.applyPublished(s.getId(), logEntry.getSnapshotJson(), 1,
                currentUserApi.getCurrentEmpId());
        if (rows == 0) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        safelyAudit("SCREEN_ROLLBACK",
                "rollback screenId=" + s.getId() + " logId=" + req.getPublishLogId(), "CONFIG");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discardDraft(Long screenId) {
        RptScreen s = requireScreen(screenId);
        // 放弃草稿:发布态组件树覆盖 DRAFT(结构同源,直接取 published.components)
        String draftJson;
        try {
            com.fasterxml.jackson.databind.JsonNode pub = objectMapper.readTree(
                    s.getCanvasPublishedJson() == null ? "{}" : s.getCanvasPublishedJson());
            ObjectNode d = objectMapper.createObjectNode();
            d.put("schemaVersion", 1);
            d.set("components", pub.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : pub.path("components"));
            draftJson = d.toString();
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        RptScreen upd = new RptScreen();
        upd.setId(s.getId());
        upd.setCanvasDraftJson(draftJson);
        upd.setPublishStatus(1); // 放弃未发布修改 → 回到已发布态
        screenMapper.updateById(upd);
    }

    @Override
    public java.util.List<com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO>
            listPublishLogs(Long screenId) {
        return publishLogMapper.selectList(new LambdaQueryWrapper<RptScreenPublishLog>()
                        .eq(RptScreenPublishLog::getScreenId, screenId)
                        .orderByDesc(RptScreenPublishLog::getId))
                .stream().map(e -> {
                    var d = new com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO();
                    d.setId(e.getId());
                    d.setScreenId(e.getScreenId());
                    d.setPublishedBy(e.getPublishedBy());
                    d.setPublishedAt(e.getPublishedAt());
                    return d;
                }).collect(Collectors.toList());
    }

    /** 按屏滚动保留最近 PUBLISH_LOG_KEEP 份,超出删最旧 */
    private void trimPublishLogs(Long screenId) {
        List<RptScreenPublishLog> all = publishLogMapper.selectList(
                new LambdaQueryWrapper<RptScreenPublishLog>()
                        .eq(RptScreenPublishLog::getScreenId, screenId)
                        .orderByDesc(RptScreenPublishLog::getId));
        for (int i = PUBLISH_LOG_KEEP; i < all.size(); i++) {
            publishLogMapper.deleteById(all.get(i).getId());
        }
    }

    /** 高危操作手工审计(失败仅告警不阻断,模块惯例,参考 ScreenDatasourceServiceImpl.safelyAudit) */
    private void safelyAudit(String action, String detail, String reason) {
        try {
            com.bank.branch.platform.governance.api.dto.AuditLogCmd cmd =
                    com.bank.branch.platform.governance.api.dto.AuditLogCmd.builder()
                            .empId(currentUserApi.getCurrentEmpId())
                            .bizType("REPORT")
                            .bizAction(action)
                            .resourceUrl("/api/screen/admin/canvas")
                            .requestMethod("POST")
                            .requestParams(detail != null && detail.length() > 1000
                                    ? detail.substring(0, 1000) : detail)
                            .responseStatus(200)
                            .reason(reason)
                            .build();
            auditApi.log(cmd);
        } catch (RuntimeException ex) {
            log.warn("[ScreenCanvasService] AuditApi.log 失败 cause={}", ex.getMessage());
        }
    }
```

- [ ] 3.5 跑测试确认通过(Green):
```bash
mvn -q -pl report-analytics-center test -Dtest=ScreenCanvasServiceTest
```
  预期:含 2.5 的 5 个 + 本任务 3 个(mismatch/ok/discard),全绿。

- [ ] 3.6 提交:
```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenCanvasPublishReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenCanvasRollbackReqDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenPublishLogRespDTO.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenCanvasService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenCanvasServiceImpl.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/screen/ScreenCanvasServiceTest.java
git commit -m "feat(screen): 发布/回滚/放弃草稿服务——结构化blockId一致性+渲染包合成+归档滚动10份+状态机+审计(TDD绿)"
```

**验收标准:** 发布用例验证 `applyPublished` 收到含 `bindSnapshots` 的渲染包 + `publishLogMapper.insert` 被调;blockId 集合不一致抛 43006;放弃草稿把 published.components 写回 draft;审计失败不阻断。

---

## Task 4: Controller 5 端点 + 渲染接口改读 PUBLISHED_JSON(?preview=draft) + PT_RESOURCE/角色绑定

新增 `ScreenCanvasAdminController`(5 端点:load/save/publish/rollback/discard + 归档列表)挂 `/api/screen/admin/canvas`;改 `ScreenViewController` 读发布态渲染包并支持 `?preview=draft`;登记 PT_RESOURCE(发布/回滚独立高危资源 + R_ADMIN 兜底)。

**Files:**
- Create `.../controller/ScreenCanvasAdminController.java`
- Modify `.../controller/ScreenViewController.java`(view 加 `?preview` 参数,改读渲染包)
- Modify `.../service/screen/ScreenConfigService.java` + `ScreenConfigServiceImpl.java`(getViewByCode 改产出渲染包 DTO,支持 draft/published)
- Create `.../dto/resp/ScreenRenderRespDTO.java`
- Create `docs/superpowers/sql/2026-07-12-screen-canvas-resources.sql`
- Test: `.../controller/ScreenCanvasControllerIT.java`(extends BaseControllerIT)

**Interfaces:**
- Produces REST(全 `@BizAuth(bizType=REPORT)`):
  - `GET  /api/screen/admin/canvas/{id}` → ScreenCanvasEditorRespDTO(R_RPT_SCR_CV_GET,READ)
  - `POST /api/screen/admin/canvas/save` → ScreenCanvasSaveRespDTO(R_RPT_SCR_CV_SAVE,WRITE)
  - `POST /api/screen/admin/canvas/publish` → Void(R_RPT_SCR_CV_PUB,**高危** WRITE)
  - `POST /api/screen/admin/canvas/rollback` → Void(R_RPT_SCR_CV_RB,**高危** WRITE)
  - `POST /api/screen/admin/canvas/discard` → Void(R_RPT_SCR_CV_DISC,WRITE)
  - `GET  /api/screen/admin/canvas/{id}/publish-logs` → List<ScreenPublishLogRespDTO>(R_RPT_SCR_CV_GET 复用,READ)
  - `GET  /api/screen/view/{screenCode}?preview=` → ScreenRenderRespDTO(R_RPT_SCR_VIEW,READ)
- Consumes: `ScreenCanvasService`,`ScreenConfigService.getRenderByCode`。

**步骤:**

- [ ] 4.1 建渲染响应 DTO。`dto/resp/ScreenRenderRespDTO.java`:
```java
package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 大屏运行时渲染响应(发布态或草稿态渲染包直投).
 *
 * <p>renderPackageJson 即 CANVAS_PUBLISHED_JSON(或草稿合成包)的 JSON 字符串,
 * 前端 JSON.parse 后按绝对定位渲染。mapPoints 供 PROVINCE 屏地图(沿用)。
 */
@Data
public class ScreenRenderRespDTO {
    private Long screenId;
    private String screenCode;
    private String screenName;
    private String viewLevel;
    /** 渲染包 JSON(canvasStyle + components + bindSnapshots) */
    private String renderPackageJson;
    /** 状态:published / draft */
    private String state;
}
```

- [ ] 4.2 写失败测试:`ScreenCanvasControllerIT`(extends BaseControllerIT,MockMvc 落真库)。创建 `.../controller/ScreenCanvasControllerIT.java`:
```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 画布双态 Controller 端到端 IT(鉴权 Mock 放行,业务落真库). */
class ScreenCanvasControllerIT extends BaseControllerIT {

    @Autowired
    private RptScreenMapper screenMapper;

    private Long screenId;

    @BeforeEach
    void seed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("TEST_SCR_E9");
        RptScreen s = new RptScreen();
        s.setScreenCode("TEST_SCR_CV_" + System.currentTimeMillis() % 100000);
        s.setScreenName("TEST_SCR_画布屏");
        s.setViewLevel("BRANCH");
        s.setStatus("ACTIVE");
        s.setCanvasVersion(0);
        s.setPublishStatus(0);
        screenMapper.insert(s);
        screenId = s.getId();
    }

    @AfterEach
    void cleanup() {
        screenMapper.delete(new LambdaQueryWrapper<RptScreen>()
                .likeRight(RptScreen::getScreenName, "TEST_SCR_"));
    }

    @Test
    void saveThenLoad_roundTrips_andBumpsVersion() throws Exception {
        String body = """
                {"screenId":%d,"expectedVersion":0,
                 "canvasStyle":{"schemaVersion":1,"adaptor":"keepProportion"},
                 "components":[{"id":"w-1","component":"TextLabel",
                    "style":{"top":10,"left":10,"width":200,"height":48},
                    "propValue":{"text":"TEST_SCR"}}]}
                """.formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/save")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.canvasVersion").value(1));

        mvc.perform(get("/api/screen/admin/canvas/" + screenId))
                .andExpect(jsonPath("$.data.canvasVersion").value(1))
                .andExpect(jsonPath("$.data.canvasDraftJson").value(
                        org.hamcrest.Matchers.containsString("TextLabel")));
    }

    @Test
    void save_staleVersion_returns43012() throws Exception {
        // 先保存一次到 version=1,再用 expectedVersion=0 重复保存 → 冲突
        String body0 = """
                {"screenId":%d,"expectedVersion":0,"canvasStyle":{"schemaVersion":1},
                 "components":[]}""".formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/save")
                .contentType(MediaType.APPLICATION_JSON).content(body0));
        mvc.perform(post("/api/screen/admin/canvas/save")
                        .contentType(MediaType.APPLICATION_JSON).content(body0))
                .andExpect(jsonPath("$.code").value("RPT-43012"));
    }

    @Test
    void load_unknownScreen_returns43004() throws Exception {
        mvc.perform(get("/api/screen/admin/canvas/999999999"))
                .andExpect(jsonPath("$.code").value("RPT-43004"));
    }
}
```

- [ ] 4.3 跑测试确认失败(Red,端点不存在 → 404/无匹配):
```bash
mvn -q clean install -DskipTests -pl report-analytics-center -am
mvn -q -pl report-analytics-center test -Dtest=ScreenCanvasControllerIT
```
  预期:编译错误或 404(Controller 未创建)。

- [ ] 4.4 最小实现——Controller。创建 `.../controller/ScreenCanvasAdminController.java`:
```java
package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenCanvasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏画布设计器(管理端)——双态加载/保存/发布/回滚/放弃草稿.
 *
 * <p>发布/回滚为高危操作:独立 URL、独立 PT_RESOURCE、服务层手工审计。
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin/canvas")
@Tag(name = "大屏-画布设计器", description = "画布双态:草稿保存(乐观锁) + 发布/回滚(高危) + 放弃草稿")
@Validated
@RequiredArgsConstructor
public class ScreenCanvasAdminController {

    private final ScreenCanvasService canvasService;

    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "编辑器加载(style+draft+blocks+版本)")
    public ResponseWrapper<ScreenCanvasEditorRespDTO> load(@PathVariable Long id) {
        return ResponseWrapper.success(canvasService.loadCanvas(id));
    }

    @PostMapping("/save")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "保存草稿(乐观锁,冲突 RPT-43012)")
    public ResponseWrapper<ScreenCanvasSaveRespDTO> save(@Valid @RequestBody ScreenCanvasSaveReqDTO req) {
        return ResponseWrapper.success(canvasService.saveCanvas(req));
    }

    @PostMapping("/publish")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "发布(高危:合成渲染包+归档+审计)")
    public ResponseWrapper<Void> publish(@Valid @RequestBody ScreenCanvasPublishReqDTO req) {
        canvasService.publishCanvas(req);
        return ResponseWrapper.success();
    }

    @PostMapping("/rollback")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "回滚(高危:从归档恢复发布态)")
    public ResponseWrapper<Void> rollback(@Valid @RequestBody ScreenCanvasRollbackReqDTO req) {
        canvasService.rollbackCanvas(req);
        return ResponseWrapper.success();
    }

    @PostMapping("/discard")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "放弃草稿(发布态覆盖草稿)")
    public ResponseWrapper<Void> discard(@RequestBody ScreenCanvasRollbackReqDTO req) {
        // 复用 ScreenCanvasRollbackReqDTO 的 screenId(publishLogId 忽略);仅需 screenId
        canvasService.discardDraft(req.getScreenId());
        return ResponseWrapper.success();
    }

    @GetMapping("/{id}/publish-logs")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "发布归档列表(回滚选择用)")
    public ResponseWrapper<List<ScreenPublishLogRespDTO>> publishLogs(@PathVariable Long id) {
        return ResponseWrapper.success(canvasService.listPublishLogs(id));
    }
}
```

- [ ] 4.5 最小实现——渲染接口改读发布态。在 `ScreenConfigService` 加:
```java
    /** 运行时渲染包读取:state=published(默认) 读 PUBLISHED_JSON,draft 读草稿合成包 */
    com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO getRenderByCode(String screenCode, String state);
```
  在 `ScreenConfigServiceImpl` 实现(不动既有 `getViewByCode`,新增方法):
```java
    @Override
    public com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO
            getRenderByCode(String screenCode, String state) {
        List<RptScreen> hits = screenMapper.selectList(new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, screenCode)
                .eq(RptScreen::getStatus, "ACTIVE"));
        if (hits.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        RptScreen s = hits.get(0);
        var d = new com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO();
        d.setScreenId(s.getId());
        d.setScreenCode(s.getScreenCode());
        d.setScreenName(s.getScreenName());
        d.setViewLevel(s.getViewLevel());
        boolean draft = "draft".equalsIgnoreCase(state);
        if (draft) {
            // 草稿态:临时合成一个只含 components 的渲染包(bindSnapshots 由前端设计器内已持有 block,
            // 或运行时按 blockId 走 /api/screen/data 实时取数);此处直投 draft 组件树 + style。
            d.setRenderPackageJson(composeDraftPreview(s));
            d.setState("draft");
        } else {
            d.setRenderPackageJson(s.getCanvasPublishedJson());
            d.setState("published");
        }
        return d;
    }

    private String composeDraftPreview(RptScreen s) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode pkg = objectMapper.createObjectNode();
            pkg.put("schemaVersion", 1);
            pkg.set("canvasStyle", objectMapper.readTree(
                    s.getCanvasStyleJson() == null ? "{}" : s.getCanvasStyleJson()));
            com.fasterxml.jackson.databind.JsonNode draft = objectMapper.readTree(
                    s.getCanvasDraftJson() == null ? "{}" : s.getCanvasDraftJson());
            pkg.set("components", draft.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : draft.path("components"));
            pkg.putObject("bindSnapshots"); // 草稿预览留空,ChartWidget 走实时取数
            return pkg.toString();
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }
```
  改 `ScreenViewController.view` 增加 `?preview` 参数(published 默认;draft 需登录 + 屏管理权限):
```java
    @GetMapping("/{screenCode}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "按编码读取渲染包(默认发布态;preview=draft 读草稿,需登录+屏管理权限)")
    public ResponseWrapper<ScreenRenderRespDTO> view(
            @PathVariable String screenCode,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String preview) {
        // preview=draft:草稿预览。@BizAuth READ + 登录已保证会话有效;
        // 屏管理权限的进一步收敛:前端仅从管理端设计器(R_RPT_SCR_CV_* 菜单门禁)入口触发预览,
        // 且草稿内容非敏感(3 屏为开发测试态)。若后续草稿承载敏感数据,再补独立管理端渲染端点(见 §风险)。
        String state = "draft".equalsIgnoreCase(preview) ? "draft" : "published";
        return ResponseWrapper.success(configService.getRenderByCode(screenCode, state));
    }
```
  (import `ScreenRenderRespDTO`。既有 `getViewByCode`/`ScreenViewRespDTO` 保留,前端切换后可在 Task 10 视需要清理。)

- [ ] 4.6 跑测试确认通过(Green):
```bash
mvn -q clean install -DskipTests -pl report-analytics-center -am
mvn -q -pl report-analytics-center test -Dtest=ScreenCanvasControllerIT
```
  预期:3 个 IT 用例全绿(save/load/version 冲突/未知屏)。

- [ ] 4.7 写 PT_RESOURCE 资源脚本(参照 `2026-07-12-screen-dashboard-resources.sql` 现场约定:API 资源 `SYS_CODE='RPT'`、`PT_ROLE_RESOURCE.SYS_CODE='PLATFORM'`、`RESOURCE_ID≤20`、AntPath `*`)。创建 `docs/superpowers/sql/2026-07-12-screen-canvas-resources.sql`:
```sql
-- 大屏画布设计器 V2 端点 PT_RESOURCE 注册 + 角色绑定(2026-07-12)
-- 7 条 API 资源(load/save/publish/rollback/discard/publish-logs + view 已存在不重注)。
-- 发布/回滚为高危:R_RPT_SCR_CV_PUB / R_RPT_SCR_CV_RB 独立资源,仅授管理角色 + R_ADMIN。
-- 幂等:先删后插。目标库 yiti + onepl_test_bootstrap 手工执行。

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_CV_GET','R_RPT_SCR_CV_SAVE','R_RPT_SCR_CV_PUB','R_RPT_SCR_CV_RB',
   'R_RPT_SCR_CV_DISC','R_RPT_SCR_CV_LOG');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN
  ('R_RPT_SCR_CV_GET','R_RPT_SCR_CV_SAVE','R_RPT_SCR_CV_PUB','R_RPT_SCR_CV_RB',
   'R_RPT_SCR_CV_DISC','R_RPT_SCR_CV_LOG');

-- API 资源(ISMENU=0,STATUS=0 启用,SYS_CODE='RPT')
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_RPT_SCR_CV_GET', '/api/screen/admin/canvas/*',              'GET',  '大屏-画布加载',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_SAVE','/api/screen/admin/canvas/save',           'POST', '大屏-画布保存',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_PUB', '/api/screen/admin/canvas/publish',        'POST', '大屏-画布发布(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_RB',  '/api/screen/admin/canvas/rollback',       'POST', '大屏-画布回滚(高危)',0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_DISC','/api/screen/admin/canvas/discard',        'POST', '大屏-放弃草稿',   0,0,0,0,'RPT',NOW(),NOW()),
  ('R_RPT_SCR_CV_LOG', '/api/screen/admin/canvas/*/publish-logs', 'GET',  '大屏-发布归档',   0,0,0,0,'RPT',NOW(),NOW());
-- 注:GET /api/screen/admin/canvas/* 与 /api/screen/admin/canvas/*/publish-logs 均以 * 收尾但路径段数不同,
--     ResourceMatcher 按段匹配可区分(load 单段、logs 两段 + 尾段字面量)。若匹配器对多级 * 有歧义,
--     实现时把 load 资源 URL 收紧为 '/api/screen/admin/canvas/*'(单层),logs 用 '/api/screen/admin/canvas/*/publish-logs'。

-- 角色绑定
-- a) 管理类(load/save/discard/log)复制 R_RPT_SCR_CFG_SAVE 的角色集(与既有屏管理同档)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCV_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_CV_GET' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CV_DISC' UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SCR_CFG_SAVE';

-- b) 高危类(publish/rollback)复制 R_RPT_SQL_EXEC 的角色集(R_BACK_TECH,与试跑同档高危)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCVH_', t.RESOURCE_ID, '_', r.ROLE_ID), r.ROLE_ID, t.RESOURCE_ID, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE r
CROSS JOIN (
  SELECT 'R_RPT_SCR_CV_PUB' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_RB'
) t
WHERE r.RESOURCE_ID = 'R_RPT_SQL_EXEC';

-- c) 全量兜底:R_ADMIN 补齐 6 条尚未持有的(防 AUTH-40304)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT('SCRCVB_', t.RESOURCE_ID, '_R_ADMIN'), 'R_ADMIN', t.RESOURCE_ID, 'PLATFORM', NOW()
FROM (
  SELECT 'R_RPT_SCR_CV_GET' AS RESOURCE_ID UNION ALL SELECT 'R_RPT_SCR_CV_SAVE' UNION ALL
  SELECT 'R_RPT_SCR_CV_PUB' UNION ALL SELECT 'R_RPT_SCR_CV_RB' UNION ALL
  SELECT 'R_RPT_SCR_CV_DISC' UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM PT_ROLE_RESOURCE x WHERE x.ROLE_ID = 'R_ADMIN' AND x.RESOURCE_ID = t.RESOURCE_ID
);
```
  手工执行两库:
```bash
mysql -uroot -pdjdev yiti                 < docs/superpowers/sql/2026-07-12-screen-canvas-resources.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-12-screen-canvas-resources.sql
```

- [ ] 4.8 提交:
```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ScreenCanvasAdminController.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ScreenViewController.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenConfigService.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenConfigServiceImpl.java \
        report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenRenderRespDTO.java \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/controller/ScreenCanvasControllerIT.java \
        docs/superpowers/sql/2026-07-12-screen-canvas-resources.sql
git commit -m "feat(screen): 画布 5+1 端点 Controller + 渲染改读PUBLISHED_JSON(?preview=draft) + PT_RESOURCE高危资源/R_ADMIN兜底"
```

**验收标准:** `ScreenCanvasControllerIT` 全绿;6 条 PT_RESOURCE + 角色绑定入两库;发布/回滚为独立高危资源;R_ADMIN 兜底行齐全;`GET /api/screen/view/{code}?preview=draft` 返回草稿渲染包。

---

## Task 5: vitest 引入 + 三个纯函数 utils(snap/scale/snapshotStack)先行 TDD

引入 vitest(devDependency + npm script + config,只测纯函数),先 TDD 出三个坐标/吸附/快照纯函数。这三个是画布内核的算法底座,后续组件依赖它们。**全程只在 `xanzc_frontend/` 内,禁止改工作区既有的 `vite.config.js`(它已是未提交状态,不得卷入)——vitest 用独立 `vitest.config.js`。**

**Files:**
- Modify `xanzc_frontend/package.json`(devDependencies 加 vitest + scripts.test)
- Create `xanzc_frontend/vitest.config.js`
- Create `xanzc_frontend/src/views/screen/designer/utils/scale.js`
- Create `xanzc_frontend/src/views/screen/designer/utils/snap.js`
- Create `xanzc_frontend/src/views/screen/designer/utils/snapshotStack.js`
- Test: `xanzc_frontend/src/views/screen/designer/utils/__tests__/scale.spec.js`
- Test: `xanzc_frontend/src/views/screen/designer/utils/__tests__/snap.spec.js`
- Test: `xanzc_frontend/src/views/screen/designer/utils/__tests__/snapshotStack.spec.js`

**Interfaces:**
- Produces:
  - `scale.js`:`DESIGN_W/DESIGN_H`、`ADAPTORS`、`fitScale(vw,vh)`、`screenDeltaToDesign(px,scale)`、`clampRect({top,left,width,height},opts)`、`stageStyle(adaptor,vw,vh)`
  - `snap.js`:`computeSnap(cur, others, diff=3)` → `{top,left,lines}`(lines:命中线 key → 设计态像素位置)
  - `snapshotStack.js`:`createSnapshotStack(limit)`、`record`、`undo`、`redo`、`canUndo`、`canRedo`、`currentSnapshot`、`deepClone`

**步骤:**

- [ ] 5.1 引入 vitest。改 `package.json`:`devDependencies` 加 `"vitest": "^1.6.0"`;`scripts` 加 `"test": "vitest run"`、`"test:watch": "vitest"`。创建 `xanzc_frontend/vitest.config.js`:
```js
import { defineConfig } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

// 仅测纯函数 utils(node 环境,无需 jsdom);与运行时 vite.config.js 解耦,不卷入其未提交改动。
export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'node',
    include: ['src/**/__tests__/**/*.spec.js'],
    globals: false
  }
});
```
  安装依赖(后台跑,产出 package-lock 变化仅限 xanzc_frontend):
```bash
cd xanzc_frontend && npm install vitest@^1.6.0 --save-dev
```

- [ ] 5.2 写失败测试:`scale.spec.js`。创建 `xanzc_frontend/src/views/screen/designer/utils/__tests__/scale.spec.js`:
```js
import { describe, it, expect } from 'vitest';
import { DESIGN_W, DESIGN_H, ADAPTORS, fitScale, screenDeltaToDesign, clampRect, stageStyle } from '../scale';

describe('scale.js 坐标换算(设计态恒 1920×1080)', () => {
  it('基准常量', () => {
    expect(DESIGN_W).toBe(1920);
    expect(DESIGN_H).toBe(1080);
    expect(ADAPTORS).toEqual(['keep', 'keepProportion', 'widthFirst', 'heightFirst']);
  });

  it('fitScale 取宽高比较小值', () => {
    expect(fitScale(1920, 1080)).toBeCloseTo(1);
    expect(fitScale(960, 1080)).toBeCloseTo(0.5);   // 宽受限
    expect(fitScale(1920, 540)).toBeCloseTo(0.5);   // 高受限
    expect(fitScale(0, 0)).toBe(1);                 // 兜底
  });

  it('screenDeltaToDesign 屏幕位移除以 scale 换算回设计坐标', () => {
    expect(screenDeltaToDesign(100, 0.5)).toBe(200); // 缩到 50% 时屏幕移 100px = 设计移 200px
    expect(screenDeltaToDesign(100, 0)).toBe(100);   // scale<=0 兜底 1
  });

  it('clampRect 禁拖出画布 + 极小尺寸下限', () => {
    // 负坐标夹到 0
    expect(clampRect({ top: -10, left: -20, width: 100, height: 50 }))
      .toEqual({ top: 0, left: 0, width: 100, height: 50 });
    // 超右边界:left 回退保证 left+width<=1920
    expect(clampRect({ top: 0, left: 1900, width: 100, height: 50 }).left).toBe(1820);
    // 极小尺寸:width/height 提升到 minW/minH(默认 20)
    expect(clampRect({ top: 0, left: 0, width: 5, height: 3 }))
      .toEqual({ top: 0, left: 0, width: 20, height: 20 });
  });

  it('stageStyle 四种适配策略(一次性映射,幂等)', () => {
    expect(stageStyle('keep', 960, 540).scale).toBe(1);
    expect(stageStyle('widthFirst', 960, 1080).scale).toBeCloseTo(0.5);
    expect(stageStyle('heightFirst', 1920, 540).scale).toBeCloseTo(0.5);
    expect(stageStyle('keepProportion', 960, 540).scale).toBeCloseTo(0.5);
  });
});
```

- [ ] 5.3 跑测试确认失败(Red,`../scale` 不存在):
```bash
cd xanzc_frontend && npx vitest run src/views/screen/designer/utils/__tests__/scale.spec.js
```
  预期:`Failed to resolve import "../scale"`(Red)。

- [ ] 5.4 最小实现:`xanzc_frontend/src/views/screen/designer/utils/scale.js`:
```js
// 大屏画布坐标换算:设计态坐标恒 1920×1080 基准(幂等),画布容器 transform: scale;
// 鼠标屏幕位移换算回设计坐标需除以 scale。运行时渲染按适配策略做一次性映射,
// 不采用 DataEase 的增量换算模型(研读避坑 #1:有状态基准易漂移)。

export const DESIGN_W = 1920;
export const DESIGN_H = 1080;

// 适配策略联合枚举:全仓库唯一来源(研读避坑 #2:锁死可选值防文档/常量/实现三方漂移)
export const ADAPTORS = ['keep', 'keepProportion', 'widthFirst', 'heightFirst'];

/** 适应窗口缩放比:取宽/高比的较小值,保证整屏可见(对齐 ScreenView.fit) */
export function fitScale(viewportW, viewportH, designW = DESIGN_W, designH = DESIGN_H) {
  if (viewportW <= 0 || viewportH <= 0) return 1;
  return Math.min(viewportW / designW, viewportH / designH);
}

/** 屏幕像素位移 → 设计态位移(除以 scale;scale<=0 兜底 1) */
export function screenDeltaToDesign(deltaPx, scale) {
  const s = scale > 0 ? scale : 1;
  return deltaPx / s;
}

/** 夹取组件矩形到画布内:禁拖出(top/left>=0 且不越右/下界)+ 极小尺寸下限 */
export function clampRect({ top, left, width, height }, opts = {}) {
  const { designW = DESIGN_W, designH = DESIGN_H, minW = 20, minH = 20 } = opts;
  let w = Math.max(minW, Math.round(width));
  let h = Math.max(minH, Math.round(height));
  w = Math.min(w, designW);
  h = Math.min(h, designH);
  let l = Math.round(left);
  let t = Math.round(top);
  l = Math.min(Math.max(0, l), designW - w);
  t = Math.min(Math.max(0, t), designH - h);
  return { top: t, left: l, width: w, height: h };
}

/** 运行时舞台 CSS:按策略算 scale(一次性映射,幂等) */
export function stageStyle(adaptor, viewportW, viewportH, designW = DESIGN_W, designH = DESIGN_H) {
  const sw = viewportW / designW;
  const sh = viewportH / designH;
  let scale;
  switch (adaptor) {
    case 'keep': scale = 1; break;
    case 'widthFirst': scale = sw; break;
    case 'heightFirst': scale = sh; break;
    case 'keepProportion':
    default: scale = Math.min(sw, sh); break;
  }
  return { width: designW, height: designH, transform: `scale(${scale})`, scale };
}
```
  跑 `npx vitest run src/views/screen/designer/utils/__tests__/scale.spec.js` 确认绿(Green)。

- [ ] 5.5 写失败测试:`snap.spec.js`。创建 `.../utils/__tests__/snap.spec.js`:
```js
import { describe, it, expect } from 'vitest';
import { computeSnap } from '../snap';

describe('snap.js 吸附对齐(参照 DataEase MarkLine.vue showLine 改写的纯函数)', () => {
  const other = { top: 100, left: 200, width: 300, height: 150 }; // 参照组件

  it('左对左命中(diff<=3)→ 吸附并显示 yl 线', () => {
    const cur = { top: 500, left: 202, width: 100, height: 60 }; // left 202 距 200 差 2
    const r = computeSnap(cur, [other], 3);
    expect(r.left).toBe(200);           // 吸附到 200
    expect(r.lines.yl).toBe(200);        // 竖线在 x=200
    expect(r.top).toBe(500);             // top 无命中,原值
  });

  it('顶对顶命中 → 吸附并显示 xt 线', () => {
    const cur = { top: 98, left: 900, width: 100, height: 60 }; // top 98 距 100 差 2
    const r = computeSnap(cur, [other], 3);
    expect(r.top).toBe(100);
    expect(r.lines.xt).toBe(100);
  });

  it('中对中命中 → 吸附并显示 yc/xc 线', () => {
    // other 水平中心 = 200+150 = 350;cur 宽 100,left=301 → 中心 351,距 350 差 1
    const cur = { top: 500, left: 301, width: 100, height: 60 };
    const r = computeSnap(cur, [other], 3);
    expect(r.left).toBe(300);            // 中心对齐 → left = 350 - 50 = 300
    expect(r.lines.yc).toBe(350);
  });

  it('超阈值不吸附', () => {
    const cur = { top: 500, left: 210, width: 100, height: 60 }; // 距 200 差 10 > 3
    const r = computeSnap(cur, [other], 3);
    expect(r.left).toBe(210);
    expect(r.lines.yl).toBeFalsy();
  });

  it('多组件取最近命中', () => {
    const o2 = { top: 100, left: 205, width: 50, height: 50 };
    const cur = { top: 500, left: 203, width: 100, height: 60 };
    // 距 o.left(200)=3, 距 o2.left(205)=2 → 取更近的 205
    const r = computeSnap(cur, [other, o2], 3);
    expect(r.left).toBe(205);
  });
});
```

- [ ] 5.6 跑测试确认失败(Red):
```bash
cd xanzc_frontend && npx vitest run src/views/screen/designer/utils/__tests__/snap.spec.js
```
  预期:`Failed to resolve import "../snap"`。

- [ ] 5.7 最小实现:`xanzc_frontend/src/views/screen/designer/utils/snap.js`:
```js
// 吸附对齐计算——参照 DataEase MarkLine.vue 的 showLine 改写为纯函数。
// 与原实现差异:①去旋转(不做 rotate 分支);②改写成纯函数返回 {top,left,lines},
// 不直接 mutate store、不走 eventBus;③坐标全部为 1920×1080 设计态像素
// (调用方 Shape.vue 已把鼠标位移除以 scale 换算,故此处与 scale 无关)。
// 6 基准线:xt/xc/xb(横,对 top/中/bottom)、yl/yc/yr(竖,对 left/中/right),阈值 3px。

/**
 * @param {{top,left,width,height}} cur 当前拖拽组件设计态矩形
 * @param {Array<{top,left,width,height}>} others 其余组件矩形
 * @param {number} diff 吸附阈值(默认 3px)
 * @returns {{top:number,left:number,lines:Object}} 吸附后 top/left + 命中线(key→设计态像素位置)
 */
export function computeSnap(cur, others, diff = 3) {
  const curRight = cur.left + cur.width;
  const curBottom = cur.top + cur.height;
  const curHCenter = cur.left + cur.width / 2;
  const curVCenter = cur.top + cur.height / 2;

  let snapLeft = cur.left;
  let snapTop = cur.top;
  const lines = {}; // 命中线 → 像素位置
  let bestX = diff + 1;
  let bestY = diff + 1;

  for (const o of others) {
    const oRight = o.left + o.width;
    const oBottom = o.top + o.height;
    const oHCenter = o.left + o.width / 2;
    const oVCenter = o.top + o.height / 2;

    // 竖向(yl 左/右→左, yc 中→中, yr 左/右→右);pos = 对齐线的 x 像素
    const xs = [
      { d: Math.abs(cur.left - o.left),      left: o.left,               line: 'yl', pos: o.left },
      { d: Math.abs(curRight - o.left),      left: o.left - cur.width,   line: 'yl', pos: o.left },
      { d: Math.abs(curHCenter - oHCenter),  left: oHCenter - cur.width / 2, line: 'yc', pos: oHCenter },
      { d: Math.abs(cur.left - oRight),      left: oRight,               line: 'yr', pos: oRight },
      { d: Math.abs(curRight - oRight),      left: oRight - cur.width,   line: 'yr', pos: oRight }
    ];
    for (const c of xs) {
      if (c.d <= diff && c.d < bestX) {
        bestX = c.d;
        snapLeft = Math.round(c.left);
        delete lines.yl; delete lines.yc; delete lines.yr;
        lines[c.line] = Math.round(c.pos);
      }
    }

    // 横向(xt 顶/底→顶, xc 中→中, xb 顶/底→底);pos = 对齐线的 y 像素
    const ys = [
      { d: Math.abs(cur.top - o.top),        top: o.top,                 line: 'xt', pos: o.top },
      { d: Math.abs(curBottom - o.top),      top: o.top - cur.height,    line: 'xt', pos: o.top },
      { d: Math.abs(curVCenter - oVCenter),  top: oVCenter - cur.height / 2, line: 'xc', pos: oVCenter },
      { d: Math.abs(cur.top - oBottom),      top: oBottom,               line: 'xb', pos: oBottom },
      { d: Math.abs(curBottom - oBottom),    top: oBottom - cur.height,  line: 'xb', pos: oBottom }
    ];
    for (const c of ys) {
      if (c.d <= diff && c.d < bestY) {
        bestY = c.d;
        snapTop = Math.round(c.top);
        delete lines.xt; delete lines.xc; delete lines.xb;
        lines[c.line] = Math.round(c.pos);
      }
    }
  }
  return { top: snapTop, left: snapLeft, lines };
}
```
  跑 `npx vitest run src/views/screen/designer/utils/__tests__/snap.spec.js` 确认绿。

- [ ] 5.8 写失败测试:`snapshotStack.spec.js`。创建 `.../utils/__tests__/snapshotStack.spec.js`:
```js
import { describe, it, expect } from 'vitest';
import { createSnapshotStack, record, undo, redo, canUndo, canRedo, currentSnapshot } from '../snapshotStack';

describe('snapshotStack.js 撤销重做(参照 DataEase snapshot.ts 全量深拷贝快照数组)', () => {
  it('record 后 index 前进,currentSnapshot 是深拷贝', () => {
    let st = createSnapshotStack();
    const a = { components: [{ id: 'w-1' }] };
    st = record(st, a);
    a.components[0].id = 'mutated'; // 改原对象不应影响快照(深拷贝)
    expect(currentSnapshot(st).components[0].id).toBe('w-1');
  });

  it('undo/redo 指针移动', () => {
    let st = createSnapshotStack();
    st = record(st, { v: 1 });
    st = record(st, { v: 2 });
    st = record(st, { v: 3 });
    expect(canUndo(st)).toBe(true);
    expect(undo(st).v).toBe(2);
    expect(undo(st).v).toBe(1);
    expect(canUndo(st)).toBe(false); // 到底
    expect(redo(st).v).toBe(2);
  });

  it('undo 后 record 截断 redo 分支(对齐 snapshot.ts slice)', () => {
    let st = createSnapshotStack();
    st = record(st, { v: 1 });
    st = record(st, { v: 2 });
    undo(st);                 // 回到 v1
    st = record(st, { v: 9 }); // 新分支,截断 v2
    expect(canRedo(st)).toBe(false);
    expect(currentSnapshot(st).v).toBe(9);
  });

  it('超过 limit 丢弃最旧', () => {
    let st = createSnapshotStack(3);
    for (let i = 1; i <= 5; i++) st = record(st, { v: i });
    expect(st.data.length).toBe(3);
    expect(st.data[0].v).toBe(3); // 最旧 v1/v2 已丢
    expect(currentSnapshot(st).v).toBe(5);
  });
});
```

- [ ] 5.9 跑测试确认失败(Red),然后最小实现:`xanzc_frontend/src/views/screen/designer/utils/snapshotStack.js`:
```js
// 撤销/重做快照栈——参照 DataEase snapshot.ts 的「全量深拷贝快照数组 + 指针前进/后退」改写为纯函数。
// 与原实现差异:①剥离 store/eventBus/localStorage 副作用,只管栈结构,便于 vitest 覆盖;
// ②3 秒防抖(snapshotDisableTime)属交互层,放在 store 里,不进纯函数;
// ③加 limit 容量上限(DataEase 无上限),防长时间编辑内存膨胀。

export function deepClone(o) {
  return o == null ? o : JSON.parse(JSON.stringify(o));
}

export function createSnapshotStack(limit = 50) {
  return { data: [], index: -1, limit };
}

/** 记录一份全量深拷贝快照;undo 后再 record 会截断 redo 分支(对齐 snapshot.ts slice) */
export function record(stack, snapshot) {
  if (stack.index < stack.data.length - 1) {
    stack.data = stack.data.slice(0, stack.index + 1);
  }
  stack.data.push(deepClone(snapshot));
  if (stack.data.length > stack.limit) {
    stack.data.shift();
  }
  stack.index = stack.data.length - 1;
  return stack;
}

export function undo(stack) {
  if (stack.index > 0) stack.index--;
  return currentSnapshot(stack);
}

export function redo(stack) {
  if (stack.index < stack.data.length - 1) stack.index++;
  return currentSnapshot(stack);
}

export function canUndo(stack) { return stack.index > 0; }
export function canRedo(stack) { return stack.index < stack.data.length - 1; }

export function currentSnapshot(stack) {
  return stack.index >= 0 ? deepClone(stack.data[stack.index]) : null;
}
```
  跑三个 spec 全绿:
```bash
cd xanzc_frontend && npx vitest run
```
  预期:scale/snap/snapshotStack 三文件全部用例绿。

- [ ] 5.10 提交:
```bash
git add xanzc_frontend/package.json xanzc_frontend/package-lock.json xanzc_frontend/vitest.config.js \
        xanzc_frontend/src/views/screen/designer/utils/scale.js \
        xanzc_frontend/src/views/screen/designer/utils/snap.js \
        xanzc_frontend/src/views/screen/designer/utils/snapshotStack.js \
        xanzc_frontend/src/views/screen/designer/utils/__tests__/scale.spec.js \
        xanzc_frontend/src/views/screen/designer/utils/__tests__/snap.spec.js \
        xanzc_frontend/src/views/screen/designer/utils/__tests__/snapshotStack.spec.js
git commit -m "test(screen): 引入vitest + 画布内核三纯函数utils(scale/snap/snapshotStack)TDD绿"
```

**验收标准:** `npx vitest run` 三文件全绿;vitest 独立 config,未触碰工作区未提交的 `vite.config.js`;三 utils 覆盖坐标换算/吸附/快照栈全部边界用例。

---

## Task 6: Pinia store `screenDesigner.js`(componentData/canvasStyle/curComponent/快照集成/序列化)

用 setup-store 写法(对齐既有 `stores/user.js`)建设计器 store:持有 `componentData`(组件树)、`canvasStyle`、`curComponent`(选中引用)、`scale`;集成快照栈(3 秒防抖记快照)、图层操作(参照 layer.ts:up/down/top/bottom + show/hide/lock)、序列化(装载 loadCanvas 响应 / 产出 saveCanvas 请求体)。

**Files:**
- Create `xanzc_frontend/src/stores/screenDesigner.js`
- Test: `xanzc_frontend/src/stores/__tests__/screenDesigner.spec.js`(仅测纯逻辑:序列化/图层/快照,不挂组件)

**Interfaces:**
- Produces(store 返回):state `componentData`/`canvasStyle`/`curComponent`/`curIndex`/`scale`/`screenId`/`canvasVersion`/`dirty`;actions `loadFromEditor(resp)`、`toSavePayload()`、`addComponent(node)`、`removeCurrent()`、`selectComponent(id)`、`setShapeStyle(patch)`、`upComponent/downComponent/topComponent/bottomComponent`、`toggleLock/toggleShow`、`recordSnapshot()`、`undo()`、`redo()`、`pushSnapshotDebounced()`。
- Consumes: `utils/snapshotStack.js`、`utils/scale.js`。

**步骤:**

- [ ] 6.1 写失败测试:`screenDesigner.spec.js`(用 `setActivePinia(createPinia())` 初始化,测纯逻辑)。创建 `xanzc_frontend/src/stores/__tests__/screenDesigner.spec.js`:
```js
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useScreenDesignerStore } from '../screenDesigner';

describe('screenDesigner store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('loadFromEditor 装载 draft/style/blocks 并可 toSavePayload 往返', () => {
    const store = useScreenDesignerStore();
    store.loadFromEditor({
      screenId: 7, canvasVersion: 3, publishStatus: 0,
      canvasStyleJson: JSON.stringify({ schemaVersion: 1, adaptor: 'keepProportion' }),
      canvasDraftJson: JSON.stringify({ schemaVersion: 1, components: [
        { id: 'w-1', component: 'TextLabel', style: { top: 10, left: 10, width: 100, height: 40 }, propValue: { text: 'A' } }
      ] }),
      blocks: []
    });
    expect(store.componentData.length).toBe(1);
    expect(store.canvasVersion).toBe(3);
    const payload = store.toSavePayload();
    expect(payload.screenId).toBe(7);
    expect(payload.expectedVersion).toBe(3);
    expect(payload.components[0].component).toBe('TextLabel');
  });

  it('addComponent 后可选中并 setShapeStyle', () => {
    const store = useScreenDesignerStore();
    store.addComponent({ id: 'w-2', component: 'RectShape', style: { top: 0, left: 0, width: 50, height: 50 } });
    store.selectComponent('w-2');
    expect(store.curComponent.id).toBe('w-2');
    store.setShapeStyle({ left: 120, top: 60 });
    expect(store.curComponent.style.left).toBe(120);
  });

  it('图层 topComponent 把选中移到数组末尾(zIndex=顺序)', () => {
    const store = useScreenDesignerStore();
    store.addComponent({ id: 'a', component: 'RectShape', style: {} });
    store.addComponent({ id: 'b', component: 'RectShape', style: {} });
    store.selectComponent('a');
    store.topComponent();
    expect(store.componentData[store.componentData.length - 1].id).toBe('a');
  });

  it('undo/redo 还原 componentData', () => {
    const store = useScreenDesignerStore();
    store.recordSnapshot();                       // 空态
    store.addComponent({ id: 'x', component: 'RectShape', style: {} });
    store.recordSnapshot();                       // 有 x 态
    store.undo();
    expect(store.componentData.length).toBe(0);
    store.redo();
    expect(store.componentData.length).toBe(1);
  });
});
```

- [ ] 6.2 跑测试确认失败(Red),再最小实现。创建 `xanzc_frontend/src/stores/screenDesigner.js`(setup-store 写法):
```js
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import {
  createSnapshotStack, record as snapRecord, undo as snapUndo,
  redo as snapRedo, canUndo as snapCanUndo, canRedo as snapCanRedo, deepClone
} from '@/views/screen/designer/utils/snapshotStack';

/**
 * 大屏设计器 store(setup-store 写法,对齐 stores/user.js)。
 * - componentData:组件树(图层顺序=数组顺序,无 zIndex,照搬 DataEase)
 * - curComponent:选中组件的同一响应式引用(属性面板直接 mutate,画布自动联动)
 * - 坐标恒 1920×1080 设计基准
 */
export const useScreenDesignerStore = defineStore('screenDesigner', () => {
  const screenId = ref(null);
  const screenCode = ref('');
  const viewLevel = ref('BRANCH');
  const canvasVersion = ref(0);
  const publishStatus = ref(0);
  const canvasStyle = ref({ schemaVersion: 1, designWidth: 1920, designHeight: 1080,
    background: '#050e2b', adaptor: 'keepProportion', themeOverride: {} });
  const componentData = ref([]);
  const curComponent = ref(null);
  const curIndex = ref(-1);
  const blocks = ref([]);           // 区块行(ChartWidget 按 blockId 关联)
  const scale = ref(0.5);           // 编辑器画布缩放(50%~150% + 适应窗口)
  const dirty = ref(false);

  // 快照栈 + 3 秒防抖(防抖属交互层,故放 store)
  let stack = createSnapshotStack(60);
  let snapshotDisableUntil = 0;
  let debounceTimer = null;

  const canUndo = computed(() => snapCanUndo(stack));
  const canRedo = computed(() => snapCanRedo(stack));

  function snapshotBody() {
    return { componentData: deepClone(componentData.value), canvasStyle: deepClone(canvasStyle.value) };
  }
  function applySnapshot(snap) {
    if (!snap) return;
    componentData.value = deepClone(snap.componentData);
    canvasStyle.value = deepClone(snap.canvasStyle);
    curComponent.value = null;
    curIndex.value = -1;
  }
  /** 立即记快照(3 秒防抖窗口内跳过,避免连续拖拽污染栈——对齐 snapshot.ts snapshotDisableTime) */
  function recordSnapshot() {
    if (Date.now() < snapshotDisableUntil) return;
    stack = snapRecord(stack, snapshotBody());
    dirty.value = true;
  }
  /** 防抖记快照(拖拽/属性连续修改用) */
  function pushSnapshotDebounced() {
    dirty.value = true;
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => recordSnapshot(), 300);
  }
  function undo() {
    applySnapshot(snapUndo(stack));
    snapshotDisableUntil = Date.now() + 3000;
  }
  function redo() {
    applySnapshot(snapRedo(stack));
    snapshotDisableUntil = Date.now() + 3000;
  }

  // ===== 装载 / 序列化 =====
  function loadFromEditor(resp) {
    screenId.value = resp.screenId;
    screenCode.value = resp.screenCode || '';
    viewLevel.value = resp.viewLevel || 'BRANCH';
    canvasVersion.value = resp.canvasVersion ?? 0;
    publishStatus.value = resp.publishStatus ?? 0;
    blocks.value = resp.blocks || [];
    canvasStyle.value = parse(resp.canvasStyleJson, canvasStyle.value);
    const draft = parse(resp.canvasDraftJson, { components: [] });
    componentData.value = Array.isArray(draft.components) ? draft.components : [];
    curComponent.value = null;
    curIndex.value = -1;
    stack = createSnapshotStack(60);
    recordSnapshot();
    dirty.value = false;
  }
  function toSavePayload() {
    return {
      screenId: screenId.value,
      expectedVersion: canvasVersion.value,
      canvasStyle: deepClone(canvasStyle.value),
      // ChartWidget 携带 bind/style/drill(供后端 upsert block);素材组件带 propValue
      components: componentData.value.map(c => deepClone(c))
    };
  }
  function adoptSaveResult(resp) {
    canvasVersion.value = resp.canvasVersion;
    const draft = parse(resp.canvasDraftJson, { components: [] });
    componentData.value = Array.isArray(draft.components) ? draft.components : componentData.value;
    dirty.value = false;
  }

  // ===== 组件增删选中 =====
  function addComponent(node) {
    componentData.value.push(node);
    selectComponent(node.id);
    recordSnapshot();
  }
  function removeCurrent() {
    if (curIndex.value < 0) return;
    componentData.value.splice(curIndex.value, 1);
    curComponent.value = null;
    curIndex.value = -1;
    recordSnapshot();
  }
  function selectComponent(id) {
    const i = componentData.value.findIndex(c => c.id === id);
    curIndex.value = i;
    curComponent.value = i >= 0 ? componentData.value[i] : null;
  }
  function setShapeStyle(patch) {
    if (!curComponent.value) return;
    curComponent.value.style = { ...curComponent.value.style, ...patch };
  }

  // ===== 图层(参照 layer.ts:数组内 swap / splice) =====
  function swap(i, j) {
    const arr = componentData.value;
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  function upComponent() {
    if (curIndex.value >= 0 && curIndex.value < componentData.value.length - 1) {
      swap(curIndex.value, curIndex.value + 1); curIndex.value++; recordSnapshot();
    }
  }
  function downComponent() {
    if (curIndex.value > 0) { swap(curIndex.value, curIndex.value - 1); curIndex.value--; recordSnapshot(); }
  }
  function topComponent() {
    if (curIndex.value >= 0 && curIndex.value < componentData.value.length - 1) {
      const [c] = componentData.value.splice(curIndex.value, 1);
      componentData.value.push(c);
      curIndex.value = componentData.value.length - 1; recordSnapshot();
    }
  }
  function bottomComponent() {
    if (curIndex.value > 0) {
      const [c] = componentData.value.splice(curIndex.value, 1);
      componentData.value.unshift(c);
      curIndex.value = 0; recordSnapshot();
    }
  }
  function toggleLock(id) {
    const c = componentData.value.find(x => x.id === id);
    if (c) { c.isLock = !c.isLock; recordSnapshot(); }
  }
  function toggleShow(id) {
    const c = componentData.value.find(x => x.id === id);
    if (c) { c.isShow = c.isShow === false ? true : false; recordSnapshot(); }
  }

  function parse(json, fallback) {
    try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
  }

  return {
    screenId, screenCode, viewLevel, canvasVersion, publishStatus,
    canvasStyle, componentData, curComponent, curIndex, blocks, scale, dirty,
    canUndo, canRedo,
    loadFromEditor, toSavePayload, adoptSaveResult,
    addComponent, removeCurrent, selectComponent, setShapeStyle,
    upComponent, downComponent, topComponent, bottomComponent, toggleLock, toggleShow,
    recordSnapshot, pushSnapshotDebounced, undo, redo
  };
});
```
  跑 `npx vitest run src/stores/__tests__/screenDesigner.spec.js` 确认绿(需 pinia 已装,已在)。

- [ ] 6.3 提交:
```bash
git add xanzc_frontend/src/stores/screenDesigner.js \
        xanzc_frontend/src/stores/__tests__/screenDesigner.spec.js
git commit -m "feat(screen): 设计器 Pinia store——componentData/curComponent/快照防抖/图层/序列化(TDD绿)"
```

**验收标准:** `screenDesigner.spec.js` 全绿;store 用 setup-store 写法;序列化往返、图层置顶、undo/redo 均通过。

---

## Task 7: 画布内核四组件(CanvasCore / Shape / MarkLine / ContextMenu,完整改写代码)

移植裁剪 DataEase 画布内核:CanvasCore(容器+点选+drop+网格+右键入口)、Shape(拖拽移动 + 8 点缩放,rAF 节流,Shift 保持宽高比)、MarkLine(消费 `computeSnap` 显示 6 对齐线)、ContextMenu(复制/粘贴/删除/置顶置底/上移下移/锁定)。全部按我们坐标模型(设计态恒 1920×1080,容器 transform:scale,鼠标位移除以 scale)改写。

**Files:**
- Create `xanzc_frontend/src/views/screen/designer/canvas/CanvasCore.vue`
- Create `xanzc_frontend/src/views/screen/designer/canvas/Shape.vue`
- Create `xanzc_frontend/src/views/screen/designer/canvas/MarkLine.vue`
- Create `xanzc_frontend/src/views/screen/designer/canvas/ContextMenu.vue`

**Interfaces:**
- Consumes: `useScreenDesignerStore`、`utils/scale.js`(screenDeltaToDesign/clampRect)、`utils/snap.js`(computeSnap)、`widgets`(Task 8 的 componentsMap,渲染 `<component :is>`)。
- Produces: 事件流——Shape `@select`/拖拽缩放直接改 store.curComponent.style;CanvasCore 提供 drop 落点换算;ContextMenu 调 store 图层/删除/复制。

> 说明:这四个是有交互副作用的 SFC,vitest 只覆盖纯函数(Task 5 已覆盖其算法内核),本任务不写单测,验收靠 `vite build` exit 0 + Task 12 手动冒烟。骨架完整可运行。

**步骤:**

- [ ] 7.1 实现 `MarkLine.vue`(消费 store.curComponent + computeSnap,渲染命中线)。创建:
```vue
<template>
  <div class="dsn-mark-line">
    <div v-for="(pos, key) in lines" :key="key"
         class="dsn-line" :class="key.startsWith('x') ? 'x' : 'y'"
         :style="key.startsWith('x') ? { top: pos + 'px' } : { left: pos + 'px' }" />
  </div>
</template>

<script setup>
// 吸附对齐线:参照 DataEase MarkLine.vue,但算法抽到 utils/snap.js(纯函数),
// 本组件只做「订阅当前拖拽矩形 → 调 computeSnap → 渲染命中线 + 回写吸附坐标」。
import { ref } from 'vue';
import { computeSnap } from '@/views/screen/designer/utils/snap';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const store = useScreenDesignerStore();
const lines = ref({}); // 命中线 key→设计态像素

/**
 * 由 Shape.vue 在拖拽 move 中调用:传入当前组件的设计态矩形,返回吸附后的 {top,left}。
 * 其余组件 = componentData 中除当前选中外、且 isShow!==false 的组件。
 */
function snapAndShow(curRect) {
  const others = store.componentData
    .filter(c => c.id !== store.curComponent?.id && c.isShow !== false)
    .map(c => ({ top: c.style.top, left: c.style.left, width: c.style.width, height: c.style.height }));
  const r = computeSnap(curRect, others, 3);
  lines.value = r.lines;
  return { top: r.top, left: r.left };
}
function clear() { lines.value = {}; }

defineExpose({ snapAndShow, clear });
</script>

<style scoped>
.dsn-mark-line { position: absolute; inset: 0; pointer-events: none; z-index: 1000; }
.dsn-line { position: absolute; background: #59c7f9; }
.dsn-line.x { left: 0; width: 100%; height: 1px; }
.dsn-line.y { top: 0; width: 1px; height: 100%; }
</style>
```

- [ ] 7.2 实现 `Shape.vue`(拖拽移动 + 8 点缩放,完整改写)。创建:
```vue
<template>
  <div class="dsn-shape" :class="{ active, locked: element.isLock }"
       :style="shapeStyle" @mousedown.stop="onBodyDown" @contextmenu.prevent.stop="onContextMenu">
    <slot />
    <!-- 8 个缩放控制点,仅选中且未锁定时显示 -->
    <template v-if="active && !element.isLock">
      <div v-for="p in points" :key="p" class="dsn-point" :class="'p-' + p"
           :style="pointStyle(p)" @mousedown.stop.prevent="onPointDown(p, $event)" />
    </template>
  </div>
</template>

<script setup>
// 单组件可交互外框——参照 DataEase Shape.vue 的 handleMouseDownOnShape(拖拽)与
// handleMouseDownOnPoint(8 点缩放)改写。与原实现差异:
//  ① 去旋转:删除 getCursor/rotate 分支与 calculateComponentPositionAndSize 的旋转参数,
//     8 点缩放退化为「按点位直接调 top/left/width/height」的无旋转版;
//  ② 坐标模型:鼠标位移 (clientX-startX) 先除以 store.scale 换算成设计态位移(screenDeltaToDesign),
//     再叠加到设计态 top/left/width/height(恒 1920×1080 基准),不做 DataEase 的增量 scale 换算;
//  ③ rAF 节流(~30fps)保留;拖拽时调 MarkLine.snapAndShow 吸附;Shift 保持宽高比;
//  ④ clampRect 兜底禁拖出画布/极小尺寸。
import { computed, inject } from 'vue';
import { screenDeltaToDesign, clampRect } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const props = defineProps({
  element: { type: Object, required: true },
  index: { type: Number, required: true }
});
const store = useScreenDesignerStore();
const markLine = inject('markLineRef');       // CanvasCore provide 的 MarkLine 实例
const points = ['lt', 't', 'rt', 'r', 'rb', 'b', 'lb', 'l'];

const active = computed(() => store.curComponent?.id === props.element.id);
const shapeStyle = computed(() => ({
  position: 'absolute',
  top: props.element.style.top + 'px',
  left: props.element.style.left + 'px',
  width: props.element.style.width + 'px',
  height: props.element.style.height + 'px',
  display: props.element.isShow === false ? 'none' : 'block'
}));

function pointStyle(p) {
  // 8 点位置(百分比锚点);无旋转,直接摆四角四边中点
  const map = {
    lt: { left: '0', top: '0' }, t: { left: '50%', top: '0' }, rt: { left: '100%', top: '0' },
    r: { left: '100%', top: '50%' }, rb: { left: '100%', top: '100%' },
    b: { left: '50%', top: '100%' }, lb: { left: '0', top: '100%' }, l: { left: '0', top: '50%' }
  };
  const cursorMap = { lt: 'nwse-resize', rb: 'nwse-resize', rt: 'nesw-resize', lb: 'nesw-resize',
    t: 'ns-resize', b: 'ns-resize', l: 'ew-resize', r: 'ew-resize' };
  return { ...map[p], transform: 'translate(-50%, -50%)', cursor: cursorMap[p] };
}

function onBodyDown(e) {
  store.selectComponent(props.element.id);
  if (props.element.isLock) return;

  const start = { x: e.clientX, y: e.clientY };
  const origin = { ...props.element.style };
  let moved = false;
  let rafId = null, lastTs = 0;

  const move = ev => {
    const now = Date.now();
    if (now - lastTs < 25 && rafId) return;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      moved = true;
      // 屏幕位移 → 设计态位移(除以 scale)
      const dx = screenDeltaToDesign(ev.clientX - start.x, store.scale);
      const dy = screenDeltaToDesign(ev.clientY - start.y, store.scale);
      let next = { top: origin.top + dy, left: origin.left + dx,
        width: origin.width, height: origin.height };
      // 吸附对齐(命中则贴齐 + 显示对齐线)
      if (markLine?.value) {
        const snapped = markLine.value.snapAndShow({ ...next });
        next.top = snapped.top; next.left = snapped.left;
      }
      next = clampRect(next);
      store.setShapeStyle({ top: next.top, left: next.left });
      lastTs = now; rafId = null;
    });
  };
  const up = () => {
    if (rafId) cancelAnimationFrame(rafId);
    markLine?.value?.clear();
    document.removeEventListener('mousemove', move);
    document.removeEventListener('mouseup', up);
    if (moved) store.recordSnapshot(); // 有位移才记快照
  };
  document.addEventListener('mousemove', move);
  document.addEventListener('mouseup', up);
}

function onPointDown(point, e) {
  store.selectComponent(props.element.id);
  const start = { x: e.clientX, y: e.clientY };
  const origin = { ...props.element.style };
  const ratio = origin.width / origin.height;
  let resized = false;
  let rafId = null, lastTs = 0;

  const move = ev => {
    const now = Date.now();
    if (now - lastTs < 25 && rafId) return;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      resized = true;
      const dx = screenDeltaToDesign(ev.clientX - start.x, store.scale);
      const dy = screenDeltaToDesign(ev.clientY - start.y, store.scale);
      let { top, left, width, height } = origin;
      // 无旋转 8 点缩放:含 l 的点改 left+width,含 r 改 width,含 t 改 top+height,含 b 改 height
      if (point.includes('l')) { left = origin.left + dx; width = origin.width - dx; }
      if (point.includes('r')) { width = origin.width + dx; }
      if (point.includes('t')) { top = origin.top + dy; height = origin.height - dy; }
      if (point.includes('b')) { height = origin.height + dy; }
      // Shift 保持宽高比(以宽为准反推高,四角点生效)
      if (ev.shiftKey && point.length === 2) {
        height = width / ratio;
        if (point.includes('t')) top = origin.top + (origin.height - height);
      }
      const clamped = clampRect({ top, left, width, height });
      store.setShapeStyle(clamped);
      lastTs = now; rafId = null;
    });
  };
  const up = () => {
    if (rafId) cancelAnimationFrame(rafId);
    document.removeEventListener('mousemove', move);
    document.removeEventListener('mouseup', up);
    if (resized) store.recordSnapshot();
  };
  document.addEventListener('mousemove', move);
  document.addEventListener('mouseup', up);
}

const emit = defineEmits(['contextmenu']);
function onContextMenu(e) {
  store.selectComponent(props.element.id);
  emit('contextmenu', { x: e.offsetX, y: e.offsetY, clientX: e.clientX, clientY: e.clientY });
}
</script>

<style scoped>
.dsn-shape { box-sizing: border-box; }
.dsn-shape.active { outline: 1px solid #00e5ff; }
.dsn-shape.locked { cursor: not-allowed; }
.dsn-point { position: absolute; width: 8px; height: 8px; background: #fff;
  border: 1px solid #00e5ff; border-radius: 50%; z-index: 1001; }
</style>
```

- [ ] 7.3 实现 `ContextMenu.vue`(参照 contextmenu.ts state + 图层动作)。创建:
```vue
<template>
  <ul v-show="visible" class="dsn-ctx" :style="{ top: top + 'px', left: left + 'px' }"
      @mouseleave="hide">
    <li @click="act('copy')">复制</li>
    <li @click="act('paste')">粘贴</li>
    <li class="danger" @click="act('delete')">删除</li>
    <li class="sep" />
    <li @click="act('top')">置顶</li>
    <li @click="act('bottom')">置底</li>
    <li @click="act('up')">上移一层</li>
    <li @click="act('down')">下移一层</li>
    <li class="sep" />
    <li @click="act('lock')">{{ lockedLabel }}</li>
  </ul>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const store = useScreenDesignerStore();
const visible = ref(false);
const top = ref(0);
const left = ref(0);
let clipboard = null;

const lockedLabel = computed(() => store.curComponent?.isLock ? '解锁' : '锁定');

function show(x, y) { visible.value = true; top.value = y; left.value = x; }
function hide() { visible.value = false; }

function act(type) {
  const cur = store.curComponent;
  switch (type) {
    case 'copy': clipboard = cur ? JSON.parse(JSON.stringify(cur)) : null; break;
    case 'paste':
      if (clipboard) {
        const node = JSON.parse(JSON.stringify(clipboard));
        node.id = 'w-' + Math.random().toString(36).slice(2, 8);
        node.style = { ...node.style, top: (node.style.top || 0) + 20, left: (node.style.left || 0) + 20 };
        store.addComponent(node);
      }
      break;
    case 'delete': store.removeCurrent(); break;
    case 'top': store.topComponent(); break;
    case 'bottom': store.bottomComponent(); break;
    case 'up': store.upComponent(); break;
    case 'down': store.downComponent(); break;
    case 'lock': if (cur) store.toggleLock(cur.id); break;
  }
  hide();
}

defineExpose({ show, hide });
</script>

<style scoped>
.dsn-ctx { position: absolute; z-index: 2000; min-width: 120px; padding: 4px 0; margin: 0;
  list-style: none; background: #0a1f4e; border: 1px solid rgba(0,229,255,.3); border-radius: 4px;
  box-shadow: 0 4px 16px rgba(0,0,0,.4); color: #d5e6ff; font-size: 13px; }
.dsn-ctx li { padding: 6px 16px; cursor: pointer; }
.dsn-ctx li:hover { background: rgba(0,229,255,.12); }
.dsn-ctx li.danger:hover { background: rgba(255,82,82,.2); }
.dsn-ctx li.sep { height: 1px; padding: 0; margin: 4px 0; background: rgba(0,229,255,.15); cursor: default; }
</style>
```

- [ ] 7.4 实现 `CanvasCore.vue`(容器 + 网格 + transform:scale + drop 落点换算 + 渲染 Shape×组件 + 挂 MarkLine/ContextMenu)。创建:
```vue
<template>
  <div class="dsn-canvas-wrap" @mousedown.self="deselect" @drop.prevent="onDrop" @dragover.prevent>
    <div ref="stageRef" class="dsn-stage" :style="stageStyle"
         @contextmenu.prevent="onCanvasContextMenu">
      <!-- 组件层:图层顺序=componentData 数组顺序 -->
      <Shape v-for="(c, i) in store.componentData" :key="c.id" :element="c" :index="i"
             @contextmenu="onShapeContextMenu">
        <component :is="widgetOf(c.component)" :element="c" mode="design" />
      </Shape>
      <MarkLine ref="markLineRef" />
    </div>
    <ContextMenu ref="ctxRef" />
  </div>
</template>

<script setup>
// 画布容器——参照 DataEase CanvasCore.vue 裁剪:保留空白点选取消、drop 落组件、右键入口、网格;
// 去掉框选套索/Tab 移入移出/矩阵。舞台用 transform: scale(store.scale),设计态恒 1920×1080。
import { computed, provide, ref } from 'vue';
import { DESIGN_W, DESIGN_H, screenDeltaToDesign } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { findWidget, newComponentFromMeta } from '@/views/screen/designer/widgets';
import Shape from './Shape.vue';
import MarkLine from './MarkLine.vue';
import ContextMenu from './ContextMenu.vue';

const store = useScreenDesignerStore();
const stageRef = ref(null);
const markLineRef = ref(null);
const ctxRef = ref(null);
provide('markLineRef', markLineRef); // 供 Shape 拖拽时调吸附

const stageStyle = computed(() => ({
  position: 'relative',
  width: DESIGN_W + 'px',
  height: DESIGN_H + 'px',
  transform: `scale(${store.scale})`,
  transformOrigin: 'top left',
  background: store.canvasStyle.background || '#050e2b',
  // 40px 网格背景(编辑态)
  backgroundImage:
    'linear-gradient(rgba(255,255,255,.04) 1px, transparent 1px),' +
    'linear-gradient(90deg, rgba(255,255,255,.04) 1px, transparent 1px)',
  backgroundSize: '40px 40px'
}));

function widgetOf(component) { return findWidget(component); }
function deselect() { store.selectComponent('__none__'); ctxRef.value?.hide(); }

/** 组件面板拖入:e.dataTransfer 携带 component 类型,落点换算成设计态坐标 */
function onDrop(e) {
  const component = e.dataTransfer.getData('component');
  const innerType = e.dataTransfer.getData('innerType');
  if (!component) return;
  const rect = stageRef.value.getBoundingClientRect();
  // 落点(屏幕) - 舞台左上(屏幕),再除以 scale = 设计态坐标
  const left = screenDeltaToDesign(e.clientX - rect.left, store.scale);
  const top = screenDeltaToDesign(e.clientY - rect.top, store.scale);
  const node = newComponentFromMeta(component, innerType);
  node.style = { ...node.style, top: Math.round(top), left: Math.round(left) };
  store.addComponent(node);
}

function onShapeContextMenu(pos) { ctxRef.value?.show(pos.clientX, pos.clientY); }
function onCanvasContextMenu(e) { /* 空白右键暂不弹菜单 */ }
</script>

<style scoped>
.dsn-canvas-wrap { position: relative; flex: 1; overflow: auto; min-width: 0;
  display: flex; align-items: flex-start; justify-content: flex-start;
  padding: 24px; background: #03081c; }
.dsn-stage { flex: none; box-shadow: 0 0 0 1px rgba(0,229,255,.15); }
</style>
```

- [ ] 7.5 前端构建门禁(此时 widgets 尚未建,会 import 失败——本步只验证前 3 个不依赖 widgets 的组件语法;CanvasCore 依赖 widgets,留到 Task 8 后统一 build)。跑 vitest 回归确保未破坏纯函数:
```bash
cd xanzc_frontend && npx vitest run
```
  预期:Task 5/6 的 spec 仍全绿(本任务未改 utils/store)。

- [ ] 7.6 提交:
```bash
git add xanzc_frontend/src/views/screen/designer/canvas/CanvasCore.vue \
        xanzc_frontend/src/views/screen/designer/canvas/Shape.vue \
        xanzc_frontend/src/views/screen/designer/canvas/MarkLine.vue \
        xanzc_frontend/src/views/screen/designer/canvas/ContextMenu.vue
git commit -m "feat(screen): 画布内核四组件——CanvasCore/Shape(拖拽+8点缩放)/MarkLine(吸附)/ContextMenu(参照DataEase改写,去旋转+scale换算)"
```

**验收标准:** 四组件语法正确(Task 8 后 `vite build` exit 0 统一验);Shape 拖拽/缩放用 `screenDeltaToDesign` 除以 scale、用 `clampRect` 兜底;MarkLine 消费 `computeSnap`;ContextMenu 覆盖复制/粘贴/删除/图层/锁定;vitest 回归绿。

---

## Task 8: 组件体系(widgets 注册表 + 5 素材组件 + ChartWidget 复用 BlockContainer + import.meta.glob 扫描)

两层注册:素材组件手写字典 `componentsMap`(命名 `Xxx` + `XxxAttr`),图表元数据 `import.meta.glob` 自动扫描。5 素材(TextLabel/ImageBox/RectShape/BorderDecor/ClockWidget)各含 Component+Attr+meta;ChartWidget 容器复用现有 `BlockContainer.vue`(43010 引导态/silent toast 契约不变),内部按 innerType 挂 9 图表。

**Files:**
- Create `xanzc_frontend/src/views/screen/designer/widgets/index.js`(componentsMap + import.meta.glob + findWidget/findAttr/newComponentFromMeta/materialMetas/chartMetas)
- Create(每素材 3 文件)`widgets/text-label/{Component.vue,Attr.vue,meta.js}`、`image-box/*`、`rect-shape/*`、`border-decor/*`、`clock-widget/*`
- Create `widgets/chart-widget/{Component.vue,Attr.vue,meta.js}`(9 图表元数据 meta 各一份放 `widgets/chart-widget/charts/*.js`)
- Test: `xanzc_frontend/src/views/screen/designer/widgets/__tests__/registry.spec.js`(测注册表纯逻辑:findWidget/newComponentFromMeta)

**Interfaces:**
- Produces:`findWidget(component)`、`findAttr(component)`、`newComponentFromMeta(component, innerType)`、`materialMetas`(数组,组件面板「素材」分组)、`chartMetas`(数组,「图表」分组)。
- Consumes:`BlockContainer.vue`(ChartWidget 内复用),`_screen-theme.scss`(BorderDecor 复用 scr-surface 风格)。

**步骤:**

- [ ] 8.1 写失败测试:`registry.spec.js`(注册表纯逻辑;`import.meta.glob` 在 vitest 下需 `{ eager:true }` 且路径存在,故先建 meta 再跑;此步先写测试)。创建 `widgets/__tests__/registry.spec.js`:
```js
import { describe, it, expect } from 'vitest';
import { findWidget, newComponentFromMeta, materialMetas, chartMetas } from '../index';

describe('widgets 注册表', () => {
  it('素材元数据 5 个 + 图表元数据 9 个', () => {
    expect(materialMetas.length).toBe(5);
    expect(chartMetas.length).toBe(9);
  });
  it('findWidget 能取到素材/图表渲染组件', () => {
    expect(findWidget('TextLabel')).toBeTruthy();
    expect(findWidget('ChartWidget')).toBeTruthy();
    expect(findWidget('NotExist')).toBeFalsy();
  });
  it('newComponentFromMeta 生成带 id/style/默认 propValue 的节点', () => {
    const n = newComponentFromMeta('TextLabel');
    expect(n.id).toMatch(/^w-/);
    expect(n.component).toBe('TextLabel');
    expect(n.style.width).toBeGreaterThan(0);
    // ChartWidget 需带 innerType 与 blockId(null 待保存 upsert)
    const c = newComponentFromMeta('ChartWidget', 'METRIC_CARD');
    expect(c.innerType).toBe('METRIC_CARD');
    expect(c.blockId).toBeNull();
  });
});
```

- [ ] 8.2 建 5 素材组件 + meta(每个 Component 渲染 propValue、Attr 复用 CommonAttr 基座 + 私有项、meta 描述默认值)。**TextLabel**:
  `widgets/text-label/meta.js`:
```js
export default {
  component: 'TextLabel', label: '文本', group: 'material', icon: 'T',
  defaultStyle: { top: 40, left: 60, width: 300, height: 48 },
  defaultProps: { text: '标题文本', fontSize: 22, color: '#d5e6ff', align: 'left', bold: false }
};
```
  `widgets/text-label/Component.vue`:
```vue
<template>
  <div class="w-text" :style="textStyle">{{ p.text }}</div>
</template>
<script setup>
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const textStyle = computed(() => ({
  width: '100%', height: '100%', display: 'flex', alignItems: 'center',
  justifyContent: p.value.align === 'center' ? 'center' : p.value.align === 'right' ? 'flex-end' : 'flex-start',
  fontSize: (p.value.fontSize || 22) + 'px', color: p.value.color || '#d5e6ff',
  fontWeight: p.value.bold ? 700 : 400, overflow: 'hidden'
}));
</script>
```
  `widgets/text-label/Attr.vue`:
```vue
<template>
  <CommonAttr :element="element">
    <el-form-item label="文本"><el-input v-model="element.propValue.text" @input="touch" /></el-form-item>
    <el-form-item label="字号"><el-input-number v-model="element.propValue.fontSize" :min="8" :max="200" @change="touch" /></el-form-item>
    <el-form-item label="颜色"><el-color-picker v-model="element.propValue.color" @change="touch" /></el-form-item>
    <el-form-item label="对齐">
      <el-radio-group v-model="element.propValue.align" @change="touch">
        <el-radio-button label="left">左</el-radio-button>
        <el-radio-button label="center">中</el-radio-button>
        <el-radio-button label="right">右</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="加粗"><el-switch v-model="element.propValue.bold" @change="touch" /></el-form-item>
  </CommonAttr>
</template>
<script setup>
// 属性面板直接 mutate 同一份 store.curComponent 引用(照搬 DataEase 共享引用模式),改动防抖记快照。
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
function touch() { store.pushSnapshotDebounced(); }
</script>
```
  **RectShape** `meta.js`(defaultProps: `{ bg:'rgba(10,32,74,.55)', borderColor:'rgba(0,229,255,.25)', borderWidth:1, radius:6 }`)、`Component.vue`(渲染一个带背景/边框/圆角的 div)、`Attr.vue`(颜色/边框/圆角字段,同 TextLabel 范式)。
  **ImageBox**(仅 URL)`meta.js`(defaultProps: `{ url:'', fit:'contain' }`)、`Component.vue`(`<img :src fit>` 或空占位)、`Attr.vue`(URL 输入 + object-fit 选择)。
  **BorderDecor**(复用 `_screen-theme.scss` scr-surface 风格,2~3 种)`meta.js`(defaultProps: `{ variant:'tech-a' }`,variant∈tech-a/tech-b/tech-c)、`Component.vue`(按 variant 渲染不同发光边框装饰,复用 `.scr-block::before` 类科技边界视觉)、`Attr.vue`(variant 选择)。
  **ClockWidget** `meta.js`(defaultProps: `{ format:'YYYY-MM-DD HH:mm:ss', fontSize:18, color:'#7d9bc9' }`)、`Component.vue`(`setInterval` 每秒刷新,`onBeforeUnmount` 清理)、`Attr.vue`(格式/字号/颜色)。
  (5 素材的 Component/Attr 骨架同 TextLabel 范式:Component 只读 `props.element.propValue` 渲染,Attr 包 `<CommonAttr>` + 私有字段 + `@change/@input="touch"`。实现时逐一补齐,代码结构一致,不赘述完整体。)

- [ ] 8.3 建 ChartWidget(容器复用 BlockContainer,内部按 innerType 取数渲染)。`widgets/chart-widget/meta.js`:
```js
// ChartWidget 只在 componentsMap 占一个坑位;9 图表类型走 charts/*.js 自动扫描(两层注册解耦)。
export default {
  component: 'ChartWidget', label: '图表', group: 'chart', icon: '▤',
  defaultStyle: { top: 120, left: 240, width: 600, height: 320 },
  defaultProps: {}
};
```
  `widgets/chart-widget/charts/` 下 9 个元数据文件(每个 `export default { innerType, label, needTimeseries }`),对应现有 5 组件类型 + 未来扩展位;一期至少覆盖现有 5(METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS)并预留 4 个占位(如 BAR_COMPARE/GAUGE/TABLE_LIST/AREA_STACK,标 `enabled:false`),使 `chartMetas.length===9`。示例 `charts/metric-card.js`:
```js
export default { innerType: 'METRIC_CARD', label: '数值卡片', needTimeseries: false, enabled: true };
```
  `widgets/chart-widget/Component.vue`(设计态用轻量占位,运行态复用 BlockContainer;设计器内嵌用 block 行的 bind 实时取数):
```vue
<template>
  <div class="w-chart">
    <!-- 复用运行时 BlockContainer:把 ChartWidget 的 blockId 映射到 block 行(bind/style/drill JSON) -->
    <BlockContainer v-if="block" :block="block" :context="ctx" />
    <div v-else class="w-chart-empty">图表(未绑定数据源)</div>
  </div>
</template>
<script setup>
// 图表容器:一期图表统一挂此容器,innerType 区分;内部复用 BlockContainer 取数逻辑
// (43010 引导态/静默 toast 契约不变)。设计态 context 用空 orgCode/empId,靠 43010 引导态占位。
import { computed, inject } from 'vue';
import BlockContainer from '@/views/screen/components/BlockContainer.vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true }, mode: { type: String, default: 'design' } });
const store = useScreenDesignerStore();
// 设计态:从 store.blocks 按 blockId 找 block 行;运行态:element.propValue 已内嵌 bindSnapshot(见 Task 10)
const block = computed(() => {
  if (props.element.__block) return props.element.__block; // 运行态渲染包注入
  const b = store.blocks.find(x => x.id === props.element.blockId);
  return b ? { ...b, componentType: props.element.innerType } : null;
});
const ctx = inject('previewContext', { orgCode: '', empId: '' });
</script>
<style scoped>
.w-chart { width: 100%; height: 100%; }
.w-chart-empty { width: 100%; height: 100%; display: flex; align-items: center;
  justify-content: center; color: #7d9bc9; font-size: 13px; border: 1px dashed rgba(96,148,214,.38); }
</style>
```
  `widgets/chart-widget/Attr.vue`(图表私有:数据源下拉、周期、标题、刷新秒、钻取开关;写回 element 的 bindJson/styleJson/drillJson,供保存 upsert block):
```vue
<template>
  <CommonAttr :element="element">
    <el-form-item label="数据源">
      <el-select v-model="bind.dsId" filterable @change="syncBind">
        <el-option v-for="d in datasources" :key="d.id" :label="d.dsName" :value="d.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="周期"><el-input v-model="bind.period" placeholder="LATEST / LAST_10D" @input="syncBind" /></el-form-item>
    <el-form-item label="标题"><el-input v-model="styleCfg.title" @input="syncStyle" /></el-form-item>
    <el-form-item label="刷新(秒)"><el-input-number v-model="styleCfg.refreshSec" :min="0" @change="syncStyle" /></el-form-item>
    <el-form-item label="钻取"><el-switch v-model="drill.drillEnabled" @change="syncDrill" /></el-form-item>
  </CommonAttr>
</template>
<script setup>
import { reactive, ref, onMounted } from 'vue';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { listScreenDatasources } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const datasources = ref([]);
const bind = reactive(parse(props.element.bindJson));
const styleCfg = reactive(parse(props.element.styleJson));
const drill = reactive(parse(props.element.drillJson));
function parse(j) { try { return j ? JSON.parse(j) : {}; } catch { return {}; } }
function syncBind() { props.element.bindJson = JSON.stringify(bind); store.pushSnapshotDebounced(); }
function syncStyle() { props.element.styleJson = JSON.stringify(styleCfg); store.pushSnapshotDebounced(); }
function syncDrill() { props.element.drillJson = JSON.stringify(drill); store.pushSnapshotDebounced(); }
onMounted(async () => { datasources.value = await listScreenDatasources(); });
</script>
```

- [ ] 8.4 建注册表 `widgets/index.js`(两层注册):
```js
// 组件两层注册——参照 DataEase:①素材/装饰用手写字典 componentsMap(命名 Xxx + XxxAttr);
// ②图表元数据用 import.meta.glob 自动扫描(新增图表=新增一个 charts/*.js,零改核心)。
import TextLabel from './text-label/Component.vue';
import TextLabelAttr from './text-label/Attr.vue';
import textLabelMeta from './text-label/meta';
import ImageBox from './image-box/Component.vue';
import ImageBoxAttr from './image-box/Attr.vue';
import imageBoxMeta from './image-box/meta';
import RectShape from './rect-shape/Component.vue';
import RectShapeAttr from './rect-shape/Attr.vue';
import rectShapeMeta from './rect-shape/meta';
import BorderDecor from './border-decor/Component.vue';
import BorderDecorAttr from './border-decor/Attr.vue';
import borderDecorMeta from './border-decor/meta';
import ClockWidget from './clock-widget/Component.vue';
import ClockWidgetAttr from './clock-widget/Attr.vue';
import clockWidgetMeta from './clock-widget/meta';
import ChartWidget from './chart-widget/Component.vue';
import ChartWidgetAttr from './chart-widget/Attr.vue';
import chartWidgetMeta from './chart-widget/meta';

const componentsMap = {
  TextLabel, TextLabelAttr,
  ImageBox, ImageBoxAttr,
  RectShape, RectShapeAttr,
  BorderDecor, BorderDecorAttr,
  ClockWidget, ClockWidgetAttr,
  ChartWidget, ChartWidgetAttr
};

export const materialMetas = [textLabelMeta, imageBoxMeta, rectShapeMeta, borderDecorMeta, clockWidgetMeta];

// 图表类型自动扫描注册(eager 同步纳入):9 个 charts/*.js
const chartModules = import.meta.glob('./chart-widget/charts/*.js', { eager: true });
export const chartMetas = Object.values(chartModules).map(m => m.default);

export function findWidget(component) { return componentsMap[component] || null; }
export function findAttr(component) { return componentsMap[component + 'Attr'] || null; }

/** 从元数据造一个新组件节点(拖入画布 / 粘贴用) */
export function newComponentFromMeta(component, innerType) {
  const meta = component === 'ChartWidget'
    ? chartWidgetMeta
    : materialMetas.find(m => m.component === component);
  const base = meta || { defaultStyle: { top: 0, left: 0, width: 200, height: 120 }, defaultProps: {} };
  const node = {
    id: 'w-' + Math.random().toString(36).slice(2, 8),
    component,
    style: { ...base.defaultStyle },
    propValue: JSON.parse(JSON.stringify(base.defaultProps || {})),
    isLock: false, isShow: true
  };
  if (component === 'ChartWidget') {
    node.innerType = innerType || 'METRIC_CARD';
    node.blockId = null;      // 待保存时后端 upsert 得 id
    node.bindJson = '{}';
    node.styleJson = JSON.stringify({ title: '未命名图表', refreshSec: 60 });
    node.drillJson = '{}';
  }
  return node;
}
```

- [ ] 8.5 跑测试确认通过(Green):
```bash
cd xanzc_frontend && npx vitest run src/views/screen/designer/widgets/__tests__/registry.spec.js
```
  预期:注册表 3 用例绿(materialMetas=5、chartMetas=9、newComponentFromMeta 生成正确节点)。

- [ ] 8.6 提交:
```bash
git add xanzc_frontend/src/views/screen/designer/widgets/
git commit -m "feat(screen): 组件体系——widgets两层注册(componentsMap+import.meta.glob)+5素材+ChartWidget复用BlockContainer(TDD绿)"
```

**验收标准:** `registry.spec.js` 绿;5 素材 + ChartWidget 各有 Component/Attr;9 图表元数据自动扫描;ChartWidget 复用 BlockContainer(43010 契约不变);Attr 直接 mutate curComponent + 防抖记快照。

---

## Task 9: 面板 + DesignerV2 三栏组装 + 顶部工具条 + 快捷键 + api/screen.js 新增 + 路由替换

组装 ComponentPanel(素材/图表分组,dragstart 塞 dataTransfer)/LayerPanel(图层列表 + 置顶置底上移下移 + 右键)/CommonAttr 基座(位置尺寸/背景/边框/透明度)/CanvasAttr(画布全局设置);DesignerV2 三栏 + 顶部工具条(屏选择/保存/发布/回滚/放弃/预览/undo-redo)+ 快捷键(Ctrl+Z/Y/C/V/Del/Ctrl+S/方向键,弹框禁用);api/screen.js 加 5 函数;路由把 designer 指向 DesignerV2。

**Files:**
- Create `widgets` 无关的 `panels/CommonAttr.vue`、`panels/CanvasAttr.vue`、`panels/ComponentPanel.vue`、`panels/LayerPanel.vue`
- Create `xanzc_frontend/src/views/screen/designer/DesignerV2.vue`
- Modify `xanzc_frontend/src/api/screen.js`(加 5 函数)
- Modify `xanzc_frontend/src/router/index.js`(第 66 行 designer 组件路径替换)

**Interfaces:**
- Produces api:`getScreenCanvas(id)`、`saveScreenCanvas(data)`、`publishScreenCanvas(data)`、`rollbackScreenCanvas(data)`、`discardScreenCanvas(screenId)`、`listScreenPublishLogs(id)`。
- Consumes:CanvasCore、widgets(findAttr/materialMetas/chartMetas)、store、utils/scale(fitScale)。

**步骤:**

- [ ] 9.1 api/screen.js 加 6 函数(先做,DesignerV2 依赖)。在 `xanzc_frontend/src/api/screen.js` 末尾追加:
```js
// ===== 画布设计器 V2(双态) =====
export function getScreenCanvas(id) {
  return call('get', `/screen/admin/canvas/${id}`, {}, null);
}
export function saveScreenCanvas(data) {
  return call('post', '/screen/admin/canvas/save', { data });
}
export function publishScreenCanvas(data) {
  return call('post', '/screen/admin/canvas/publish', { data });
}
export function rollbackScreenCanvas(data) {
  return call('post', '/screen/admin/canvas/rollback', { data });
}
export function discardScreenCanvas(screenId) {
  return call('post', '/screen/admin/canvas/discard', { data: { screenId } });
}
export function listScreenPublishLogs(id) {
  return call('get', `/screen/admin/canvas/${id}/publish-logs`, {}, []);
}
```

- [ ] 9.2 建 `CommonAttr.vue`(公共属性基座:位置尺寸数值输入/背景/边框/透明度 + slot 私有项)。创建 `panels/CommonAttr.vue`:
```vue
<template>
  <div class="dsn-attr">
    <el-collapse v-model="open">
      <el-collapse-item title="位置与尺寸" name="pos">
        <el-form label-width="42px" size="small">
          <div class="row2">
            <el-form-item label="X"><el-input-number :model-value="element.style.left" :min="0" @change="v => set('left', v)" /></el-form-item>
            <el-form-item label="Y"><el-input-number :model-value="element.style.top" :min="0" @change="v => set('top', v)" /></el-form-item>
          </div>
          <div class="row2">
            <el-form-item label="宽"><el-input-number :model-value="element.style.width" :min="1" @change="v => set('width', v)" /></el-form-item>
            <el-form-item label="高"><el-input-number :model-value="element.style.height" :min="1" @change="v => set('height', v)" /></el-form-item>
          </div>
        </el-form>
      </el-collapse-item>
      <el-collapse-item title="外观" name="look">
        <el-form label-width="52px" size="small">
          <el-form-item label="透明度">
            <el-slider :model-value="opacity" :min="0" :max="100" @change="setOpacity" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      <slot />  <!-- 组件私有属性项 -->
    </el-collapse>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue';
import { clampRect } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const open = ref(['pos', 'look']);
const opacity = computed(() => Math.round((props.element.style.opacity ?? 1) * 100));
function set(key, v) {
  const next = clampRect({ ...props.element.style, [key]: v });
  props.element.style = { ...props.element.style, ...next };
  store.pushSnapshotDebounced();
}
function setOpacity(v) {
  props.element.style = { ...props.element.style, opacity: v / 100 };
  store.pushSnapshotDebounced();
}
</script>
<style scoped>
.dsn-attr { padding: 8px; } .row2 { display: flex; gap: 8px; }
</style>
```

- [ ] 9.3 建 `CanvasAttr.vue`(未选中时画布全局设置:背景、适配策略)。创建 `panels/CanvasAttr.vue`:
```vue
<template>
  <div class="dsn-attr">
    <el-form label-width="72px" size="small">
      <el-form-item label="背景色"><el-color-picker v-model="store.canvasStyle.background" @change="touch" /></el-form-item>
      <el-form-item label="适配策略">
        <el-select v-model="store.canvasStyle.adaptor" @change="touch">
          <el-option v-for="a in ADAPTORS" :key="a" :label="adaptorLabel(a)" :value="a" />
        </el-select>
      </el-form-item>
      <el-form-item label="设计基准"><span class="dim">1920 × 1080(固定)</span></el-form-item>
    </el-form>
  </div>
</template>
<script setup>
import { ADAPTORS } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
function touch() { store.pushSnapshotDebounced(); }
function adaptorLabel(a) {
  return { keep: '不缩放居中', keepProportion: '等比适配', widthFirst: '宽度铺满', heightFirst: '高度铺满' }[a] || a;
}
</script>
<style scoped>.dsn-attr { padding: 12px; } .dim { color: #7d9bc9; font-size: 12px; }</style>
```

- [ ] 9.4 建 `ComponentPanel.vue`(素材/图表两分组,HTML5 dragstart 塞 dataTransfer)。创建 `panels/ComponentPanel.vue`:
```vue
<template>
  <div class="dsn-comp-panel">
    <div class="grp-title">素材组件</div>
    <div class="grp">
      <div v-for="m in materialMetas" :key="m.component" class="cell" draggable="true"
           @dragstart="onDrag($event, m.component)">
        <span class="ic">{{ m.icon }}</span><span>{{ m.label }}</span>
      </div>
    </div>
    <div class="grp-title">图表组件</div>
    <div class="grp">
      <div v-for="c in enabledCharts" :key="c.innerType" class="cell" draggable="true"
           @dragstart="onDrag($event, 'ChartWidget', c.innerType)">
        <span class="ic">▤</span><span>{{ c.label }}</span>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue';
import { materialMetas, chartMetas } from '@/views/screen/designer/widgets';
const enabledCharts = computed(() => chartMetas.filter(c => c.enabled !== false));
function onDrag(e, component, innerType = '') {
  e.dataTransfer.setData('component', component);
  e.dataTransfer.setData('innerType', innerType);
  e.dataTransfer.effectAllowed = 'copy';
}
</script>
<style scoped>
.dsn-comp-panel { padding: 8px; } .grp-title { color: #00e5ff; font-size: 13px; margin: 10px 4px 6px; }
.grp { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.cell { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 4px;
  background: rgba(10,32,74,.5); border: 1px solid rgba(0,229,255,.18); border-radius: 4px;
  cursor: grab; color: #d5e6ff; font-size: 12px; }
.cell:hover { border-color: #00e5ff; } .ic { font-size: 20px; color: #00e5ff; }
</style>
```

- [ ] 9.5 建 `LayerPanel.vue`(图层列表倒序展示=顶层在上,置顶/置底/上移/下移 + 显隐/锁定 + 点选)。创建 `panels/LayerPanel.vue`:
```vue
<template>
  <div class="dsn-layer">
    <div class="toolbar">
      <el-button link size="small" @click="store.topComponent()">置顶</el-button>
      <el-button link size="small" @click="store.bottomComponent()">置底</el-button>
      <el-button link size="small" @click="store.upComponent()">上移</el-button>
      <el-button link size="small" @click="store.downComponent()">下移</el-button>
    </div>
    <div v-for="c in reversed" :key="c.id" class="layer-item"
         :class="{ on: store.curComponent?.id === c.id }" @click="store.selectComponent(c.id)">
      <span class="name">{{ labelOf(c) }}</span>
      <span class="ops">
        <el-icon @click.stop="store.toggleShow(c.id)"><View v-if="c.isShow !== false" /><Hide v-else /></el-icon>
        <el-icon @click.stop="store.toggleLock(c.id)"><Lock v-if="c.isLock" /><Unlock v-else /></el-icon>
      </span>
    </div>
  </div>
</template>
<script setup>
// 不引入 vuedraggable(守零运行时新依赖);拖拽排序二期,一期用置顶/置底/上移/下移按钮。
import { computed } from 'vue';
import { View, Hide, Lock, Unlock } from '@element-plus/icons-vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
const reversed = computed(() => [...store.componentData].reverse()); // 顶层(数组末尾)显示在最上
function labelOf(c) {
  return c.component === 'ChartWidget' ? `图表·${c.innerType}` : c.component + (c.propValue?.text ? `(${c.propValue.text})` : '');
}
</script>
<style scoped>
.dsn-layer { padding: 8px; } .toolbar { display: flex; gap: 4px; margin-bottom: 6px; }
.layer-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 8px;
  border-radius: 4px; cursor: pointer; color: #d5e6ff; font-size: 12px; }
.layer-item:hover, .layer-item.on { background: rgba(0,229,255,.12); }
.ops { display: flex; gap: 8px; color: #7d9bc9; }
</style>
```

- [ ] 9.6 建 `DesignerV2.vue`(三栏 + 顶部工具条 + 快捷键 + 数据流)。创建 `xanzc_frontend/src/views/screen/designer/DesignerV2.vue`:
```vue
<template>
  <div class="dsn2" :class="'scr-surface-host'">
    <!-- 顶部工具条 -->
    <div class="dsn2-toolbar">
      <el-select v-model="curId" placeholder="选择大屏" size="small" style="width:220px" @change="loadCanvas">
        <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName} (${s.viewLevel})`" :value="s.id" />
      </el-select>
      <span class="spacer" />
      <el-button-group size="small">
        <el-button :disabled="!store.canUndo" @click="store.undo()">撤销</el-button>
        <el-button :disabled="!store.canRedo" @click="store.redo()">重做</el-button>
      </el-button-group>
      <el-slider v-model="scalePct" :min="50" :max="150" :step="10" style="width:120px" @input="onScale" />
      <el-button size="small" @click="fitWindow">适应窗口</el-button>
      <el-button size="small" @click="onPreview">预览草稿</el-button>
      <el-button size="small" @click="onDiscard">放弃草稿</el-button>
      <el-button size="small" @click="onRollback">回滚</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="onSave">保存</el-button>
      <el-button size="small" type="danger" :loading="publishing" @click="onPublish">发布</el-button>
    </div>
    <!-- 三栏 -->
    <div class="dsn2-cols">
      <div class="dsn2-left">
        <el-tabs v-model="leftTab">
          <el-tab-pane label="组件" name="comp"><ComponentPanel /></el-tab-pane>
          <el-tab-pane label="图层" name="layer"><LayerPanel /></el-tab-pane>
        </el-tabs>
      </div>
      <CanvasCore class="dsn2-center" />
      <div class="dsn2-right">
        <component v-if="store.curComponent" :is="attrOf(store.curComponent.component)" :element="store.curComponent" />
        <CanvasAttr v-else />
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount, provide } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listScreens, getScreenCanvas, saveScreenCanvas, publishScreenCanvas,
  discardScreenCanvas, rollbackScreenCanvas, listScreenPublishLogs } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { fitScale } from '@/views/screen/designer/utils/scale';
import { findAttr } from '@/views/screen/designer/widgets';
import CanvasCore from './canvas/CanvasCore.vue';
import ComponentPanel from './panels/ComponentPanel.vue';
import LayerPanel from './panels/LayerPanel.vue';
import CanvasAttr from './panels/CanvasAttr.vue';

const router = useRouter();
const store = useScreenDesignerStore();
const screens = ref([]);
const curId = ref(null);
const leftTab = ref('comp');
const saving = ref(false);
const publishing = ref(false);
const scalePct = ref(50);
provide('previewContext', { orgCode: '', empId: '' }); // 设计态预览上下文(空→43010 引导态)

function attrOf(component) { return findAttr(component); }
function onScale(v) { store.scale = v / 100; }
function fitWindow() {
  const wrap = document.querySelector('.dsn2-center');
  if (wrap) { const s = fitScale(wrap.clientWidth - 48, wrap.clientHeight - 48); store.scale = s; scalePct.value = Math.round(s * 100); }
}
async function loadScreens() { screens.value = await listScreens(); if (screens.value[0]) { curId.value = screens.value[0].id; await loadCanvas(); } }
async function loadCanvas() {
  const resp = await getScreenCanvas(curId.value);
  store.loadFromEditor(resp);
}
async function onSave() {
  saving.value = true;
  try {
    const resp = await saveScreenCanvas(store.toSavePayload());
    store.adoptSaveResult(resp);
    ElMessage.success('已保存草稿');
  } catch (e) {
    if (e?.code === 'RPT-43012') {
      // 乐观锁冲突:二次确认强制覆盖(前端带最新 version 重发)
      await ElMessageBox.confirm('画布已被他处保存,是否重新加载最新版本?', '保存冲突', { type: 'warning' });
      await loadCanvas();
    }
  } finally { saving.value = false; }
}
async function onPublish() {
  publishing.value = true;
  try {
    await saveScreenCanvas(store.toSavePayload()).then(store.adoptSaveResult); // 先存后发,保证发布最新
    await publishScreenCanvas({ screenId: store.screenId, expectedVersion: store.canvasVersion });
    ElMessage.success('已发布');
  } finally { publishing.value = false; }
}
async function onDiscard() {
  await ElMessageBox.confirm('放弃当前草稿,恢复到已发布版本?', '放弃草稿', { type: 'warning' });
  await discardScreenCanvas(store.screenId);
  await loadCanvas();
  ElMessage.success('已放弃草稿');
}
async function onRollback() {
  const logs = await listScreenPublishLogs(store.screenId);
  if (!logs.length) { ElMessage.info('暂无发布归档'); return; }
  // 简化:回滚到最近一次归档(完整版可弹选择列表)
  await ElMessageBox.confirm(`回滚到 ${logs[0].publishedAt} 的发布版本?`, '回滚', { type: 'warning' });
  await rollbackScreenCanvas({ screenId: store.screenId, publishLogId: logs[0].id });
  ElMessage.success('已回滚');
}
function onPreview() {
  window.open(`#/screen/${store.screenCode}?preview=draft`, '_blank');
}

// 快捷键(弹框打开时禁用,对齐 DeShortcutKey.checkDialog)
function onKey(e) {
  if (document.querySelector('.el-overlay')) return; // 有弹框则禁用
  const meta = e.ctrlKey || e.metaKey;
  if (meta && e.key.toLowerCase() === 'z') { e.preventDefault(); store.undo(); }
  else if (meta && e.key.toLowerCase() === 'y') { e.preventDefault(); store.redo(); }
  else if (meta && e.key.toLowerCase() === 's') { e.preventDefault(); onSave(); }
  else if (e.key === 'Delete') { store.removeCurrent(); }
  else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key) && store.curComponent) {
    e.preventDefault();
    const step = e.shiftKey ? 10 : 1;
    const s = store.curComponent.style;
    const patch = { ArrowUp: { top: s.top - step }, ArrowDown: { top: s.top + step },
      ArrowLeft: { left: s.left - step }, ArrowRight: { left: s.left + step } }[e.key];
    store.setShapeStyle(patch);
    store.pushSnapshotDebounced();
  }
}
onMounted(() => { loadScreens(); window.addEventListener('keydown', onKey); });
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>
<style scoped lang="scss">
@use '@/styles/screen-theme' as theme;
.dsn2 { display: flex; flex-direction: column; height: calc(100vh - 60px); background: #03081c; }
.scr-surface-host { @include theme.scr-theme-vars; } // 供画布内复用 .scr-* 视觉变量
.dsn2-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-bottom: 1px solid rgba(0,229,255,.2); }
.dsn2-toolbar .spacer { flex: 1; }
.dsn2-cols { flex: 1; display: flex; min-height: 0; }
.dsn2-left, .dsn2-right { width: 260px; flex: none; overflow: auto; background: #050e2b;
  border-right: 1px solid rgba(0,229,255,.15); }
.dsn2-right { border-right: none; border-left: 1px solid rgba(0,229,255,.15); }
.dsn2-center { flex: 1; min-width: 0; }
</style>
```

- [ ] 9.7 路由替换。在 `xanzc_frontend/src/router/index.js` 第 66 行,把 `component: () => import('@/views/screen/admin/Designer.vue')` 改为 `component: () => import('@/views/screen/designer/DesignerV2.vue')`(路径/name/meta 其余不变)。

- [ ] 9.8 前端构建门禁 + vitest 回归(此时 CanvasCore 依赖的 widgets 已齐):
```bash
cd xanzc_frontend && npx vite build --logLevel error && npx vitest run
```
  预期:`vite build` exit 0;vitest 全绿(scale/snap/snapshotStack/store/registry)。若 build 报某素材 Component/Attr 未补齐,回 Task 8.2 补全再 build。

- [ ] 9.9 提交:
```bash
git add xanzc_frontend/src/views/screen/designer/panels/ \
        xanzc_frontend/src/views/screen/designer/DesignerV2.vue \
        xanzc_frontend/src/api/screen.js \
        xanzc_frontend/src/router/index.js
git commit -m "feat(screen): 面板(组件/图层/CommonAttr/CanvasAttr)+DesignerV2三栏+快捷键+api5函数+路由替换(vite build绿)"
```

**验收标准:** `vite build` exit 0;vitest 全绿;designer 路由指向 DesignerV2;工具条含保存/发布/回滚/放弃/预览/undo-redo;快捷键弹框时禁用;api 6 函数就位。

---

## Task 10: 渲染层切换(ScreenView/ScreenRenderer 改读渲染包 + 绝对定位 + 删旧 Designer/行块分支)

运行时渲染改读发布态渲染包(`renderPackageJson`),组件按绝对定位铺在 1920×1080 舞台;ChartWidget 从 `bindSnapshots` 取绑定快照。`?preview=draft` 读草稿包。删除旧 `admin/Designer.vue` 与 ScreenRenderer 的行/块渲染分支(3 屏开发测试阶段直接切换,无 fallback)。

**Files:**
- Modify `xanzc_frontend/src/views/screen/ScreenView.vue`(改调 render 接口 + 传 preview)
- Rewrite `xanzc_frontend/src/views/screen/components/ScreenRenderer.vue`(行/块布局 → 绝对定位渲染包)
- Modify `xanzc_frontend/src/api/screen.js`(getScreenView 支持 preview 参数,或新增 getScreenRender)
- Modify `xanzc_frontend/src/views/screen/components/BlockContainer.vue`(兼容运行态从 bindSnapshot 注入 bind——已有 parse(block.bindJson) 逻辑,新增可直接接收对象 bind)
- Delete `xanzc_frontend/src/views/screen/admin/Designer.vue`

**Interfaces:**
- Consumes:`getScreenRender(screenCode, preview)`、`widgets.findWidget`、`utils/scale.stageStyle`。
- Produces:全屏渲染读 `CANVAS_PUBLISHED_JSON`(或 draft 包),组件绝对定位 + ChartWidget 复用 BlockContainer。

**步骤:**

- [ ] 10.1 api 调整:`getScreenView` 增 preview 参数(渲染新契约)。改 `xanzc_frontend/src/api/screen.js` 的 `getScreenView`:
```js
export function getScreenView(screenCode, preview) {
  const params = preview ? { preview } : {};
  // silent:false —— 整屏加载失败要提示;区块级取数才 silent
  return call('get', `/screen/view/${screenCode}`, { params }, null);
}
```

- [ ] 10.2 重写 `ScreenRenderer.vue`(绝对定位渲染包)。整体替换 `xanzc_frontend/src/views/screen/components/ScreenRenderer.vue`:
```vue
<template>
  <div class="scr-canvas-render" :style="stageCss">
    <div v-for="c in components" :key="c.id" class="scr-abs"
         :style="absStyle(c)" v-show="c.isShow !== false">
      <!-- ChartWidget:注入 bindSnapshot 后复用 BlockContainer;素材组件用 widgets 渲染 -->
      <BlockContainer v-if="c.component === 'ChartWidget'" :block="blockOf(c)" :context="context" />
      <component v-else :is="widgetOf(c.component)" :element="c" mode="runtime" />
    </div>
  </div>
</template>
<script setup>
// 运行时渲染——读发布态渲染包(canvasStyle + components + bindSnapshots),组件绝对定位铺在
// 1920×1080 舞台(外层 ScreenView 已 transform: scale 整体缩放,这里只按设计态像素绝对定位)。
// 已删除旧「region/row/block flex 布局」分支(3 屏直接切换,无 fallback)。
import { computed } from 'vue';
import BlockContainer from './BlockContainer.vue';
import { findWidget } from '@/views/screen/designer/widgets';

const props = defineProps({
  renderPackage: { type: Object, default: () => ({ components: [], bindSnapshots: {}, canvasStyle: {} }) },
  context: { type: Object, default: () => ({}) }
});
const components = computed(() => props.renderPackage.components || []);
const stageCss = computed(() => ({
  position: 'relative', width: '1920px', height: '1080px',
  background: props.renderPackage.canvasStyle?.background || 'transparent'
}));
function absStyle(c) {
  return { position: 'absolute', top: c.style.top + 'px', left: c.style.left + 'px',
    width: c.style.width + 'px', height: c.style.height + 'px',
    opacity: c.style.opacity ?? 1 };
}
function widgetOf(component) { return findWidget(component); }
/** 从 bindSnapshots 合成 BlockContainer 需要的 block(bindJson/styleJson/drillJson 字符串) */
function blockOf(c) {
  const snap = (props.renderPackage.bindSnapshots || {})[String(c.blockId)];
  if (!snap) return null;
  return {
    id: c.blockId,
    componentType: c.innerType || snap.componentType,
    bindJson: JSON.stringify(snap.bind || {}),
    styleJson: JSON.stringify(snap.styleCfg || {}),
    drillJson: JSON.stringify(snap.drill || {})
  };
}
</script>
<style scoped>
.scr-canvas-render { transform-origin: top left; }
.scr-abs { overflow: hidden; }
</style>
```
  说明:MAP/PROVINCE 地图组件一期作为 ChartWidget 或后续素材扩展;3 屏重配(Task 11)时省级屏的地图用 MapCenter——如需保留地图,把 MapCenter 也纳入 `widgets` 注册(component: 'MapWidget')或在渲染包中作为一种 ChartWidget innerType。**实施决策**:一期把 `MapCenter` 追加进 `widgets` 手写字典(component: 'MapCenter' + 空 Attr),使省级屏地图能作为组件摆放;此扩展并入本步(在 `widgets/index.js` 补 `MapCenter` import + 注册 + 一个 `map-center/meta.js`,复用现有 `@/views/screen/components/MapCenter.vue`)。

- [ ] 10.3 改 `ScreenView.vue` 适配渲染包。改其 `<script setup>` 与 `<template>`:
  - `load()` 改为:
```js
async function load() {
  loadError.value = '';
  view.value = null;
  try {
    const resp = await getScreenView(route.params.screenCode, route.query.preview);
    // 渲染包 JSON 字符串 → 对象
    resp.renderPackage = resp.renderPackageJson ? JSON.parse(resp.renderPackageJson) : { components: [] };
    view.value = resp;
  } catch (e) {
    loadError.value = e?.message || '未知错误';
  }
}
```
  - `<template>` 的 `<ScreenRenderer .../>` 改为:
```html
<div class="scr-body" v-if="view">
  <ScreenRenderer :render-package="view.renderPackage" :context="context" />
</div>
```
  - `context` computed 保留(orgCode/empId from query)。

- [ ] 10.4 `BlockContainer.vue` 运行态兼容(已用 `parse(block.bindJson)`,blockOf 已产出字符串 bindJson,无需改;确认无破坏)。若 blockId 为 null(素材误入)已被 ScreenRenderer 的 `v-if c.component==='ChartWidget'` 隔离,不影响。**本步仅核对,不改代码**(若确需改再改)。

- [ ] 10.5 删除旧设计器。`git rm xanzc_frontend/src/views/screen/admin/Designer.vue`(路由已在 Task 9 切走,无引用)。核对无其它引用:
```bash
cd xanzc_frontend && grep -rn "admin/Designer" src/ || echo "无残留引用"
```

- [ ] 10.6 前端构建门禁 + vitest:
```bash
cd xanzc_frontend && npx vite build --logLevel error && npx vitest run
```
  预期:exit 0 + vitest 全绿。

- [ ] 10.7 提交:
```bash
git add xanzc_frontend/src/views/screen/ScreenView.vue \
        xanzc_frontend/src/views/screen/components/ScreenRenderer.vue \
        xanzc_frontend/src/api/screen.js \
        xanzc_frontend/src/views/screen/designer/widgets/index.js \
        xanzc_frontend/src/views/screen/designer/widgets/map-center/meta.js
git rm xanzc_frontend/src/views/screen/admin/Designer.vue
git commit -m "feat(screen): 渲染层切换——读发布态渲染包+绝对定位+MapCenter入注册表,删旧Designer与行块分支(无fallback)"
```

**验收标准:** `vite build` exit 0;运行时读 `renderPackageJson` 绝对定位渲染;`?preview=draft` 读草稿包;旧 `admin/Designer.vue` 删除且无残留引用;ChartWidget 从 bindSnapshots 取数复用 BlockContainer。

---

## Task 11: 3 屏手工重配指引(操作清单 → 发布 → 导出新种子 SQL)

用新设计器手工重配现有 3 屏(SCR_PROVINCE/SCR_BRANCH/SCR_PERSON,视觉对齐现有深色主题)→ 发布 → mysqldump 导出新种子 SQL 落盘。**本任务是操作清单式步骤,不写业务代码。**

**Files:**
- Create `docs/superpowers/sql/2026-07-12-screen-canvas-seed.sql`(重配后从 yiti 导出的 RPT_SCREEN(画布字段)+ RPT_SCREEN_BLOCK + RPT_SCREEN_PUBLISH_LOG 种子)

**步骤:**

- [ ] 11.1 前置就绪:确认 Task 1/4 的 DDL/资源脚本已在 **yiti** 库执行;后端已按「跨模块改动后重启」流程重启并 `Started`;前端 `npm run dev` 起在 8091。登录:
```bash
curl --noproxy '*' -c /tmp/scr_cookie.txt -X POST http://localhost:18081/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}'
```

- [ ] 11.2 逐屏重配(浏览器操作,3 屏各一遍):
  1. 打开 `http://localhost:8091/#/screen-admin/designer`,顶部下拉选中目标屏。
  2. 首次进入草稿为空(旧行/块数据不迁移)——从左「组件」面板拖入图表/素材,摆放到 1920×1080 画布对应位置(参照旧屏视觉:省级=左右 KPI + 中间地图;支行/个人=多区块指标卡+折线+排名+饼图)。
  3. 每个 ChartWidget 在右属性面板选「数据源」(沿用既有 RPT_SCREEN_DATASOURCE:9001 EMP宽表 / 9003 KPI 等)、填周期/标题/刷新/钻取。
  4. 拖拽用吸附对齐找齐;必要时用图层面板置顶置底;方向键微调。
  5. 点「保存」(乐观锁,version 递增)→ 内嵌「预览草稿」核对 → 点「发布」(写 PUBLISHED_JSON + 归档)。
  6. 全屏核对:`http://localhost:8091/#/screen/SCR_PROVINCE`(等,带 `?orgCode=...&empId=...` 验取数)。

- [ ] 11.3 验证 SQL(重配发布后,确认 3 屏均有 published_json 与归档):
```sql
-- 手工在 yiti 执行核对
SELECT id, screen_code, canvas_version, publish_status,
       LENGTH(canvas_published_json) AS pub_len, published_at
FROM RPT_SCREEN WHERE screen_code IN ('SCR_PROVINCE','SCR_BRANCH','SCR_PERSON');
SELECT screen_id, COUNT(*) AS log_cnt FROM RPT_SCREEN_PUBLISH_LOG GROUP BY screen_id;
```
  预期:3 屏 `publish_status=1`、`pub_len>0`;每屏 `log_cnt>=1`。

- [ ] 11.4 导出新种子 SQL(mysqldump 仅导 3 张画布相关表,带 where 收敛到 3 屏及其 block/log)。生成 `docs/superpowers/sql/2026-07-12-screen-canvas-seed.sql`:
```bash
# 导出 RPT_SCREEN 3 屏行(含画布字段)、其 block 行、发布归档;--no-create-info 只导数据(结构走 DDL 脚本)
SIDS=$(mysql -uroot -pdjdev -N -e "SELECT GROUP_CONCAT(id) FROM yiti.RPT_SCREEN WHERE screen_code IN ('SCR_PROVINCE','SCR_BRANCH','SCR_PERSON')")
mysqldump -uroot -pdjdev --no-create-info --complete-insert --default-character-set=utf8mb4 \
  yiti RPT_SCREEN --where="screen_code IN ('SCR_PROVINCE','SCR_BRANCH','SCR_PERSON')" \
  > docs/superpowers/sql/2026-07-12-screen-canvas-seed.sql
mysqldump -uroot -pdjdev --no-create-info --complete-insert --default-character-set=utf8mb4 \
  yiti RPT_SCREEN_BLOCK --where="screen_id IN ($SIDS)" \
  >> docs/superpowers/sql/2026-07-12-screen-canvas-seed.sql
mysqldump -uroot -pdjdev --no-create-info --complete-insert --default-character-set=utf8mb4 \
  yiti RPT_SCREEN_PUBLISH_LOG --where="screen_id IN ($SIDS)" \
  >> docs/superpowers/sql/2026-07-12-screen-canvas-seed.sql
```
  在文件头补一行注释说明用途与执行前提(手工加):
```sql
-- 大屏画布 V2 三屏重配种子(2026-07-12,从 yiti 库重配发布后导出)
-- 前提:先执行 2026-07-12-screen-canvas-ddl.sql 建结构;本脚本只灌数据(RPT_SCREEN 画布字段更新 + block + 归档)。
-- 注意:RPT_SCREEN 是 INSERT,fresh 库可直接跑;已有同 id 行需先手工清理或改 REPLACE。
```

- [ ] 11.5 提交(仅种子 SQL):
```bash
git add docs/superpowers/sql/2026-07-12-screen-canvas-seed.sql
git commit -m "chore(screen): 3屏画布重配发布后导出新种子SQL(RPT_SCREEN画布字段+block+归档)"
```

**验收标准:** 3 屏在设计器重配并发布成功;全屏渲染读发布态正常(带上下文取数);种子 SQL 落盘且含 3 屏画布字段 + block + 归档,可在 fresh 库复现。

---

## Task 12: 回归与文档同步(mvn test 基线 + vite build + 模块 CLAUDE.md + docs 大屏文档)

全量回归核对不破基线;前端构建绿;更新模块 CLAUDE.md 版本变更日志与 docs 大屏文档(DDL 基线说明、新端点/资源、双态模型)。

**Files:**
- Modify `report-analytics-center/CLAUDE.md`(screen 子域段追加 V2 画布设计器条目)
- Modify `docs/CLAUDE.md`(schema 分层约定补 canvas DDL/seed 脚本说明,如需)
- Modify `docs/modules/report-analytics-center/05-表结构DDL.md`(补 RPT_SCREEN 7 字段 + RPT_SCREEN_PUBLISH_LOG,如该文件存在且维护 DDL)

**步骤:**

- [ ] 12.1 后端全量回归(先预构建防 stale jar):
```bash
mvn -q clean install -DskipTests -pl report-analytics-center -am
mvn -q -pl report-analytics-center test 2>&1 | tail -40
```
  预期:新增测试(`RptScreenPublishLogMapperIT`/`ScreenCanvasServiceTest`/`ScreenCanvasControllerIT`/`RptErrorCodeTest`)全绿;**失败数不超过任务启动时实测的 pre-existing 基线**(spec 记 ≤9;启动时先跑一次记录真实基线,此处比对不得增加)。若新增失败,回对应 Task 修复,不得靠删测试掩盖。

- [ ] 12.2 前端全量门禁:
```bash
cd xanzc_frontend && npx vitest run && npx vite build --logLevel error
```
  预期:vitest 全绿 + build exit 0。

- [ ] 12.3 更新 `report-analytics-center/CLAUDE.md`:在 screen 子域说明块后追加一段(V2 画布设计器):新增字段(RPT_SCREEN 7 列 canvas_*/publish_*)、新表 RPT_SCREEN_PUBLISH_LOG、新端点(`/api/screen/admin/canvas/*` 5+1)、新资源(R_RPT_SCR_CV_* 6 条,发布/回滚高危)、新错误码 RPT-43012、双态模型(DRAFT/PUBLISHED/乐观锁/归档滚动10)、渲染改读 PUBLISHED_JSON(?preview=draft)、脚本清单(`2026-07-12-screen-canvas-{ddl,resources,seed}.sql`)、前端设计器目录(`views/screen/designer/`)与 vitest 引入。错误码计数 44→45 同步。

- [ ] 12.4 更新 docs:`docs/CLAUDE.md` 的「数据脚本分层约定」如需补 canvas 脚本;`docs/modules/report-analytics-center/05-表结构DDL.md`(若维护 DDL 真相)补 RPT_SCREEN 新增 7 列与 RPT_SCREEN_PUBLISH_LOG 表结构。**仅在这些文件确实承载对应内容时更新**,避免无谓改动。

- [ ] 12.5 提交:
```bash
git add report-analytics-center/CLAUDE.md docs/CLAUDE.md \
        docs/modules/report-analytics-center/05-表结构DDL.md
git commit -m "docs(screen): 画布设计器V2文档同步——模块CLAUDE.md版本日志+DDL基线说明+双态/资源/错误码"
```

**验收标准(= 一期 DoD):** 画布设计器全功能(拖拽/缩放/吸附/undo/图层/右键/属性面板 + 5 素材 + 9 图表)可用;保存/发布/回滚/放弃草稿后端 TDD 证据齐全;双态渲染生效,3 屏重配完成并发布,新种子 SQL 落盘;新增测试全绿,`mvn test -pl report-analytics-center` 失败数 ≤ 基线;vite build exit 0;PT_RESOURCE/角色绑定/审计落位;文档同步。

---

## Spec 覆盖对照(自检:规格逐节 → 任务映射)

| Spec 节 | 要点 | 落点任务 |
|---|---|---|
| §2.1 RPT_SCREEN 新增字段 | STYLE/DRAFT/PUBLISHED/VERSION/PUBLISH_STATUS/PUBLISHED_AT+BY,schemaVersion | Task 1(DDL 7 列 + 实体) |
| §2.2 组件树 JSON 节点 | id/component/innerType/blockId/style/propValue/isLock/isShow,图层=数组序,坐标 1920×1080 幂等 | Task 2(schema/校验)、Task 6(store)、共享契约节 |
| §2.3 BLOCK 职责收窄 | 图表取数配置留行,旧布局字段只读备份,素材不占 block | Task 2(upsert 保留 id + 旧布局列填默认) |
| §2.4 RPT_SCREEN_PUBLISH_LOG | 归档表,滚动 10 份 | Task 1(建表)、Task 3(trim 10) |
| §3 端点 GET canvas/{id} | 编辑器加载 | Task 4(load) |
| §3 POST canvas/save | 乐观锁→43012,单事务 | Task 2(saveCanvas + XML) |
| §3 POST canvas/publish | 结构化解析+一致性+渲染包+归档+状态机 | Task 3(publishCanvas) |
| §3 POST canvas/rollback | 归档回滚 | Task 3(rollbackCanvas) |
| §3 POST canvas/discard | 发布态覆盖草稿 | Task 3(discardDraft) |
| §3 服务端校验 | 白名单/blockId 归属/JSON≤2MB/数值范围 + RPT-43012 守护 | Task 2(校验 + 错误码守护) |
| §3 渲染改读 PUBLISHED_JSON | view 改读 + ?preview=draft | Task 4(getRenderByCode)、Task 10(前端) |
| §4.1 目录/三栏 | designer/ 目录、三栏、缩放 50~150%+适应窗口 | Task 9(DesignerV2)、Task 7(CanvasCore) |
| §4.2 画布内核 | 拖拽/8点缩放/吸附/右键/锁定隐藏/undo防抖/快捷键/transform:scale 除以 scale | Task 5(utils)、Task 6(store)、Task 7(四组件)、Task 9(快捷键) |
| §4.3 组件体系两层注册 | componentsMap 5 素材 + import.meta.glob 图表 + CommonAttr 基座 + 直接 mutate | Task 8(widgets)、Task 9(CommonAttr) |
| §4.4 编辑器数据流 | GET→store→序列化 POST(43012 冲突)→内嵌预览→全屏 ?preview=draft | Task 6/9(数据流)、Task 4/10(preview) |
| §5 安全权限 | R_RPT_SCR_* 复用 + 发布/回滚独立高危资源 + R_ADMIN 兜底 + 审计 + 草稿预览登录+管理权限 | Task 4(资源脚本 + view preview)、Task 3(审计) |
| §6 测试策略 | 后端 TDD(乐观锁/发布/归档/回滚/错误码守护)+ 前端 vitest 纯函数 + vite build 门禁 | Task 2/3/5/6/8(TDD)、Task 12(回归) |
| §7 三屏切换与种子 | 直接改读 PUBLISHED + 无 fallback + 手工重配 + 导出种子 + 删旧 Designer/行块分支 | Task 10(切换/删除)、Task 11(重配/种子) |
| §8 DoD | 全功能可用 + TDD 证据 + 双态渲染 + 种子落盘 + 测试绿 + build 0 + 权限审计 + 文档 | Task 12(DoD 核对) |
| §9 风险对策 | 边界 utils 单测 + schemaVersion + 43012 二次确认 + 发布结构化一致性校验 | Task 5(边界)、Task 2(schema/43012)、Task 3(一致性)、Task 9(冲突二次确认) |

**未覆盖/明确二期(spec §1 已声明本期不做):** 多选框选成组、数据集抽象、联动钻取(区块内趋势钻取沿用既有 DrillTrend,不新增)、模板市场、图片上传管理、图层拖拽排序、公共免登录分享链接、组件旋转、移动端布局——本计划均不含。

**遗留待硬化(计划内已标注):** `?preview=draft` 的「屏管理权限」当前依赖设计器管理端菜单门禁 + 草稿非敏感(3 屏开发测试态),Task 4 已注记若草稿承载敏感数据需补独立管理端渲染端点(快速跟进项,非一期阻塞)。



