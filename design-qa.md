# 陕西省地市区县地图下拉与地图切换视觉 QA

- source visual truth: 用户在大屏设计器中标注的“陕西兼容地图 / 西安复合经营地图”控件及说明
- reference capture: `docs/superpowers/evidence/2026-08-24-designer-inspector-xian-colors/implementation-after.png`
- target viewport: in-app browser 1329 × 912
- target state: 大屏设计器“零售经营总览（全辖）”，选中经营地图，地域切换到“宝鸡市”

## Findings

没有 P0、P1 或 P2 视觉或交互问题。

- [P3] 九份随包地市 GeoJSON 会增加地图组件构建分块体积。
  - Location: `src/assets/geo/shaanxi-cities/`。
  - Impact: 首次进入设计器时地图分块下载量增加，但换市无需临时联网，避免外部边界服务不可用导致空白地图。
  - Decision: 接受；稳定离线展示优先，后续如设计器首屏性能出现实测问题再拆为按市异步加载。

## Fidelity Review

- 控件：原两项单选改为分组、可搜索的详细下拉；“省级概览”和“地市区县地图”层级清楚，共 11 个选项。
- 反馈：选项下方即时显示当前地域及区县数量；市级地图补充“区县范围”和保存说明。
- 地图：除既有西安经营六区外，铜川、宝鸡、咸阳、渭南、延安、汉中、榆林、安康、商洛均展示真实区县边界与名称。
- 配色：每个市内区县按随包 GeoJSON 的稳定顺序使用最多 14 种低饱和颜色；青色边界、白色描边标签和三层拟 3D 厚度保持一致。
- 回归：陕西省概览继续显示十地市；西安继续使用经营六区、真实本地筛选和四个异地经营节点，不套用其他城市的通用区县逻辑。
- 持久化：选择写入 `regionCode`；切回西安时恢复 schema v2 复合地图，其他地域保持兼容 schema v1。

## Verification

- [x] TDD 红灯：地域常量、下拉选项、宝鸡区县地图与配置转换测试先失败。
- [x] TDD 绿灯：相关 54/54 测试通过。
- [x] `npm run build` 通过。
- [x] 真实设计器页面已展开下拉，确认陕西省及十个地市选项完整。
- [x] 现场切换陕西省、西安市、宝鸡市；画布标题和右侧提示均与地域同步，最终停留在宝鸡市（12 个区县）。
- [x] 地图切换期间 console error 为 0；既有缺失路由与 Element Plus 弃用提示与本次改动无关。
- [x] 全量测试尝试执行；其余失败来自既有 Windows Sass 绝对路径、登录路由断言及随后 Node OOM，不涉及本次三组定向测试。

final result: passed

---

# 地图贴图短引导线修正视觉 QA（2026-08-24）

- source visual truth: 用户指出全宽引导线占用左右区域；修正前截图 `docs/superpowers/evidence/2026-08-24-map-callout-leaders/implementation.png`
- implementation screenshot: `docs/superpowers/evidence/2026-08-24-map-compact-callouts/implementation.png`
- full-view comparison: `docs/superpowers/evidence/2026-08-24-map-compact-callouts/comparison.png`
- viewport: 1049 × 904 CSS px；修正前后截图均为 1049 × 904 px，device scale 1，无密度缩放
- state: “零售经营总览（全辖）”设计态，西安六区地图，12 家支行，综合达成指标

## Findings

修正后没有 P0、P1 或 P2 问题。

## Comparison History

- Iteration 1 [P1] 全宽引导线与外沿标签占用地图组件左右大面积空间，妨碍继续叠加其他大屏组件。
  - Fix: 复合地图收拢到中央 46%；标签轨道从组件外沿移至左右 24% 位置；引导线终点改为 39%/61%，最大长度通过测试限制为不超过 26% 画布宽度。
- Iteration 2: 并排对比确认标签和引导线集中在地图中央带，左右外侧恢复连续空白；无新增 P0/P1/P2。

## Required Fidelity Surfaces

- Fonts and typography: 网点名称、完成率字号和字重保持原暗色大屏层级，12 条标签无换行或截断。
- Spacing and layout rhythm: 标签左右各 6 条、上下分段；中央地图与标签形成紧凑整体，外侧约 24% 不再绘制标签或引导线。
- Colors and visual tokens: 状态色、深蓝背景、青色描边和弱发光效果未改变。
- Image quality and assets: 使用原有真实 GeoJSON/ECharts 地图资产，无新增替代图形资产；地图缩放后边界仍清晰。
- Copy and content: 12 个数据库支行名称及完成率全部保留，DOM 核验左右各 6 条。

