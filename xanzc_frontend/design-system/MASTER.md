# Branch Platform 主平台 UI 设计系统

> 本文档是第一阶段主平台浅色桌面界面的视觉与交互契约。实现页面前先读本文件；若后续新增 `design-system/pages/[page].md`，页面文件只允许在本文件基础上做明确的局部覆盖。

## 1. 范围与硬边界

### 覆盖范围

- **全部主平台页面**：除下述明确排除项外，`src/views/` 下由 `DefaultLayout` 承载的全部主平台路由视图都遵守本契约，而不是只覆盖少数首期页面。
- **全局壳层与认证**：`DefaultLayout`、`AppSidebar`、`AppHeader`、`WorkspaceTabs`、通用内容区，以及 `/login` 的 UIAS 与 `?normal` 账号密码入口。`AppBreadcrumb` 源码保留供局部场景复用，但不再由 `DefaultLayout` 全局挂载。
- **门户、信息与业务协作**：`/workspace`、公告、通知、待办、转交、门户信息及客户营销、业务申请等主平台业务页面。
- **绩效与考核**：指标、目标、计算、评价、奖励分配、审批任务及相关导入、导出、详情页面。
- **报表查询与历史数据**：行长仪表盘、动态/预置/自由报表、SQL 探查、业绩分配审批查询，以及各类历史查询与详情页面。
- **系统治理与流程**：用户、角色、权限、机构、字典、审计、通知、调度、配置、流程监控、流程设计与流程详情页面。

### 不在本阶段

- 仅设计 **主平台浅色体系**；本阶段不产出暗色主题，也不为暗色预留第二套页面稿。
- 仅验证 **1920×1080** 与 **2560×1440** 两种桌面分辨率；不做手机、平板或移动端断点方案。
- `src/views/redengine/**` 是独立红色引擎，完全排除在本规范之外，沿用自己的布局和 `re-` 样式隔离。
- `src/views/screen/admin/Datasources.vue`、`OrgProfiles.vue`、`OrgGroups.vue` 对应三个浅色 CRUD 管理页，是主平台规范的明确例外，遵守本文件的桌面浅色 CRUD 基线。
- `src/views/screen/**` 的运行态与设计器是独立大屏/设计器子系统，不继承本文件的卡片、图表或壳层密度规则；上述三个管理页除外。
- 顶栏搜索当前没有检索逻辑：第一阶段隐藏搜索输入及其占位，不保留空白槽位，不新增搜索 API，也不伪造可用的搜索 affordance。

## 2. 依据、校准与决策记录

本系统由 `ui-ux-pro-max` 生成器查询后，按仓库实际结构和第一阶段边界校准。查询固定参数如下：

```bash
python3 /home/djdev/leid/yiti/.agents/skills/ui-ux-pro-max/scripts/search.py \
  "enterprise banking branch operations admin dashboard professional restrained modern light theme high-density desktop" \
  --design-system --persist --output-dir /home/djdev/leid/yiti/xanzc_frontend \
  --project-name "Branch Platform" --format markdown \
  --variance 3 --motion 3 --density 8

python3 /home/djdev/leid/yiti/.agents/skills/ui-ux-pro-max/scripts/search.py \
  "desktop enterprise admin dashboard accessibility interaction density loading states semantic tokens" \
  --stack vue --full

python3 /home/djdev/leid/yiti/.agents/skills/ui-ux-pro-max/scripts/search.py \
  "accessibility focus loading z-index" --domain ux --max-results 10 --full
```

校准结论：

- 生成器的 **Variance 3 / Motion 3 / Density 8** 保留：主平台使用克制、结构化、高信息密度但可扫描的桌面工作台。
- 生成器推荐的 Fira 字体与 Google Fonts 链接不采用：工程是中文本地系统字体优先，禁止网络字体和 `@import` 外链字体。
- 生成器的“夸张极简、超大标题、落地页 Hero、移动端断点”不适用于已存在的 B2B 业务壳层；本阶段改为企业后台层级、紧凑卡片、表格和数据状态优先。
- 生成器给出的 GSAP scroll reveal 不作为壳层依赖；路由、面板、弹窗只使用 CSS/Element Plus 的轻量过渡，并遵守 reduced-motion。
- Vue 栈查询的“使用语义元素、动态绑定 ARIA”与 UX 查询的“可见焦点、加载反馈、避免任意超大 z-index”是强制实现约束。

