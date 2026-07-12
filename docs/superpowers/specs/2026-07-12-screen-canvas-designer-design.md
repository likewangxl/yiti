# 大屏画布设计器(RPT_SCREEN V2)设计规格

> 日期:2026-07-12 | 状态:已经用户逐节评审通过(三节)
> 参考研读:`.omc/research/dataease-frontend.md`、`.omc/research/dataease-backend.md`(DataEase v2 主分支,commit 82637220)
> 本文是唯一设计真相;实施计划见后续 `docs/superpowers/plans/` 对应文档。

## 1. 背景与目标

现有 RPT_SCREEN 大屏体系(report-analytics-center)采用"行/块百分比"配置式布局,设计器表达力有限。目标:参照 DataEase 大屏编辑器架构,将设计器升级为**自由拖拽画布**,使大屏具备"组件任意摆放/缩放、吸附对齐、图层管理、撤销重做、素材组件、草稿/发布双态"能力。

**一期范围(P0)**:自由画布设计器 + 组件库/属性面板 + 吸附对齐/参考线 + 撤销重做 + 图层面板/右键菜单 + 基础素材组件 + 保存/发布/回滚后端 + 3 屏重配。
**明确二期(本期不做)**:多选/框选/成组、数据集抽象、联动钻取、模板市场、图片上传管理、图层拖拽排序、公共免登录分享链接、组件旋转、移动端布局。

**约束与前提**(需求澄清结论):
- 取数引擎零改动:`ScreenQueryEngine` + `/api/screen/data`(RPT-43010/43011/43008 错误码契约)原样沿用,一期只换画布层。
- GPL 合规边界:可参照/改写 DataEase 关键算法片段(内部部署,风险已由用户评估接受);可用 MIT/Apache 第三方库。
- 画布交互内核走**参照自研**路线(方案 A):运行时零新增依赖,对照 DataEase `Shape.vue`/`MarkLine.vue` 移植裁剪。
- 现有 3 屏(SCR_PROVINCE/SCR_BRANCH/SCR_PERSON)**尚在开发测试阶段、非生产**:直接切换,不写渲染 fallback,手工重配、不写迁移代码。

## 2. 数据模型

### 2.1 RPT_SCREEN 新增字段(手写 SQL 脚本进 `docs/superpowers/sql/`,禁 Flyway)

| 字段 | 类型 | 说明 |
|------|------|------|
| `CANVAS_STYLE_JSON` | longtext | 画布全局样式:设计基准 1920×1080、背景、适配策略(keep/keepProportion/widthFirst/heightFirst,TS 联合类型锁死全仓唯一来源)、主题覆盖 |
| `CANVAS_DRAFT_JSON` | longtext | 编辑态组件树(草稿),编辑器唯一读写对象 |
| `CANVAS_PUBLISHED_JSON` | longtext | 发布态渲染包 = 组件树 + 各图表组件绑定配置快照合成;线上/预览渲染只读它 |
| `CANVAS_VERSION` | int | 真乐观锁:保存 `WHERE CANVAS_VERSION=?` 并自增,冲突返回 RPT-43012(不采用 DataEase 的 content_id 哨兵模式) |
| `PUBLISH_STATUS` | tinyint | 0 未发布 / 1 已发布 / 2 已发布但有未发布修改 |
| `PUBLISHED_AT` / `PUBLISHED_BY` | datetime / varchar | 发布审计 |

> 三个 JSON 字段(STYLE/DRAFT/PUBLISHED)的文档根均携带 `schemaVersion`(自 v1 起),读时兼容补丁集中在唯一的适配函数处理——这是"读时兼容从第一版就有版本抓手"的落点。

### 2.2 组件树 JSON 节点结构(类 DataEase componentData 裁剪版)

```json
{
  "schemaVersion": 1,
  "components": [
    {
      "id": "w-8f3a…",
      "component": "ChartWidget | TextLabel | ImageBox | RectShape | BorderDecor | ClockWidget",
      "innerType": "METRIC_CARD | LINE_TREND | …(仅 ChartWidget 有)",
      "blockId": 1008,
      "style": { "top": 120, "left": 240, "width": 600, "height": 320 },
      "propValue": { "…素材组件私有配置(文本内容/图片URL/边框样式等)": "…" },
      "isLock": false,
      "isShow": true
    }
  ]
}
```

