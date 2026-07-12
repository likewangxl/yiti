# 经营管理大屏（三级视角 + 全配置化体系）设计文档

- 日期：2026-07-12
- 需求来源：`大屏需求.txt`（银行省分行一体化经营管理系统大屏规划需求文档）
- 归属模块：`report-analytics-center`（业绩展示模块）+ `xanzc_frontend`
- 状态：设计定稿，待实现

## 1. 背景与目标

为省分行各级经营管理人员提供业绩指标、流程进度、业务执行情况的全局可视化监控大屏，共三级业务视角：

1. **省分行行长视角（总览大屏）**：中间固定陕西省地图 + 支行点位，左右两侧完全可配置。
2. **支行行长视角（支行详情大屏）**：核心经营指标区 + 人员业绩列表区，全区可配置。
3. **人员维度大屏（个人详情大屏）**：个人 KPI、指标明细、历史趋势、流程进度，全区可配置。

核心设计原则：**全配置化、通用化**——展示内容、布局、数据源均通过后台配置实现，不做硬编码，适配指标频繁调整。

## 2. 关键决策（已与需求方确认）

| # | 决策点 | 结论 |
|---|---|---|
| D1 | 数据访问架构 | 大屏落 report-analytics-center，查询引擎走已有 `rptReadOnlyDataSource` 独立只读数据源 + **表白名单**直查（指标宽表/KPI 表/流程表等）；写路径与其他模块仍严格走 `*Api`。模块内已有安全先例（SQL 探查）。 |
| D2 | 布局配置交互 | **参数化网格 + 实时预览**（表单定义区域→行→区块→百分比占比，右侧缩略预览），不做拖拽设计器。 |
| D3 | 交付范围 | 一期全量：配置体系（数据源/布局/组件/钻取）+ 三级大屏（含陕西地图）。组件先做 5 种。 |
| D4 | 展示形态 | 大屏为 `/screen/*` 独立全屏路由 + 深色大屏视觉；配置后台嵌现有 Element Plus 管理框架。 |

**现状事实修正**：需求文档 2.1 称"KPI 计算与存储完全复用指标宽表设计"，但实际 `KPI_RESULT` 为**行式表**（`emp_id + cycle_type + cycle_date + as_of_date + kpi_total_score`），非 val_N 宽表。数据源设计按真实结构适配（见 §5.2）。

## 3. 总体架构

```
report-analytics-center（只读模块，不暴露 *Api，架构守护不变）
├── controller/screen/
│   ├── ScreenDatasourceAdminController   # 数据源配置管理（高危：自定义 SQL）
│   ├── ScreenConfigAdminController       # 屏/区块/地图点位配置管理
│   ├── ScreenViewController              # 大屏运行时读取整屏配置
│   └── ScreenDataController              # 统一取数端点
├── service/screen/
│   ├── ScreenDatasourceService           # 数据源 CRUD + 能力标签 + 试跑
│   ├── ScreenConfigService               # 屏/区块 CRUD + 组件-数据源联动校验
│   ├── ScreenQueryEngine                 # 查询执行引擎（核心）
│   └── ScreenSqlValidator                # 白名单 SQL 校验（对齐 SqlSafeValidator 标准）
├── mapper/ + entity/                     # MyBatis-Plus（BaseMapper，新功能红线）
└── resources/mapper/                     # 仅自定义 SQL（批量/JOIN）落 XML

xanzc_frontend
├── views/screen/admin/Datasources.vue    # 数据源管理（嵌管理框架）
├── views/screen/admin/Designer.vue       # 大屏设计器（参数化网格+实时预览）
├── views/screen/ScreenView.vue           # 大屏运行时入口（/screen/:screenCode 全屏深色）
├── views/screen/components/              # ScreenRenderer + BlockContainer + 5 组件 + DrillTrend + MapCenter
└── api/screen.js                         # 大屏 API 封装
```

## 4. 配置数据模型（4 张新表）

遵守 Flyway 禁令：DDL 以 SQL 脚本 `docs/superpowers/sql/2026-07-12-screen-dashboard-ddl.sql` 手工在目标库执行（yiti）。实体用 `@TableName` / `@TableId(type = IdType.AUTO)`。

