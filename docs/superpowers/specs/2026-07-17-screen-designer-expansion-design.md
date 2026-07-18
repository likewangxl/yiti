# 大屏设计器扩充（借鉴 DataEase）+ KPI 明细数据源 + DATA_SCOPE 设计规格

> 日期：2026-07-17 | 状态：设计定稿（范围已经用户四问确认：KPI 口径 T+1、图表组件全选、画布能力全选、数据源做到字段元数据+聚合过滤）
> 前置真相：`2026-07-12-screen-dashboard-design.md`（数据源/取数引擎）、`2026-07-12-screen-canvas-designer-design.md`（V2 自由画布，本文在其上增量演进）
> 借鉴来源：DataEase v2 用户手册（数据大屏基础功能 / 组件基础功能 / 图表数据设计 / 数据集功能设计），仅借鉴设计思想，无代码引入

## 1. 背景与目标

现有 V2 自由画布设计器已具备拖拽/8 点缩放/吸附线/撤销重做/图层面板/右键菜单/草稿发布回滚，但：

1. **组件太少**：图表仅 5 种启用（METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS，另有 4 个 `enabled:false` 占位），素材仅 5 种，页面观感单薄。
2. **KPI 数据源只有总分**：`source_kind=KPI_RESULT` 只返回 `KPI_RESULT.kpi_total_score`，看不到细项得分与完成情况（目标值/还差多少）。
3. **数据源配置能力弱**：无字段元数据（别名/单位/格式），引导式配置不支持聚合/过滤。
4. **取数无行级权限**：一期决策"大屏查看不做行级 DATA_SCOPE"，本期按用户要求补齐，沿用平台统一 DATA_SCOPE 机制。

## 2. 关键决策（已与用户确认）

| # | 决策点 | 结论 |
|---|---|---|
| E1 | KPI 完成情况口径 | **T+1 每日快照**：直接读 `PERF_KPI_SCORE`（每日定时计算落表），完成率=actual/target、缺口=target-actual 由查询 SQL 现算，不新增批任务、不做实时重算 |
| E2 | 图表组件 | 启用并实现 4 个占位（柱状对比/堆叠面积/仪表盘/明细表格）+ 新增 4 个 KPI 专属组件 + 表格轮播 |
| E3 | 画布能力 | 多选/框选/成组/对齐分布、素材装饰扩充+渐变/图片背景、图层拖拽排序+组件改名、全屏周期过滤器（联动）——全做 |
| E4 | 数据源深度 | 字段元数据 + 聚合/过滤引导式配置 + KPI_DETAIL 数据源；**不做**计算字段（四则运算）留三期 |
| E5 | 权限 | `/api/screen/data` 与整屏查看应用 DATA_SCOPE（common-dev-guide §5 七类谓词，fail-close），配置后台维持角色控制不变 |

## 3. 数据源体系扩充（后端）

### 3.1 新增 source_kind=KPI_DETAIL（KPI 细项引导式）

数据底座：`PERF_KPI_SCORE`（唯一键 data_date+scheme_code+metric_code+subject_type+subject_id，已含 actual_value/target_value/weight/score）+ `PERF_METRIC_DEF`（指标名）+ `PERF_KPI_SCHEME`（方案下拉）。

`config_json`（schemaVersion:2）：

```json
{ "schemaVersion": 2, "schemeCode": "KPI_2026_STD", "subjectType": "EMP", "mode": "SNAPSHOT" }
```

- `subjectType`：EMP（contextParams.empId → subject_id）/ ORG（contextParams.orgCode → subject_id）。
- `mode=SNAPSHOT`（ds_type=SINGLE）：取 `data_date = (SELECT MAX(data_date) …)` 当日全部细项，一行一个细项：

```
columns: [metric_code, 细项名称, 目标值, 实际值, 权重, 得分, 完成率, 缺口]
完成率 = ROUND(actual_value / NULLIF(target_value,0) * 100, 2)   -- target=0 → NULL，前端显示"—"
缺口   = target_value - actual_value                              -- 允许负数（超额），前端按正负渲染
```

- `mode=TREND`（ds_type=TIMESERIES）：按周期模板/日期范围返回 `data_date × 细项得分`（可选列切完成率），适配折线/面积趋势与钻取。
- 表白名单新增：`PERF_KPI_SCORE`、`PERF_KPI_ITEM`、`PERF_KPI_SCHEME`（`PERF_METRIC_DEF` 已在）。
- 管理端配套：`GET /api/screen/admin/kpi-schemes`（方案下拉，读 PERF_KPI_SCHEME ACTIVE），归入现有 DS 管理资源。

### 3.2 字段元数据（全 source_kind 通用，借鉴 DataEase 数据集字段管理）

`config_json` 顶层新增可选 `fieldMeta`（保存时校验、执行后套用）：