## 3. 设计原则

1. **业务优先**：先呈现待办、指标、状态和下一步操作；装饰不能抢夺数据层级。
2. **克制而明确**：以银行品牌深蓝、浅灰页面底色、白色工作面为主，使用边框和少量阴影分层，不使用玻璃拟态、紫粉渐变或娱乐化插画。
3. **高密度但可读**：密度 8 只压缩无关留白，不压缩字号、行高、焦点环或操作命中区。
4. **语义优先**：颜色、HTML 元素、ARIA、状态文本和图标共同表达含义，不能只靠颜色或 emoji。
5. **状态完整**：所有真实请求都有加载、空数据、错误、成功和重试/恢复路径；禁止用假数据填充真实报表或权限失败。
6. **令牌驱动**：组件只消费语义 token 或 Element Plus 变量，不在页面样式中散落原始色值、随意圆角和阴影。

## 4. 桌面布局与壳层

### 目标视口

| 目标 | 处理方式 |
|---|---|
| 1920×1080 | 基准验收视口。侧栏固定 220px，主区优先容纳筛选、表格和两列仪表盘。 |
| 2560×1440 | 同一信息架构，不简单放大字号；主内容可扩展但保持阅读宽度，超宽空间用于列间距、图表和表格，而非巨型标题。 |

主平台是桌面优先的固定信息架构：不为本阶段添加 375/768/1024 等移动断点，不通过横向滚动隐藏关键操作。建议主内容最大宽度为 `2240px`，宽屏下居中；具体页面仍以数据表格的可读性为准。

### 当前事实与目标 token

仓库当前壳层已经使用以下结构事实，后续页面应保持一致：

- `AppSidebar`：宽度 `220px`，动态消费 `menuStore.tree`，分组可展开，当前路由有明确 active 状态。
- `AppHeader`：高度 `52px`，保留账户下拉、通知入口和必要的壳层操作；无逻辑搜索隐藏且不占位。
- `AppBreadcrumb`：源码保留，但不再占用全局壳层高度；详情页使用 `PageTitle` 与既有返回路径定位，不新增全局面包屑。
- `WorkspaceTabs`：总高 `48px`；单页签高 `40px`、字号 `14px`、页签间距 `8px`、宽 `112–200px`，关闭按钮命中区 `40×40px`。页签支持横向滚动、当前项自动滚入可视区、方向键/Home/End 键盘导航、关闭和长标题截断提示。
- `DefaultLayout` 内容区：主滚动区域独立滚动，普通页面使用内边距；`fullBleed` 仅供大屏设计器等明确的特殊路由使用，不套用主平台卡片规则。

```css
:root {
  --layout-sidebar-width: 220px;
  --layout-sidebar-collapsed-width: 64px;
  --layout-header-height: 52px;
  --layout-breadcrumb-height: 40px; /* AppBreadcrumb 源码兼容值，不是 DefaultLayout 占位 */
  --layout-workspace-tabs-height: 48px;
  --layout-content-max-width: 2240px;
  --layout-content-gutter: 24px;
  --layout-content-gutter-wide: 32px;
}
```

壳层行为：

- 侧栏加载菜单时保留可解释的加载态；权限/菜单加载失败时清空旧菜单并 fail-close，不展示上一个用户的菜单。
- 桌面侧栏支持 `220px` ↔ `64px` 折叠；折叠态只收窄视觉区域，保留权限菜单的图标入口，并为每项提供完整 `aria-label` 与 tooltip。折叠不隐藏已授权菜单，也不改变 `fullBleed` 路由的内容契约。
- 侧栏分组用真实按钮/可访问的控制元素表达展开收起，`aria-expanded` 随状态变化；当前路由使用 `aria-current="page"` 或等价语义。
- 当前页签是唯一高亮项；页签关闭后回到 store 选定的相邻页签，不能静默跳回首页。
- 账户、通知、弹窗等悬浮层不依赖 hover 才能发现；所有 icon-only 控件都有可读的 `aria-label` 和 tooltip/title。

## 5. 颜色与语义 token

主平台沿用 `src/styles/tokens.scss` 已建立的蓝色品牌基线，并把组件使用方式收敛为语义 token。下面的值是浅色主题唯一真相；页面不得直接写另一套 hex。