- 图层顺序 = `components` 数组顺序(无 zIndex 字段,照搬 DataEase);
- **图表节点只携带 `blockId` 引用,取数配置绝不进 JSON**——画布层与取数层分表、ID 弱关联(DataEase 解耦边界的项目化映射);
- 坐标恒存 1920×1080 设计基准像素值(幂等),渲染时一次性映射——**不采用** DataEase 的增量换算模型(研读避坑 #1)。

### 2.3 RPT_SCREEN_BLOCK 职责收窄

保留并聚焦"图表取数与业务配置行"(数据源 ID、绑定配置、刷新参数),支撑实体级二次权限校验、DATA_SCOPE、审计(本项目红线,DataEase 无此约束、不可照抄其全 JSON 方案)。旧行/块布局字段保留只读备份、不删列。纯素材组件不占 block 行。

### 2.4 新表 RPT_SCREEN_PUBLISH_LOG

`ID / SCREEN_ID / SNAPSHOT_JSON(发布时的渲染包) / PUBLISHED_BY / PUBLISHED_AT`,按屏保留最近 10 次滚动归档(避坑:DataEase 双态互相覆盖后历史彻底丢失)。

## 3. 后端接口(report-analytics-center 内演进)

模块惯例沿用:不暴露 *Api、@BizAuth(bizType=REPORT)、RptException 仅 (code)/(code,Throwable)、新增 DB 访问一律 MyBatis-Plus。

> URL 前缀勘误(2026-07-12 计划阶段核实):既有 4 个 Screen Controller 与 13 条 PT_RESOURCE 的实际前缀为 `/api/screen/...`(非 `/api/report/screen/...`),新端点与实况保持一致。

| 端点 | 职责 | 权限 |
|------|------|------|
| `GET /api/screen/admin/canvas/{id}` | 编辑器加载:styleJson + draftJson + blocks 行 | 现有屏管理资源 |
| `POST /api/screen/admin/canvas/save` | 保存草稿:styleJson + draftJson + blocks 增删改,单事务;乐观锁冲突→RPT-43012 | 现有屏管理资源 |
| `POST /api/screen/admin/canvas/publish` | 发布:解析 JSON 收集 blockId 集合与行数据交叉校验(结构化解析,禁字符串 contains)→ 合成渲染包 → 写 PUBLISHED_JSON + 归档 + 状态机流转 | **R_RPT_SCR_PUBLISH(高危:独立 URL/单独授权/单独审计)** |
| `POST /api/screen/admin/canvas/rollback` | 从归档回滚指定一次发布到 PUBLISHED_JSON | R_RPT_SCR_PUBLISH |
| `POST /api/screen/admin/canvas/discard` | 放弃草稿:发布态覆盖 DRAFT_JSON | 现有屏管理资源 |

**服务端校验**(用户输入必须验证红线):组件类型白名单、blockId 归属校验(禁越权引用他屏 block)、画布 JSON ≤ 2MB、数值范围(坐标/尺寸)。新增错误码:`RPT-43012 画布保存冲突`(错误码守护测试同步)。

**渲染链路**:`/api/report/screen/view/**` 全屏渲染改读 `CANVAS_PUBLISHED_JSON`;`?preview=draft` 读 DRAFT_JSON(需登录 + 屏管理权限);取数接口不动。

## 4. 前端架构

### 4.1 页面结构与目录

新设计器直接替换 `/screen-admin/designer` 路由。三栏:左 = 组件面板(素材/图表分组)+ 图层面板(双 tab);中 = 1920×1080 画布(网格背景,缩放 50%~150% + 适应窗口);右 = 选中组件属性面板 / 未选中时画布全局设置。

```
xanzc_frontend/src/views/screen/designer/
├── DesignerV2.vue          # 三栏骨架 + 顶部工具条(保存/发布/预览/undo-redo)
├── canvas/
│   ├── CanvasCore.vue      # 画布容器:点选、drop、右键入口、网格
│   ├── Shape.vue           # 交互外框:拖拽移动 + 8 点缩放(rAF 节流,Shift 保持宽高比)
│   ├── MarkLine.vue        # 吸附对齐线(其余组件 6 基准线,3px 阈值)
│   └── ContextMenu.vue     # 右键:复制/粘贴/删除/置顶/置底/上移/下移/锁定
├── panels/                 # ComponentPanel / LayerPanel / CommonAttr 基座
└── widgets/                # 每组件 Component.vue + Attr.vue + 元数据
xanzc_frontend/src/stores/screenDesigner.js   # Pinia store
xanzc_frontend/src/views/screen/designer/utils/  # snap.js / scale.js / snapshotStack.js(纯函数,vitest 覆盖)
```

### 4.2 画布内核(参照 DataEase 移植裁剪)

- 移植:拖拽移动、8 点缩放、吸附对齐线、右键菜单、锁定/隐藏;裁剪不做:旋转、Tab 容器、栅格矩阵、框选/成组;
- undo/redo:全量深拷贝快照数组 + 指针 + 3 秒防抖;快捷键 Ctrl+Z/Y/C/V、Del、Ctrl+S、方向键微移(弹框打开时全局禁用);
- 图层面板:置顶/置底/上移/下移按钮 + 右键(不引入 vuedraggable,守零运行时新依赖);
- 编辑器缩放:画布容器 `transform: scale`,鼠标坐标统一除以 scale 换算,设计态坐标恒 1920×1080 基准。

### 4.3 组件体系(两层注册)

- **素材组件**(手写字典 `componentsMap`,命名约定 `Xxx` + `XxxAttr`,一期 5 个):TextLabel、ImageBox(仅 URL)、RectShape、BorderDecor(复用 `_screen-theme.scss` 的 scr-surface 风格,2~3 种)、ClockWidget;
- **图表组件**:现有 9 个渲染组件统一挂 `ChartWidget` 容器(`innerType` 区分),内部复用 BlockContainer 取数逻辑(43010 引导态/静默 toast 契约不变);图表元数据 `import.meta.glob` 自动扫描注册,新增图表类型 = 新增一个元数据文件;
- **属性面板**:CommonAttr 基座(位置尺寸数值输入/背景/边框/透明度)+ 组件私有 Attr;直接 mutate Pinia `curComponent` 同一响应式引用,改动防抖记快照(团队规范:Attr 只改自身组件字段)。

### 4.4 编辑器数据流

打开 → GET canvas/{id} → store 初始化;保存 → 序列化 POST(带 CANVAS_VERSION,43012 冲突提示);内嵌预览 = 画布只读态 + 现有 orgCode/empId 预览工具条;全屏草稿预览 `/screen/:code?preview=draft`。

## 5. 安全与权限

- PT_RESOURCE:画布加载/保存/放弃草稿归入现有 `R_RPT_SCR_*` 管理资源;发布/回滚新增独立资源 `R_RPT_SCR_PUBLISH`;上线同步补角色绑定(含 R_ADMIN 行,防 AUTH-40304);
- 发布/回滚写操作审计日志;
- 服务端全量校验见 §3;草稿预览需登录 + 屏管理权限;公共免登录分享链接二期(DataEase link-token 白名单方案已留研读档案)。

## 6. 测试策略(TDD 红线)

- **后端**(surefire `*Test`,先 Red 后 Green):画布保存(乐观锁→43012、blockId 归属、组件白名单、JSON 上限)、发布(渲染包合成、归档滚动 10 份、回滚)、RptErrorCode 守护测试同步;回归门禁:`mvn test -pl report-analytics-center` 失败数不超过实施启动时**实测记录**的 pre-existing 基线(近两次实测分别为 10 与 9,以启动时实测值为准);
- **前端**:吸附计算/缩放换算/快照栈抽为纯函数 utils,**引入 vitest(devDependency,用户已确认)** 仅测纯函数;`npx vite build --logLevel error` exit 0 门禁沿用;
- 提交纪律:每步独立 commit + pathspec,不卷入工作区既有未提交文件。

## 7. 三屏切换与种子(已确认:3 屏在开发测试阶段,直接切换)

1. 渲染层直接改读 CANVAS_PUBLISHED_JSON,**不写行/块 fallback**;
2. 一期交付内包含:用新设计器手工重配 3 屏(视觉对齐现有深色主题)→ 发布 → 导出新种子 SQL(`docs/superpowers/sql/2026-07-XX-screen-canvas-seed.sql`);
3. 同期删除旧 Designer.vue 与行/块渲染分支;RPT_SCREEN_BLOCK 旧布局字段保留只读备份;严禁重跑既有 DDL 基线脚本。

## 8. 一期完成定义(DoD)

- 画布设计器全功能(拖拽/缩放/吸附/undo/图层/右键/属性面板 + 5 素材 + 9 图表)可用;
- 保存/发布/回滚/放弃草稿后端交付且 TDD 证据齐全;
- 双态渲染生效,3 屏重配完成并发布,新种子 SQL 落盘;
- 新增测试全绿;`mvn test -pl report-analytics-center` 失败数 ≤ 9;vite build exit 0;
- PT_RESOURCE/角色绑定/审计落位;文档(模块 CLAUDE.md/DDL 基线说明)同步。

## 9. 主要风险与对策

| 风险 | 对策 |
|------|------|
| 手写画布交互边界 bug(拖出画布、极小尺寸、缩放坐标漂移) | 对照 DataEase 源码移植 + 纯函数 utils 单测 + 边界用例清单(实施计划中列明) |
| 画布 JSON schema 演进失控 | schemaVersion 从 v1 起 + 服务端白名单校验;新增字段必须走读时默认值补丁函数(集中一处) |
| 乐观锁误伤单人多标签页场景 | 43012 冲突提示提供"强制覆盖"二次确认(前端带最新 version 重发) |
| 发布渲染包与 block 行漂移 | 发布时结构化解析 JSON 收集 blockId 与行集合做一致性校验,不一致拒绝发布 |