### 4.1 RPT_SCREEN_DATASOURCE（数据源定义）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint AUTO | 主键 |
| ds_code | varchar(64) UK | 数据源编码 |
| ds_name | varchar(100) | 名称 |
| ds_type | varchar(20) | **能力标签**：TIMESERIES 时序型 / SINGLE 单值型 |
| source_kind | varchar(20) | WIDE_TABLE 宽表引导式 / KPI_RESULT KPI结果引导式 / CUSTOM_SQL 自定义SQL |
| config_json | text | 类型化配置（见 §5） |
| time_param_json | varchar(500) | 允许的预设周期模板列表（LATEST/LAST_10D/LAST_1M/LAST_6M_EOM…），SINGLE 型为空 |
| status | varchar(10) | ACTIVE / DISABLED |
| remark / created_by / created_time / updated_by / updated_time / deleted | - | 通用列（逻辑删除） |

### 4.2 RPT_SCREEN（大屏定义）

| 字段 | 说明 |
|---|---|
| id / screen_code(UK) / screen_name | 主键 / 编码 / 名称 |
| view_level | PROVINCE / BRANCH / PERSON |
| theme_json | 主题变量覆盖（一期仅深色一套，留扩展） |
| status + 通用列 | 同上 |

约束（代码层保证，非 DDL）：`view_level=PROVINCE` 的屏 MAIN 区固定渲染地图组件，配置后台不允许在 MAIN 区增删普通区块；BRANCH/PERSON 无固定区。

### 4.3 RPT_SCREEN_BLOCK（区块 = 布局 + 组件 + 绑定 + 钻取）

| 字段 | 说明 |
|---|---|
| id / screen_id(FK) | 主键 / 所属屏 |
| region | LEFT / MAIN / RIGHT |
| row_no / col_no | 区域内行号 / 行内列号（排序即位置） |
| width_pct / height_pct | int 百分比：width 为行内占比，height 为区域内行高占比 |
| component_type | METRIC_CARD / LINE_TREND / PIE_SHARE / RANK_LIST / FLOW_STATUS |
| bind_json | `{dsId, items:[{key,label}], valueCol, nameCol, …}` 数据项绑定 |
| style_json | `{title, unit, format, decimals, colors[], refreshSec}` |
| drill_json | `{drillEnabled, drillPeriods:[LAST_10D,…], jump:{targetScreenCode, paramMapping:{orgCode:"…", empId:"…"}}}` |
| 通用列 | 同上 |

### 4.4 RPT_SCREEN_MAP_POINT（支行地图点位）

| 字段 | 说明 |
|---|---|
| id / org_code(UK) / org_name | 支行机构号 / 名称 |
| lng / lat | decimal(10,6) 经纬度，管理员在配置后台维护 |
| target_screen_code | 点击跳转的目标屏（默认支行详情屏） |
| status + 通用列 | 同上 |

## 5. 数据源体系与查询执行引擎

### 5.1 宽表引导式（WIDE_TABLE，零 SQL，时序型）

`config_json` 示例：

```json
{ "table": "EMP_INDEX_RESULT", "subjectCol": "emp_id",
  "metrics": [ {"metricCode": "M_0001", "metricName": "存款余额", "slot": 3} ] }
```

- 配置界面选宽表（EMP_INDEX_RESULT / ORG_INDEX_RESULT）→ 从 `PERF_METRIC_DEF` 选指标，保存时后端按 `metric_code → val_slot` 翻译并快照到 config_json（指标槽位永久绑定，快照安全）。
- 自动 `ds_type=TIMESERIES`。执行时按 `data_date` 过滤：
  - `LATEST`：`data_date = (SELECT MAX(data_date) FROM t WHERE …)`
  - 日期范围/预设周期：`data_date BETWEEN #{dateFrom} AND #{dateTo}`；`LAST_6M_EOM` 取近 6 个月每月末时点（`data_date = LAST_DAY(data_date)` 过滤）。
- `version` 取 `SYS_CONTROL` 当前版本（引擎内查询，一期直查白名单内 SYS_CONTROL 表）。
- contextParams：`orgCode`/`empId` 映射到 `subjectCol`（个人屏传 empId 查员工宽表；支行屏传 orgCode 查机构宽表）。

### 5.2 KPI 结果引导式（KPI_RESULT，时序型）

`config_json`：`{ "cycleType": "MONTHLY" }`。执行时查 `KPI_RESULT`，主体列 `emp_id`，时间轴用 `cycle_date`，每周期取 `as_of_date` 最新一条（`ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC)`）。返回 `kpi_total_score`。

