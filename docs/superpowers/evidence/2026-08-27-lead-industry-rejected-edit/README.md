# 线索行业字典、退回重提与隐藏编号验收

## 验收边界

- 日期：2026-08-27
- 页面：`/customers/leads/new`、`/customers/leads/approval`
- 浏览器：官方 `playwright-cli`，Chromium
- 会话：`lead-dict-rejected-20260827`
- mock route：无
- 数据写入：无；仅查看真实已退回线索并打开编辑抽屉，未保存、未重新提交、未修改字典。

## 执行命令摘要

```bash
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 open http://127.0.0.1:8090
# 通过真实 /api/auth/login 建立本地开发会话，凭据不归档
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 goto http://127.0.0.1:8090/#/customers/leads/new
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 snapshot
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 click <编辑并重新提交>
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 click <取消>
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 fill <关键词> 测试客户1
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 click <已退回卡片>
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 click <重置>
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 goto http://127.0.0.1:8090/#/customers/leads/approval
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 click <已退回卡片>
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 route-list
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 requests --filter '/api/sys/dicts/INDUSTRY/items'
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 response-body 11
npx --no-install playwright-cli -s=lead-dict-rejected-20260827 console
```

## 真实页面结果

- 线索录入表头为：企业名称、统一社会信用代码、线索来源、录入人、录入时间、状态、操作；不再展示线索编号。
- 筛选栏可见“重置”。输入关键词并选中“已退回”后点击重置，关键词清空，四张状态卡 `aria-pressed` 均为 `false`。
- 真实数据存在 1 条已退回线索，行操作显示“编辑并重新提交”。打开抽屉后底部显示“已退回线索修改后可重新提交审批”和“重新提交审批”；本次未触发保存或提交。
- 线索审批已退回列表表头不含线索编号；行业编码 `EDU` 展示为中文“教育”。
- 审批页查询框提示为“客户名称 / 统一社会信用代码 / 提交人”，不再提示线索编号。

## 字典与网络证据

`route-list`：

```text
No active routes
```

切换审批卡片后，真实字典请求再次发出：

```text
8.  GET /api/sys/dicts/INDUSTRY/items => 200
9.  GET /api/sys/dicts/INDUSTRY/items => 200
11. GET /api/sys/dicts/INDUSTRY/items => 200
```

请求 11 的真实响应包含：

```json
{"dictType":"INDUSTRY","dictCode":"EDU","dictLabel":"教育","status":"ACTIVE"}
```

这证明审批列表使用 `SYS_DICT` 的启用项，并在列表查询/卡片切换时重新获取字典。

## Console

```text
[ERROR] /api/auth/current-user => 401
[WARNING] No match found for location with path "/bizexec/supports"
```

401 发生在建立开发会话之前；登录后本轮涉及的当前用户、线索列表、审批历史和行业字典请求均为 200。`/bizexec/supports` 为现有菜单路由告警，与本轮改动无关。

## 截图

- `lead-entry-rejected-edit.png`
- `lead-approval-industry-chinese.png`
