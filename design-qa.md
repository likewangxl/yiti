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