### 5.3 自定义 SQL（CUSTOM_SQL，时序/单值由管理员标注）

- `config_json`：`{ "sql": "SELECT …", "dateCol": "stat_date|null" }`；`ds_type` 由管理员标注，标 TIMESERIES 必须声明 `dateCol`。
- 占位参数（命名占位，引擎用 PreparedStatement 绑定，**禁止字符串拼接**）：`#{orgCode}` `#{empId}` `#{dateFrom}` `#{dateTo}`。SQL 中未使用的占位忽略。
- 保存与试跑前经 `ScreenSqlValidator`（JSqlParser AST）：仅 SELECT、表全部在白名单、禁 INTO OUTFILE/危险函数、子查询深度 ≤3、长度 ≤8000。
- 执行约束：跑 `rptReadOnlyDataSource`、`queryTimeout=5s`、强制 `LIMIT`（引擎包一层 `SELECT * FROM (…) t LIMIT 1000`；试跑 LIMIT 10）。
- 流程类统计（触达红黄绿灯、在途流程数、平均时效）即用此类数据源直查触达/业务执行/Flowable 表。

### 5.4 表白名单（初始清单，常量类维护）

`EMP_INDEX_RESULT`、`ORG_INDEX_RESULT`、`CUST_INDEX_RESULT`、`KPI_RESULT`、`PERF_METRIC_DEF`、`PERF_TARGET_VALUE`、`PERF_TARGET_PLAN`、`SYS_CONTROL`、`EXT_ORG_INFO`、`ADDRBOOK_EMPLOYEE`、触达/流程状态表 `TOUCH_TASK`、`TOUCH_LOG`、`ACT_RU_TASK`、`ACT_HI_PROCINST`、`BIZ_PROCESS_MAP`（表名已对照 `docs/schema/ddl-customer.sql` / `ddl-workflow.sql` 核实）。白名单与 SQL 探查的 schema-whitelist 各自独立维护（用途不同）。

### 5.5 统一取数端点与返回结构

`POST /api/screen/data`：

```json
// 请求
{ "dsId": 1, "timeParam": {"period": "LAST_10D"},
  "contextParams": {"orgCode": "610100", "empId": null} }
// 响应（统一二维结构，所有组件同构消费）
{ "columns": ["data_date", "存款余额", "贷款余额"],
  "rows": [["2026-07-01", 123.4, 56.7], …] }
```

### 5.6 组件-数据源联动校验（双侧）

- 后端：保存区块时校验——`LINE_TREND` 仅可绑 TIMESERIES；`drillEnabled=true` 仅当数据源为 TIMESERIES；违规抛 `RPT-43005`。
- 前端：设计器中选组件类型后数据源下拉自动过滤（反向同理）。

## 6. REST 端点、PT_RESOURCE、错误码

### 6.1 端点清单（12 个）

| Controller | 端点 | 鉴权 action | RESOURCE_ID |
|---|---|---|---|
| ScreenDatasourceAdminController | GET /api/screen/admin/datasources | LIST | R_RPT_SCR_DS_LIST |
| 〃 | POST /api/screen/admin/datasources | WRITE | R_RPT_SCR_DS_SAVE |
| 〃 | PUT /api/screen/admin/datasources/{id} | WRITE | R_RPT_SCR_DS_UPD |
| 〃 | DELETE /api/screen/admin/datasources/{id} | DELETE | R_RPT_SCR_DS_DEL |
| 〃 | POST /api/screen/admin/datasources/try-run | EXECUTE_SQL | R_RPT_SCR_DS_TRY（高危独立授权） |
| ScreenConfigAdminController | GET /api/screen/admin/screens | LIST | R_RPT_SCR_CFG_LIST |
| 〃 | GET /api/screen/admin/screens/{id} | READ | R_RPT_SCR_CFG_GET |
| 〃 | POST /api/screen/admin/screens | WRITE | R_RPT_SCR_CFG_SAVE（屏+区块整体保存） |
| 〃 | DELETE /api/screen/admin/screens/{id} | DELETE | R_RPT_SCR_CFG_DEL |
| 〃 | PUT /api/screen/admin/map-points | WRITE | R_RPT_SCR_MAP_SAVE（整表覆盖保存）；GET 复用 R_RPT_SCR_CFG_LIST |
| ScreenViewController | GET /api/screen/view/{screenCode} | READ | R_RPT_SCR_VIEW（整屏配置：屏+区块+点位） |
| ScreenDataController | POST /api/screen/data | READ | R_RPT_SCR_DATA |