## Verification

- [x] TDD 红灯先确认旧实现仍为 `1.2%` 外沿标签。
- [x] MapCenter 测试覆盖左右预留 24%、最大引导线长度 26% 和地图中央 46% 布局。
- [x] 相关回归 70/70 通过；`npm run build` 通过。
- [x] 浏览器现场显示 12 条完成率，左右各 6 个支行名称。
- [x] 全视图已并排比较；核心区域足以判断线长、占用范围、字体和标签间距，无需额外局部放大。

final result: passed

---

# 地图网点外置标注与引导线视觉 QA（2026-08-24）

- source visual truth: 用户提供的大屏设计器截图及“地图内网点标注过于拥挤”反馈
- comparison capture: `docs/superpowers/evidence/2026-08-24-map-callout-leaders/comparison.png`
- implementation capture: `docs/superpowers/evidence/2026-08-24-map-callout-leaders/implementation.png`
- target state: “零售经营总览（全辖）”西安六区地图，12 家本地支行，综合达成指标

## Findings

没有 P0、P1 或 P2 视觉、可读性或交互问题。

- [P3] 内嵌浏览器当前仅有 1049px 可见宽度，设计器仍按既有 1920×1080、最小 50% 缩放契约横向滚动，因此单张现场截图只能看到左侧 6 个外置标签。
  - Decision: 不改变设计器全局缩放；以左右均衡布局测试、12 条 DOM 完成率语义和现场左侧引导线截图联合验收。

## Fidelity Review

- 地图内只保留真实经纬度发光点，网点名称与当前完成率移到地图左右两侧。
- 12 家网点按纬度排序后稳定均分为左右各 6 家；标签纵向分两段排布，主动避开左右中部的宝鸡/渭南示意节点。
- 每条引导线从真实点位连接到对应外侧标签，颜色与完成率状态色一致；标签延续现有深色、青蓝和低饱和告警色体系。
- 指标切换后外置完成率、颜色和无障碍名称同步更新；设计态继续禁止钻取，运行态保留键盘及点击进入机构详情屏。
- 行政区名称和区域完成率不再与网点名称叠加，地图主体可辨识度明显提高。

## Verification

- [x] TDD 红灯：新增外置标签、12 条引导线与左右均衡断言最初失败（0/12）。
- [x] 相关回归 70/70 通过（MapCenter 32、ScreenRenderer 13、DesignerV2 25）。
- [x] `npm run build` 通过。
- [x] 内嵌浏览器现场确认 12 个支行均显示“网点名称 + 综合达成完成率”，左侧标签无重叠且引导线清晰连接地图点位。
- [x] 参考截图与实现截图已并排复核；暗色大屏视觉体系保持一致，拥挤问题按用户要求改为外置标注。

final result: passed

---

# 地图模拟指标与区县支行明细视觉 QA（2026-08-24）

- source visual truth: 用户提供的西安六区经营地图设计器截图
- comparison capture: `docs/superpowers/evidence/2026-08-24-map-demo-branch-detail/comparison.png`
- implementation capture: `docs/superpowers/evidence/2026-08-24-map-demo-branch-detail/implementation.png`
- target state: “零售经营总览（全辖）”西安六区地图，12 家数据库演示机构画像已加载

## Findings

没有 P0、P1 或 P2 视觉、数据来源标识或核心交互问题。

- [P3] Codex 内嵌浏览器本次可见宽度为 1049px，低于原参考截图 1917px；设计器保持既有 1920×1080、最小 50% 缩放和横向滚动契约，因此验收截图只能看到画布右侧地图。
  - Decision: 不改动既有设计器缩放契约；以同一页面截图、DOM 语义和组件交互测试联合验收。

## Fidelity Review

- 地图保持原有深色科技风、六区独立底色、青色轮廓和拟 3D 厚度。
- 无真实指标时显示“模拟演示 / 指标为模拟数据”，区域标签展示稳定完成率，悬浮卡再次标注模拟来源。
- 点击行政区后使用同一暗色体系打开右侧明细面板，列出支行、经纬度、完成率、实际/目标及来源。
- 真实机构名称取自 `EXT_ORG_INFO`；演示坐标写入 `PT_ORG_PROFILE`，且通过 remark 和面板文案明确标识；机构组成员仍经原范围门禁加载。
- 找不到带坐标画像时显示数据库空态，不伪造支行名称。

