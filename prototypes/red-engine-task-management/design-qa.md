# 红色引擎任务管理、任务处理与审核角色原型 Design QA

- source visual truth path: `/home/djdev/leid/yiti/docs/superpowers/evidence/2026-08-20-redengine-user-map-playwright/user-map-list.png`
- implementation screenshot path: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-playwright/01-task-management.png`
- full-view comparison path: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-playwright/07-source-implementation-comparison.png`
- role-inheritance source visual truth paths: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-role-inheritance-playwright/03-reporter-home.png`、`/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-playwright/01-task-management.png`
- role-inheritance implementation screenshot paths: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-role-inheritance-playwright/05-branch-secretary-home.png`、`04-organization-reviewer-task-management.png`
- role-inheritance comparison paths: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-role-inheritance-playwright/06-reporter-secretary-comparison.png`、`07-admin-reviewer-comparison.png`
- viewport: `1440 x 900` CSS px
- source pixels: `1440 x 900` px
- implementation pixels: `1440 x 900` px
- density normalization: `devicePixelRatio=1`，截图按 CSS 像素输出，无缩放
- state: 桌面端“任务管理”默认列表、报送员首页、支部书记首页、组织审核员任务管理

**Findings**

最终没有可执行的 P0、P1 或 P2 问题。

- [P3] 原型顶栏增加“原型演示 / 演示身份”辅助控件。
  - Location: 顶栏用户名区域。
  - Evidence: 参考图仅显示用户名和退出；原型为了演示组织管理员、报送员、支部书记和组织审核员四种权限，增加低干扰身份选择。
  - Impact: 属原型辅助，不是正式产品字段；README 已明确不会作为生产权限设计。
  - Disposition: 保留，便于单文件原型演示完整闭环。

**Open Questions**

- 正式实现仍需确认员工范围、任务实例幂等、周期重复生成、四大维度完成回写和文件安全规则；这些不阻塞原型验收。

**Full-view Comparison Evidence**

- `07-source-implementation-comparison.png` 左侧为现有用户党组织映射页面，右侧为新增任务管理页面，两者均为 `1440 x 900`。
- `06-reporter-secretary-comparison.png` 在同一“首页”状态比较报送员与支部书记；后者保持前者全部菜单，仅增加支部审核工作台。
- `07-admin-reviewer-comparison.png` 在同一“任务管理”状态比较组织管理员与组织审核员；后者保持前者全部菜单，仅增加工作台。两张比较图均为 `2880 x 900`，单侧截图为 `1440 x 900`、DPR 1。
- 275px 深棕侧栏、60px 红色顶栏、浅灰内容背景、白色卡片、表格密度、红色激活态和页边距保持同一视觉体系。
- 新页面按需求增加查询区和更宽的数据表，因此卡片内容高度高于参考页；这是信息量差异，不是视觉漂移。

**Focused Region Comparison Evidence**

- 侧栏与顶栏已在全图中达到可读尺度；DOM 实测侧栏 `275px`、顶栏 `60px`。
- `02-new-temporary-task.png` 用于核对表单标签、必填状态、单选/复选控件、时间窗口预览和底部操作区。
- `04-task-detail-before-submit.png` 用于核对说明链接、附件上传和提交确认状态。
- `03`、`05` 分别核对提交前后工作台待办数量变化；`06` 核对四大维度分支。
- 角色继承证据 `01`、`02` 核对两个专属工作台及完整继承菜单；`03`、`05` 在相同首页状态比较报送员与支部书记；`04` 与既有管理员任务管理截图在相同状态比较组织管理员与组织审核员。

**Required Fidelity Surfaces**

- Fonts and typography: 使用 `Microsoft YaHei`、`PingFang SC`、`Noto Sans SC` 等中文系统字体栈；页面标题、卡片标题、表头、正文和辅助文字层级与参考页一致，无异常换行或截断。
- Spacing and layout rhythm: 侧栏、顶栏、内容区、卡片间距和表格行高保持后台桌面密度；`documentElement.clientWidth=scrollWidth=1440`，无整页横向溢出。
- Colors and visual tokens: 复用深棕侧栏、红色顶栏与激活色、浅灰背景、白卡片及蓝/橙/绿语义标签；对比度和状态区分清楚。
- Image quality and asset fidelity: 页面无业务位图；图标使用 `@tabler/icons-react`，favicon 复用平台现有资产，无自制 SVG/CSS 图标、emoji 或占位图形。
- Copy and content: 已核对任务管理、任务处理、新增、详情、首页待办、四大维度分支、支部审核工作台和组织审核员工作台；页面不再显示“支部审核员”身份，“沉浸式审核工作台”只在更名说明中作为历史名称出现。

**Primary Interactions Tested**

- 任务管理查询、重置、分页。
- 新增任务空表单校验、临时/定时分支、文件要求联动和发布成功。
- 组织管理员 / 报送员 / 支部书记 / 组织审核员原型身份切换。
- 支部书记保留报送员全部入口，新增“支部审核工作台”，且继承的首页可正常进入。
- 组织审核员保留组织管理员全部入口，新增更名后的“工作台”，且继承的任务管理可正常进入。
- 报送员默认进入“首页”，与审核“工作台”不重名。
- 发布后首页待办生成、普通任务详情、链接属性、必传附件、提交确认。
- 提交后任务状态变为已完成，并从首页待办移除。
- 四大维度任务进入现有材料上报入口说明分支。

**Console Errors Checked**

- 最终 `Total messages: 3 (Errors: 0, Warnings: 0)`；只有 React DevTools 开发提示。
- `route-list` 为 `No active routes`；没有 `/api` 请求。

**Comparison History**

1. 首轮 [P1] favicon 缺失导致 console 404。Fix: 复用平台 favicon 并在 `index.html` 声明；复测 error 为 0。
2. 首轮 [P2] 新增表单填写后仍保留旧错误，radio/checkbox 因全宽规则造成文字异常换行。Fix: 字段变化清理对应错误，选择控件固定 `16 x 16`；复测 `errorsAfter=[]`。
3. 首轮 [P2] 侧栏 logo 和菜单左对齐，与参考页居中布局不一致。Fix: 恢复品牌和菜单居中，新增标记绝对定位到右侧；最终比较图无剩余 P0/P1/P2。
4. 首次角色调整将两个审核角色误解为仅保留各自工作台；用户澄清后改为权限继承模型。这是业务口径修正，不沿用旧截图作为最终验收依据。
5. 权限继承同图比较无 P0/P1/P2：基础角色与继承角色在相同页面状态下布局一致，仅增加各自专属工作台菜单，无视觉漂移。

**Implementation Checklist**

- [x] 领域测试通过。
- [x] 角色访问策略测试通过。
- [x] 生产构建和 Sites Worker 模板测试通过。
- [x] Chromium 1440 x 900 关键流程通过。
- [x] 支部书记继承报送员菜单并增加支部审核工作台。
- [x] 组织审核员继承组织管理员菜单并增加工作台。
- [x] route、request、console 和截图证据已归档。
- [x] 源图与实现图同图比较完成。

**Follow-up Polish**

- 正式实现时应移除原型身份选择，并接入真实菜单、RBAC 和任务实例接口。

final result: passed
