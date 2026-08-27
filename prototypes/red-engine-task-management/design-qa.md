# 红色引擎任务协同原型 Design QA

- source visual truth path: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-25-role-inheritance-playwright/04-organization-reviewer-task-management.png`
- implementation screenshot path: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-27-workflow-v2-playwright/01-admin-task-management.png`
- full-view comparison path: `/home/djdev/leid/yiti/prototypes/red-engine-task-management/evidence/2026-08-27-workflow-v2-playwright/10-source-implementation-comparison.png`
- workflow screenshot paths: `02-reporter-report-info.png`、`03-reporter-popup-filled.png`、`04-reporter-reviewing-history.png`、`05-branch-secretary-reviewing.png`、`06-org-reviewer-temporary.png`、`07-task-export-preview.png`、`08-material-export-detail-selection.png`、`09-admin-warning-deduction.png`
- viewport: `1440 x 900` CSS px；popup 为 `1000 x 820`
- density normalization: `devicePixelRatio=1`，全页 `clientWidth=scrollWidth=1440`
- state: 管理员任务管理、报送员上报信息与填报、支部审核、组织审核、导出和预警池

## Findings

最终没有可执行的 P0、P1 或 P2 问题。

- [P3] 顶栏保留“原型演示 / 演示身份”控件。
  - Location: 顶栏右侧。
  - Evidence: 参考页只呈现正式用户区；本原型需要在一个入口演示四种权限。
  - Impact: 仅用于演示，不是生产字段或正式 RBAC。
  - Disposition: 保留，并在 README 明确原型边界。

## Full-view Comparison Evidence

- `10-source-implementation-comparison.png` 左侧为上一版任务管理，右侧为本版任务管理，单侧均为 `1440 x 900`。
- 275px 深棕侧栏、60px 红色顶栏、浅灰内容背景、白色卡片、红色激活态、表格密度和内容边距一致。
- 本版按需求将“上报记录”改为“上报信息”、移除“任务处理”，并增加填报情况列；属于信息架构变化，不是视觉漂移。
- 同图检查未发现组件裁切、整页横向溢出、异常圆角、字体跳变或错误图标。

## Focused Region Comparison Evidence

- `02`、`04`：核对上报信息四页签、查询区和临时任务历史列。
- `03`：核对同源独立填报窗口、说明链接、多行内容和附件选择。
- `05`：核对支部审核工作台的审核中状态与临时任务列。
- `06`：核对组织审核员三栏工作台、左侧类型筛选、填报内容、附件和审核操作。
- `07`、`08`：核对临时任务多支部 ZIP 目录和四维字典明细选择。
- `09`：核对红黄牌板块、管理员专属扣分表格和通知说明。

## Required Fidelity Surfaces

- Fonts and typography: 继续使用 `Microsoft YaHei`、`PingFang SC`、`Noto Sans SC` 中文系统字体栈；标题、表头、正文、辅助文字层级与参考页一致。
- Spacing and layout rhythm: 侧栏、顶栏、内容区、卡片间距及表格行高保持后台桌面密度；工作台三栏在 1440px 视口完整展示。
- Colors and visual tokens: 复用深棕侧栏、红色顶栏和激活色、浅灰背景、白卡片及红/蓝/橙/绿语义标签。
- Image quality and asset fidelity: 无新增业务位图；图标全部使用 `@tabler/icons-react`，无 emoji、ASCII 图标或手绘 SVG。
- Copy and content: 已核对上报信息、审核中、支部审核工作台、工作台、临时任务、四大维度材料上报、红黄牌预警池和逾期扣分等正式业务称谓。

## Primary Interactions Tested

- 四种演示身份菜单切换与任务处理菜单移除。
- 报送员待处理查询、四维任务跳转、临时任务新开窗口、链接安全属性、内容与附件提交、父窗口审核中更新。
- 审核中/已通过/已驳回按任务类型拆表；临时任务隐藏审核状态、审核意见和端员得分。
- 支部书记先审核通过、再提交组织审核；页签计数和状态同步更新。
- 组织审核员按四维/临时筛选，查看填报内容与附件，模拟下载、提交通过和直接驳回。
- 任务管理保留不同流程状态的全部已发布任务，查看多支部填报并预览 ZIP 目录。
- 四维明细可重复上传；导出未选择明细时阻断，选择后只出现对应明细和材料。
- 全角色红黄牌可见；仅管理员显示逾期扣分区，并完成扣分录入提交。

## Console, Route and Request Evidence

- `route-list`: `No active routes`。
- console: `Total messages: 3 (Errors: 0, Warnings: 0)`。
- requests: 35 条均为 `127.0.0.1:4176` 的 Vite、React、源码和 favicon 静态请求，全部 200；无 `/api`。
- 原始摘要见本轮证据目录中的 `routes.txt`、`console.txt`、`requests.txt`。

## Comparison History

1. 主验收发现审核中临时任务误用待处理表格。Fix: 审核中、已通过、已驳回统一按任务类型拆表；临时任务只保留四个业务字段。
2. 主验收发现导出只读取单个 submission。Fix: 改为优先读取 branchSubmissions，为每个有附件支部生成目录；四维导出按选择明细过滤。
3. 浏览器验收发现 submitted/approved/rejected 任务从任务管理消失。Fix: 分离“已发布列表”与流程状态判断，修复后仍显示全部 7 条任务并完成导出复测。
4. 视觉同图比较没有发现剩余 P0、P1 或 P2 问题。

## Implementation Checklist

- [x] 领域测试、角色访问测试和 Sites Worker 测试通过。
- [x] Vite 生产构建通过。
- [x] 官方 Playwright CLI Chromium 四角色关键流程通过。
- [x] 无 mock route、无 API 请求、console errors/warnings 为 0。
- [x] 参考图与实现图同视口并排比较完成。
- [x] 原型边界、字段、入口和业务分支文档已更新。

## Open Questions

- 正式实现仍需确认真实任务实例、支部范围、字典接口、文件安全与 OBS、ZIP 生成、通知渠道、扣分权限和审计契约；这些不阻塞本次纯前端原型。

final result: passed