- 全部 `@BizAuth(bizType = BizType.REPORT, action = …)`；写操作 `@AuditLog`；自定义 SQL 保存/试跑 `@AuditLog(reasonRequired = true)`。
- PT_RESOURCE 注册 SQL 随 DDL 脚本一并提供；配置类资源绑管理角色，VIEW/DATA 绑大屏查看角色。
- 大屏查看不做行级 DATA_SCOPE（管理视角，靠角色 + 页面参数控制范围）——与 DashboardController 现状一致。

### 6.2 新增错误码（RPT-43xxx 段，避开既有 4xxxx/42xxx）

| 错误码 | 语义 |
|---|---|
| RPT-43001 | SCREEN_DS_NOT_FOUND 数据源不存在 |
| RPT-43002 | SCREEN_DS_SQL_INVALID 自定义 SQL 校验不通过（附白名单/语法明细） |
| RPT-43003 | SCREEN_DS_TIMESERIES_NEED_DATECOL 时序型必须声明日期列 |
| RPT-43004 | SCREEN_NOT_FOUND 大屏不存在 |
| RPT-43005 | SCREEN_BLOCK_BIND_MISMATCH 组件与数据源能力不匹配 |
| RPT-43006 | SCREEN_LAYOUT_INVALID 布局非法（占比越界/固定区违规） |
| RPT-43007 | SCREEN_DS_IN_USE 数据源被区块引用不可删除 |
| RPT-43008 | SCREEN_DATA_QUERY_FAILED 取数执行失败（超时/越权表） |

## 7. 前端配置后台（嵌现有管理框架）

### 7.1 数据源管理页 `views/screen/admin/Datasources.vue`

列表（名称/编码/能力标签/来源类型/状态）+ 新建/编辑抽屉：

- 第一步选 `source_kind`；宽表引导式走三步表单（选宽表 → 指标多选下拉（复用 `api/metrics.js` 指标库）→ 取数规则勾选预设周期）；KPI 引导式选周期类型；自定义 SQL 给编辑框 + 占位参数说明 + 时序/单值标注 + 日期列声明。
- **试跑按钮**：调 try-run 端点，预览 10 行 + 列名，校验失败展示 RPT-43002 明细。

### 7.2 大屏设计器页 `views/screen/admin/Designer.vue`（参数化网格 + 实时预览）

三栏：

- 左：结构树（区域 → 行 → 区块），增删行/块、上移下移、宽/高百分比输入框（行内宽度合计 ≤100 校验）。
- 中：**16:9 缩略实时预览**，用真实组件渲染（有绑定则实时取数，无绑定用内置示例数据），选中高亮。
- 右：属性面板——组件类型（5 选 1）→ 数据源（按类型联动过滤）→ 数据项绑定（multi-select）→ 样式（标题/单位/数值格式/小数位/颜色/刷新间隔）→ 钻取（开关、周期多选、跳转目标屏 + 参数映射）。
- PROVINCE 屏 MAIN 区锁定为地图：属性面板变为地图样式 + **点位管理表格**（org_code 下拉自机构接口、经纬度输入、目标屏）。
- 保存 = 屏 + 区块整体提交（POST screens），后端事务内先删后插区块。

## 8. 大屏渲染引擎（运行时，三级共用）

```
ScreenView.vue  路由 /screen/:screenCode?orgCode=&empId=（独立全屏，无后台框架）
└── ScreenRenderer.vue      # GET /api/screen/view/{code} → 按 region→row→block 递归 flex 渲染
    └── BlockContainer.vue  # 深色卡片外框（标题+发光描边）+ 钻取状态机 + 轮询定时器
        ├── MetricCard / LineTrend / PieShare / RankList / FlowStatus（5 组件，echarts + vue-echarts）
        ├── DrillTrend.vue  # 钻取态：折线 + 周期 tabs + 返回按钮
        └── MapCenter.vue   # PROVINCE 专用：陕西 geoJSON registerMap + 点位散点 + 点击跳转
```

