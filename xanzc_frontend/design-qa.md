# 工作区多页签导航 Design QA

- source visual truth path: `/home/djdev/leid/yiti/导航.png`
- implementation screenshot path: `/home/djdev/leid/yiti/xanzc_frontend/artifacts/workspace-tabs-final-v2-full.png`
- focused implementation screenshot path: `/home/djdev/leid/yiti/xanzc_frontend/artifacts/workspace-tabs-final-v2-focused.png`
- overflow implementation screenshot path: `/home/djdev/leid/yiti/xanzc_frontend/artifacts/workspace-tabs-final-v2-overflow-focused.png`
- full-view comparison path: `/home/djdev/leid/yiti/xanzc_frontend/artifacts/workspace-tabs-comparison-v2-full.png`
- focused comparison path: `/home/djdev/leid/yiti/xanzc_frontend/artifacts/workspace-tabs-comparison-v2-focused.png`
- viewport: 1664 × 399 CSS px
- source pixels: 1664 × 399 px
- implementation pixels: 1664 × 399 px
- CSS size / density normalization: Playwright viewport 1664 × 399，`devicePixelRatio=1`，截图使用 CSS 像素；聚焦对比将源图页签栏归一化为 1444 × 76 px，与实现元素截图同尺寸
- state: `branch_wang` 真实登录后的 DefaultLayout；首签激活以及 15 个页签的横向溢出状态
- browser: `playwright-cli 0.1.18` + Chromium Headless Shell 152.0.7977.8

**Findings**

- 最终未发现可执行的 P0/P1/P2 差异。
- [P3] Element Plus 关闭图标线条比参考图略细。
  - Location: `.workspace-tabs__close svg`。
  - Evidence: 聚焦比较中，两者占位与对齐一致，实现使用的 Element Plus `Close` 笔画更轻。
  - Impact: 不影响识别、点击区域或主要视觉层级。
  - Disposition: 保留项目标准图标库，不制作自定义 SVG。

**Full-view comparison evidence**

- `workspace-tabs-comparison-v2-full.png` 上半为参考图，下半为实现图；两者均为 1664 × 399 px。
- 任务范围内的页签栏均位于面包屑下、内容区上，未遮挡头部、侧边栏或内容滚动区。
- 参考图与现有产品壳层的侧边栏宽度、头部高度和工作台内容不同；这些是任务外既有差异，本次不改造壳层。

**Focused region comparison evidence**

- `workspace-tabs-comparison-v2-focused.png` 上半为参考图红框内页签区的 1444 × 76 px 归一化裁剪，下半为 Playwright 元素截图。
- 76px 栏高、44px 页签高、9px 上留白、22px 下留白、6px 间距、浅灰背景、白色页签、激活顶部蓝线与边框节奏均与参考结构一致。
- 实现使用 16px 中文 UI 字体和灰色未激活文字，与参考图的字号、对比度和垂直居中接近。

**Required fidelity surfaces**

- Fonts and typography: `-apple-system, BlinkMacSystemFont, PingFang SC, Microsoft YaHei, sans-serif`，16px，行高 22.4px；单行省略和中文居中正常。
- Spacing and layout rhythm: 栏高、页签高、留白、间距、边框和圆角已在同尺寸聚焦截图中验证。
- Colors and visual tokens: 背景 `#eef2f6`，激活色使用项目 `$primary`，未激活文字使用 `$text-3`，边框使用 `$border-1`。
- Image quality and asset fidelity: 目标区域无位图素材；关闭图标复用 `@element-plus/icons-vue` 的 `Close`，无自制 SVG/CSS 图标。
- Copy and content: 真实页签优先使用 DB 菜单名，再回退到路由标题/名称/路径；参考图的具体业务文案未被硬编码。

**Primary interactions tested**

- 从 `http://localhost:8091/#/login?normal` 使用真实账号登录，成功进入 `#/workspace`。
- 依次点击 15 个已授权菜单，页签自动登记且标题与 DB 菜单名同源。
- 固定工作台首签无关闭按钮，点击可返回 `#/workspace`。
- 点击已打开页签可切换路由。
- 关闭当前最后一签后，页签数从 16 变为 15，路由从 `#/report/free` 回退到左侧相邻的 `#/report/sql`。
- 溢出状态下 `clientWidth=1404`、`scrollWidth=1870`、`scrollLeft=466`，当前 `SQL 探查` 页签完全可见。
- 文档宽度始终为 `clientWidth=1664`、`scrollWidth=1664`，没有整页横向溢出。

**Console errors checked**

- Playwright 会话最终统计：4 条消息，0 errors，0 warnings。

**Comparison history**

1. 初始状态：报告因缺少浏览器渲染证据而阻断。
2. Playwright 首轮：打开 16 个页签后，当前末尾页签不在可见区，`scrollLeft=0`。证据为 `workspace-tabs-before-full.png` 与 `workspace-tabs-before-focused.png`。
3. 修复 P2：路由变化后在 DOM 更新完成时调用 `scrollIntoView({ block: 'nearest', inline: 'nearest' })`；补充回归测试。复测末尾页签完全可见，且整页无横向溢出。
4. Playwright 第二轮：聚焦比较发现原 14px 文字小于参考尺度，未激活文字对比偏重。
5. 修复 P2：页签文字调整为 16px，未激活色调整为 `$text-3`。第二轮同尺寸聚焦比较无剩余 P0/P1/P2 差异。

**Implementation Checklist**

- [x] 单元测试覆盖登记、去重、切换、关闭、固定页签与当前页签自动滚入可视区。
- [x] 布局回归测试覆盖页签栏位置及 `fullBleed` 契约。
- [x] Playwright 真实登录、菜单导航、页签切换/关闭、溢出和控制台检查通过。
- [x] 同 viewport 整页截图和同尺寸聚焦比较已完成。
- [x] 相关测试 12/12 通过。
- [x] 生产构建通过。

**Follow-up Polish**

- 如后续改用更接近参考图的图标库，可再调整关闭图标的笔画粗细；当前不影响验收。

final result: passed