### 基础色板

| 语义 | 值 | 用途 |
|---|---|---|
| `--color-brand-700` | `#003D7A` | 主按钮、active 指示、关键链接、主图表线 |
| `--color-brand-500` | `#1E5BBA` | hover、次级强调、焦点环候选 |
| `--color-brand-100` | `#E6EEF7` | 选中/信息浅底、标签浅底 |
| `--color-page` | `#F5F7FA` | 页面背景 |
| `--color-surface` | `#FFFFFF` | 卡片、表格、表单工作面 |
| `--color-surface-soft` | `#F9FAFB` | 次级面、输入 hover、筛选条 |
| `--color-text-strong` | `#1F2937` | 标题、正文、关键数值 |
| `--color-text` | `#4B5563` | 普通辅助文本、表头、控件标签 |
| `--color-text-muted` | `#6B7280` | 非关键元数据，建议只用于 12px 辅助信息 |
| `--color-border` | `#E5E7EB` | 卡片、表格、分割线 |
| `--color-border-strong` | `#D1D5DB` | 输入边界、hover/active 边界 |
| `--color-focus` | `#1E5BBA` | 键盘焦点环，不得移除 |

### 状态色

状态必须同时有文本或图标，不能只用红/黄/绿区分。小字号状态文本使用前景色，浅色背景用于面积承载：

| 语义 | 前景色 | 浅色背景 | 适用 |
|---|---|---|---|
| `--color-success-fg` | `#166534` | `#ECFDF5` | 已启用、正常 SLA、完成 |
| `--color-warning-fg` | `#92400E` | `#FFFBEB` | 预警、停用、即将超时 |
| `--color-danger-fg` | `#B91C1C` | `#FEF2F2` | 错误、锁定、删除、超时 |
| `--color-info-fg` | `#0E7490` | `#ECFEFF` | 说明、处理中、信息提示 |

`#16A34A`、`#D97706` 等现有色值可以继续作为面积或图形色，但不要作为白底小字号文本；需要文本时映射到上述 `*-fg` token。正文文字与白色工作面保持至少 **4.5:1**，大字号至少 **3:1**；焦点、表单边界和数据图形使用可辨识的非颜色辅助。

### Element Plus 映射

保持 `src/main.js` 的 `app.use(ElementPlus, { locale: zhCn })` **全量注册**，不改为按需组件解析。全局主题只在 token/Element Plus 变量层做映射：

```css
:root {
  --el-color-primary: var(--color-brand-700);
  --el-color-primary-light-3: var(--color-brand-500);
  --el-bg-color-page: var(--color-page);
  --el-bg-color: var(--color-surface);
  --el-border-color: var(--color-border);
  --el-border-color-hover: var(--color-border-strong);
  --el-text-color-primary: var(--color-text-strong);
  --el-text-color-regular: var(--color-text);
  --el-text-color-secondary: var(--color-text-muted);
  --el-border-radius-base: 4px;
}
```

## 6. 字体、字号与数据可读性

不引入网络字体，不使用 Google Fonts `@import`，不依赖 Fira Code/Fira Sans。中文和数字使用本地系统 sans-serif 回退链：

```css
font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei",
  "Noto Sans CJK SC", "Source Han Sans SC", sans-serif;
```

建议层级（桌面密集模式）：

| 层级 | 字号/行高 | 用途 |
|---|---|---|
| 页面标题 | 18px / 28px，600 | `PageTitle`、仪表盘标题 |
| 区块标题 | 16px / 24px，600 | 卡片、表格区块 |
| 正文/控件 | 14px / 22px，400-500 | 表格、表单、菜单 |
| 辅助文本 | 12px / 18px，400 | 时间、来源、描述、状态补充 |
| 登录主标题 | 28-32px / 40px，600 | 仅登录品牌信息层 |
| KPI 数值 | 22-24px / 32px，600 | 使用 `font-variant-numeric: tabular-nums` |

正文不低于 14px；中文长句不使用紧缩字距。用户、工号、业务键、金额、日期、排名等数据列使用等宽数字或 `tabular-nums`，并为溢出提供 tooltip/完整值路径，避免只截断不解释。

## 7. 间距、形状与层级

主节奏是 **8px spacing system**。允许 4px 作为图标与文字的半步内间距，其余组件、区块和页面间距优先使用 8/16/24/32/40/48px：