## Verification

- [x] TDD 红灯确认新增模拟标识、区域点击明细和空态最初均不存在。
- [x] 69/69 定向前端测试通过；完整相关回归曾达 82/82。
- [x] `npm run build` 通过。
- [x] 数据库 `PT_ORG_PROFILE` 演示画像 12 行、`PT_ORG_GROUP_MEMBER` 有效成员 12 行，重复执行保持 12 行。
- [x] 后端日志确认画像查询返回 12 行，机构组查询返回 12 个有效成员。
- [x] 内嵌浏览器确认“模拟演示”可见，并加载 12 个西安本地支行节点。
- [x] 区域点击、支行筛选、经纬度展示和空态由组件级真实 GeoJSON 测试覆盖。

final result: passed
# 说明

本文件汇总不同功能页面的视觉验收记录，各章节相互独立。

---

# 违规管理页面 Design QA

- source visual truth path:
  - `/home/djdev/lijh/tmp/rywg.jpg`
  - `/home/djdev/lijh/tmp/xdfx.jpg`
- implementation screenshot path: unavailable（当前会话未提供云浏览器或浏览器截图工具；按 Product Design 浏览器约束，未擅自调用 Playwright CLI）
- viewport: 参考图为 2504 × 1080 px；实现未取得同视口浏览器截图
- source pixel dimensions: 两张参考图均为 2504 × 1080 px
- implementation pixel dimensions / CSS size / density normalization: unavailable
- state: 桌面端、搜索区域折叠、空表格状态；功能通过单元测试、构建和本地接口验证，未完成浏览器渲染态验证

**Full-view Comparison Evidence**

- 已打开并检查两张源图，确认核心结构为：三列搜索项、右侧 2 × 3 操作按钮、展开入口、横向数据表格与右下分页。
- 实现页面已按上述结构编码，但没有浏览器渲染截图，无法将源图与实现图放入同一比较输入，因此不能作像素级通过结论。

**Focused Region Comparison Evidence**

- 未执行。筛选标签、按钮间距、表头密度和分页区域都属于需要浏览器截图才能可靠判断的细节区域。

**Findings**

- [P1] 缺少浏览器渲染证据
  - Location: `xanzc_frontend/src/views/yundun/ViolationManagement.vue`
  - Evidence: 有两张源图，但当前环境没有实现截图，无法进行同视口、同状态并排比较。
  - Impact: 字体、间距、颜色、表格密度和响应式溢出尚不能获得视觉验收结论。
  - Fix: 在可用浏览器中以 2504 × 1080 视口分别打开两条路由，登录并截取折叠空表状态，与对应源图合并比较后迭代。

**Required Fidelity Surfaces**

- Fonts and typography: blocked；只能从代码确认沿用项目 Element Plus 字体体系，未取得渲染证据。
- Spacing and layout rhythm: blocked；代码结构对应源图，但未验证实际尺寸、换行和溢出。
- Colors and visual tokens: blocked；实现复用了项目与 Element Plus 颜色，未做截图采样对比。
- Image quality and asset fidelity: 页面主体没有产品图片；图标复用 Element Plus 图标。仍缺浏览器渲染证据。
- Copy and content: 静态文案已对照源图及表中文注释；搜索、重置、删除、导入、导出、新增、展开、表格和分页文案均已覆盖。

**Open Questions**

- `yundun_xdfx.txt` 的“是否回收返还”没有可匹配的布尔字段；表中只有数值字段“回收返还（元）”。当前实现将其作为金额区间，不挂“是/否”下拉。

**Implementation Checklist**

1. 在用户允许且浏览器可用时，获取两条路由的 2504 × 1080 实现截图。
2. 对筛选区、按钮区、表格区和分页区做源图/实现图同图比较。
3. 修复任何 P0/P1/P2 差异并重新截图复核。
4. 检查搜索、展开、分页、查看/编辑、新增、删除、导入、导出的浏览器交互和控制台错误。

**Comparison History**

- Iteration 1: 因缺少浏览器渲染截图而阻塞；尚无可执行的视觉差异修复证据。

**Follow-up Polish**

- 视觉比较通过后，再判断是否需要微调长标签宽度、筛选区行距和固定操作列宽度。

final result: blocked
