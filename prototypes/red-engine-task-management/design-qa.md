# 红色引擎首页排名与角色菜单 Design QA

- source visual truth paths: `evidence/2026-08-31-home-ranking-playwright/00-source-admin-home.png`、`00-source-reporter-home.png`
- implementation screenshot paths: `01-admin-home-ranking.png`、`02-org-reviewer-home-ranking.png`、`03-reporter-institution-home.png`、`04-branch-secretary-institution-home.png`
- full-view comparison paths: `05-admin-source-implementation-comparison.png`、`06-reporter-source-implementation-comparison.png`
- viewport: `1440 x 900` CSS px
- density normalization: `devicePixelRatio=1`，`clientWidth=scrollWidth=1440`
- state: 四种演示身份的首页与左侧菜单

## Findings

最终没有可执行的 P0、P1 或 P2 问题。

- [P3] 顶栏继续保留“原型演示 / 演示身份”控件。
  - Location: 顶栏右侧。
  - Evidence: 本原型需要在同一入口切换四种角色进行业务演示。
  - Impact: 仅用于演示，不代表正式 RBAC 或生产字段。
  - Disposition: 保留，README 已说明原型边界。

## Full-view Comparison Evidence

- `05-admin-source-implementation-comparison.png`：左侧为调整前组织管理员首页，右侧为调整后首页；新增支部排名后仍保持 275px 深棕侧栏、60px 红顶栏、三列指标卡和原有内容间距。
- `06-reporter-source-implementation-comparison.png`：左侧为调整前报送员首页，右侧为调整后首页；只替换第二、第三张指标卡为所在机构得分与排名，待办列表和提示区未发生视觉漂移。
- 同图检查未发现组件裁切、整页横向溢出、异常圆角、字体跳变、错误图标或密度失衡。

## Required Fidelity Surfaces

- Fonts and typography: 沿用 `Microsoft YaHei`、`PingFang SC`、`Noto Sans SC` 中文系统字体栈，标题、正文、辅助文字及数字层级与既有后台一致。
- Spacing and layout rhythm: 侧栏、顶栏、内容区、卡片间距和表格行高保持现有后台桌面密度；支部排名与待办事项在 1440px 视口完整并列。
- Colors and visual tokens: 复用深棕侧栏、红色顶栏、浅灰背景、白卡片及红/蓝/橙语义色。
- Image quality and asset fidelity: 无新增业务位图；得分、排名与列表图标复用 `@tabler/icons-react`，无 emoji、ASCII 图标或手绘 SVG。
- Copy and content: 已核对支部排名、支部名称、得分、排名、所在机构得分、所在机构排名、任务管理等正式称谓。

## Primary Interactions Tested

- 组织管理员首页展示 5 条支部排名，列为支部名称、得分、排名；菜单保留任务管理并移除年度考核归档、数据导出。
- 组织审核员首页展示相同支部排名；菜单保留工作台、任务管理并移除年度考核归档、数据导出。
- 报送员首页展示待办事项、所在机构得分 `92.5 分`、所在机构排名 `3/12`，不展示全员达标率或支部排名表。
- 支部书记首页展示待办事项、所在机构得分 `89.8 分`、所在机构排名 `5/12`，菜单仍仅为首页、支部审核工作台、红黄牌预警池。
- 报送员点击首页临时任务后成功新开同源填报窗口，任务标题和提交入口可见。

## Console, Route and Request Evidence

- `route-list`: `No active routes`。
- console: `Total messages: 3 (Errors: 0, Warnings: 0)`，warning/error 均返回 0 条。
- requests: 17 条均为 `127.0.0.1:4179` 的 Vite、React、源码及 favicon 静态请求，全部 200；无 `/api`。
- 原始摘要见 `evidence/2026-08-31-home-ranking-playwright/` 中的 `routes.txt`、`console.txt`、`requests.txt`。

## Implementation Checklist

- [x] 首页领域测试、角色访问测试和 Sites Worker 测试通过。
- [x] Vite 生产构建通过。
- [x] 官方 Playwright CLI Chromium 四角色首页与菜单验收通过。
- [x] 无 mock route、无 API 请求、console errors/warnings 为 0。
- [x] 参考图与实现图在相同视口并排比较完成。
- [x] README、原型规则和浏览器证据已同步。

## Open Questions

- 得分、排名和支部列表目前均为纯前端演示数据；正式实现仍需确认统计口径、考核周期、并列排名规则、数据范围及对应接口。这些不阻塞本次纯前端原型。

final result: passed