| token | 值 | 用途 |
|---|---:|---|
| `--space-1` | 4px | 图标与文字、紧凑标签 |
| `--space-2` | 8px | 控件内部、表格单元内间距 |
| `--space-3` | 12px | 紧凑卡片/列表行 |
| `--space-4` | 16px | 常规卡片内边距、字段间距 |
| `--space-6` | 24px | 页面/区块间距、桌面内容 gutter |
| `--space-8` | 32px | 登录品牌区、宽屏分组留白 |
| `--space-10` | 40px | 仅用于较大层级分隔 |

- 默认卡片圆角 4px；输入、按钮和表格工作面不使用夸张圆角。登录卡片可使用 8px，但不做浮夸玻璃效果。
- 默认使用 1px 边框和 `0 1px 2px rgba(15, 23, 42, .06)` 的轻阴影；弹窗/下拉层最多升一级，避免每张卡片都漂浮。
- 可交互区域之间至少留出 8px 间距；icon-only 控件的可操作命中区不小于 40×40px，关键操作优先 44×44px。

## 8. 组件与交互契约

### 按钮与操作

- 每个视图只设一个主操作（主色实心）；查询、刷新、导出、编辑等为次级或 link 操作；删除、停用、锁定使用 danger 并和正常操作分组。
- 异步提交时按钮 `loading + disabled`，禁止重复点击；完成后给出可读成功/失败反馈，失败包含恢复路径。
- 按钮、链接、树节点、页签使用真实 `button`/`a`/`nav`/`main` 等语义元素，不用可点击 `div` 冒充控件。
- hover/pressed/disabled/focus 状态要互相可区分，但不改变布局尺寸、不造成表格或页签抖动。

### 表单、弹窗与筛选

- 输入框有可见 label；placeholder 只作示例，不能代替 label。错误紧邻字段显示，提交错误后焦点落到第一个无效字段。
- 密码字段保持 `show-password` 和 `autocomplete`；修改密码、用户编辑等弹窗提供取消/关闭出口，未保存内容不得静默丢弃。
- 批量删除、停用、锁定等高风险动作必须二次确认，确认文字说清对象、影响和不可逆性。
- 弹窗打开后焦点进入标题/首个控件，关闭后回到触发源；`aria-modal`、标题关联和动态 `aria-expanded` 与状态同步。

### 表格、树与页签

- 用户管理采用“机构树 + 右侧筛选/表格”的双栏结构；树筛选、表格筛选、批量操作和分页顺序固定，选择状态清晰。
- 表格默认高密度但保留 40px 左右行高、14px 正文和 8px 组间距；操作列固定在右侧时不得遮挡关键数据。
- 状态 tag 同时输出“启用/停用/正常/锁定”等文本；排序、分页、表格选中状态提供键盘和读屏语义。
- 页签使用 `<nav aria-label="工作区页签">` 路由导航语义；每个页签对应独立 URL，当前入口使用 `aria-current="page"`。不要伪装成 `role="tablist"`/`role="tab"`，也不要把路由页签包装成单一 tabpanel。方向键在页签间移动焦点，Home/End 定位首尾，Enter/Space 仍按普通按钮激活路由。关闭按钮有“关闭 + 页签标题”的 label，长标题使用 tooltip 展开完整文本。

桌面 CLI 密度验收门禁：

- 1920×1080 与 2560×1440 下，document、`.content` 和 main 均不得出现非预期横向滚动；宽表格只允许在 Element Plus 表格内部滚动容器中滚动，不得用页面级 `overflow-x: hidden` 掩盖问题。
- 实载表格的表头和数据行总 border-box 高度为 40px（39px 内容预算 + 1px 可见下边框）；同时抽查 24px tag、32px 控件和右侧 sticky 操作列不被截断。
- 主信息 + 辅助信息双行单元格必须显式使用 `compact-stack-cell`：两行各 18px、无额外纵向 margin，在 39px 内容预算内完整保留；状态 + 详情使用 24px tag + 15px 辅助行，连同 1px 可见下边框总高 40px。不得用 `overflow: hidden`、固定高度或 `display: none` 裁掉第二行；子行横向省略时仍须由受控 mouseover 提供两行完整值。
- 说明类长文本必须显式使用 `compact-clamp-cell`：最多两行、每行 18px、总内容高度不超过 36px，超出部分仅在单元格内省略；原始全文必须保留在 DOM，且仅在真实溢出时由受控 mouseover 写入完整 `title`，短文本不得误加 `title`。禁止通过隐藏整表或页面横向溢出来实现省略。
- 长文本不以初始无 `title` 判失败；必须对真实溢出单元格执行 mouseover，并必须在真实 mouseover 后校验 title 等于完整文本，未溢出单元格不得生成 title。

