# 西安运行态地图规范

## 1. 适用范围与边界

本规范仅适用于 `src/views/screen/` 的运行态 `MapCenter`，用于西安六区经营地图。

- 不适用于 `src/views/redengine/**`；红色引擎不读取地图资产、地图样式或机构钻取规则。
- 不修改大屏设计器、已发布画布 JSON、接口、数据库或权限契约。本期将运行态展示语义硬编码在前端。
- `XIAN_OUTLINE` 仍是既有持久化配置值；运行态 schema v2 实际注册并渲染 `xian-six-districts`，不得以改写历史配置完成迁移。
- 陕西 schema v1 地图继续使用既有 `shaanxi.json`，不得因本规范改变旧屏行为。
- 六区激活的唯一条件是运行包中 `schemaVersion === 2`（原生 JSON 整数）且 `mode === 'XIAN_COMPOSITE'`。运行态存在授权 `mapPayload` 时必须以该包为准，不能用伴随画布配置掩盖其缺字段；不得由 `mode` 推断版本，也不得为 schema v2 缺失的 `mode` 补默认值。只给其中一个字段、`1 + XIAN_COMPOSITE`、字符串版本号或任一冲突值均须拒绝渲染，不能回退为陕西图或六区图。

## 2. 行政区范围

西安运行态底图只保留以下六个县级行政区。未在此表中的西安市其他区县不绘制为底图，也不得被新增为本地经营节点。

| 名称 | 行政区代码 | OSM relation ID |
|---|---:|---:|
| 未央区 | 610112 | 3226095 |
| 莲湖区 | 610104 | 3226093 |
| 新城区 | 610102 | 3226096 |
| 碑林区 | 610103 | 3226088 |
| 雁塔区 | 610113 | 3226098 |
| 长安区 | 610116 | 3226089 |

