# 红色引擎任务流三角色验收证据

执行日期：2026-09-01（Asia/Shanghai）

本目录归档本轮对红色引擎非四大维度任务链路的官方 `playwright-cli` 验收证据，包含临时任务和定时普通任务。验收范围是报送员 `rm_zhang`、支部书记 `wangw67`、组织审核员 `yangdb1` 三个角色；四大维度材料上报按用户明确要求未测。

## 结论

本轮非四大维度任务主链路已通过真实页面和真实后端请求验证：

- 组织审核员可以新增、发布临时任务和定时普通任务；任务管理列表、任务详情、支部填报明细和明细字典请求正常。
- 报送员在上报信息的待处理页签填报并提交后进入支部审核；任务说明中的网页链接按可点击链接展示；不要求附件的任务不显示上传要求。
- 定时普通任务 `E2E-RECURRING-GENERAL-20260901` 的周期选项包含每月末、每月初、每周末、每周初、每季度末、每季度初；5 天窗口可发布，报送员详情显示“定时任务填报/任务性质定时任务”，审核列表显示“普通任务/定时任务/每月初”。
- 支部书记可以在支部审核工作台独立执行“通过”与“提交组织审核”，也可以填写意见驳回；支部审核工作台已承接原任务处理入口。
- 组织审核员可以通过或驳回临时任务；驳回意见为空时页面不发送请求，填写意见后驳回请求返回 `200 OK`。
- 支部驳回后，报送员复用原任务分配重新提交；重提后任务再次进入支部审核并可流转到组织审核。
- 定时普通任务 task5 的完整链路 `SUBMIT → APPROVE_BRANCH → SUBMIT_TO_ORG → APPROVE_ORG` 已通过，最终状态为 `APPROVED`；任务性质、周期和审核卡片均不再误标为临时任务。
- 最终通过任务从报送员待办中去除；最终驳回任务回退报送员端并保留后续重提路径。审核中/已通过/已驳回页签的临时任务展示规则已在页面中核对。
- 三角色最终 `route-list` 均为 `No active routes`；最终 console 的 Errors 均为 `0`，仅保留已有 Vue Router/Element Plus warnings，未将 warning 误报为功能错误。

## 本轮发现并修复

重提链路曾出现队列版本选择缺陷：报送员复用原任务分配重新提交后，支部审核队列可能仍按旧提交版本读取，导致重提任务不能稳定回到待处理队列。本轮由提交 `b8b6e5de` 修复并通过“支部驳回 → 报送员重提 → 支部再次通过并提交组织审核”的真实流程验证。

本轮早期填报提交还暴露过动作幂等码长度超出数据库字段的问题；当前运行版本已修复，最终提交与重提请求均返回 `200 OK`。该问题不改变本次证据的业务结论。

定时普通任务详情曾硬编码显示为临时任务，报送员详情由提交 `1f9bf9ab` 修复；支部和组织审核工作台曾误标任务性质，分别由提交 `a91f6369` 修复。最终 CLI 复验已确认详情、列表和审核卡片显示定时任务/普通任务及每月初周期。

## 页面与权限结论

- 报送员首页展示所在机构得分、排名，不展示全员达标率；上报信息合并任务处理并提供待处理、审核中、已通过、已驳回页签。
- 支部书记不展示四大维度材料上报、上报信息；仅使用支部审核工作台处理任务，支部驳回已实际验证。
- 组织审核员沿用工作台，工作台支持“四大维度材料上报/临时任务”筛选；任务管理替代原年度考核归档和数据导出菜单。
- 红黄牌预警池三个角色均可进入；当前红牌、黄牌测试数据为空。组织管理员专属逾期上报待执行扣分板块不在本轮三个账号范围内。

## 证据边界

- 当前环境没有 OBS。附件真实上传、附件在线下载、导出 ZIP/Excel/附件目录均未做真实联调。
- 任务导出仅验证 `POST` 创建成功、返回排队状态和状态轮询；隔离测试配置关闭异步 worker，未生成可下载文件。
- 四大维度材料上报及其多次上传、字典明细选择后的真实导出未测。
- 红黄牌自动统计未构造连续季度排名数据，因此只核对入口、页面和接口可用性。
- 本目录只记录新增任务功能验收，不审计或修改既有页面缺陷。

## 截图清单

已保留既有角色截图：

- `screenshots/rm_zhang-dashboard.png`
- `screenshots/rm_zhang-records.png`
- `screenshots/rm_zhang-warning.png`
- `screenshots/wangw67-dashboard.png`
- `screenshots/wangw67-branch-review.png`
- `screenshots/wangw67-warning.png`
- `screenshots/yangdb1-login.png`
- `screenshots/yangdb1-home-menu.png`
- `screenshots/yangdb1-workbench.png`
- `screenshots/yangdb1-warning-before.png`

本轮新增的六张根目录终态/过程截图保持原位置，不移动：

- `secretary-after-branch-reject.png`：支部驳回后工作台状态
- `secretary-resubmit-to-org.png`：报送员重提后支部再次提交组织审核
- `org-after-final-approve.png`：组织审核通过后的工作台
- `org-final-reject.png`：组织审核驳回（意见必填）
- `reporter-final-rejected.png`：报送员端最终驳回结果
- `org-task-detail-submission.png`：组织审核员任务详情及支部填报信息

本轮新增的定时普通任务截图也保持在证据目录根部：

- `reporter-recurring-reviewing.png`：报送员审核中列表中的定时普通任务
- `reporter-recurring-passed.png`：报送员已通过列表中的定时普通任务
- `org-recurring-review.png`：组织审核工作台中的定时普通任务

## 文件索引

- 命令与流程：`commands/round2-e2e.md`
- 最终请求摘要：`network/round2-reporter-final.network.txt`、`network/round2-secretary-final.network.txt`、`network/round2-org-final.network.txt`
- 最终 mock 路由核验：`routes/round2-reporter-final.route-list.txt`、`routes/round2-secretary-final.route-list.txt`、`routes/round2-org-final.route-list.txt`
- 最终 console 摘要：`console/round2-reporter-final.console.txt`、`console/round2-secretary-final.console.txt`、`console/round2-org-final.console.txt`
- 早期只读阶段的原始 console/network 文件仍保留，不能替代上述最终三会话摘要。