## 9. 页面节点规则

### 登录 `/login`

- 保持当前“左品牌信息 + 右表单”的单页结构：品牌面板承载平台名称、客户营销/流程审批/绩效报表能力；右侧承载 UIAS 或普通账号入口。
- 默认入口是 UIAS；`?normal` 才展示用户名/密码表单。登录中禁用提交并展示“登录中”，失败保留错误恢复提示。
- 表单控件必须使用可见 label/ARIA label，不用 emoji 做用户、锁、盾牌等结构图标；使用现有 Element Plus 图标体系或 SVG。
- 品牌渐变可收敛为深蓝到品牌蓝的低对比渐变；不放大到落地页级别，不使用网络字体或动画装饰。

### 工作台 `/workspace`

- 保持欢迎条、公告/通知两列、我的任务、待认领转交、我发起的顺序；用户先看到待处理事项和 SLA，再进入明细。
- 空公告、空通知、空任务是明确的 empty state；加载时用骨架/局部 loading，不能将空数组当作错误或假数据。
- “办理/详情/认领/拒绝/撤回”按风险分层；拒绝、撤回保留确认和状态反馈。通知未读状态同时用点、文本和计数表达。

### 用户管理 `/system/users`

- 左侧机构树先筛选范围，右侧再筛选工号、姓名、启用状态和锁定状态；当前机构以文本标签展示，可一键清除。
- 新增/编辑、角色分配、机构维护使用有标题的弹窗；角色 transfer 的搜索、主角色语义和最终状态提示保持可见。
- “导出、刷新、新增用户”在页面头部；批量启停/锁解/重置和删除在表格上方，禁用态说明“已选 0 条”而非仅置灰。
- 用户表格、分页、加载、空数据、错误和权限失败都必须保留稳定布局，避免操作列跳动。

### 报表仪表盘 `/report/dashboard`

- 保持当前“日期/导出操作 + 5 项 KPI + 趋势图 + 机构排名”的信息层级；1920 宽度下趋势区优先，排名区保持约 360px 可读宽度。
- 日期选择器禁止未来日期；导出 PDF 显示进行中、成功或失败反馈，不阻塞其他只读浏览。
- 趋势图提供图例、tooltip、轴标签和可读的文本摘要；不能仅凭红绿区分趋势，图表为空显示“暂无仪表盘数据”及原因范围。
- 接口加载超过 300ms 使用 skeleton/局部 spinner；接口失败提供重试/日期切换路径，不回退到仿真 KPI。

### 门户信息与业务协作

- 工作台、门户信息、公告通知、客户营销和业务申请页面先呈现待办、状态、负责人和下一步操作；列表、详情、附件与转交保持同一业务上下文。
- 对未读、待认领、超时、撤回、审批中等状态同时提供文本和非颜色提示；空态区分“暂无数据”“暂无权限”“尚未生成”。
- 导入、提交、转交、撤回等写操作必须在请求中防重复，并保留成功、失败与恢复路径；不得以静态占位数据掩盖真实接口异常。

### 绩效与考核

- 指标、目标、计算、评价和奖励分配按“筛选/操作/数据表或详情”的桌面层级组织，金额、比例、得分、期间与状态列保持可扫描和完整值路径。
- 触发计算、批量审批、导入覆盖、启停与删除等会影响业务结果的操作，必须明确影响范围、二次确认并在执行中禁用重复提交。
- 详情、审批记录和计算结果使用稳定区域承载加载、空、错状态；审批人、意见、时间和业务键可追溯。

### 报表查询与历史数据

- 查询类页面保持“条件 → 查询/导出操作 → 结果/分页或图表”的顺序；条件、数据范围、期间、指标与导出状态可见，且不改变既有 API、payload、SQL 或报表口径。
- SQL 探查、删除报表批次、停用/启用、历史详情等高风险或敏感场景，使用醒目但克制的风险说明、确认和审计反馈；只读页面不得伪造可写入口。
- 图表、动态列和历史明细提供加载、空、错、重试与完整值路径；结果不能因请求失败而残留上一轮数据或回退成仿真数值。