```json
"fieldMeta": [ { "col": "存款余额", "alias": "一般性存款", "role": "METRIC", "unit": "万元", "decimals": 2 },
               { "col": "data_date", "role": "DIM" } ]
```

- `role: DIM|METRIC`（文本/日期默认 DIM、数值默认 METRIC，可改——DataEase 同款约定）；alias 用于组件展示列名替换；unit/decimals 供组件默认格式化（组件 style_json 可覆盖）。
- `/api/screen/data` 响应扩展（向后兼容，新增字段不动 columns/rows 契约）：`"columnsMeta": [{col, alias, role, unit, decimals}]`；旧组件忽略即可。
- try-run 响应同步返回 columnsMeta。

### 3.3 WIDE_TABLE 聚合/过滤（引导式，借鉴 DataEase 图表数据设计）

`config_json` 新增可选 `aggregation`：

```json
"aggregation": { "groupBy": "NONE|SUBJECT|DATE", "agg": "SUM|AVG|MAX|MIN|COUNT",
                 "filters": [ { "col": "org_code", "op": "EQ|NE|IN|GT|GE|LT|LE", "value": "…" } ] }
```

- 无 `aggregation` 时行为与现状完全一致（明细行）；有则引擎生成 `SELECT {groupBy}, AGG(slotN)... GROUP BY ...`，filters 逐条 PreparedStatement 绑定（op 白名单枚举，禁拼接）。
- filters 的 col 仅允许该宽表白名单列（subjectCol/data_date/已绑定槽位），越界抛 RPT-43009。

### 3.4 schemaVersion 与读时兼容

`config_json` 根携带 `schemaVersion`（旧数据视为 1）；读取集中在唯一适配函数补默认值（沿用画布 JSON 的既有模式），不写迁移 SQL。

## 4. 取数权限：DATA_SCOPE（后端）

沿用平台机制（common-dev-guide §5 + PT_ROLE_BIZ_SCOPE，BizType=REPORT，多角色取并集，Fail Close）：

1. **主体参数约束**（核心路径）：`/api/screen/data` 在执行前解析当前用户 scope：
   - `SELF` → 仅允许 `contextParams.empId = 当前用户`；orgCode 参数仅允许本人主机构；
   - `ORG` → orgCode 必须=本人主机构；empId 必须属于本机构（EXT_USER_ORG/ADDRBOOK 校验）；
   - `ORG_SUBTREE` → orgCode/empId 所属机构必须落在本人机构子树（CTE 递归，机制同 §5.1 模板）；
   - `ALL` → 不限；
   - 其余类型/未配置 → **fail-close 拒绝（RPT-43013）**。
2. **无主体参数的数据源**（全省聚合/在途流程类 CUSTOM_SQL）：数据源新增标注 `scopeMode: SUBJECT|GLOBAL`（config_json，默认 SUBJECT）；GLOBAL 数据源要求用户 scope 为 ALL 或 ORG_SUBTREE 且主机构为省行节点，否则拒绝。
3. **整屏查看** `GET /api/screen/view/{code}`：PERSON 屏未带 empId 时默认注入当前用户 empId；BRANCH 屏 orgCode 缺省注入主机构——既是易用性也是权限兜底。
4. 新错误码：`RPT-43013 SCREEN_DATA_SCOPE_DENIED 数据范围不允许`（错误码守护测试同步）。
5. 配置后台（数据源/画布管理）维持现有角色资源控制，不加行级过滤。

## 5. 前端：组件体系扩充

### 5.1 图表组件（charts/*.js 注册缝隙现成，新增=新增元数据文件+渲染组件）

| innerType | 名称 | 动作 | 适配数据源 |
|---|---|---|---|
| BAR_COMPARE | 柱状对比 | 占位启用+实现（含堆叠/横向条形三形态，propValue.barMode） | 任意 |
| AREA_STACK | 堆叠面积图 | 占位启用+实现 | TIMESERIES |
| GAUGE | 仪表盘 | 占位启用+实现（绑完成率列，展示目标达成） | 任意 |
| TABLE_LIST | 明细表格 | 占位启用+实现（深色表格+自动滚动轮播开关 propValue.carousel） | 任意 |
| KPI_DETAIL_TABLE | KPI 细项表 | 新增（列：细项/目标/实际/完成率/缺口/得分；缺口红绿、完成率进度条内嵌） | KPI_DETAIL(SNAPSHOT) |
| KPI_RADAR | KPI 细项雷达 | 新增（维度=细项，值=得分或完成率） | KPI_DETAIL(SNAPSHOT) |
| LIQUID_PROGRESS | 水波完成度 | 新增（echarts-liquidfill 若引依赖需确认；**默认自绘 SVG 波形**，守零新增运行时依赖红线） | 单值完成率 |
| PROGRESS_LIST | 进度条列表 | 新增（细项完成率横向进度条 + "还差 X"文案） | KPI_DETAIL/任意 |

