# 页面优化 2 — 1920×1080 final-pass

结论：**PASS**。

- 验收提交：`4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4`
- 工具：项目内官方 `node_modules/.bin/playwright-cli` `0.1.18`，Chromium，单会话 `pageopt2finalpass1920`
- 视口：`1920 × 1080`
- 数据链路：真实 `8091 -> 18081`，normal admin；未注册 route/mock，起讫均为 `No active routes`
- 约束：只读页面、键盘、取消和放弃本地未保存状态；没有保存、发布、删除确认、导入或业务写请求

## 验收结果

1. 59 个命名路由全部完成真实动态 ID 扫描：`59/59 PASS`。console error、API fallback、ResizeObserver loop、失败 API、意外写请求均为 `0`。15 种动态菜单无匹配 warning 单列，不计产品错误。
2. 全路由运行时共观察 `556` 个可见操作单元格、`305` 个自适应 host；静态矩阵为 `58` 个物理操作列、`30` 个自适应操作列。自然数据均为 expanded（`305`），自然 compact 样本为 `0`。
3. Products 自然 expanded 显示“编辑 / 附件 / 删除”；仅对真实首行父 cell 做临时布局缩窄，得到 compact“编辑 / 更多”，随后恢复 expanded。此项明确标记为 `synthetic-layout-only`，未改源码、数据或请求。
4. Products 危险“删除”确认已打开后取消；确认框动画稳定可见，写请求 `0`。Report SQL 的“我的查询方案”对话框有 2 行真实数据，两个操作 host 均为 expanded，包含危险删除；probe 均为 `aria-hidden=true`、`inert=true`、`pointer-events:none`、`visibility:hidden`。
5. 壳层 expanded 为 Header `52px`、Sidebar `220px`；collapsed 为 `64px` 且折叠按钮居中。WorkspaceTabs 导航全局唯一且位于 Header；内容顶部 `52px`，没有旧 `48px` 空行。侧栏按钮真实键盘 Tab 可见 `2px` outline，ARIA 完整。
6. 57 个页签下 Home / End / ArrowLeft、横向滚动和完整 title 均正常。路由切换后活动页签稳定可见；鼠标点击关闭和 Enter 关闭当前页签后，`1200ms` 焦点均稳定落相邻活动页签。普通导航聚焦 main，后台页签关闭保持当前页签焦点，用户主动移焦不会被异步恢复抢走。
7. 设计器侧栏点击打开固定名 `yiti-screen-designer` 独立 popup，主窗口 URL/页签不变；popup 无 Sidebar/Header/WorkspaceTabs，全部工具与返回按钮存在。重复点击复用并聚焦同一窗口。无 dirty 返回关闭、直接 URL 无 opener 回退 workspace、本地 canvasStyle dirty 的 beforeunload、三按钮对话框、取消回焦、放弃关闭均通过；保存 endpoint 调用 `0`、全部写请求 `0`。

## 原始瞬时采样说明

`json/shell-tabs-actions.json` 在生成大量页签后立即采样到一次 `activeVisible=false`，并据此保留原始 `fatalIssue.tabs-keyboard-focus`。该瞬时结果没有被改写；后续稳定时序证据 `json/tab-focus-diagnostic.json` 记录 microtask、双 rAF、100/600/1200ms，确认活动页签始终稳定可见，并确认两种关闭路径焦点均稳定落相邻页签。因此该原始瞬时采样由专项稳定诊断取代，不构成产品失败。

## 单列 warning

- 动态菜单：15 种不同的 Vue Router “No match found” warning，59 路由扫描共 855 个事件；属于当前动态菜单注册过程的已知噪声，单独计数。
- 设计器直接 URL：浏览器标准 warning `Scripts may close only the windows that were opened by them.` 共 1 条；随后应用按契约回退到 workspace。
- 最终官方 CLI 控制台：Errors `0`。

## 未执行 / 未自然覆盖

- 没有自然 compact 行，因此 compact 仅有 `synthetic-layout-only` 视觉布局诊断，不冒充真实数据状态。
- 当前真实页签标题没有触发长标题截断（`longTruncatedCount=0`）；完整 `title` 属性和 57 页签横向滚动已覆盖。
- 按只读边界未执行设计器保存成功、CAS 冲突、发布、真正删除、导入等写分支；这些不计本次失败。