### 系统治理与流程

- 系统治理页面统一使用页面标题、筛选、工具栏、数据面板、详情/编辑弹窗的层级；权限失败与数据范围收缩时按 fail-close 处理，不展示旧用户或越权缓存。
- 流程监控、任务转交、审批与流程设计保留业务状态、只读边界、节点/连线/审批人等可理解文本；发布、停用、删除、转交等写操作必须防重复并明确后果。
- 表格、树、画布周边面板和弹窗均提供键盘路径、焦点回收、加载/错误提示；复杂流程页面在 1920 与 2560 宽度下优先保证主工作区与关键操作可读。

## 10. 可访问性、动效与加载

### 焦点与键盘

```css
:where(button, a, input, select, textarea, [tabindex]:not([tabindex="-1"])):focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}
```

- 不得用 `outline: none` 消除焦点而没有同等或更强替代；焦点环与相邻背景保持可见。
- Tab 顺序遵循视觉和业务顺序；路由切换后把焦点移到主内容标题/`main`，弹窗关闭后回到触发元素。
- 菜单展开、下拉、页签（方向键、Home/End、Enter/Space）、表格排序和分页均提供键盘路径；图标按钮有明确 label。

### 动效

- 交互过渡统一 **150-300ms**：默认 180ms，复杂面板 240ms；使用 ease-out 进入、ease-in 退出。
- 优先 transform/opacity，不动画化 width/height/top/left，不使用 GSAP 作为全局依赖，不做装饰性滚动揭示。
- `@media (prefers-reduced-motion: reduce)` 下取消或缩短过渡，保留状态变化、焦点和加载反馈；不能因减动画而失去可理解性。

### 加载、空态与错误

- 请求超过约 300ms 显示骨架或 spinner；按钮请求中禁用，列表和图表保留尺寸以避免 CLS。
- 空态说明“没有数据/没有权限/尚未生成”是哪一种，并在可能时提供查询、重试或返回操作。
- 错误消息说明原因和下一步，不用空白页面、静默 catch 或旧用户缓存掩盖授权失败。

### z-index 与堆叠

只使用集中管理的层级，先处理 stacking context 再加层级，禁止 `z-index: 9999`：

| 层级 | 值 | 对象 |
|---|---:|---|
| base | 0 | 页面内容、卡片、表格 |
| sticky | 10 | 侧栏 logo、壳层 sticky 区域 |
| dropdown | 20 | 菜单、选择器下拉 |
| popover | 30 | tooltip、popover、通知浮层 |
| dialog | 40 | 弹窗与遮罩 |
| toast | 50 | 全局成功/错误提示 |

## 11. 验收清单

- [ ] 覆盖全部主平台页面：壳层与登录、门户信息与业务协作、绩效考核、报表查询、历史数据、系统治理与流程；`screen/admin` 三个浅色 CRUD 例外遵守本规范，screen 运行态/设计器与 `redengine` 保持独立。
- [ ] 在 1920×1080 与 2560×1440 检查侧栏、内容 gutter、表格、图表和弹窗；不以移动端断点作为本阶段验收条件。
- [ ] 只使用中文本地系统字体；没有 Google Fonts、网络字体或 Fira 字体依赖。
- [ ] `src/main.js` 继续全量注册 Element Plus 与中文 locale；主题通过语义 token/Element Plus 变量映射。
- [ ] 正文/控件颜色满足 WCAG AA 4.5:1；状态颜色同时有文本或图标；图表有图例、tooltip、摘要或表格替代。
- [ ] 键盘路径、`focus-visible`、动态 ARIA、弹窗焦点回收和页签语义经过检查。
- [ ] 动效在 150-300ms 内，并验证 `prefers-reduced-motion`；加载超过 300ms 有 skeleton/spinner，按钮防重复提交。
- [ ] 所有间距回到 8px 主节奏；没有 emoji 结构图标、任意超大 z-index、布局抖动或隐藏关键操作。
- [ ] 查询、导入导出、审批、转交、计算、发布、启停、删除等异步写操作均有 loading + disabled、防重复、错误恢复及与风险相称的确认；只读数据不以仿真结果替代真实失败。