- 联动校验沿用双侧机制：`needTimeseries` 元数据 + 后端 RPT-43005；KPI 专属组件加 `needKinds:['KPI_DETAIL']` 元数据（前端过滤 + 后端校验）。
- 后端 `component_type` 白名单同步扩充（画布保存校验、BLOCK 保存校验）。

### 5.2 素材/装饰组件（componentsMap 手写字典，命名 Xxx+XxxAttr 约定）

- `TitleBar` 科技感标题条（渐变底+发光线，2~3 预设）；`DecorLine` 装饰线/角标；`Marquee` 跑马灯文本（速度/方向可配）；`BorderDecor` 扩到 6+ 种边框样式。
- 背景增强：画布全局与组件 CommonAttr 支持**线性渐变（双色+角度）与图片 URL 背景**（canvas_style_json / style 扩展，读时兼容默认值）。

### 5.3 全屏周期过滤器（借鉴 DataEase 查询组件的最小裁剪）

- 新素材类组件 `PeriodFilter`（画布任意摆放）：propValue 配置可选周期模板集合（复用 LATEST/LAST_10D/…）与默认值。
- 联动模型：运行时 PeriodFilter 把选中周期写入 screen 级 Pinia/provide 上下文；所有绑定 TIMESERIES 数据源的 ChartWidget 监听并以该周期覆盖自身 period 重新取数（SINGLE 数据源与 propValue.ignoreGlobalPeriod=true 的组件不受影响）。每屏最多 1 个（保存校验）。

## 6. 前端：画布交互增强（上期二期清单落地）

1. **多选/框选**：画布空白拖拽出框选矩形（与组件求交集命中）、Ctrl+点选增删；多选态批量拖动、Del 批量删、对齐分布可用；`curComponents[]` 进 store（单选为长度 1 的特例，兼容现有 Attr 面板只在单选时显示）。
2. **成组/解组**：Group 节点进组件树（children 数组，参照 DataEase 组合），组内相对坐标；组可整体移动/缩放（子组件按比例换算）、锁定；解组回填绝对坐标。图表子组件取数行为不变（blockId 引用不动）。
3. **对齐/分布**：多选工具条：左/右/上/下对齐、水平/垂直居中、水平/垂直等间距分布——纯函数落 `utils/align.js`，vitest 覆盖。
4. **图层拖拽排序**：LayerPanel 原生 HTML5 drag 实现（不引 vuedraggable，守零依赖）；**组件改名**：图层双击改 `name` 字段（组件树 JSON 新增，读时兼容默认 label）。
5. 撤销重做/快照栈覆盖以上全部新操作（成组/解组/批量移动/排序/改名均记快照）。

## 7. 端点与资源

| 端点 | 说明 | 资源 |
|---|---|---|
| GET /api/screen/admin/kpi-schemes | KPI 方案下拉 | 归入 R_RPT_SCR_DS_LIST（同 URL 前缀族新增行，登记 PT_RESOURCE） |
| 既有端点 | 行为扩展（columnsMeta/DATA_SCOPE/新组件类型校验），URL 不变 | 不新增 |

PT_RESOURCE 新增行 + 角色绑定（含 R_ADMIN，防 AUTH-40304）；`RPT-43013` 入 RptErrorCode 守护测试。

## 8. 测试策略（TDD 红线）

- **后端（先 Red）**：KPI_DETAIL 三形态 SQL 构造（SNAPSHOT/TREND/完成率缺口列）、fieldMeta 校验与 columnsMeta 透出、aggregation SQL 生成与 op 白名单、DATA_SCOPE 七类谓词判定（含 fail-close/RPT-43013）、component_type 白名单扩充、错误码守护；Mapper/引擎 IT 走 `*IT`（failsafe，onepl_test_bootstrap）。
- **前端**：align/框选命中/成组坐标换算/周期联动 reducer 纯函数 vitest；`npx vite build --logLevel error` exit 0 门禁。
- 回归门禁：`mvn test -pl report-analytics-center` 失败数不超启动时实测基线；bootstrap 冒烟。

## 9. 交付阶段

1. 后端数据源三件套（KPI_DETAIL → fieldMeta → aggregation）+ DATA_SCOPE（任务 #2/#3/#4）
2. 前端图表 8 种（任务 #5/#6）与画布增强（#7/#8/#9/#10）并行推进
3. 数据源管理页适配（#11）→ 种子+三屏重配美化+资源对齐（#12）→ 全量回归+Playwright 验收（#13）

## 10. 边界与非目标

- 不做计算字段（四则运算）、模板市场、组件旋转、移动端、公共免登分享（维持上期边界）；
- 不做图表间点击联动（DataEase 联动仅借鉴其"查询组件→图表"单向联动形态）；
- KPI 实时重算明确不做（E1）；
- LIQUID_PROGRESS 不引入 echarts-liquidfill 依赖，自绘实现。