- **参数注入**：路由 query 的 `orgCode`/`empId` 作为 contextParams 注入所有区块取数——同一份支行屏配置服务全部支行、同一份个人屏配置服务全部员工。
- **自适应**：根容器按 1920×1080 设计稿 `transform: scale(min(vw/1920, vh/1080))` 等比缩放居中，保证电视墙一致性。
- **深色主题**：CSS 变量集中定义（深蓝底 #0a1a3a 系、青色发光数字、大字号）；echarts 注册配套深色主题。
- **数据刷新**：区块级轮询（style_json.refreshSec，默认 60s，0=不刷新）；页面隐藏时暂停。
- **陕西地图**：geoJSON 静态资源打包进前端（`src/assets/geo/shaanxi.json`），无外网依赖。

## 9. 钻取交互（需求 4.4 落地）

1. **区块内钻取**：`drillEnabled` 且数据源 TIMESERIES 时，点击组件内指标项 → BlockContainer 切至 DrillTrend，携带被点击指标 key，按 drillPeriods 第一项取数渲染折线；周期 tabs 切换重新取数；返回按钮回原组件。不跳页。
2. **跨页跳转**：`drill_json.jump` 配置目标屏 + 参数映射；渲染层从被点击行数据取值 `router.push({path:'/screen/'+target, query})`。地图点位跳转走 MAP_POINT 的 target_screen_code，同一机制。

## 10. 种子配置（随 DDL 脚本入库，管理员可改）

| 屏 | screen_code | 种子区块 |
|---|---|---|
| 省分行总览 | SCR_PROVINCE | MAIN=陕西地图+全部支行点位；LEFT=全省存/贷款指标卡 + 存款趋势折线；RIGHT=支行排名列表（跳支行屏）+ 在途流程状态概览 |
| 支行详情 | SCR_BRANCH | 核心指标区=指标卡×N（目标值/完成值/完成比例/分行内排名/缺口——排名与缺口用机构宽表 CUSTOM_SQL `RANK() OVER` + 目标表 JOIN）；人员业绩排名列表（KPI 综合分，点击跳个人屏） |
| 个人详情 | SCR_PERSON | 个人 KPI 卡 + 个人指标明细列表 + 历史业绩趋势折线（宽表时序）+ 负责流程状态概览 |

配套种子数据源约 8-10 个（宽表引导式 + KPI 引导式 + 排名/流程 CUSTOM_SQL）。

## 11. 测试策略（TDD 红线）

- **单测（surefire, *Test）**：ScreenSqlValidator ≥15 边界用例（对齐 SqlSafeValidator 标准）；ScreenDatasourceService 槽位翻译/能力标签/日期列校验；ScreenConfigService 联动校验/布局合法性/固定区约束/引用删除保护；ScreenQueryEngine 周期模板→日期范围换算、占位参数绑定、LIMIT 包裹。
- **IT（failsafe, *IT）**：4 张表 Mapper CRUD；QueryEngine 对真库宽表取数（LATEST/范围/月末时点）；Controller `@BizAuth`/`@AuditLog` 声明基线。
- **前端**：以种子配置对三级屏手工验收（渲染/钻取/跳转/自适应缩放）。

## 12. 交付阶段（单一实现计划内的顺序）

1. DDL + PT_RESOURCE + 种子 SQL 脚本；实体/Mapper（MyBatis-Plus）
2. ScreenSqlValidator + ScreenQueryEngine（白名单/占位/周期/LIMIT）——核心风险最先
3. 数据源 CRUD + 试跑 + 能力标签
4. 屏/区块/点位 CRUD + 联动校验 + view/data 端点
5. 前端渲染引擎 + 5 组件 + 深色主题 + 自适应
6. 配置后台两页（Datasources / Designer）
7. 三级屏种子配置 + 陕西地图 + 钻取/跳转联调
8. 全量回归 + 验收

## 13. 边界与非目标（一期）

- 不做拖拽设计器（D2）；不做浅色主题切换（D4）；组件仅 5 种，柱状图/仪表盘等二期按同一组件协议扩展。
- 不做大屏级 DATA_SCOPE 行级过滤（管理视角）；不做配置版本化/发布审批（直接生效）。
- CUST_INDEX_RESULT 纳入白名单但一期无种子场景。
- 需求 2.3 的触达/业务执行模块若表结构与白名单初稿不符，仅调整白名单常量与种子 SQL，不影响引擎设计。