行政区代码以陕西省民政厅《[2025年度陕西省行政区划代码信息](https://mzt.shaanxi.gov.cn/bs/bmcx/201711/t20171107_2703806.html)》（截至 2025-12-31）交叉核对。

## 3. 边界资产与许可

资产为 [xian-six-districts.json](../src/assets/geo/xian-six-districts.json)，附带元数据模块 [xian-six-districts.js](../src/assets/geo/xian-six-districts.js)。

| 项目 | 约定 |
|---|---|
| 几何来源 | OpenStreetMap contributors；通过 Nominatim `lookup` 一次性读取上表六个 relation 的 GeoJSON。 |
| 许可 | [ODbL 1.0](https://www.openstreetmap.org/copyright)；运行态必须将“边界数据：© OpenStreetMap contributors（ODbL）”链接至官方版权页，外链使用 `target="_blank" rel="noopener noreferrer"`。 |
| 获取日期 | 2026-08-13 |
| 原始坐标系 | WGS84 / EPSG:4326 |
| 交付坐标系 | GCJ-02；为了与既有机构画像 GCJ-02 点位处于同一坐标空间。 |
| 裁剪方式 | 仅按 relation ID 保留六个 Feature；不手绘、不平滑、不补点。 |
| 坐标转换 | 构建时逐坐标执行 WGS84 → GCJ-02 标准正向转换；不做几何简化。 |
| 原始内容 SHA-256 | `1b469566e42dad9cc21dbfce0f55b2b31f384ac362b0b3a25ddbf0d49ad93e95` |
| 交付内容 SHA-256 | `fb3980443aada65db8703c1c29399ba065f91a34959b9072ebf6d373a086e389` |

资产更新必须重新完成以下动作：核对六个 relation 名称和行政区代码、记录新的源/交付哈希、复跑地图单测，并审查 ODbL 的署名与再分发义务。无法取得具明确许可和可追溯来源的几何时，不得用手绘边界或近似多边形替代。

## 4. 机构与钻取规则

西安本地机构保留已有真实坐标和既有点击行为。四个二级分行是机构语义真实、视觉位置近似的独立导航节点：

| 机构 | orgCode | 视觉锚点 | 详情目标 |
|---|---:|---|---|
| 宝鸡分行 | 128 | LEFT | `/screen/SCR_BRANCH?orgCode=128` |
| 渭南分行 | 191 | RIGHT | `/screen/SCR_BRANCH?orgCode=191` |
| 咸阳分行 | 169 | TOP | `/screen/SCR_BRANCH?orgCode=169` |
| 榆林分行 | 129 | FAR_TOP | `/screen/SCR_BRANCH?orgCode=129` |

- 四个二级分行不得被转换成伪造经纬度，定位只使用 anchor 百分比/像素布局。
- 运行态必须显示“二级分行示意位置，非地理比例”，并在每个二级分行的可访问名称中说明该信息。
- 运行态只把后端已授权渲染包中的 `orgCode` 作为“是否允许出现”的白名单；名称、锚点和目标屏必须以硬编码表为准。未知编码、伪造名称、伪造锚点和伪造目标屏均不可生效。
- 前端导航不替代服务端 RBAC 或数据范围校验；详情屏仍由 `/api/screen/view/*` 的既有授权守卫处理。
- 禁止跳转 `/redengine/**`，也不得导入红色引擎模块或样式。

## 5. 视觉与可访问性交互

- 大屏沿用 `scr-*` 运行态隔离，不接入主平台浅色卡片/表格规范。
- 运行态外层 `.scr-header` 占据 1920×1080 设计稿的顶部 72px 浮层。西安复合地图组件落入该区域时，须按组件 `top` 补足剩余安全区，使 `.mp-title` 从浮层下方开始；安全区随舞台整体缩放，适用于 1920×1080 与 2560×1440。该偏移不得应用到设计态预览或陕西 schema v1 地图。
- 六区边界显示区名；焦点、节点、边界高亮不能只通过颜色表达。
- 仅运行态的二级分行和本地机构使用真实 `<button>`，鼠标 click、`Enter`、`Space` 等效；焦点环必须可见。
- 设计态仅展示位置和名称，不得钻取：全部地图节点必须禁用、移出 Tab 序列并标记 `aria-disabled="true"`；即使脚本派发 click/键盘事件，导航函数也必须拒绝跳转。
- 二级分行的近似位置与本地真实点位使用不同空间模型：前者为 anchor，后者为 ECharts geo 坐标；不得把两者混为同一地理比例含义。
- 动效遵循现有 ECharts 效果配置；需要新增动效时仅使用 transform/opacity，并在 `prefers-reduced-motion` 下保留可理解状态。

运行态区块取数仍遵守既有 `/api/screen/data` 版本身份：schema v1 保留 `schemaVersion + screenCode + dsId`，schema v2 保留 `schemaVersion + screenCode + blockId`。`dateFrom`、`dateTo`、`contextParams.orgCode`、`contextParams.empId` 仅在值非 `null`/`undefined` 时序列化；不得把可选 String 显式序列化为 JSON `null`，也不得借清理空值改写任何非空 payload。

## 6. 验收基线

1. 单测覆盖六区名称/代码/relation ID、来源与坐标系元数据、四个二级分行的编码/锚点/目标、授权交集、键盘钻取和 redengine 隔离；另覆盖 mode-only、schema-only、v1+XIAN、字符串版本号、非法/缺字段的 Fail Close、设计态不可钻取、schema v1/v2 空值序列化，以及双分辨率标题安全区。
2. 真实运行页使用 `/#/login?normal` 登录后，在 1920×1080 和 2560×1440 查看西安运行屏；仅执行查看与点击导航，不执行保存、发布、删除或导入。
3. 验收记录必须保留真实请求摘要、console、截图和路由结果；不得把 mock 数据或手工改写截图作为真实页面证据。

## 7. 设计器独立窗口附录

本节只约束设计器的窗口壳层与退出保护，不改写本规范前述运行态地图、schema、权限或取数契约。

- `/screen-admin/designer` 保留原路径和 `/api/screen/admin/screens` 资源守卫，但作为顶层独立路由渲染；不挂载主平台侧栏、Header 或工作区页签。
- 设计器保留新建、范围、角色、撤销/重做、缩放、预览、放弃草稿、回滚、保存和发布全部工具，另提供键盘可达的“返回”按钮。
- dirty 基线仅比较可保存的画布样式和组件树：初始加载不脏，用户修改布局/配置后变脏，回到基线或保存成功后清除；选区、缩放和面板切换不计入。
- 未保存时返回必须明示“保存并关闭 / 放弃并关闭 / 取消”三个按钮；保存失败或发生 CAS 冲突不得关闭。页面有未保存修改时同时注册 `beforeunload` 保护，卸载时清理。
- 返回先尝试关闭当前命名子窗口；直达 URL 或浏览器拒绝关闭时，降级为 `router.replace('/workspace')`。
