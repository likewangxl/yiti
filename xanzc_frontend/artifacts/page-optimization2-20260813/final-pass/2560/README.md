# 页面优化2：2560×1440 最终验收

结论：**PASS**，基于 `HEAD 4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4` 的全新官方 Playwright CLI Chromium 会话。

- 59/59 普通命名路由通过；唯一 Header 内唯一 WorkspaceTabs，无旧独立空行，Sidebar `220 → 64 → 220`。
- 55 个真实累积页签下，左右键/Home/End、滚入、当前页签鼠标/Enter 关闭、普通导航、后台关闭、主动移焦、连续关闭意图全部通过。
- 静态矩阵 58 个物理操作列、30 个自适应多操作列；重点 8 页与 Scheme 共 146 个真实自适应行实例。7 个有数据重点页和 Scheme 完成 `expanded → synthetic-layout-only compact → expanded`，动作顺序、danger、disabled、测量探针和宽度关系一致；危险确认只取消，0 写。ReportSql 的操作列依赖条件性下载任务，当前数据无运行时行，未伪造业务数据。
- designer 固定命名窗口复用/聚焦、主窗口不导航且 tab 不变、子窗无主壳且工具齐、clean 返回、直达 fallback、本地 dirty 的取消/放弃、beforeunload 全部通过；未点击保存、发布、回滚、服务端放弃或任何 CAS 写入口。
- 起讫 route-list 均为 `No active routes`；仅登录有 1 次允许的 POST，业务写 0；API 失败、console error、fallback、ResizeObserver loop 均为 0。

核心证据：

- `json/routes-59-shell-actions.json`
- `json/tabs-focus-intents.json`
- `json/row-actions-focus-scheme.json`
- `json/designer-readonly.json`
- `json/qa-summary.json`
- `redaction-audit.json`
- `manifest.sha256`

## 证据归档安全复核

本次证据包在不重跑产品流程、不编辑图片的前提下，对原有 **25 张 PNG 逐张进行了人工目视检查**。PNG 未使用自动内容扫描，也不声称自动扫描能够证明图片安全。

发现并直接删除 10 张包含验收非必要真实业务内容的原始截图：

- `73-designer-direct-url-fallback-workspace.png`：工作台通知正文、机构和业务编号。
- `Workspace.png`：工作台公告/通知正文、机构和业务编号。
- `shell-collapsed.png`：工作台公告/通知正文、机构和业务编号。
- `SysUsers.png`：真实机构树。
- `focus-SysUsers.png`：真实机构树。
- `tabs-focus-intents-pass.png`：真实机构树。
- `danger-confirm-cancel.png`：真实产品、部门、员工编号和姓名。
- `ReportSql.png`：真实 SQL、表名和字段名。
- `72-designer-independent-popup.png`：真实大屏名称以及角色/机构范围。
- `74-designer-dirty-three-branches.png`：真实大屏名称以及角色/机构范围。

保留 15 张安全截图，仅用于以下必要验收用途；为保持验收语义，保留图仍可能显示页面日期和列表聚合计数，但不含业务行正文、真实实体 ID、客户明细或具体机构树：

- `Login.png`：空白登录控件，不含凭据。
- `EvalTasks.png`、`InfoProducts.png`、`ScreenAdminDs.png`、`SysJobs.png`、`SysNotifications.png`、`SysResources.png`：页面壳层、表头、空态/加载遮罩和控件结构。
- `focus-EvalTasks.png`、`focus-InfoProducts.png`、`focus-ReportSql.png`、`focus-ScreenAdminDs.png`、`focus-SysJobs.png`、`focus-SysNotifications.png`、`focus-SysResources.png`：业务单元格被加载遮罩覆盖后的操作列结构。
- `scheme-row-actions.png`：空查询方案弹窗与页面控件结构。

部分保留的已登录主平台截图会显示“系统管理员 / 西安分行”。这是用户授权使用的 normal admin QA 通用测试身份标签，用于证明登录后壳层与交互状态；归档未包含密码、用户 ID、token、cookie，也未保留更具体的用户或机构树。因此本证据包明确记录该通用用户/机构标签已归档，不声称用户或机构标签完全未归档。

功能验收数据仍以 JSON 为准，59/59 路由、58 个物理操作列、30 个自适应操作列及各专项指标未更改。文本文件另行递归扫描；范围、候选分类和人工处置见 `redaction-audit.json`。