## 证据归档安全复核

本次在不重跑产品流程、不编辑图片的前提下，对原始 **83 张 PNG 全部逐张进行了人工目视检查**。PNG 没有使用自动内容扫描，也不声称文本扫描能够证明图片安全。

直接删除 25 张包含验收非必要真实业务内容的原始截图：

- `03-Workspace.png`、`05-AnnouncementDetail.png`、`07-InfoNav.png`：公告/通知正文、业务编号或内部导航数据。
- `11-PerfMetrics.png`、`14-PerfTargetValues.png`、`17-PerfCompute.png`、`18-PerfTaskMonitor.png`：真实指标、目标值、计算/任务聚合数据。
- `26-ReportDash.png`、`27-ReportPresets.png`、`29-ReportFreeDetail.png`、`32-ReportAmasApprovalDetail.png`：报表数值、内部接口、业务详情或审批数据。
- `35-ScreenAdminOrgGroups.png`、`42-SysUsers.png`、`focus-SysUsers.png`、`45-SysPermission.png`、`46-SysDict.png`、`51-SysConfig.png`：机构树、人员、权限、字典或系统配置数据。
- `55-SysAnnouncementDetail.png`、`57-SysWorkflowFlowEdit.png`：公告正文或真实流程配置。
- `69-ordinary-navigation-focus-main.png`、`71-user-focus-not-stolen.png`、`72-designer-independent-popup.png`、`73-designer-direct-url-fallback-workspace.png`、`74-designer-dirty-three-branches.png`、`75-sidebar-keyboard-focus-visible.png`：焦点/设计器场景中同时出现的真实流程名、工作台内容、角色/机构范围或其他业务数据。

保留 58 张截图，仅用于空白登录/无权限页、空表、加载遮罩后的表格与操作列、通用控件、壳层布局、页签焦点、危险确认取消和查询方案结构等必要验收用途。为保持验收语义，保留图仍可能显示页面日期和列表聚合计数；不含业务行正文、真实实体 ID、客户明细或具体机构树。完整保留与删除清单、人工审查范围见 `redaction-audit.json`。

部分保留的已登录主平台截图会显示“系统管理员 / 西安分行”。这是用户授权使用的 normal admin QA 通用测试身份标签，用于证明登录后壳层与交互状态；归档未包含密码、用户 ID、token、cookie，也未保留更具体的用户或机构树。因此本证据包明确记录该通用用户/机构标签已归档，不声称用户或机构标签完全未归档。

文本文件递归扫描后，机械替换了 `scan-59-and-actions.json` 中 20 个 KPI scheme 动态 ID、1 个指标代码路径段，以及 `tab-focus-extended.json` 中 1 个流程代码和 1 个流程名称。59/59 路由、58 个物理操作列、30 个自适应操作列、状态和 DOM 指标均未改变。

## 证据索引

- `json/scan-59-and-actions.json`：59 路由、DOM、操作列、console、请求/响应原始结构化记录
- `json/shell-tabs-actions.json`：壳层、页签键盘、重点操作列、Scheme、危险取消、synthetic compact
- `json/tab-focus-diagnostic.json`：活动页签滚动与关闭焦点稳定时序
- `json/tab-focus-extended.json`：普通导航 / 后台关闭 / 用户移焦仲裁
- `json/sidebar-keyboard-focus.json`：真实键盘 Tab 与 focus-visible
- `json/designer-readonly.json`：设计器窗口、dirty/return/beforeunload、console 与真实请求摘要
- `json/danger-cancel-visual.json`：危险确认稳定可见及取消零写
- `screenshots/`：58 张经人工逐图复核后保留的安全截图
- `redaction-audit.json`：83 张 PNG 的人工审查范围、25 张删除清单、58 张保留清单及文本扫描结果
- `scripts/`：7 个官方 CLI `run-code` 原始脚本
- `raw/`：命令、route-list、console、请求摘要、服务门禁
- `SHA256SUMS`：本目录除自身外的全部证据 SHA-256
